package com.ray.classflow.widget

import com.ray.classflow.data.db.TimetableSlotEntity
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableWidgetTest {
    private val zone = ZoneId.of("Asia/Taipei")

    @Test
    fun ongoingClassAndFollowingClassAreSelected() {
        val now = ZonedDateTime.of(2026, 9, 28, 8, 30, 0, 0, zone)
        val result = nextTimetableClasses(
            slots = listOf(
                slot("first", day = 1, start = 490, end = 540),
                slot("second", day = 1, start = 550, end = 600),
                slot("tomorrow", day = 2, start = 490, end = 540),
            ),
            now = now,
        )

        assertEquals(listOf("first", "second"), result.map { it.slot.id })
    }

    @Test
    fun selectionContinuesOnNextSchoolDay() {
        val now = ZonedDateTime.of(2026, 9, 28, 18, 0, 0, 0, zone)
        val result = nextTimetableClasses(
            slots = listOf(
                slot("finished", day = 1, start = 490, end = 540),
                slot("tuesday-first", day = 2, start = 490, end = 540),
                slot("tuesday-second", day = 2, start = 550, end = 600),
            ),
            now = now,
        )

        assertEquals(listOf("tuesday-first", "tuesday-second"), result.map { it.slot.id })
    }

    @Test
    fun aSingleWeeklySlotCanFillBothPositions() {
        val now = ZonedDateTime.of(2026, 9, 28, 18, 0, 0, 0, zone)
        val result = nextTimetableClasses(
            slots = listOf(slot("weekly", day = 1, start = 490, end = 540)),
            now = now,
        )

        assertEquals(2, result.size)
        assertEquals(listOf("weekly", "weekly"), result.map { it.slot.id })
        assertEquals(7L, java.time.temporal.ChronoUnit.DAYS.between(result[0].date, result[1].date))
    }

    private fun slot(id: String, day: Int, start: Int, end: Int) = TimetableSlotEntity(
        id = id,
        courseId = "course-$id",
        dayOfWeek = day,
        startMinutes = start,
        endMinutes = end,
        roomOverride = "",
        version = 0,
        updatedAt = 0,
        syncState = "SYNCED",
    )
}
