package com.turboo.applock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.turboo.applock.BuildConfig
import com.turboo.applock.data.ThemeMode

private val RELOCK_OPTIONS = listOf(
    "Immediately" to 0L,
    "After 1 min" to 60_000L,
    "After 5 min" to 300_000L,
)

private val THEME_OPTIONS = listOf(
    "System" to ThemeMode.SYSTEM,
    "Light" to ThemeMode.LIGHT,
    "Dark" to ThemeMode.DARK,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppListUiState,
    selfLockEnabled: Boolean,
    versionName: String,
    permissionsComplete: Boolean,
    onBack: () -> Unit,
    onRelockDelayChange: (Long) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSelfLockChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (!permissionsComplete) {
                SectionHeader("Setup incomplete")
                SettingRow(
                    title = "Finish setting up",
                    subtitle = "Locking will not work until the required permissions are granted.",
                    onClick = onOpenSetup,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }

            SectionHeader("Re-lock")
            Text(
                "How long an unlocked app stays open before AppLock asks again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RELOCK_OPTIONS.forEach { (label, millis) ->
                    FilterChip(
                        selected = state.relockDelayMillis == millis,
                        onClick = { onRelockDelayChange(millis) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            SectionHeader("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THEME_OPTIONS.forEach { (label, mode) ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            SectionHeader("Security")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lock AppLock itself", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Otherwise anyone holding your unlocked phone can open this screen " +
                            "and switch a lock off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = selfLockEnabled, onCheckedChange = onSelfLockChange)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            SectionHeader("About")
            // Only the sideloaded flavour can update itself; a Play build leaves
            // that to Play, so it shows the version without offering a check.
            if (BuildConfig.SELF_UPDATE) {
                SettingRow(
                    title = "Check for updates",
                    subtitle = "Version $versionName",
                    onClick = onCheckForUpdates,
                )
            } else {
                InfoRow(title = "Version $versionName", subtitle = "Updates arrive through Google Play.")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
