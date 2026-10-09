package com.ray.classflow.ui

import com.ray.classflow.i18n.UiText

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ray.classflow.BuildConfig
import com.ray.classflow.ClassFlowApplication
import com.ray.classflow.model.AgendaItem
import com.ray.classflow.model.ClassFlowState
import com.ray.classflow.model.Course
import com.ray.classflow.model.StudyPlan
import com.ray.classflow.model.TimetableSlot
import com.ray.classflow.sync.ConflictChoice
import com.ray.classflow.sync.ConflictItem
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

data class ConflictResolutionUiState(val resolvingKey: String? = null, val error: String? = null)

class ClassFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ClassFlowApplication).repository
    val data: StateFlow<ClassFlowState> =
        repository.state.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ClassFlowState(),
        )
    val conflicts: StateFlow<List<ConflictItem>> =
        repository.conflicts.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    private val _conflictResolution = MutableStateFlow(ConflictResolutionUiState())
    val conflictResolution = _conflictResolution.asStateFlow()

    fun clearConflictError() {
        _conflictResolution.value = _conflictResolution.value.copy(error = null)
    }

    fun resolveConflict(item: ConflictItem, choice: ConflictChoice) {
        if (_conflictResolution.value.resolvingKey != null) return
        _conflictResolution.value = ConflictResolutionUiState(resolvingKey = item.key)
        viewModelScope.launch {
            try {
                repository.resolveConflict(item, choice)
                _connection.value =
                    _connection.value.copy(
                        message =
                            when (choice) {
                                ConflictChoice.LOCAL -> UiText.TEXT_20B83CEC7C.text()
                                ConflictChoice.SERVER -> UiText.TEXT_D5C66C5B38.text()
                                ConflictChoice.BOTH -> UiText.TEXT_B0EDA066FD.text()
                            }
                    )
                _conflictResolution.value = ConflictResolutionUiState()
            } catch (error: CancellationException) {
                _conflictResolution.value = ConflictResolutionUiState()
                throw error
            } catch (error: Throwable) {
                _conflictResolution.value =
                    ConflictResolutionUiState(error = error.message ?: UiText.TEXT_FFE4CF3DF7.text())
            }
        }
    }

    private val _connection =
        MutableStateFlow(ConnectionUiState(connectedServer = repository.account()?.serverUrl))
    val connection: StateFlow<ConnectionUiState> = _connection.asStateFlow()

    init {
        if (BuildConfig.CLOUD_SYNC_ENABLED) {
            if (repository.account() == null) {
                repository.pendingLogin()?.let(::resumeLogin)
            } else {
                repository.clearPendingLogin()
            }
        }
    }

    fun connect(serverUrl: String) {
        if (!BuildConfig.CLOUD_SYNC_ENABLED) return
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
            _connection.value =
                _connection.value.copy(
                    isLoggingIn = true,
                    message = UiText.TEXT_50873069F7.text(),
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
        _connection.value =
            ConnectionUiState(
                connectedServer = account.serverUrl,
                lastSyncAt = System.currentTimeMillis(),
                message = UiText.TEXT_A46C5D0648.text(),
            )
    }

    private fun failLogin(error: Throwable) {
        repository.clearPendingLogin()
        _connection.value =
            _connection.value.copy(
                isLoggingIn = false,
                loginUrl = null,
                message = error.message ?: UiText.TEXT_E6C5A59DAF.text(),
            )
    }

    fun consumeLoginUrl() {
        _connection.value = _connection.value.copy(loginUrl = null)
    }

    fun sync() {
        if (!BuildConfig.CLOUD_SYNC_ENABLED) return
        if (_connection.value.connectedServer == null || _connection.value.isSyncing) return
        viewModelScope.launch {
            _connection.value = _connection.value.copy(isSyncing = true, message = null)
            runCatching { repository.performSync() }
                .onSuccess { allSupported ->
                    _connection.value =
                        _connection.value.copy(
                            isSyncing = false,
                            lastSyncAt = System.currentTimeMillis(),
                            message =
                                when {
                                    !allSupported -> UiText.TEXT_293026F9D9.text()
                                    conflicts.value.isNotEmpty() -> UiText.TEXT_51ACF46BF5.text()
                                    else -> UiText.TEXT_D28A0438F5.text()
                                },
                        )
                }
                .onFailure {
                    _connection.value =
                        _connection.value.copy(
                            isSyncing = false,
                            message = it.message ?: UiText.TEXT_6D3579D857.text(),
                        )
                }
        }
    }

    fun logout() =
        launchAction(UiText.TEXT_1FC79ACBA4.text()) { repository.logout() }
            .also {
                _connection.value = ConnectionUiState()
            }

    fun saveCourse(course: Course) = launchAction(UiText.TEXT_2E0A99567D.text()) { repository.saveCourse(course) }

    fun deleteCourse(course: Course) = launchAction(UiText.TEXT_FE3F774A1D.text()) { repository.deleteCourse(course) }

    fun saveSlot(slot: TimetableSlot) = launchAction(UiText.TEXT_0930D822C0.text()) { repository.saveSlot(slot) }

    fun deleteSlot(slot: TimetableSlot) = launchAction(UiText.TEXT_89EE7E1992.text()) { repository.deleteSlot(slot) }

    fun saveAgenda(item: AgendaItem) = launchAction(UiText.TEXT_E424ABA4F0.text()) { repository.saveAgenda(item) }

    fun saveStudyPlan(plan: StudyPlan) = launchAction(UiText.TEXT_C36110A7D9.text()) { repository.saveStudyPlan(plan) }

    fun deleteStudyPlan(plan: StudyPlan) =
        launchAction(UiText.TEXT_BD04280ED0.text()) { repository.deleteStudyPlan(plan) }

    fun deleteAgenda(item: AgendaItem) = launchAction(UiText.TEXT_5FA7EC29D7.text()) { repository.deleteAgenda(item) }

    fun setCompleted(item: AgendaItem, completed: Boolean) =
        launchAction(null) {
            repository.setAgendaCompleted(item, completed)
        }

    fun clearMessage() {
        _connection.value = _connection.value.copy(message = null)
    }

    private fun launchAction(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess {
                    successMessage?.let { message ->
                        _connection.value = _connection.value.copy(message = message)
                    }
                }
                .onFailure {
                    _connection.value = _connection.value.copy(message = it.message ?: UiText.TEXT_626C6DBFC2.text())
                }
        }
    }
}
