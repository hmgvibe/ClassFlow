package com.ray.classflow.data

import com.ray.classflow.i18n.UiText

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
import com.ray.classflow.BuildConfig
import com.ray.classflow.data.db.AgendaEntity
import com.ray.classflow.data.db.AgendaLinkEntity
import com.ray.classflow.data.db.AgendaWithLinks
import com.ray.classflow.data.db.ClassFlowDatabase
import com.ray.classflow.data.db.CourseEntity
import com.ray.classflow.data.db.PendingMutationEntity
import com.ray.classflow.data.db.StudyPlanEntity
import com.ray.classflow.data.db.TimetableSlotEntity
import com.ray.classflow.model.Account
import com.ray.classflow.model.AgendaItem
import com.ray.classflow.model.AgendaStatus
import com.ray.classflow.model.AgendaType
import com.ray.classflow.model.ClassFlowState
import com.ray.classflow.model.Course
import com.ray.classflow.model.StudyPlan
import com.ray.classflow.model.SyncState
import com.ray.classflow.model.TimetableSlot
import com.ray.classflow.model.Validation
import com.ray.classflow.notification.ReminderScheduler
import com.ray.classflow.sync.ApiAgenda
import com.ray.classflow.sync.ApiCourse
import com.ray.classflow.sync.ApiMutation
import com.ray.classflow.sync.ApiSlot
import com.ray.classflow.sync.ApiState
import com.ray.classflow.sync.ApiStudyPlan
import com.ray.classflow.sync.ConflictChoice
import com.ray.classflow.sync.ConflictItem
import com.ray.classflow.sync.CredentialStore
import com.ray.classflow.sync.LoginSession
import com.ray.classflow.sync.NextcloudApi
import com.ray.classflow.sync.SyncWorker
import com.ray.classflow.sync.coalesceMutations
import com.ray.classflow.sync.conflictItems
import com.ray.classflow.sync.mergePendingState
import com.ray.classflow.sync.payloadFor
import com.ray.classflow.sync.resolvedMutation
import com.ray.classflow.widget.ClassFlowWidget
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClassFlowRepository(
    private val context: Context,
    private val database: ClassFlowDatabase,
    private val credentials: CredentialStore,
    private val api: NextcloudApi,
    private val gson: Gson = Gson(),
    private val syncEnabled: Boolean = BuildConfig.CLOUD_SYNC_ENABLED,
) {
    private val dao = database.dao()
    private val syncMutex = Mutex()

    val conflicts: Flow<List<ConflictItem>> =
        combine(
            dao.observeMutations(),
            dao.observeCourses(),
            dao.observeSlots(),
            dao.observeAgenda(),
        ) { mutations, courses, slots, agenda ->
            conflictItems(mutations, courses, slots, agenda)
        }

    val state: Flow<ClassFlowState> =
        combine(
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
            .combine(dao.observeStudyPlans()) { current, plans ->
                current.copy(studyPlans = plans.map { it.toModel() })
            }

    fun account(): Account? = if (syncEnabled) credentials.read() else null

    fun pendingLogin(): LoginSession? = if (syncEnabled) credentials.readPendingLogin() else null

    fun clearPendingLogin() = credentials.clearPendingLogin()

    suspend fun beginLogin(serverUrl: String): LoginSession {
        check(syncEnabled) { UiText.TEXT_EC0B942262.text() }
        return api.beginLogin(serverUrl).also {
            credentials.writePendingLogin(it)
        }
    }

    suspend fun finishLogin(session: LoginSession): Account {
        check(syncEnabled) { UiText.TEXT_EC0B942262.text() }
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

    suspend fun saveCourse(course: Course) = syncMutex.withLock {
        Validation.course(course)?.let { throw IllegalArgumentException(it.text()) }
        val now = System.currentTimeMillis()
        val pending = course.copy(syncState = SyncState.forLocalWrite(syncEnabled))
        database.withTransaction {
            dao.upsertCourse(pending.toEntity(now))
            queue(
                "course",
                course.id,
                "upsert",
                course.version,
                gson.toJson(
                    ApiCourse(
                        id = course.id,
                        name = course.name.trim(),
                        teacher = course.teacher.trim(),
                        room = course.room.trim(),
                        colorKey = course.colorKey,
                        notes = course.notes.trim(),
                        version = course.version,
                        updatedAt = now,
                    )
                ),
            )
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun deleteCourse(course: Course) = syncMutex.withLock {
        database.withTransaction {
            dao.deleteCourse(course.id)
            queue("course", course.id, "delete", course.version, null)
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun saveSlot(slot: TimetableSlot) = syncMutex.withLock {
        Validation.slot(slot)?.let { throw IllegalArgumentException(it.text()) }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertSlot(
                slot.copy(syncState = SyncState.forLocalWrite(syncEnabled)).toEntity(now)
            )
            queue(
                "slot",
                slot.id,
                "upsert",
                slot.version,
                gson.toJson(
                    ApiSlot(
                        id = slot.id,
                        courseId = slot.courseId,
                        dayOfWeek = slot.dayOfWeek,
                        startMinutes = slot.startMinutes,
                        endMinutes = slot.endMinutes,
                        roomOverride = slot.roomOverride.trim(),
                        version = slot.version,
                        updatedAt = now,
                    )
                ),
            )
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun deleteSlot(slot: TimetableSlot) = syncMutex.withLock {
        database.withTransaction {
            dao.deleteSlot(slot.id)
            queue("slot", slot.id, "delete", slot.version, null)
        }
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun saveAgenda(item: AgendaItem) = syncMutex.withLock {
        Validation.agenda(item)?.let { throw IllegalArgumentException(it.text()) }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertAgenda(
                item.copy(syncState = SyncState.forLocalWrite(syncEnabled)).toEntity(now)
            )
            dao.deleteAgendaLinks(item.id)
            dao.upsertAgendaLinks(item.linkedSlotIds.map { AgendaLinkEntity(item.id, it) })
            queue(
                "agenda",
                item.id,
                "upsert",
                item.version,
                gson.toJson(
                    ApiAgenda(
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
                    )
                ),
            )
        }
        ReminderScheduler.schedule(context, item)
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun deleteAgenda(item: AgendaItem) = syncMutex.withLock {
        database.withTransaction {
            dao.deleteAgenda(item.id)
            queue("agenda", item.id, "delete", item.version, null)
        }
        ReminderScheduler.cancel(context, item.id)
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    suspend fun setAgendaCompleted(item: AgendaItem, completed: Boolean) {
        saveAgenda(
            item.copy(status = if (completed) AgendaStatus.COMPLETED else AgendaStatus.PENDING)
        )
    }

    suspend fun performSync() = syncMutex.withLock {
        if (!syncEnabled) return@withLock true
        val account = credentials.read() ?: return@withLock true
        normalizeQueue()
        val queued = dao.pendingMutations().filter { it.conflictServerPayload == null }
        val snapshot = if (queued.any { it.entityType == "study" }) api.getState(account) else null
        val unsupportedStudies = snapshot != null && snapshot.studyPlans == null
        val pending = queued.filter { !unsupportedStudies || it.entityType != "study" }.take(200)
        val result =
            if (pending.isEmpty()) {
                com.ray.classflow.sync.SyncResult(snapshot ?: api.getState(account))
            } else {
                api.sync(
                    account,
                    pending.map { mutation ->
                        ApiMutation(
                            localId = mutation.localId,
                            operationId = mutation.operationId,
                            entityType = mutation.entityType,
                            entityId = mutation.entityId,
                            operation = mutation.operation,
                            baseVersion = mutation.baseVersion,
                            payload = mutation.payload?.let(JsonParser::parseString),
                        )
                    },
                )
            }

        database.withTransaction {
            val acceptedIds =
                pending.filter { it.operationId in result.acceptedOperationIds }.map { it.localId }
            if (acceptedIds.isNotEmpty()) dao.deleteMutations(acceptedIds)
            result.conflicts.forEach { conflict ->
                pending
                    .firstOrNull { it.operationId == conflict.operationId }
                    ?.let {
                        val server =
                            if (conflict.error != null)
                                result.state.payloadFor(it.entityType, it.entityId, gson)
                            else
                                conflict.server
                                    ?.takeUnless { payload -> payload.isJsonNull }
                                    ?.toString()
                        dao.setMutationConflict(
                            it.localId,
                            server ?: "null",
                            conflict.error ?: "conflict",
                        )
                    }
            }
            dao.pendingMutations()
                .filter { it.conflictServerPayload != null }
                .forEach { mutation ->
                    if (mutation.entityType == "study" && result.state.studyPlans == null)
                        return@forEach
                    val serverPayload =
                        result.state.payloadFor(mutation.entityType, mutation.entityId, gson)
                            ?: "null"
                    dao.setMutationConflict(
                        mutation.localId,
                        serverPayload,
                        mutation.lastError ?: "conflict",
                    )
                }
            replaceFromServer(result.state, dao.pendingMutations())
        }
        ReminderScheduler.rescheduleAll(context, dao.allAgenda().map { it.toModel() })
        ClassFlowWidget.updateAll(context)
        !unsupportedStudies
    }

    suspend fun saveStudyPlan(plan: StudyPlan) = syncMutex.withLock {
        Validation.studyPlan(plan)?.let { throw IllegalArgumentException(it.text()) }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.upsertStudyPlan(
                plan.copy(syncState = SyncState.forLocalWrite(syncEnabled)).toEntity(now)
            )
            queue("study", plan.id, "upsert", plan.version, gson.toJson(plan.toApi(now)))
        }
        scheduleImmediateSync()
    }

    suspend fun deleteStudyPlan(plan: StudyPlan) = syncMutex.withLock {
        database.withTransaction {
            dao.deleteStudyPlan(plan.id)
            queue("study", plan.id, "delete", plan.version, null)
        }
        scheduleImmediateSync()
    }

    suspend fun resolveConflict(item: ConflictItem, choice: ConflictChoice) {
        check(syncEnabled) { UiText.TEXT_8EF94D90EF.text() }
        syncMutex.withLock {
            val account = credentials.read() ?: throw IllegalStateException(UiText.TEXT_1090917027.text())
            val edits =
                dao.pendingMutations().filter {
                    it.entityType == item.mutation.entityType &&
                        it.entityId == item.mutation.entityId
                }
            val latest =
                coalesceMutations(edits).singleOrNull() ?: throw IllegalStateException(UiText.TEXT_8B3F0B7106.text())
            require(
                latest.operationId == item.mutation.operationId &&
                    latest.conflictServerPayload != null &&
                    latest.conflictServerPayload == item.mutation.conflictServerPayload &&
                    latest.lastError == item.mutation.lastError
            ) {
                UiText.TEXT_0A03F33AF5.text()
            }
            require(choice != ConflictChoice.BOTH || item.canKeepBoth) { UiText.TEXT_9902797549.text() }
            val serverState = api.getState(account)
            require(latest.entityType != "study" || serverState.studyPlans != null) {
                UiText.TEXT_F328247CE6.text()
            }
            val serverPayload = serverState.payloadFor(latest.entityType, latest.entityId, gson)
            if (
                JsonParser.parseString(latest.conflictServerPayload) !=
                    JsonParser.parseString(serverPayload ?: "null")
            ) {
                database.withTransaction {
                    dao.setMutationConflict(
                        latest.localId,
                        serverPayload ?: "null",
                        latest.lastError ?: "conflict",
                    )
                    replaceFromServer(serverState, dao.pendingMutations())
                }
                throw IllegalStateException(UiText.TEXT_FE63CF0E1D.text())
            }
            if (
                choice == ConflictChoice.SERVER &&
                    latest.entityType == "course" &&
                    serverPayload == null
            ) {
                val hasPendingSlots =
                    dao.pendingMutations().any { mutation ->
                        mutation.entityType == "slot" &&
                            mutation.operation == "upsert" &&
                            gson.fromJson(mutation.payload, ApiSlot::class.java).courseId ==
                                latest.entityId
                    }
                require(!hasPendingSlots) { UiText.TEXT_CEEDFEBB78.text() }
            }
            val replacement =
                resolvedMutation(
                    latest,
                    choice,
                    serverPayload,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                )
            database.withTransaction {
                dao.deleteMutations(edits.map { it.localId })
                replacement?.let { dao.insertMutation(it) }
                replaceFromServer(serverState, dao.pendingMutations())
            }
        }
        ReminderScheduler.rescheduleAll(context, dao.allAgenda().map { it.toModel() })
        ClassFlowWidget.updateAll(context)
        scheduleImmediateSync()
    }

    fun scheduleImmediateSync() {
        if (!syncEnabled) return
        if (credentials.read() == null) return
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(IMMEDIATE_SYNC, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodicSync() {
        if (!syncEnabled) return
        if (credentials.read() == null) return
        val request =
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    private suspend fun queue(
        type: String,
        id: String,
        operation: String,
        baseVersion: Long,
        payload: String?,
    ) {
        if (!syncEnabled) return
        val previous = dao.pendingMutations().filter { it.entityType == type && it.entityId == id }
        val conflict = previous.lastOrNull { it.conflictServerPayload != null }
        if (previous.isNotEmpty()) dao.deleteMutations(previous.map { it.localId })
        dao.insertMutation(
            PendingMutationEntity(
                operationId = UUID.randomUUID().toString(),
                entityType = type,
                entityId = id,
                operation = operation,
                baseVersion = previous.firstOrNull()?.baseVersion ?: baseVersion,
                payload = payload,
                createdAt = previous.firstOrNull()?.createdAt ?: System.currentTimeMillis(),
                conflictServerPayload = conflict?.conflictServerPayload,
                lastError = conflict?.lastError,
            )
        )
    }

    private suspend fun normalizeQueue() {
        val pending = dao.pendingMutations()
        val compact = coalesceMutations(pending)
        if (compact.size == pending.size) return
        database.withTransaction {
            dao.deleteMutations(pending.map { it.localId })
            compact.forEach { dao.insertMutation(it.copy(localId = 0)) }
        }
    }

    private suspend fun replaceFromServer(state: ApiState, pending: List<PendingMutationEntity>) {
        val compatibleState =
            state.copy(
                studyPlans =
                    state.studyPlans ?: dao.allStudyPlans().map { it.toModel().toApi(it.updatedAt) }
            )
        var merged = mergePendingState(compatibleState, pending, gson)
        // Keep parents required by unsynced slots when the cloud deleted a course.
        // The server can then report a useful conflict instead of Room losing the edit.
        val deletedCourses =
            pending
                .filter { it.entityType == "course" && it.operation == "delete" }
                .map { it.entityId }
                .toSet()
        val pendingSlotCourses =
            pending
                .filter { it.entityType == "slot" && it.operation == "upsert" }
                .map { gson.fromJson(it.payload, ApiSlot::class.java).courseId }
                .toSet()
        val missingParents =
            dao.allCourses().filter { course ->
                course.id in pendingSlotCourses &&
                    course.id !in deletedCourses &&
                    merged.courses.none { it.id == course.id }
            }
        val validCourseIds = merged.courses.map { it.id }.toSet() + missingParents.map { it.id }
        merged = merged.copy(slots = merged.slots.filter { it.courseId in validCourseIds })
        val validSlotIds = merged.slots.map { it.id }.toSet()

        dao.clearLinks()
        dao.clearAgenda()
        dao.clearSlots()
        dao.clearCourses()
        dao.clearStudyPlans()

        dao.upsertCourses(merged.courses.map { it.toEntity() })
        dao.upsertCourses(missingParents)
        dao.upsertSlots(merged.slots.map { it.toEntity() })
        dao.upsertAgendaItems(merged.agendaItems.map { it.toEntity() })
        dao.upsertStudyPlans(merged.studyPlans.orEmpty().map { it.toEntity() })
        dao.upsertAgendaLinks(
            merged.agendaItems.flatMap { item ->
                item.linkedSlotIds
                    .filter { it in validSlotIds }
                    .map { AgendaLinkEntity(item.id, it) }
            }
        )
        pending.forEach { mutation ->
            val status =
                if (mutation.conflictServerPayload == null) SyncState.PENDING.name
                else SyncState.CONFLICT.name
            when (mutation.entityType) {
                "course" ->
                    merged.courses
                        .find { it.id == mutation.entityId }
                        ?.let { dao.upsertCourse(it.toEntity().copy(syncState = status)) }
                "slot" ->
                    merged.slots
                        .find { it.id == mutation.entityId }
                        ?.let { dao.upsertSlot(it.toEntity().copy(syncState = status)) }
                "agenda" ->
                    merged.agendaItems
                        .find { it.id == mutation.entityId }
                        ?.let { dao.upsertAgenda(it.toEntity().copy(syncState = status)) }
                "study" ->
                    merged.studyPlans
                        .orEmpty()
                        .find { it.id == mutation.entityId }
                        ?.let {
                            dao.upsertStudyPlan(it.toEntity().copy(syncState = status))
                        }
            }
        }
    }

    private companion object {
        const val IMMEDIATE_SYNC = "classflow-immediate-sync"
        const val PERIODIC_SYNC = "classflow-periodic-sync"
    }
}

private fun CourseEntity.toModel() =
    Course(
        id,
        name,
        teacher,
        room,
        colorKey,
        notes,
        version,
        SyncState.valueOf(syncState),
    )

private fun TimetableSlotEntity.toModel() =
    TimetableSlot(
        id,
        courseId,
        dayOfWeek,
        startMinutes,
        endMinutes,
        roomOverride,
        version,
        SyncState.valueOf(syncState),
    )

private fun AgendaWithLinks.toModel() =
    AgendaItem(
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

private fun Course.toEntity(now: Long) =
    CourseEntity(
        id,
        name.trim(),
        teacher.trim(),
        room.trim(),
        colorKey,
        notes.trim(),
        version,
        now,
        syncState.name,
    )

private fun TimetableSlot.toEntity(now: Long) =
    TimetableSlotEntity(
        id,
        courseId,
        dayOfWeek,
        startMinutes,
        endMinutes,
        roomOverride.trim(),
        version,
        now,
        syncState.name,
    )

private fun AgendaItem.toEntity(now: Long) =
    AgendaEntity(
        id,
        type.name,
        title.trim(),
        occursAt,
        endsAt,
        allDay,
        status.name,
        notes.trim(),
        reminderAt,
        version,
        now,
        syncState.name,
    )

private fun ApiCourse.toEntity() =
    CourseEntity(
        id,
        name,
        teacher,
        room,
        colorKey,
        notes,
        version,
        updatedAt,
        SyncState.SYNCED.name,
    )

private fun ApiSlot.toEntity() =
    TimetableSlotEntity(
        id,
        courseId,
        dayOfWeek,
        startMinutes,
        endMinutes,
        roomOverride,
        version,
        updatedAt,
        SyncState.SYNCED.name,
    )

private fun ApiAgenda.toEntity() =
    AgendaEntity(
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

private fun StudyPlanEntity.toModel() =
    StudyPlan(
        id,
        title,
        startsAt,
        endsAt,
        linkedCourseId,
        linkedAgendaId,
        notes,
        version,
        SyncState.valueOf(syncState),
    )

private fun StudyPlan.toEntity(now: Long) =
    StudyPlanEntity(
        id,
        title.trim(),
        startsAt,
        endsAt,
        linkedCourseId,
        linkedAgendaId,
        notes.trim(),
        version,
        now,
        syncState.name,
    )

private fun StudyPlan.toApi(now: Long) =
    ApiStudyPlan(
        id,
        title.trim(),
        startsAt,
        endsAt,
        linkedCourseId,
        linkedAgendaId,
        notes.trim(),
        version,
        now,
    )

private fun ApiStudyPlan.toEntity() =
    StudyPlanEntity(
        id,
        title,
        startsAt,
        endsAt,
        linkedCourseId,
        linkedAgendaId,
        notes,
        version,
        updatedAt,
        SyncState.SYNCED.name,
    )
