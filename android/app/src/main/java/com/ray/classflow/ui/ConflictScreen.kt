package com.ray.classflow.ui

import com.ray.classflow.i18n.UiText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ray.classflow.sync.ConflictChoice
import com.ray.classflow.sync.ConflictField
import com.ray.classflow.sync.ConflictItem
import com.ray.classflow.ui.theme.courseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictScreen(
    conflicts: List<ConflictItem>,
    resolution: ConflictResolutionUiState,
    isSyncing: Boolean,
    connected: Boolean,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onResolve: (ConflictItem, ConflictChoice) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<Pair<ConflictItem, ConflictChoice>?>(null) }
    val selected = conflicts.find { it.key == selectedKey }
    val busy = resolution.resolvingKey != null || isSyncing
    val back = {
        if (selectedKey != null) {
            selectedKey = null
            onClearError()
        } else onBack()
    }
    BackHandler(onBack = back)
    LaunchedEffect(conflicts) {
        if (selectedKey != null && selected == null) selectedKey = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) UiText.TEXT_58848B1751.text() else UiText.TEXT_F429F6DC7F.text()) },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, UiText.TEXT_11D0241540.text()) }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = connected && !busy) {
                        Icon(Icons.Default.Refresh, UiText.TEXT_68D1964BB2.text())
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected != null) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!connected)
                        Text(UiText.TEXT_D035CD1DA2.text(), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            onClearError()
                            confirmation = selected to ConflictChoice.LOCAL
                        },
                        enabled = connected && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (resolution.resolvingKey == selected.key) UiText.TEXT_1E038F9B55.text() else UiText.TEXT_D2B2966BBD.text())
                    }
                    OutlinedButton(
                        onClick = {
                            onClearError()
                            confirmation = selected to ConflictChoice.SERVER
                        },
                        enabled = connected && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(UiText.TEXT_216EFB46E5.text())
                    }
                    if (selected.canKeepBoth) {
                        TextButton(
                            onClick = {
                                onClearError()
                                confirmation = selected to ConflictChoice.BOTH
                            },
                            enabled = connected && !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(UiText.TEXT_E3072F94E5.text())
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (conflicts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(UiText.TEXT_8B275F9FA3.text(), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    UiText.TEXT_B2CEACBF7C.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)
            ) {
                item {
                    if (busy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(UiText.TEXT_F1FF00BD65.text(), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    resolution.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (selected == null) {
                    item {
                        Text(
                            UiText.TEXT_E3FB321CDE.text(conflicts.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            UiText.TEXT_C84E06EBCD.text(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    items(conflicts, key = { it.key }) { item ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !busy, role = Role.Button) {
                                        selectedKey = item.key
                                        onClearError()
                                    }
                                    .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.typeLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    when {
                                        item.error != null -> UiText.TEXT_93C2A3507B.text()
                                        item.mutation.operation == "delete" -> UiText.TEXT_21C0511622.text()
                                        item.serverDeleted -> UiText.TEXT_24FDB1FA27.text()
                                        else -> UiText.TEXT_92A6B029D6.text()
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            selected.typeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            UiText.TEXT_1A06FF541C.text(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        selected.error?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                UiText.TEXT_35F94FBC26.text(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        VersionContent(
                            UiText.TEXT_1D61F8244A.text(),
                            selected.localFields,
                            selected.serverFields,
                            UiText.TEXT_1A0914D4D7.text(selected.typeLabel),
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(24.dp))
                        VersionContent(
                            UiText.TEXT_2A56C7AA0D.text(),
                            selected.serverFields,
                            selected.localFields,
                            UiText.TEXT_E87D5EAF02.text(selected.typeLabel),
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    confirmation?.let { (item, choice) ->
        val label =
            when (choice) {
                ConflictChoice.LOCAL -> UiText.TEXT_D2B2966BBD.text()
                ConflictChoice.SERVER -> UiText.TEXT_216EFB46E5.text()
                ConflictChoice.BOTH -> UiText.TEXT_E3072F94E5.text()
            }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(label) },
            text = {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (choice) {
                            ConflictChoice.LOCAL ->
                                when {
                                    item.mutation.operation == "delete" ->
                                        UiText.TEXT_2339D0721B.text()
                                    item.serverDeleted ->
                                        UiText.TEXT_31D8E73225.text() +
                                            if (item.mutation.entityType == "course")
                                                UiText.TEXT_4F7DB9EC77.text()
                                            else ""
                                    else -> UiText.TEXT_A48634FC70.text()
                                }
                            ConflictChoice.SERVER ->
                                UiText.TEXT_5B2C189DF4.text() +
                                    if (item.serverDeleted) UiText.TEXT_A4CBA0DCDC.text() else ""
                            ConflictChoice.BOTH ->
                                UiText.TEXT_E9B0DA6BBB.text() +
                                    if (item.mutation.entityType == "course") UiText.TEXT_4DCF3AD011.text()
                                    else ""
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        onResolve(item, choice)
                    },
                    enabled = !busy,
                ) {
                    Text(UiText.TEXT_86A07295C5.text())
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(UiText.TEXT_4D0B4688C7.text()) } },
        )
    }
}

@Composable
private fun VersionContent(
    title: String,
    fields: List<ConflictField>,
    other: List<ConflictField>,
    deletedText: String,
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
    if (fields.isEmpty())
        Text(
            deletedText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    fields.forEach { field ->
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    field.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (other.find { it.label == field.label } != field) {
                    Text(
                        UiText.TEXT_B3D980B349.text(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (field.colorKey != null) {
                Box(Modifier.size(20.dp).background(courseColor(field.colorKey), CircleShape))
            } else {
                Text(field.value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
