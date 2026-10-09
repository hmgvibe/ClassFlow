package com.ray.classflow.model

import com.ray.classflow.i18n.UiText

object Validation {
    fun studyPlan(plan: StudyPlan): UiText? =
        when {
            plan.title.isBlank() -> UiText.TEXT_B87634F0F8
            plan.title.length > 255 -> UiText.TEXT_E97815E127
            plan.startsAt <= 0 || plan.endsAt <= plan.startsAt -> UiText.TEXT_4888E81530
            plan.linkedCourseId != null && plan.linkedAgendaId != null -> UiText.TEXT_B0FECE0FCD
            plan.notes.length > 10000 -> UiText.TEXT_A87E245870
            else -> null
        }

    fun course(course: Course): UiText? =
        when {
            course.name.isBlank() -> UiText.TEXT_637DE04E6B
            course.name.length > 160 -> UiText.TEXT_77F40CBD96
            else -> null
        }

    fun slot(slot: TimetableSlot): UiText? =
        when {
            slot.courseId.isBlank() -> UiText.TEXT_4D43AA68C9
            slot.dayOfWeek !in 1..7 -> UiText.TEXT_509C461D56
            slot.startMinutes !in 0..1439 -> UiText.TEXT_821F7F512E
            slot.endMinutes !in 1..1440 -> UiText.TEXT_3B85765FA0
            slot.startMinutes >= slot.endMinutes -> UiText.TEXT_4888E81530
            else -> null
        }

    fun agenda(item: AgendaItem): UiText? =
        when {
            item.title.isBlank() -> UiText.TEXT_66378A0754
            item.title.length > 255 -> UiText.TEXT_BA64CE1C9E
            item.occursAt <= 0L -> UiText.TEXT_6B7D311F06
            item.endsAt != null && item.endsAt < item.occursAt -> UiText.TEXT_4888E81530
            item.reminderAt != null && item.reminderAt > item.occursAt -> UiText.TEXT_31C7966BFA
            else -> null
        }
}
