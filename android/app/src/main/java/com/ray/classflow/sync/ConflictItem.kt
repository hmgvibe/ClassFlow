package com.ray.classflow.sync

import com.ray.classflow.i18n.UiText

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ray.classflow.data.db.AgendaWithLinks
import com.ray.classflow.data.db.CourseEntity
import com.ray.classflow.data.db.PendingMutationEntity
import com.ray.classflow.data.db.TimetableSlotEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ConflictField(val label: String, val value: String, val colorKey: Int? = null)

data class ConflictItem(
    val mutation: PendingMutationEntity,
    val title: String,
    val typeLabel: String,
    val localFields: List<ConflictField>,
    val serverFields: List<ConflictField>,
    val serverDeleted: Boolean,
    val error: String?,
) {
    val key: String
        get() = "${mutation.entityType}:${mutation.entityId}"

    val canKeepBoth: Boolean
        get() = mutation.operation == "upsert" && !serverDeleted && error == null
}

internal fun conflictItems(
    mutations: List<PendingMutationEntity>,
    courses: List<CourseEntity>,
    slots: List<TimetableSlotEntity>,
    agenda: List<AgendaWithLinks> = emptyList(),
): List<ConflictItem> =
    coalesceMutations(mutations)
        .filter { it.conflictServerPayload != null }
        .map { mutation ->
            val local =
                mutation.payload
                    ?.let(JsonParser::parseString)
                    ?.takeUnless { it.isJsonNull }
                    ?.asJsonObject
            val server =
                mutation.conflictServerPayload
                    ?.let(JsonParser::parseString)
                    ?.takeUnless { it.isJsonNull }
                    ?.asJsonObject
            val title =
                (local ?: server)?.let { payload ->
                    when (mutation.entityType) {
                        "course" -> payload.text("name")
                        "agenda",
                        "study" -> payload.text("title")
                        else -> courses.find { it.id == payload.text("courseId") }?.name
                    }
                }
                    ?: when (mutation.entityType) {
                        "course" -> UiText.TEXT_ECA9598BA5.text()
                        "slot" -> UiText.TEXT_80573F129F.text()
                        "study" -> UiText.TEXT_4363945A7E.text()
                        else -> UiText.TEXT_1C16B9D2A8.text()
                    }
            val error =
                mutation.lastError
                    ?.takeUnless { it == "conflict" }
                    ?.let {
                        when (it) {
                            "Course does not exist" -> UiText.TEXT_5D81FE78DC.text()
                            "Linked slot does not exist" -> UiText.TEXT_413E467153.text()
                            "Invalid timetable range" -> UiText.TEXT_57715AB76E.text()
                            "Invalid agenda date range" -> UiText.TEXT_1D9A18EF3A.text()
                            "Invalid study date range" -> UiText.TEXT_FA2BC73D7D.text()
                            "Study link does not exist" -> UiText.TEXT_79FD9D67B4.text()
                            else -> UiText.TEXT_A965FED3EC.text(it)
                        }
                    }
            ConflictItem(
                mutation = mutation,
                title = title.ifBlank { UiText.TEXT_67FC4150CF.text() },
                typeLabel =
                    when (mutation.entityType) {
                        "course" -> UiText.TEXT_ECA9598BA5.text()
                        "slot" -> UiText.TEXT_80573F129F.text()
                        "study" -> UiText.TEXT_4363945A7E.text()
                        else -> UiText.TEXT_1C16B9D2A8.text()
                    },
                localFields =
                    local
                        ?.let { conflictFields(mutation.entityType, it, courses, slots, agenda) }
                        .orEmpty(),
                serverFields =
                    server
                        ?.let { conflictFields(mutation.entityType, it, courses, slots, agenda) }
                        .orEmpty(),
                serverDeleted = server == null,
                error = error,
            )
        }

private fun JsonObject.text(key: String): String =
    get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

private fun minutes(value: String): String =
    value.toIntOrNull()?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()

private fun date(value: String): String =
    value
        .toLongOrNull()
        ?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withLocale(UiText.displayLocale()))
        }
        .orEmpty()

private fun conflictFields(
    type: String,
    payload: JsonObject,
    courses: List<CourseEntity>,
    slots: List<TimetableSlotEntity>,
    agenda: List<AgendaWithLinks>,
): List<ConflictField> = buildList {
    fun field(label: String, value: String) {
        add(ConflictField(label, value.ifBlank { UiText.TEXT_79AACC634A.text() }))
    }
    when (type) {
        "course" -> {
            field(UiText.TEXT_E0D245570A.text(), payload.text("name"))
            field(UiText.TEXT_00034F2A3B.text(), payload.text("teacher"))
            field(UiText.TEXT_F3255B3073.text(), payload.text("room"))
            add(ConflictField(UiText.TEXT_76431DFA26.text(), "", payload.text("colorKey").toIntOrNull() ?: 0))
        }
        "study" -> {
            field(UiText.TEXT_BEC815141B.text(), payload.text("title"))
            field(UiText.TEXT_A95BF2FED6.text(), date(payload.text("startsAt")))
            field(UiText.TEXT_6B9C732A28.text(), date(payload.text("endsAt")))
            field(
                UiText.TEXT_E070F0274A.text(),
                payload
                    .text("linkedCourseId")
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> courses.find { it.id == id }?.name ?: UiText.TEXT_0368D03019.text() }
                    .orEmpty(),
            )
            field(
                UiText.TEXT_65558573D2.text(),
                payload
                    .text("linkedAgendaId")
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> agenda.find { it.agenda.id == id }?.agenda?.title ?: UiText.TEXT_81169F2F2D.text() }
                    .orEmpty(),
            )
        }
        "slot" -> {
            field(UiText.TEXT_ECA9598BA5.text(), courses.find { it.id == payload.text("courseId") }?.name ?: UiText.TEXT_0368D03019.text())
            field(
                UiText.TEXT_E35269378D.text(),
                payload
                    .text("dayOfWeek")
                    .toIntOrNull()
                    ?.let { listOf(UiText.TEXT_A0B21BDF86.text(), UiText.TEXT_898FD5BE1A.text(), UiText.TEXT_538556D98F.text(), UiText.TEXT_313FBD0219.text(), UiText.TEXT_6EAAE7141B.text(), UiText.TEXT_E6839E3C6A.text(), UiText.TEXT_084801F47D.text()).getOrNull(it - 1) }
                    .orEmpty(),
            )
            field(
                UiText.TEXT_09558B2CC6.text(),
                "${minutes(payload.text("startMinutes"))}–${minutes(payload.text("endMinutes"))}",
            )
            field(UiText.TEXT_3A711B258F.text(), payload.text("roomOverride"))
        }
        "agenda" -> {
            field(UiText.TEXT_BEC815141B.text(), payload.text("title"))
            field(
                UiText.TEXT_469FB16B34.text(),
                when (payload.text("type")) {
                    "homework" -> UiText.TEXT_631A2479D9.text()
                    "exam" -> UiText.TEXT_2AA23AAED8.text()
                    "activity" -> UiText.TEXT_47E6C61BB4.text()
                    else -> UiText.TEXT_1A26EDF94A.text()
                },
            )
            field(UiText.TEXT_A95BF2FED6.text(), date(payload.text("occursAt")))
            field(UiText.TEXT_6B9C732A28.text(), date(payload.text("endsAt")))
            field(UiText.TEXT_79B62069F5.text(), if (payload.text("allDay") == "true") UiText.TEXT_30160A21B9.text() else UiText.TEXT_8BF5C10AD9.text())
            field(UiText.TEXT_45293595EA.text(), if (payload.text("status") == "completed") UiText.TEXT_E99B48A29B.text() else UiText.TEXT_F9DDFD6434.text())
            field(UiText.TEXT_81944E48A3.text(), date(payload.text("reminderAt")))
            val links =
                payload
                    .getAsJsonArray("linkedSlotIds")
                    ?.map { link ->
                        slots
                            .find { it.id == link.asString }
                            ?.let { slot ->
                                UiText.TEXT_E3EF6EB8F1.text(courses.find { it.id == slot.courseId }?.name ?: UiText.TEXT_80573F129F.text(), listOf(UiText.TEXT_D274EEE8A1.text(), UiText.TEXT_1D5639F716.text(), UiText.TEXT_49DDB069D5.text(), UiText.TEXT_4F88740B34.text(), UiText.TEXT_8F07F53D63.text(), UiText.TEXT_3D72C724E0.text(), UiText.TEXT_15917F3B32.text())[slot.dayOfWeek - 1], minutes(slot.startMinutes.toString()))
                            } ?: UiText.TEXT_D678230B98.text()
                    }
                    .orEmpty()
            field(UiText.TEXT_4B71A2571F.text(), links.joinToString("\n").ifBlank { UiText.TEXT_D7A13985C0.text() })
        }
    }
    if (type != "slot") field(UiText.TEXT_3AE9AB7D02.text(), payload.text("notes"))
}
