package com.turboo.applock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turboo.applock.update.AvailableUpdate
import java.io.File
import java.util.Locale

/** Only shows once there is something to say; silent while idle or checking. */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onDownload: (AvailableUpdate) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is UpdateState.Idle || state is UpdateState.Checking) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                is UpdateState.Available -> {
                    Text(
                        "Version ${state.update.versionName} is available",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.update.apkSizeBytes > 0) {
                        Text(
                            formatSize(state.update.apkSizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDownload(state.update) }) { Text("Download") }
                        TextButton(onClick = onDismiss) { Text("Not now") }
                    }
                }

                is UpdateState.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "Downloading ${state.update.versionName}…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is UpdateState.ReadyToInstall -> {
                    Text(
                        "Version ${state.update.versionName} is ready",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Android will ask you to confirm the install.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onInstall(state.apk) }) { Text("Install") }
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }

                is UpdateState.Failed -> {
                    Text(state.reason, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }

                else -> Unit
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
