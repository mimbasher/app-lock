package com.turboo.applock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.turboo.applock.Permissions
import com.turboo.applock.service.ForegroundPollService
import com.turboo.applock.ui.theme.AppLockTheme
import com.turboo.applock.ui.theme.isDark

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: AppListViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val query by vm.query.collectAsStateWithLifecycle()

            // Status/navigation bar icons have to be re-styled when the choice
            // changes, otherwise dark icons end up on a dark bar.
            val dark = state.themeMode.isDark()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(dark),
                    navigationBarStyle = systemBarStyle(dark),
                )
            }

            AppLockTheme(state.themeMode) {

                // Permission state can only change outside the app, so it is re-read
                // every time this screen comes back to the foreground.
                var permissions by remember { mutableStateOf(readPermissions()) }
                LifecycleResumeEffect(Unit) {
                    permissions = readPermissions()
                    // No accessibility service but usage access granted: run the poller.
                    ForegroundPollService.startIfNeeded(this@MainActivity)
                    onPauseOrDispose { }
                }

                AppListScreen(
                    state = state,
                    query = query,
                    permissions = permissions,
                    onQueryChange = vm::onQueryChange,
                    onToggleLock = vm::toggleLock,
                    onRelockDelayChange = vm::setRelockDelay,
                    onThemeModeChange = vm::setThemeMode,
                    onFixAccessibility = { Permissions.openAccessibilitySettings(this) },
                    onFixOverlay = { Permissions.openOverlaySettings(this) },
                    onFixBattery = { Permissions.requestIgnoreBatteryOptimizations(this) },
                    onFixUsageAccess = { Permissions.openUsageAccessSettings(this) },
                )
            }
        }
    }

    private fun systemBarStyle(dark: Boolean): SystemBarStyle =
        if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            )
        }

    private fun readPermissions() = PermissionState(
        accessibility = Permissions.isAccessibilityEnabled(this),
        overlay = Permissions.canDrawOverlays(this),
        batteryExempt = Permissions.isIgnoringBatteryOptimizations(this),
        usageAccess = Permissions.hasUsageAccess(this),
    )
}

data class PermissionState(
    val accessibility: Boolean,
    val overlay: Boolean,
    val batteryExempt: Boolean,
    val usageAccess: Boolean,
) {
    /** Either watcher will do, but without the overlay grant neither can show a lock screen. */
    val allCriticalGranted: Boolean get() = (accessibility || usageAccess) && overlay
}
