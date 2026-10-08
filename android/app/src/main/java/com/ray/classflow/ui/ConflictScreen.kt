package com.ray.classflow.ui

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
                title = { Text(if (selected == null) "同步衝突" else "比較版本") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = connected && !busy) {
                        Icon(Icons.Default.Refresh, "更新雲端版本")
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
                        Text("請先連接 Nextcloud 才能處理衝突", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            onClearError()
                            confirmation = selected to ConflictChoice.LOCAL
                        },
                        enabled = connected && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(if (resolution.resolvingKey == selected.key) "處理中…" else "使用手機版本")
                    }
                    OutlinedButton(
                        onClick = {
                            onClearError()
                            confirmation = selected to ConflictChoice.SERVER
                        },
                        enabled = connected && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("使用雲端版本")
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
                            Text("保留兩份")
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
                Text("沒有待處理的衝突", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "已選擇的手機版本會在連線後同步",
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
                            Text("正在與 Nextcloud 確認版本…", style = MaterialTheme.typography.bodySmall)
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
                            "${conflicts.size} 筆資料需要你決定保留的版本",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "點選項目比較完整內容。尚未處理的修改會繼續保存在手機。",
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
                                        item.error != null -> "這筆資料無法同步，查看原因"
                                        item.mutation.operation == "delete" -> "手機已刪除，雲端保留了修改"
                                        item.serverDeleted -> "雲端已刪除，手機保留了修改"
                                        else -> "手機與雲端的內容不同"
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
                            "標記「不同」的欄位有差異。選擇版本前，請確認所有內容。",
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
                                "可返回對應頁面修改內容，再使用手機版本重試；也可選擇雲端版本。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        VersionContent(
                            "手機版本",
                            selected.localFields,
                            selected.serverFields,
                            "手機已刪除此${selected.typeLabel}",
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(24.dp))
                        VersionContent(
                            "雲端版本",
                            selected.serverFields,
                            selected.localFields,
                            "雲端已刪除此${selected.typeLabel}",
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
                ConflictChoice.LOCAL -> "使用手機版本"
                ConflictChoice.SERVER -> "使用雲端版本"
                ConflictChoice.BOTH -> "保留兩份"
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
                                        "將再次刪除雲端的這筆資料。雲端目前的修改不會保留。"
                                    item.serverDeleted ->
                                        "將使用手機內容重新建立雲端的這筆資料。" +
                                            if (item.mutation.entityType == "course")
                                                "已被雲端刪除的課堂不會一併恢復。"
                                            else ""
                                    else -> "將以完整的手機內容更新這筆資料。雲端目前的修改不會保留。"
                                }
                            ConflictChoice.SERVER ->
                                "將放棄這筆資料尚未同步的手機修改，並套用雲端內容。" +
                                    if (item.serverDeleted) "雲端已刪除它，因此手機也會移除此項目。" else ""
                            ConflictChoice.BOTH ->
                                "雲端原項目會保留，手機內容會另存為一筆新項目。" +
                                    if (item.mutation.entityType == "course") "只複製這門課程，原課堂的關聯不變。"
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
                    Text("確認")
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } },
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
                        "不同",
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
