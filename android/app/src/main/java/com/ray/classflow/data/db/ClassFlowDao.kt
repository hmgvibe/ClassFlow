package com.ray.classflow.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassFlowDao {
    @Query("SELECT * FROM courses ORDER BY name COLLATE NOCASE")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek, startMinutes")
    fun observeSlots(): Flow<List<TimetableSlotEntity>>

    @Transaction
    @Query("SELECT * FROM agenda_items ORDER BY occursAt")
    fun observeAgenda(): Flow<List<AgendaWithLinks>>

    @Upsert
    suspend fun upsertCourse(course: CourseEntity)

    @Upsert
    suspend fun upsertCourses(courses: List<CourseEntity>)

    @Upsert
    suspend fun upsertSlot(slot: TimetableSlotEntity)

    @Upsert
    suspend fun upsertSlots(slots: List<TimetableSlotEntity>)

    @Upsert
    suspend fun upsertAgenda(item: AgendaEntity)

    @Upsert
    suspend fun upsertAgendaItems(items: List<AgendaEntity>)

    @Upsert
    suspend fun upsertAgendaLinks(links: List<AgendaLinkEntity>)

    @Query("DELETE FROM agenda_links WHERE agendaId = :agendaId")
    suspend fun deleteAgendaLinks(agendaId: String)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourse(id: String)

    @Query("DELETE FROM timetable_slots WHERE id = :id")
    suspend fun deleteSlot(id: String)

    @Query("DELETE FROM agenda_items WHERE id = :id")
    suspend fun deleteAgenda(id: String)

    @Query("DELETE FROM courses")
    suspend fun clearCourses()

    @Query("DELETE FROM timetable_slots")
    suspend fun clearSlots()

    @Query("DELETE FROM agenda_items")
    suspend fun clearAgenda()

    @Query("DELETE FROM agenda_links")
    suspend fun clearLinks()

    @Upsert
    suspend fun insertMutation(mutation: PendingMutationEntity)

    @Query("SELECT * FROM pending_mutations ORDER BY createdAt, localId")
    suspend fun pendingMutations(): List<PendingMutationEntity>

    @Query("SELECT COUNT(*) FROM pending_mutations")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_mutations WHERE conflictServerPayload IS NOT NULL")
    fun observeConflictCount(): Flow<Int>

    @Query("DELETE FROM pending_mutations WHERE localId IN (:ids)")
    suspend fun deleteMutations(ids: List<Long>)

    @Query("UPDATE pending_mutations SET lastError = :message WHERE localId = :id")
    suspend fun setMutationError(id: Long, message: String?)

    @Query("UPDATE pending_mutations SET conflictServerPayload = :payload, lastError = 'conflict' WHERE localId = :id")
    suspend fun setMutationConflict(id: Long, payload: String)

    @Query("SELECT * FROM courses")
    suspend fun allCourses(): List<CourseEntity>

    @Query("SELECT * FROM timetable_slots")
    suspend fun allSlots(): List<TimetableSlotEntity>

    @Transaction
    @Query("SELECT * FROM agenda_items ORDER BY occursAt")
    suspend fun allAgenda(): List<AgendaWithLinks>
}

