package com.ray.classflow.model

object Validation {
    fun studyPlan(plan: StudyPlan): String? =
        when {
            plan.title.isBlank() -> "請輸入計劃標題"
            plan.title.length > 255 -> "計劃標題不可超過 255 個字元"
            plan.startsAt <= 0 || plan.endsAt <= plan.startsAt -> "結束時間必須晚於開始時間"
            plan.linkedCourseId != null && plan.linkedAgendaId != null -> "請只選擇一個連結對象"
            plan.notes.length > 10000 -> "備註不可超過 10000 個字元"
            else -> null
        }

    fun course(course: Course): String? =
        when {
            course.name.isBlank() -> "請輸入課程名稱"
            course.name.length > 160 -> "課程名稱不可超過 160 個字元"
            else -> null
        }

    fun slot(slot: TimetableSlot): String? =
        when {
            slot.courseId.isBlank() -> "請選擇課程"
            slot.dayOfWeek !in 1..7 -> "星期格式不正確"
            slot.startMinutes !in 0..1439 -> "開始時間格式不正確"
            slot.endMinutes !in 1..1440 -> "結束時間格式不正確"
            slot.startMinutes >= slot.endMinutes -> "結束時間必須晚於開始時間"
            else -> null
        }

    fun agenda(item: AgendaItem): String? =
        when {
            item.title.isBlank() -> "請輸入日程標題"
            item.title.length > 255 -> "日程標題不可超過 255 個字元"
            item.occursAt <= 0L -> "日期時間格式不正確"
            item.endsAt != null && item.endsAt < item.occursAt -> "結束時間必須晚於開始時間"
            item.reminderAt != null && item.reminderAt > item.occursAt -> "提醒時間不可晚於日程時間"
            else -> null
        }
}
