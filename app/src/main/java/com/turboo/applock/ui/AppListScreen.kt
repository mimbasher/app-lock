package com.turboo.applock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turboo.applock.data.InstalledApp

private val RELOCK_OPTIONS = listOf(
    "Immediately" to 0L,
    "After 1 min" to 60_000L,
    "After 5 min" to 300_000L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    state: AppListUiState,
    query: String,
    permissions: PermissionState,
    onQueryChange: (String) -> Unit,
    onToggleLock: (String, Boolean) -> Unit,
    onRelockDelayChange: (Long) -> Unit,
    onFixAccessibility: () -> Unit,
    onFixOverlay: () -> Unit,
    onFixBattery: () -> Unit,
    onFixUsageAccess: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AppLock") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (!permissions.allCriticalGranted || !permissions.batteryExempt) {
                item {
                    SetupCard(
                        permissions = permissions,
                        onFixAccessibility = onFixAccessibility,
                        onFixOverlay = onFixOverlay,
                        onFixBattery = onFixBattery,
                        onFixUsageAccess = onFixUsageAccess,
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Re-lock",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RELOCK_OPTIONS.forEach { (label, millis) ->
                            FilterChip(
                                selected = state.relockDelayMillis == millis,
                                onClick = { onRelockDelayChange(millis) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search apps") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.loading) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(state.apps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    locked = app.packageName in state.lockedPackages,
                    onToggle = { onToggleLock(app.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    locked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (app.icon != null) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(checked = locked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SetupCard(
    permissions: PermissionState,
    onFixAccessibility: () -> Unit,
    onFixOverlay: () -> Unit,
    onFixBattery: () -> Unit,
    onFixUsageAccess: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Finish setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Locking cannot work until these are granted.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))

            if (!permissions.accessibility) {
                SetupStep(
                    title = "Enable the app watcher",
                    detail = "Accessibility → AppLock app watcher. This is how AppLock knows which app you just opened.",
                    onClick = onFixAccessibility,
                )
            }
            if (!permissions.accessibility && !permissions.usageAccess) {
                SetupStep(
                    title = "Or grant usage access instead",
                    detail = "A slower fallback that polls which app is in front. Use it only if you would rather not enable the accessibility service.",
                    onClick = onFixUsageAccess,
                )
            }
            if (!permissions.overlay) {
                SetupStep(
                    title = "Allow display over other apps",
                    detail = "Required so the lock screen can appear on top of the app you opened.",
                    onClick = onFixOverlay,
                )
            }
            if (!permissions.batteryExempt) {
                SetupStep(
                    title = "Turn off battery optimisation",
                    detail = "Optional, but Android may otherwise kill the watcher in the background.",
                    onClick = onFixBattery,
                )
            }
        }
    }
}

@Composable
private fun SetupStep(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(detail, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onClick) { Text("Open settings") }
    }
}
