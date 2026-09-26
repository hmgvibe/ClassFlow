package com.ray.classflow.model

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class AgendaType { HOMEWORK, EXAM, ACTIVITY, OTHER }

enum class AgendaStatus { PENDING, COMPLETED }

enum class SyncState { SYNCED, PENDING, CONFLICT }

@Immutable
data class Course(
    val id: String,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    val colorKey: Int = 0,
    val notes: String = "",
    val version: Long = 0,
    val syncState: SyncState = SyncState.PENDING,
)

@Immutable
data class TimetableSlot(
    val id: String,
    val courseId: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val roomOverride: String = "",
    val version: Long = 0,
    val syncState: SyncState = SyncState.PENDING,
) {
    val startTime: LocalTime get() = LocalTime.of(startMinutes / 60, startMinutes % 60)
    val endTime: LocalTime get() = LocalTime.of(endMinutes / 60, endMinutes % 60)
    val day: DayOfWeek get() = DayOfWeek.of(dayOfWeek)
}

@Immutable
data class AgendaItem(
    val id: String,
    val type: AgendaType,
    val title: String,
    val occursAt: Long,
    val endsAt: Long? = null,
    val allDay: Boolean = false,
    val status: AgendaStatus = AgendaStatus.PENDING,
    val notes: String = "",
    val reminderAt: Long? = null,
    val linkedSlotIds: List<String> = emptyList(),
    val version: Long = 0,
    val syncState: SyncState = SyncState.PENDING,
) {
    fun localDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(occursAt).atZone(zoneId).toLocalDate()
}

@Immutable
data class ClassFlowState(
    val courses: List<Course> = emptyList(),
    val slots: List<TimetableSlot> = emptyList(),
    val agenda: List<AgendaItem> = emptyList(),
    val pendingChanges: Int = 0,
    val conflicts: Int = 0,
)

data class Account(
    val serverUrl: String,
    val loginName: String,
    val appPassword: String,
)

