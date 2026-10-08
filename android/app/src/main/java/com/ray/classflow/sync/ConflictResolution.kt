package com.ray.classflow.sync

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.ray.classflow.data.db.PendingMutationEntity

enum class ConflictChoice {
    LOCAL,
    SERVER,
    BOTH,
}

/** One latest intent per entity; preserve the version and ordering before local edits. */
internal fun coalesceMutations(
    mutations: List<PendingMutationEntity>
): List<PendingMutationEntity> =
    mutations
        .groupBy { it.entityType to it.entityId }
        .values
        .map { edits ->
            val first = edits.first()
            val latest = edits.last()
            val conflict = edits.lastOrNull { it.conflictServerPayload != null }
            latest.copy(
                baseVersion = first.baseVersion,
                createdAt = first.createdAt,
                conflictServerPayload = conflict?.conflictServerPayload,
                lastError = conflict?.lastError ?: latest.lastError,
            )
        }
        .sortedWith(compareBy({ it.createdAt }, { it.localId }))

internal fun ApiState.payloadFor(type: String, id: String, gson: Gson): String? =
    when (type) {
        "course" -> courses.find { it.id == id }?.let(gson::toJson)
        "slot" -> slots.find { it.id == id }?.let(gson::toJson)
        "agenda" -> agendaItems.find { it.id == id }?.let(gson::toJson)
        "study" -> studyPlans.orEmpty().find { it.id == id }?.let(gson::toJson)
        else -> null
    }

internal fun payloadVersion(payload: String?): Long =
    payload
        ?.let(JsonParser::parseString)
        ?.takeUnless { it.isJsonNull }
        ?.asJsonObject
        ?.get("version")
        ?.asLong ?: 0

internal fun resolvedMutation(
    mutation: PendingMutationEntity,
    choice: ConflictChoice,
    serverPayload: String?,
    operationId: String,
    copyId: String,
    now: Long,
): PendingMutationEntity? {
    if (choice == ConflictChoice.SERVER) return null
    if (choice == ConflictChoice.BOTH) {
        require(mutation.operation == "upsert" && serverPayload != null) { "這筆變更無法保留兩份" }
    }
    if (mutation.operation == "delete" && serverPayload == null) return null
    val id = if (choice == ConflictChoice.BOTH) copyId else mutation.entityId
    val version = if (choice == ConflictChoice.BOTH) 0 else payloadVersion(serverPayload)
    val payload =
        mutation.payload?.let { raw ->
            JsonParser.parseString(raw)
                .asJsonObject
                .apply {
                    addProperty("id", id)
                    addProperty("version", version)
                    addProperty("updatedAt", now)
                }
                .toString()
        }
    return mutation.copy(
        localId = 0,
        entityId = id,
        operationId = operationId,
        baseVersion = version,
        payload = payload,
        createdAt = now,
        conflictServerPayload = null,
        lastError = null,
    )
}

/** Overlay queued payloads, including deletions, instead of losing unsynced edits on a pull. */
internal fun mergePendingState(
    server: ApiState,
    pending: List<PendingMutationEntity>,
    gson: Gson,
): ApiState {
    val courses = server.courses.associateBy { it.id }.toMutableMap()
    val slots = server.slots.associateBy { it.id }.toMutableMap()
    val agenda = server.agendaItems.associateBy { it.id }.toMutableMap()
    val studies = server.studyPlans.orEmpty().associateBy { it.id }.toMutableMap()
    coalesceMutations(pending).forEach { mutation ->
        when (mutation.entityType) {
            "course" ->
                if (mutation.operation == "delete") courses.remove(mutation.entityId)
                else
                    courses[mutation.entityId] =
                        gson.fromJson(mutation.payload, ApiCourse::class.java)
            "slot" ->
                if (mutation.operation == "delete") slots.remove(mutation.entityId)
                else slots[mutation.entityId] = gson.fromJson(mutation.payload, ApiSlot::class.java)
            "agenda" ->
                if (mutation.operation == "delete") agenda.remove(mutation.entityId)
                else
                    agenda[mutation.entityId] =
                        gson.fromJson(mutation.payload, ApiAgenda::class.java)
            "study" ->
                if (mutation.operation == "delete") studies.remove(mutation.entityId)
                else
                    studies[mutation.entityId] =
                        gson.fromJson(mutation.payload, ApiStudyPlan::class.java)
        }
    }
    return server.copy(
        courses = courses.values.toList(),
        slots = slots.values.toList(),
        agendaItems = agenda.values.toList(),
        studyPlans =
            if (server.studyPlans != null || pending.any { it.entityType == "study" })
                studies.values.toList()
            else null,
    )
}
