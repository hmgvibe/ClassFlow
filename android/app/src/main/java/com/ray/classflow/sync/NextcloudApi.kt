package com.ray.classflow.sync

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.ray.classflow.model.Account
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class LoginSession(val token: String, val endpoint: String, val loginUrl: String)

data class ApiCourse(
    val id: String,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    val colorKey: Int = 0,
    val notes: String = "",
    val version: Long = 0,
    val updatedAt: Long = 0,
)

data class ApiSlot(
    val id: String,
    val courseId: String,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val roomOverride: String = "",
    val version: Long = 0,
    val updatedAt: Long = 0,
)

data class ApiAgenda(
    val id: String,
    val type: String,
    val title: String,
    val occursAt: Long,
    val endsAt: Long? = null,
    val allDay: Boolean = false,
    val status: String,
    val notes: String = "",
    val reminderAt: Long? = null,
    val linkedSlotIds: List<String> = emptyList(),
    val version: Long = 0,
    val updatedAt: Long = 0,
)

data class ApiState(
    val courses: List<ApiCourse> = emptyList(),
    val slots: List<ApiSlot> = emptyList(),
    val agendaItems: List<ApiAgenda> = emptyList(),
    val serverTime: Long = 0,
    // null means an older server which does not support study plans yet.
    val studyPlans: List<ApiStudyPlan>? = null,
)

data class ApiStudyPlan(
    val id: String,
    val title: String,
    val startsAt: Long,
    val endsAt: Long,
    val linkedCourseId: String? = null,
    val linkedAgendaId: String? = null,
    val notes: String = "",
    val version: Long = 0,
    val updatedAt: Long = 0,
)

data class ApiMutation(
    val localId: Long,
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val baseVersion: Long,
    val payload: JsonElement?,
)

data class SyncConflict(
    val operationId: String,
    val server: JsonElement?,
    val error: String? = null,
)

data class SyncResult(
    val state: ApiState,
    val acceptedOperationIds: List<String> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
)

class NextcloudApi(
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
    private val gson: Gson = Gson(),
) {
    suspend fun beginLogin(serverInput: String): LoginSession =
        withContext(Dispatchers.IO) {
            val server = normalizeServer(serverInput)
            val request =
                Request.Builder()
                    .url("$server/index.php/login/v2")
                    .post(ByteArray(0).toRequestBody(null))
                    .header("User-Agent", USER_AGENT)
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("登入服務回傳 ${response.code}")
                val root = gson.fromJson(response.body.string(), JsonObject::class.java)
                val poll = root.getAsJsonObject("poll")
                LoginSession(
                    token = poll["token"].asString,
                    endpoint = poll["endpoint"].asString,
                    loginUrl = root["login"].asString,
                )
            }
        }

    suspend fun awaitLogin(session: LoginSession, attempts: Int = 600): Account =
        withContext(Dispatchers.IO) {
            var lastNetworkError: IOException? = null
            repeat(attempts) {
                val request =
                    Request.Builder()
                        .url(session.endpoint)
                        .post(FormBody.Builder().add("token", session.token).build())
                        .header("User-Agent", USER_AGENT)
                        .build()
                val response =
                    try {
                        client.newCall(request).execute()
                    } catch (error: IOException) {
                        lastNetworkError = error
                        delay(2_000)
                        return@repeat
                    }
                response.use {
                    when (it.code) {
                        200 -> {
                            val root = gson.fromJson(it.body.string(), JsonObject::class.java)
                            return@withContext Account(
                                serverUrl = normalizeServer(root["server"].asString),
                                loginName = root["loginName"].asString,
                                appPassword = root["appPassword"].asString,
                            )
                        }
                        404 -> Unit
                        else -> throw IOException("授權輪詢回傳 ${it.code}")
                    }
                }
                lastNetworkError = null
                delay(2_000)
            }
            throw IOException("登入授權已逾時", lastNetworkError)
        }

    suspend fun getState(account: Account): ApiState =
        withContext(Dispatchers.IO) {
            val data =
                executeOcs(
                    account,
                    Request.Builder()
                        .url(
                            "${account.serverUrl}/ocs/v2.php/apps/classflow/api/v1/state?format=json"
                        )
                        .get(),
                )
            gson.fromJson(data, ApiState::class.java)
        }

    suspend fun sync(account: Account, mutations: List<ApiMutation>): SyncResult =
        withContext(Dispatchers.IO) {
            val bodyObject =
                JsonObject().apply {
                    add(
                        "mutations",
                        gson.toJsonTree(
                            mutations.map { mutation ->
                                mapOf(
                                    "operationId" to mutation.operationId,
                                    "entityType" to mutation.entityType,
                                    "entityId" to mutation.entityId,
                                    "operation" to mutation.operation,
                                    "baseVersion" to mutation.baseVersion,
                                    "payload" to mutation.payload,
                                )
                            }
                        ),
                    )
                }
            val data =
                executeOcs(
                    account,
                    Request.Builder()
                        .url(
                            "${account.serverUrl}/ocs/v2.php/apps/classflow/api/v1/sync?format=json"
                        )
                        .post(gson.toJson(bodyObject).toRequestBody(JSON)),
                )
            gson.fromJson(data, SyncResult::class.java)
        }

    private fun executeOcs(account: Account, builder: Request.Builder): JsonElement {
        val request =
            builder
                .header("Authorization", Credentials.basic(account.loginName, account.appPassword))
                .header("OCS-APIRequest", "true")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                val hint = if (response.code == 404) "請確認伺服器已啟用 ClassFlow App" else raw.take(160)
                throw IOException("同步失敗 (${response.code})：$hint")
            }
            val root = gson.fromJson(raw, JsonObject::class.java)
            return root.getAsJsonObject("ocs")?.get("data") ?: throw IOException("伺服器回應格式不正確")
        }
    }

    private fun normalizeServer(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "請輸入包含 https:// 的伺服器網址"
        }
        return trimmed
    }

    private companion object {
        val USER_AGENT = "ClassFlow-Android/${com.ray.classflow.BuildConfig.VERSION_NAME}"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
