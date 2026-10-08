package com.ray.classflow.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ray.classflow.BuildConfig
import com.ray.classflow.ui.screens.AgendaScreen
import com.ray.classflow.ui.screens.StudyPlanScreen
import com.ray.classflow.ui.screens.TimetableScreen

private enum class MainTab(val label: String) {
    TIMETABLE("課表"),
    AGENDA("日程"),
    STUDY("學習計劃"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassFlowApp(viewModel: ClassFlowViewModel) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val conflictResolution by viewModel.conflictResolution.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showConflicts by remember { mutableStateOf(false) }
    var showLogin by
        remember(connection.connectedServer) {
            mutableStateOf(BuildConfig.CLOUD_SYNC_ENABLED && connection.connectedServer == null)
        }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(connection.message) {
        connection.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (BuildConfig.CLOUD_SYNC_ENABLED && showConflicts) {
        ConflictScreen(
            conflicts = conflicts,
            resolution = conflictResolution,
            isSyncing = connection.isSyncing,
            connected = connection.connectedServer != null,
            snackbar = snackbar,
            onRefresh = viewModel::sync,
            onResolve = viewModel::resolveConflict,
            onClearError = viewModel::clearConflictError,
            onBack = { showConflicts = false },
        )
        return
    }

    if (BuildConfig.CLOUD_SYNC_ENABLED && showLogin) {
        LoginScreen(
            isLoading = connection.isLoggingIn,
            onConnect = viewModel::connect,
            onOffline = { showLogin = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(MainTab.entries[selectedTab].label) },
                actions = {
                    if (BuildConfig.CLOUD_SYNC_ENABLED && connection.connectedServer != null) {
                        IconButton(onClick = viewModel::sync, enabled = !connection.isSyncing) {
                            BadgedBox(
                                badge = {
                                    if (data.pendingChanges > 0 || data.conflicts > 0) {
                                        Badge {
                                            Text(data.pendingChanges.coerceAtMost(99).toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "同步")
                            }
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("課表") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("日程") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text("學習計劃") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (selectedTab) {
            0 ->
                TimetableScreen(
                    state = data,
                    contentPadding = padding,
                    onSaveCourse = viewModel::saveCourse,
                    onDeleteCourse = viewModel::deleteCourse,
                    onSaveSlot = viewModel::saveSlot,
                    onDeleteSlot = viewModel::deleteSlot,
                )
            1 ->
                AgendaScreen(
                    state = data,
                    contentPadding = padding,
                    onSave = viewModel::saveAgenda,
                    onDelete = viewModel::deleteAgenda,
                    onSetCompleted = viewModel::setCompleted,
                )
            else ->
                StudyPlanScreen(
                    state = data,
                    contentPadding = padding,
                    onSave = viewModel::saveStudyPlan,
                    onDelete = viewModel::deleteStudyPlan,
                )
        }
    }

    if (showSettings) {
        SettingsSheet(
            connection = connection,
            pendingChanges = data.pendingChanges,
            conflicts = data.conflicts,
            onSync = viewModel::sync,
            onConnect = {
                showSettings = false
                showLogin = true
            },
            onLogout = {
                viewModel.logout()
                showSettings = false
                showLogin = true
            },
            onConflicts = {
                showSettings = false
                showConflicts = true
                viewModel.clearConflictError()
            },
            onDismiss = { showSettings = false },
        )
    }
}
