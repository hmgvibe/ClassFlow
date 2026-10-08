package com.ray.classflow.sync

import com.google.gson.Gson
import com.ray.classflow.data.db.PendingMutationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionTest {
    private val gson = Gson()
    private val cloud = ApiCourse("course", "雲端課程", notes = "雲端筆記", version = 7)
    private val phone = ApiCourse("course", "手機課程", notes = "尚未同步的筆記", version = 3)

    private fun mutation(
        localId: Long = 1,
        payload: String? = gson.toJson(phone),
        operation: String = "upsert",
        conflict: String? = gson.toJson(cloud),
    ) =
        PendingMutationEntity(
            localId,
            "operation-$localId",
            "course",
            "course",
            operation,
            3,
            payload,
            localId * 100,
            if (conflict != null) "conflict" else null,
            conflict,
        )

    @Test
    fun repeatedEditsUseLatestPayloadWithOriginalBaseAndDependencyOrder() {
        val first = mutation(conflict = null)
        val slot = first.copy(localId = 2, entityType = "slot", entityId = "slot", createdAt = 200)
        val latest =
            mutation(localId = 3, payload = gson.toJson(phone.copy(name = "最後修改")), conflict = null)
        val compact = coalesceMutations(listOf(first, slot, latest))
        assertEquals(2, compact.size)
        assertEquals("course", compact[0].entityType)
        assertEquals(100L, compact[0].createdAt)
        assertEquals(3L, compact[0].baseVersion)
        assertEquals(latest.payload, compact[0].payload)
        assertEquals(latest.operationId, compact[0].operationId)
    }

    @Test
    fun editingAnUnresolvedConflictPreservesCloudComparison() {
        val first = mutation()
        val latest = mutation(localId = 2, conflict = null)
        val compact = coalesceMutations(listOf(first, latest)).single()
        assertEquals(first.conflictServerPayload, compact.conflictServerPayload)
        assertEquals("conflict", compact.lastError)
        assertEquals(latest.operationId, compact.operationId)
    }

    @Test
    fun keepingPhoneRebasesWithoutDiscardingNotesAndUsesNewOperationId() {
        val original = mutation()
        val retry =
            resolvedMutation(
                original,
                ConflictChoice.LOCAL,
                gson.toJson(cloud),
                "fresh-op",
                "unused",
                500,
            )!!
        val payload = gson.fromJson(retry.payload, ApiCourse::class.java)
        assertEquals(7L, retry.baseVersion)
        assertEquals(7L, payload.version)
        assertEquals(phone.name, payload.name)
        assertEquals(phone.notes, payload.notes)
        assertNotEquals(original.operationId, retry.operationId)
        assertNull(retry.conflictServerPayload)
        assertEquals(0L, retry.localId)
    }

    @Test
    fun keepingCloudDiscardsOnlySelectedEntityIntent() {
        assertNull(
            resolvedMutation(
                mutation(),
                ConflictChoice.SERVER,
                gson.toJson(cloud),
                "op",
                "copy",
                500,
            )
        )
        val other = phone.copy(id = "other", name = "另一門本機課程")
        val otherPending = mutation().copy(entityId = "other", payload = gson.toJson(other))
        val result =
            mergePendingState(ApiState(courses = listOf(cloud)), listOf(otherPending), gson)
        assertEquals(cloud, result.courses.find { it.id == "course" })
        assertEquals(other, result.courses.find { it.id == "other" })
    }

    @Test
    fun keepingBothRetainsCloudAndCreatesIndependentPhoneCopy() {
        val copy =
            resolvedMutation(
                mutation(),
                ConflictChoice.BOTH,
                gson.toJson(cloud),
                "op",
                "new-course",
                500,
            )!!
        assertEquals(0L, copy.baseVersion)
        assertEquals("new-course", copy.entityId)
        val result = mergePendingState(ApiState(courses = listOf(cloud)), listOf(copy), gson)
        assertEquals(2, result.courses.size)
        assertEquals(cloud, result.courses.find { it.id == "course" })
        assertEquals(phone.notes, result.courses.find { it.id == "new-course" }!!.notes)
    }

    @Test
    fun cloudDeletionCanBeRestoredFromPhoneWithBaseZero() {
        val retry =
            resolvedMutation(
                mutation(conflict = "null"),
                ConflictChoice.LOCAL,
                null,
                "op",
                "unused",
                500,
            )!!
        assertEquals(0L, retry.baseVersion)
        assertEquals("course", retry.entityId)
        assertEquals(phone.name, gson.fromJson(retry.payload, ApiCourse::class.java).name)
    }

    @Test
    fun deletionAlreadyAppliedOnCloudRequiresNoRetry() {
        assertNull(
            resolvedMutation(
                mutation(payload = null, operation = "delete"),
                ConflictChoice.LOCAL,
                null,
                "op",
                "copy",
                500,
            )
        )
    }

    @Test
    fun pendingDeletionIsNotResurrectedByServerPull() {
        val deletion = mutation(payload = null, operation = "delete")
        val result = mergePendingState(ApiState(courses = listOf(cloud)), listOf(deletion), gson)
        assertTrue(result.courses.isEmpty())
        val retry =
            resolvedMutation(
                deletion,
                ConflictChoice.LOCAL,
                gson.toJson(cloud),
                "op",
                "copy",
                500,
            )!!
        assertEquals(7L, retry.baseVersion)
        assertNull(retry.payload)
    }

    @Test
    fun agendaCopyPreservesTimesRemindersAndClassLinks() {
        val agenda =
            ApiAgenda(
                "agenda",
                "homework",
                "報告",
                1000,
                2000,
                false,
                "pending",
                "筆記",
                900,
                listOf("slot"),
                3,
            )
        val edit =
            mutation()
                .copy(entityType = "agenda", entityId = "agenda", payload = gson.toJson(agenda))
        val copy =
            resolvedMutation(
                edit,
                ConflictChoice.BOTH,
                gson.toJson(agenda.copy(version = 4)),
                "op",
                "copy-agenda",
                500,
            )!!
        val result = gson.fromJson(copy.payload, ApiAgenda::class.java)
        assertEquals(agenda.copy(id = "copy-agenda", version = 0, updatedAt = 500), result)
    }

    @Test
    fun comparisonShowsLatestPhoneFieldsAndDeletedCloud() {
        val result =
            conflictItems(listOf(mutation(conflict = "null")), emptyList(), emptyList()).single()
        assertEquals(phone.name, result.title)
        assertTrue(result.serverDeleted)
        assertFalse(result.canKeepBoth)
        assertEquals(phone.notes, result.localFields.find { it.label == "備註" }!!.value)
    }

    @Test
    fun validationFailureIsShownAsActionableReason() {
        val edit = mutation().copy(lastError = "Course does not exist")
        val result = conflictItems(listOf(edit), emptyList(), emptyList()).single()
        assertTrue(result.error!!.contains("課程已不存在"))
        assertFalse(result.canKeepBoth)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deletedItemsCannotBeDuplicated() {
        resolvedMutation(
            mutation(payload = null, operation = "delete"),
            ConflictChoice.BOTH,
            gson.toJson(cloud),
            "op",
            "copy",
            500,
        )
    }
}
