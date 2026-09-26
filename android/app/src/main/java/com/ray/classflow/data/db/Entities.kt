package com.ray.classflow.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "courses")
data class CourseEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val teacher: String,
    val room: String,
    val colorKey: Int,
    val notes: String,
    val version: Long,
    val updatedAt: Long,
    val syncState: String,
)

@Entity(
    tableName = "timetable_slots",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId"), Index(value = ["dayOfWeek", "startMinutes"])],
)
data class TimetableSlotEntity(
    @androidx.room.PrimaryKey val id: String,
    val courseId: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val roomOverride: String,
    val version: Long,
    val updatedAt: Long,
    val syncState: String,
)

@Entity(tableName = "agenda_items", indices = [Index("occursAt"), Index("status")])
data class AgendaEntity(
    @androidx.room.PrimaryKey val id: String,
    val type: String,
    val title: String,
    val occursAt: Long,
    val endsAt: Long?,
    val allDay: Boolean,
    val status: String,
    val notes: String,
    val reminderAt: Long?,
    val version: Long,
    val updatedAt: Long,
    val syncState: String,
)

@Entity(
    tableName = "agenda_links",
    primaryKeys = ["agendaId", "slotId"],
    foreignKeys = [
        ForeignKey(
            entity = AgendaEntity::class,
            parentColumns = ["id"],
            childColumns = ["agendaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TimetableSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("agendaId"), Index("slotId")],
)
data class AgendaLinkEntity(
    val agendaId: String,
    val slotId: String,
)

@Entity(tableName = "pending_mutations", indices = [Index("entityId")])
data class PendingMutationEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val baseVersion: Long,
    val payload: String?,
    val createdAt: Long,
    val lastError: String? = null,
    val conflictServerPayload: String? = null,
)

data class AgendaWithLinks(
    @androidx.room.Embedded val agenda: AgendaEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = androidx.room.Junction(
            value = AgendaLinkEntity::class,
            parentColumn = "agendaId",
            entityColumn = "slotId",
        ),
    )
    val slots: List<TimetableSlotEntity>,
)
