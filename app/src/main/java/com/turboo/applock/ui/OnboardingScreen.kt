package com.turboo.applock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * First-run setup. Replaces the old red card: these grants are not optional
 * extras, they are the whole mechanism, so they get a screen rather than a
 * warning box above the list.
 */
@Composable
fun OnboardingScreen(
    permissions: PermissionState,
    onFixAccessibility: () -> Unit,
    onFixUsageAccess: () -> Unit,
    onFixOverlay: () -> Unit,
    onFixBattery: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Text("Set up AppLock", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android has no built-in way to lock an app, so AppLock needs two " +
                    "permissions to do it. Nothing leaves your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            SetupStep(
                icon = Icons.Filled.Visibility,
                title = "Let AppLock see which app opened",
                detail = "Turn on \"AppLock app watcher\" under Accessibility. This is the " +
                    "only way to know what you just opened — AppLock never reads screen contents.",
                done = permissions.accessibility,
                action = "Open Accessibility settings",
                onClick = onFixAccessibility,
            )

            if (!permissions.accessibility) {
                SetupStep(
                    icon = Icons.Filled.Visibility,
                    title = "Or grant usage access instead",
                    detail = "A slower fallback that checks the foreground app a few times a " +
                        "second. Use it only if you would rather not enable an accessibility service.",
                    done = permissions.usageAccess,
                    action = "Open Usage access",
                    onClick = onFixUsageAccess,
                    optional = true,
                )
            }

            SetupStep(
                icon = Icons.Filled.Layers,
                title = "Allow display over other apps",
                detail = "Without this, Android blocks the lock screen from appearing on top " +
                    "of the app you just opened.",
                done = permissions.overlay,
                action = "Open settings",
                onClick = onFixOverlay,
            )

            SetupStep(
                icon = Icons.Filled.BatterySaver,
                title = "Turn off battery optimisation",
                detail = "Optional, but Android may otherwise stop the watcher in the " +
                    "background and locking will quietly stop working.",
                done = permissions.batteryExempt,
                action = "Open settings",
                onClick = onFixBattery,
                optional = true,
            )

            Spacer(Modifier.height(16.dp))

            if (permissions.allCriticalGranted) {
                Button(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("Done — choose apps to lock")
                }
            } else {
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now")
                }
                Text(
                    "Locking will not work until the two required permissions are granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetupStep(
    icon: ImageVector,
    title: String,
    detail: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit,
    optional: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (done) Icons.Filled.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (optional) {
                    Text(
                        "Optional",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!done) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onClick) { Text(action) }
                }
            }
        }
    }
}
