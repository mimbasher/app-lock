package com.turboo.applock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.turboo.applock.data.InstalledApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppListUiState,
    query: String,
    updateState: UpdateState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onToggleLock: (String, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onDownloadUpdate: (com.turboo.applock.update.AvailableUpdate) -> Unit,
    onInstallUpdate: (java.io.File) -> Unit,
    onDismissUpdate: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AppLock")
                        if (state.lockedCount > 0) {
                            Text(
                                "${state.lockedCount} app${if (state.lockedCount == 1) "" else "s"} locked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                UpdateBanner(
                    state = updateState,
                    onDownload = onDownloadUpdate,
                    onInstall = onInstallUpdate,
                    onDismiss = onDismissUpdate,
                )
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search apps") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterOption("All", AppFilter.ALL, state.filter, onFilterChange)
                        FilterOption("Locked", AppFilter.LOCKED, state.filter, onFilterChange)
                        FilterOption("Unlocked", AppFilter.UNLOCKED, state.filter, onFilterChange)
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.apps.isEmpty()) {
                item { EmptyState(query = query, filter = state.filter) }
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
private fun FilterOption(
    label: String,
    value: AppFilter,
    selected: AppFilter,
    onSelect: (AppFilter) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun EmptyState(query: String, filter: AppFilter) {
    val message = when {
        query.isNotBlank() -> "No app matches \"$query\"."
        filter == AppFilter.LOCKED -> "Nothing is locked yet. Flip a switch to lock an app."
        filter == AppFilter.UNLOCKED -> "Every app is locked."
        else -> "No apps found."
    }
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
