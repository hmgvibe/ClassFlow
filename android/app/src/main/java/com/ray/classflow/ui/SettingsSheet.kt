package com.ray.classflow.ui

import com.ray.classflow.i18n.UiText

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
                UiText.TEXT_6329F21C41.text(),
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
                    connection.connectedServer ?: UiText.TEXT_5B1A352ADF.text(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        conflicts > 0 -> UiText.TEXT_73F07F4BEB.text(conflicts)
                        pendingChanges > 0 -> UiText.TEXT_C1B363108F.text(pendingChanges)
                        connection.connectedServer != null -> UiText.TEXT_524FD4116D.text()
                        else -> UiText.TEXT_758F326BD8.text()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (conflicts > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                connection.lastSyncAt?.let {
                    Text(
                        UiText.TEXT_368BABBF26.text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm").withLocale(UiText.displayLocale()))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (conflicts > 0) {
                    OutlinedButton(onClick = onConflicts, modifier = Modifier.fillMaxWidth()) {
                        Text(UiText.TEXT_FFD84031E5.text(conflicts))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (connection.connectedServer == null) {
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                        Text(UiText.TEXT_7769DE4349.text())
                    }
                } else {
                    Button(
                        onClick = onSync,
                        enabled = !connection.isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (connection.isSyncing) UiText.TEXT_915D375A0B.text() else UiText.TEXT_098D06ECAC.text())
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                        Text(UiText.TEXT_8FA2CB0892.text())
                    }
                }
            } else {
                Text(UiText.TEXT_5E7D6AE4CE.text(), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    UiText.TEXT_378D4D7E70.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    UiText.TEXT_A6515B95EB.text(),
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
                        UiText.TEXT_9BD27DDB95.text(com.ray.classflow.BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Android 8.0+", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
