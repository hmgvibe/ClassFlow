package com.ray.classflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ray.classflow.ClassFlowApplication
import com.ray.classflow.model.AgendaItem
import com.ray.classflow.model.ClassFlowState
import com.ray.classflow.model.Course
import com.ray.classflow.model.TimetableSlot
import com.ray.classflow.sync.LoginSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val connectedServer: String? = null,
    val loginUrl: String? = null,
    val isLoggingIn: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val message: String? = null,
)

class ClassFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ClassFlowApplication).repository
    val data: StateFlow<ClassFlowState> = repository.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ClassFlowState(),
    )

    private val _connection = MutableStateFlow(
        ConnectionUiState(connectedServer = repository.account()?.serverUrl),
    )
    val connection: StateFlow<ConnectionUiState> = _connection.asStateFlow()

    init {
        if (repository.account() == null) {
            repository.pendingLogin()?.let(::resumeLogin)
        } else {
            repository.clearPendingLogin()
        }
    }

    fun connect(serverUrl: String) {
        if (_connection.value.isLoggingIn) return
        viewModelScope.launch {
            _connection.value = _connection.value.copy(isLoggingIn = true, message = null)
            try {
                val session = repository.beginLogin(serverUrl)
                _connection.value = _connection.value.copy(loginUrl = session.loginUrl)
                completeLogin(session)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failLogin(error)
            }
        }
    }

    private fun resumeLogin(session: LoginSession) {
        viewModelScope.launch {
            _connection.value = _connection.value.copy(
                isLoggingIn = true,
                message = "正在完成 Nextcloud 連線…",
            )
            try {
                completeLogin(session)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failLogin(error)
            }
        }
    }

    private suspend fun completeLogin(session: LoginSession) {
        val account = repository.finishLogin(session)
        _connection.value = ConnectionUiState(
            connectedServer = account.serverUrl,
            lastSyncAt = System.currentTimeMillis(),
            message = "Nextcloud 已連線",
        )
    }

    private fun failLogin(error: Throwable) {
        repository.clearPendingLogin()
        _connection.value = _connection.value.copy(
            isLoggingIn = false,
            loginUrl = null,
            message = error.message ?: "無法連線 Nextcloud",
        )
    }

    fun consumeLoginUrl() {
        _connection.value = _connection.value.copy(loginUrl = null)
    }

    fun sync() {
        if (_connection.value.connectedServer == null || _connection.value.isSyncing) return
        viewModelScope.launch {
            _connection.value = _connection.value.copy(isSyncing = true, message = null)
            runCatching { repository.performSync() }
                .onSuccess {
                    _connection.value = _connection.value.copy(
                        isSyncing = false,
                        lastSyncAt = System.currentTimeMillis(),
                        message = "同步完成",
                    )
                }
                .onFailure {
                    _connection.value = _connection.value.copy(
                        isSyncing = false,
                        message = it.message ?: "同步失敗",
                    )
                }
        }
    }

    fun logout() = launchAction("已登出；本機資料仍保留") { repository.logout() }.also {
        _connection.value = ConnectionUiState()
    }

    fun saveCourse(course: Course) = launchAction("課程已儲存") { repository.saveCourse(course) }
    fun deleteCourse(course: Course) = launchAction("課程已刪除") { repository.deleteCourse(course) }
    fun saveSlot(slot: TimetableSlot) = launchAction("課表已更新") { repository.saveSlot(slot) }
    fun deleteSlot(slot: TimetableSlot) = launchAction("課堂已刪除") { repository.deleteSlot(slot) }
    fun saveAgenda(item: AgendaItem) = launchAction("日程已儲存") { repository.saveAgenda(item) }
    fun deleteAgenda(item: AgendaItem) = launchAction("日程已刪除") { repository.deleteAgenda(item) }
    fun setCompleted(item: AgendaItem, completed: Boolean) = launchAction(null) {
        repository.setAgendaCompleted(item, completed)
    }

    fun clearMessage() {
        _connection.value = _connection.value.copy(message = null)
    }

    private fun launchAction(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { successMessage?.let { message ->
                    _connection.value = _connection.value.copy(message = message)
                } }
                .onFailure {
                    _connection.value = _connection.value.copy(message = it.message ?: "操作失敗")
                }
        }
    }
}

