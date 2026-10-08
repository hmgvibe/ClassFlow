package com.ray.classflow.sync

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
                        "course" -> "課程"
                        "slot" -> "課堂"
                        "study" -> "學習計劃"
                        else -> "日程"
                    }
            val error =
                mutation.lastError
                    ?.takeUnless { it == "conflict" }
                    ?.let {
                        when (it) {
                            "Course does not exist" -> "對應的課程已不存在。請先檢查課程與課堂的關聯。"
                            "Linked slot does not exist" -> "連接的課堂已不存在。請先修改日程的課堂關聯。"
                            "Invalid timetable range" -> "課堂的星期或時間範圍不正確。請先修改課堂。"
                            "Invalid agenda date range" -> "日程的日期或結束時間不正確。請先修改日程。"
                            "Invalid study date range" -> "計劃的結束時間必須晚於開始時間。"
                            "Study link does not exist" -> "連結的課程或日程已不存在，請修改計劃的連結。"
                            else -> "伺服器無法接受這筆資料：$it"
                        }
                    }
            ConflictItem(
                mutation = mutation,
                title = title.ifBlank { "未命名項目" },
                typeLabel =
                    when (mutation.entityType) {
                        "course" -> "課程"
                        "slot" -> "課堂"
                        "study" -> "學習計劃"
                        else -> "日程"
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
                .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm"))
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
        add(ConflictField(label, value.ifBlank { "未設定" }))
    }
    when (type) {
        "course" -> {
            field("名稱", payload.text("name"))
            field("教師", payload.text("teacher"))
            field("教室", payload.text("room"))
            add(ConflictField("課程色彩", "", payload.text("colorKey").toIntOrNull() ?: 0))
        }
        "study" -> {
            field("標題", payload.text("title"))
            field("開始", date(payload.text("startsAt")))
            field("結束", date(payload.text("endsAt")))
            field(
                "連結課程",
                payload
                    .text("linkedCourseId")
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> courses.find { it.id == id }?.name ?: "課程已不存在" }
                    .orEmpty(),
            )
            field(
                "連結日程",
                payload
                    .text("linkedAgendaId")
                    .takeIf { it.isNotBlank() }
                    ?.let { id -> agenda.find { it.agenda.id == id }?.agenda?.title ?: "日程已不存在" }
                    .orEmpty(),
            )
        }
        "slot" -> {
            field("課程", courses.find { it.id == payload.text("courseId") }?.name ?: "課程已不存在")
            field(
                "星期",
                payload
                    .text("dayOfWeek")
                    .toIntOrNull()
                    ?.let { listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日").getOrNull(it - 1) }
                    .orEmpty(),
            )
            field(
                "時間",
                "${minutes(payload.text("startMinutes"))}–${minutes(payload.text("endMinutes"))}",
            )
            field("教室覆寫", payload.text("roomOverride"))
        }
        "agenda" -> {
            field("標題", payload.text("title"))
            field(
                "類型",
                when (payload.text("type")) {
                    "homework" -> "作業"
                    "exam" -> "考試"
                    "activity" -> "活動"
                    else -> "其他"
                },
            )
            field("開始", date(payload.text("occursAt")))
            field("結束", date(payload.text("endsAt")))
            field("全天", if (payload.text("allDay") == "true") "是" else "否")
            field("狀態", if (payload.text("status") == "completed") "已完成" else "待完成")
            field("提醒", date(payload.text("reminderAt")))
            val links =
                payload
                    .getAsJsonArray("linkedSlotIds")
                    ?.map { link ->
                        slots
                            .find { it.id == link.asString }
                            ?.let { slot ->
                                "${courses.find { it.id == slot.courseId }?.name ?: "課堂"} · 週${listOf("一", "二", "三", "四", "五", "六", "日")[slot.dayOfWeek - 1]} ${minutes(slot.startMinutes.toString())}"
                            } ?: "課堂已不存在"
                    }
                    .orEmpty()
            field("連接課堂", links.joinToString("\n").ifBlank { "未連接" })
        }
    }
    if (type != "slot") field("備註", payload.text("notes"))
}
