package com.ray.classflow.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {
    @Test
    fun slotRequiresEndAfterStart() {
        val slot = TimetableSlot("slot", "course", 1, 600, 540)
        assertEquals("結束時間必須晚於開始時間", Validation.slot(slot))
    }

    @Test
    fun validCourseAndAgendaPass() {
        assertNull(Validation.course(Course("course", "數學")))
        assertNull(Validation.agenda(AgendaItem(
            id = "agenda",
            type = AgendaType.HOMEWORK,
            title = "習題一",
            occursAt = 1_800_000_000_000,
            reminderAt = 1_799_996_400_000,
        )))
    }

    @Test
    fun reminderCannotBeAfterAgenda() {
        val item = AgendaItem(
            id = "agenda",
            type = AgendaType.EXAM,
            title = "期中考",
            occursAt = 1_000,
            reminderAt = 2_000,
        )
        assertEquals("提醒時間不可晚於日程時間", Validation.agenda(item))
    }
}

