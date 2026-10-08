package com.ray.classflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ray.classflow.BuildConfig
import com.ray.classflow.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    connection: ConnectionUiState,
    pendingChanges: Int,
    conflicts: Int,
    onSync: () -> Unit,
    onConnect: () -> Unit,
    onLogout: () -> Unit,
    onConflicts: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
        ) {
            Text(
                "設定",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))
            if (BuildConfig.CLOUD_SYNC_ENABLED) {
                Text(
                    "NEXTCLOUD",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    connection.connectedServer ?: "尚未連線",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        conflicts > 0 -> "$conflicts 筆同步衝突需要處理"
                        pendingChanges > 0 -> "$pendingChanges 筆變更等待同步"
                        connection.connectedServer != null -> "資料已同步"
                        else -> "目前只儲存在這台裝置"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (conflicts > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                connection.lastSyncAt?.let {
                    Text(
                        "上次同步 ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (conflicts > 0) {
                    OutlinedButton(onClick = onConflicts, modifier = Modifier.fillMaxWidth()) {
                        Text("處理同步衝突（$conflicts）")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (connection.connectedServer == null) {
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                        Text("連接 Nextcloud")
                    }
                } else {
                    Button(
                        onClick = onSync,
                        enabled = !connection.isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (connection.isSyncing) "同步中…" else "立即同步")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                        Text("登出此裝置")
                    }
                }
            } else {
                Text("本機儲存", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "課表、日程與學習計劃只儲存在這台裝置，不需要帳號，也不會連接 Nextcloud。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "與原版資料獨立。卸載 App 或清除資料後，本機內容將無法恢復。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Medium)
                    Text(
                        "版本 ${com.ray.classflow.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Android 8.0+", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
