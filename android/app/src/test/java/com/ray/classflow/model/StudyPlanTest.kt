package com.ray.classflow.model

import com.google.gson.Gson
import com.ray.classflow.data.db.PendingMutationEntity
import com.ray.classflow.sync.*
import com.ray.classflow.ui.screens.studyLinkLabel
import com.ray.classflow.ui.screens.studyTimeHint
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class StudyPlanTest {
    private val zone = ZoneId.of("Asia/Taipei")

    private fun at(hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 10, 8).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val plan = StudyPlan("plan", "複習數學", at(14), at(15))

    @Test
    fun periodBoundariesAreHalfOpen() {
        assertEquals(StudyPeriod.MORNING, plan.copy(startsAt = at(11, 59)).period(zone))
        assertEquals(StudyPeriod.AFTERNOON, plan.copy(startsAt = at(12)).period(zone))
        assertEquals(StudyPeriod.AFTERNOON, plan.copy(startsAt = at(17, 59)).period(zone))
        assertEquals(StudyPeriod.EVENING, plan.copy(startsAt = at(18)).period(zone))
        assertEquals(StudyPeriod.EVENING, plan.copy(startsAt = at(23, 59)).period(zone))
    }

    @Test
    fun dateUsesLocalTimezoneNotUtcDay() {
        assertEquals(LocalDate.of(2026, 10, 8), plan.copy(startsAt = at(0)).localDate(zone))
        assertEquals(
            LocalDate.of(2026, 10, 7),
            plan.copy(startsAt = at(0)).localDate(ZoneId.of("UTC")),
        )
    }

    @Test
    fun validatesTitleAndTimeRangeAndSingleLink() {
        assertNull(Validation.studyPlan(plan))
        assertNotNull(Validation.studyPlan(plan.copy(title = " ")))
        assertNotNull(Validation.studyPlan(plan.copy(title = "a".repeat(256))))
        assertNotNull(Validation.studyPlan(plan.copy(endsAt = plan.startsAt)))
        assertNotNull(
            Validation.studyPlan(plan.copy(linkedCourseId = "course", linkedAgendaId = "exam"))
        )
    }

    @Test
    fun courseTimeHintDoesNotAlterPlanTimes() {
        val linked = plan.copy(linkedCourseId = "course")
        val state =
            ClassFlowState(
                courses = listOf(Course("course", "數學")),
                slots = listOf(TimetableSlot("slot", "course", 1, 480, 540)),
            )
        assertEquals("每週課堂時間\n週一 08:00–09:00", studyTimeHint(linked.linkedCourseId, null, state))
        assertEquals(plan.startsAt, linked.startsAt)
        assertEquals(plan.endsAt, linked.endsAt)
        assertEquals("課程 · 數學", studyLinkLabel(linked, state))
    }

    @Test
    fun missingReferenceIsShownWithoutLosingPlan() {
        assertEquals(
            "原連結日程已刪除",
            studyLinkLabel(plan.copy(linkedAgendaId = "deleted"), ClassFlowState()),
        )
        assertTrue(studyTimeHint(null, "deleted", ClassFlowState())!!.contains("重新選擇"))
    }

    @Test
    fun agendaHintIsReferenceOnlyAndSupportsAllDay() {
        val state =
            ClassFlowState(
                agenda = listOf(AgendaItem("exam", AgendaType.EXAM, "期中考", at(9), allDay = true))
            )
        assertTrue(studyTimeHint(null, "exam", state)!!.contains("全天"))
        assertEquals("考試 · 期中考", studyLinkLabel(plan.copy(linkedAgendaId = "exam"), state))
    }

    @Test
    fun syncRetainsPendingStudyAndDeletionAndUnrelatedPlans() {
        val gson = Gson()
        val cloud = ApiStudyPlan("cloud", "雲端", at(9), at(10), version = 2)
        val phone = cloud.copy(id = "phone", title = "本機", linkedCourseId = "course")
        val mutation =
            PendingMutationEntity(
                operationId = "op",
                entityType = "study",
                entityId = phone.id,
                operation = "upsert",
                baseVersion = 0,
                payload = gson.toJson(phone),
                createdAt = 1,
            )
        val merged = mergePendingState(ApiState(studyPlans = listOf(cloud)), listOf(mutation), gson)
        assertEquals(listOf(cloud, phone), merged.studyPlans)
        val deleted =
            mergePendingState(
                merged,
                listOf(mutation.copy(entityId = "cloud", operation = "delete", payload = null)),
                gson,
            )
        assertEquals(listOf(phone), deleted.studyPlans)
    }

    @Test
    fun conflictCopyPreservesReferenceAndManualTime() {
        val gson = Gson()
        val local =
            ApiStudyPlan(
                "plan",
                plan.title,
                at(14),
                at(15),
                linkedAgendaId = "exam",
                notes = "讀第二章",
                version = 1,
            )
        val mutation =
            PendingMutationEntity(
                operationId = "old",
                entityType = "study",
                entityId = "plan",
                operation = "upsert",
                baseVersion = 1,
                payload = gson.toJson(local),
                createdAt = 1,
                conflictServerPayload = gson.toJson(local.copy(version = 2)),
            )
        val copy =
            resolvedMutation(
                mutation,
                ConflictChoice.BOTH,
                mutation.conflictServerPayload,
                "new-op",
                "copy",
                100,
            )!!
        val payload = gson.fromJson(copy.payload, ApiStudyPlan::class.java)
        assertEquals(local.startsAt, payload.startsAt)
        assertEquals(local.linkedAgendaId, payload.linkedAgendaId)
        assertEquals(local.notes, payload.notes)
        assertEquals(
            "學習計劃",
            conflictItems(listOf(mutation), emptyList(), emptyList()).single().typeLabel,
        )
        assertEquals(0L, copy.baseVersion)
    }

    @Test
    fun oldServerStateIsDistinguishableFromEmptyPlans() {
        val gson = Gson()
        assertNull(
            gson
                .fromJson("{\"courses\":[],\"slots\":[],\"agendaItems\":[]}", ApiState::class.java)
                .studyPlans
        )
        assertEquals(
            emptyList<ApiStudyPlan>(),
            gson.fromJson("{\"studyPlans\":[]}", ApiState::class.java).studyPlans,
        )
    }
}
