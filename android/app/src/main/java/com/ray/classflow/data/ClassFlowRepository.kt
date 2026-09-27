package com.ray.classflow.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.ray.classflow.data.db.AgendaEntity
import com.ray.classflow.data.db.AgendaLinkEntity
import com.ray.classflow.data.db.AgendaWithLinks
import com.ray.classflow.data.db.ClassFlowDatabase
import com.ray.classflow.data.db.CourseEntity
import com.ray.classflow.data.db.PendingMutationEntity
import com.ray.classflow.data.db.TimetableSlotEntity
import com.ray.classflow.model.Account
import com.ray.classflow.model.AgendaItem
import com.ray.classflow.model.AgendaStatus
import com.ray.classflow.model.AgendaType
import com.ray.classflow.model.ClassFlowState
import com.ray.classflow.model.Course
import com.ray.classflow.model.SyncState
import com.ray.classflow.model.TimetableSlot
import com.ray.classflow.model.Validation
import com.ray.classflow.notification.ReminderScheduler
import com.ray.classflow.sync.ApiAgenda
import com.ray.classflow.sync.ApiCourse
import com.ray.classflow.sync.ApiMutation
import com.ray.classflow.sync.ApiSlot
import com.ray.classflow.sync.ApiState
import com.ray.classflow.sync.CredentialStore
import com.ray.classflow.sync.LoginSession
import com.ray.classflow.sync.NextcloudApi
import com.ray.classflow.sync.SyncWorker
import com.ray.classflow.widget.ClassFlowWidget
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ClassFlowRepository(
    private val context: Context,
    private val database: ClassFlowDatabase,
    private val credentials: CredentialStore,
    private val api: NextcloudApi,
    private val gson: Gson = Gson(),
) {
    private val dao = database.dao()

    val state: Flow<ClassFlowState> = combine(
        dao.observeCourses(),
        dao.observeSlots(),
        dao.observeAgenda(),
        dao.observePendingCount(),
        dao.observeConflictCount(),
    ) { courses, slots, agenda, pending, conflicts ->
        ClassFlowState(
            courses = courses.map { it.toModel() },
            slots = slots.map { it.toModel() },
            agenda = agenda.map { it.toModel() },
            pendingChanges = pending,
            conflicts = conflicts,
        )
    }

    fun account(): Account? = credentials.read()

    fun pendingLogin(): LoginSession? = credentials.readPendingLogin()

    fun clearPendingLogin() = credentials.clearPendingLogin()

    suspend fun beginLogin(serverUrl: String): LoginSession = api.beginLogin(serverUrl).also {
        credentials.writePendingLogin(it)
    }

    suspend fun finishLogin(session: LoginSession): Account {
        val account = api.awaitLogin(session)
        credentials.write(account)
        credentials.clearPendingLogin()
        schedulePeriodicSync()
        performSync()
        return account
    }

    suspend fun logout() {
        credentials.clear()
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SYNC)
    }

    suspend fun saveCourse(course: Course) {
        Validation.course(course)?.let { throw IllegalArgumentException(it) }
        val now = System.currentTimeMillis()
        val pending = course.copy(syncState = SyncState.PENDING)
        database.withTransaction {
            dao.upsertCourse(pending.toEntity(now))
            queue("course", course.id, "upsert", course.version, gson.toJson(ApiCourse(
                id = course.id,
                name = course.name.trim(),
                teacher = course.teacher.trim(),
                room = course.room.trim(),
                colorKey = course.colorKey,
                notes = course.notes.trim(),
                version = course.version,
                updatedAt = now,
            )))
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun deleteCourse(course: Course) {
        database.withTransaction {
            dao.deleteCourse(course.id)
            queue("course", course.id, "delete", course.version, null)
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun saveSlot(slot: TimetableSlot) {
        Validation.slot(slot)?.let { throw IllegalArgumentException(it) }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertSlot(slot.copy(syncState = SyncState.PENDING).toEntity(now))
            queue("slot", slot.id, "upsert", slot.version, gson.toJson(ApiSlot(
                id = slot.id,
                courseId = slot.courseId,
                dayOfWeek = slot.dayOfWeek,
                startMinutes = slot.startMinutes,
                endMinutes = slot.endMinutes,
                roomOverride = slot.roomOverride.trim(),
                version = slot.version,
                updatedAt = now,
            )))
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun deleteSlot(slot: TimetableSlot) {
        database.withTransaction {
            dao.deleteSlot(slot.id)
            queue("slot", slot.id, "delete", slot.version, null)
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun saveAgenda(item: AgendaItem) {
        Validation.agenda(item)?.let { throw IllegalArgumentException(it) }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertAgenda(item.copy(syncState = SyncState.PENDING).toEntity(now))
            dao.deleteAgendaLinks(item.id)
            dao.upsertAgendaLinks(item.linkedSlotIds.map { AgendaLinkEntity(item.id, it) })
            queue("agenda", item.id, "upsert", item.version, gson.toJson(ApiAgenda(
                id = item.id,
                type = item.type.name.lowercase(),
                title = item.title.trim(),
                occursAt = item.occursAt,
                endsAt = item.endsAt,
                allDay = item.allDay,
                status = item.status.name.lowercase(),
                notes = item.notes.trim(),
                reminderAt = item.reminderAt,
                linkedSlotIds = item.linkedSlotIds,
                version = item.version,
                updatedAt = now,
            )))
        }
        ReminderScheduler.schedule(context, item)
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun deleteAgenda(item: AgendaItem) {
        database.withTransaction {
            dao.deleteAgenda(item.id)
            queue("agenda", item.id, "delete", item.version, null)
        }
        ReminderScheduler.cancel(context, item.id)
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun setAgendaCompleted(item: AgendaItem, completed: Boolean) {
        saveAgenda(item.copy(status = if (completed) AgendaStatus.COMPLETED else AgendaStatus.PENDING))
    }

    suspend fun performSync() {
        val account = credentials.read() ?: return
        val pending = dao.pendingMutations()
        val result = if (pending.isEmpty()) {
            com.ray.classflow.sync.SyncResult(api.getState(account))
        } else {
            api.sync(account, pending.map { mutation ->
                ApiMutation(
                    localId = mutation.localId,
                    operationId = mutation.operationId,
                    entityType = mutation.entityType,
                    entityId = mutation.entityId,
                    operation = mutation.operation,
                    baseVersion = mutation.baseVersion,
                    payload = mutation.payload?.let(JsonParser::parseString),
                )
            })
        }

        database.withTransaction {
            val acceptedIds = pending.filter { it.operationId in result.acceptedOperationIds }.map { it.localId }
            if (acceptedIds.isNotEmpty()) dao.deleteMutations(acceptedIds)
            result.conflicts.forEach { conflict ->
                pending.firstOrNull { it.operationId == conflict.operationId }?.let {
                    dao.setMutationConflict(it.localId, gson.toJson(conflict.server))
                }
            }
            replaceFromServer(result.state, dao.pendingMutations())
        }
        ReminderScheduler.rescheduleAll(context, dao.allAgenda().map { it.toModel() })
        ClassFlowWidget.updateAll(context)
    }

    fun scheduleImmediateSync() {
        if (credentials.read() == null) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_SYNC, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodicSync() {
        if (credentials.read() == null) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private suspend fun queue(type: String, id: String, operation: String, baseVersion: Long, payload: String?) {
        dao.insertMutation(PendingMutationEntity(
            operationId = UUID.randomUUID().toString(),
            entityType = type,
            entityId = id,
            operation = operation,
            baseVersion = baseVersion,
            payload = payload,
            createdAt = System.currentTimeMillis(),
        ))
    }

    private suspend fun replaceFromServer(state: ApiState, pending: List<PendingMutationEntity>) {
        val pendingUpserts = pending.filter { it.operation == "upsert" }.map { it.entityId }.toSet()
        val localCourses = dao.allCourses().filter { it.id in pendingUpserts }
        val localSlots = dao.allSlots().filter { it.id in pendingUpserts }
        val localAgenda = dao.allAgenda().filter { it.agenda.id in pendingUpserts }

        dao.clearLinks()
        dao.clearAgenda()
        dao.clearSlots()
        dao.clearCourses()

        dao.upsertCourses(state.courses.map { it.toEntity() })
        dao.upsertSlots(state.slots.map { it.toEntity() })
        dao.upsertAgendaItems(state.agendaItems.map { it.toEntity() })
        dao.upsertAgendaLinks(state.agendaItems.flatMap { item ->
            item.linkedSlotIds.map { AgendaLinkEntity(item.id, it) }
        })

        dao.upsertCourses(localCourses)
        dao.upsertSlots(localSlots)
        dao.upsertAgendaItems(localAgenda.map { it.agenda })
        dao.upsertAgendaLinks(localAgenda.flatMap { row -> row.slots.map { AgendaLinkEntity(row.agenda.id, it.id) } })
    }

    private companion object {
        const val IMMEDIATE_SYNC = "classflow-immediate-sync"
        const val PERIODIC_SYNC = "classflow-periodic-sync"
    }
}

private fun CourseEntity.toModel() = Course(
    id, name, teacher, room, colorKey, notes, version, SyncState.valueOf(syncState),
)

private fun TimetableSlotEntity.toModel() = TimetableSlot(
    id, courseId, dayOfWeek, startMinutes, endMinutes, roomOverride, version, SyncState.valueOf(syncState),
)

private fun AgendaWithLinks.toModel() = AgendaItem(
    id = agenda.id,
    type = AgendaType.valueOf(agenda.type),
    title = agenda.title,
    occursAt = agenda.occursAt,
    endsAt = agenda.endsAt,
    allDay = agenda.allDay,
    status = AgendaStatus.valueOf(agenda.status),
    notes = agenda.notes,
    reminderAt = agenda.reminderAt,
    linkedSlotIds = slots.map { it.id },
    version = agenda.version,
    syncState = SyncState.valueOf(agenda.syncState),
)

private fun Course.toEntity(now: Long) = CourseEntity(
    id, name.trim(), teacher.trim(), room.trim(), colorKey, notes.trim(), version, now, syncState.name,
)

private fun TimetableSlot.toEntity(now: Long) = TimetableSlotEntity(
    id, courseId, dayOfWeek, startMinutes, endMinutes, roomOverride.trim(), version, now, syncState.name,
)

private fun AgendaItem.toEntity(now: Long) = AgendaEntity(
    id, type.name, title.trim(), occursAt, endsAt, allDay, status.name, notes.trim(), reminderAt,
    version, now, syncState.name,
)

private fun ApiCourse.toEntity() = CourseEntity(
    id, name, teacher, room, colorKey, notes, version, updatedAt, SyncState.SYNCED.name,
)

private fun ApiSlot.toEntity() = TimetableSlotEntity(
    id, courseId, dayOfWeek, startMinutes, endMinutes, roomOverride, version, updatedAt, SyncState.SYNCED.name,
)

private fun ApiAgenda.toEntity() = AgendaEntity(
    id = id,
    type = type.uppercase(),
    title = title,
    occursAt = occursAt,
    endsAt = endsAt,
    allDay = allDay,
    status = status.uppercase(),
    notes = notes,
    reminderAt = reminderAt,
    version = version,
    updatedAt = updatedAt,
    syncState = SyncState.SYNCED.name,
)
