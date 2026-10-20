package com.turboo.applock.ui

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.turboo.applock.Permissions
import com.turboo.applock.R
import com.turboo.applock.service.ForegroundPollService
import com.turboo.applock.ui.theme.AppLockTheme
import com.turboo.applock.ui.theme.isDark
import com.turboo.applock.update.UpdateInstaller

private enum class Screen { ONBOARDING, HOME, SETTINGS }

/** FragmentActivity rather than ComponentActivity: BiometricPrompt requires one. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val vm: AppListViewModel = viewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()
            val query by vm.query.collectAsStateWithLifecycle()
            val updateState by vm.updateState.collectAsStateWithLifecycle()

            // null while the preference is still being read. Rendering the list in
            // that window would show the settings to someone who should not see them.
            val selfLock by vm.selfLockEnabled.collectAsStateWithLifecycle(initialValue = null)

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
                var unlocked by remember { mutableStateOf(false) }

                // Leaving AppLock re-locks it, so returning asks again.
                LifecycleStartEffect(Unit) {
                    onStopOrDispose { unlocked = false }
                }

                when {
                    selfLock == null -> Surface(Modifier.fillMaxSize()) {}
                    selfLock == false || unlocked ->
                        UnlockedApp(vm, state, query, updateState, selfLock == true)
                    else -> SelfLockGate(themeMode = state.themeMode) { unlocked = true }
                }
            }
        }
    }

    @Composable
    private fun UnlockedApp(
        vm: AppListViewModel,
        state: AppListUiState,
        query: String,
        updateState: UpdateState,
        selfLockEnabled: Boolean,
    ) {
        // Permission state can only change outside the app, so it is re-read
        // every time this screen comes back to the foreground.
        var permissions by remember { mutableStateOf(readPermissions()) }
        LifecycleResumeEffect(Unit) {
            permissions = readPermissions()
            // No accessibility service but usage access granted: run the poller.
            ForegroundPollService.startIfNeeded(this@MainActivity)
            onPauseOrDispose { }
        }

        // Start on setup when the app cannot actually lock anything yet.
        var screen by rememberSaveable {
            mutableStateOf(if (permissions.allCriticalGranted) Screen.HOME else Screen.ONBOARDING)
        }

        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

        when (screen) {
            Screen.ONBOARDING -> OnboardingScreen(
                permissions = permissions,
                onFixAccessibility = { Permissions.openAccessibilitySettings(this) },
                onFixUsageAccess = { Permissions.openUsageAccessSettings(this) },
                onFixOverlay = { Permissions.openOverlaySettings(this) },
                onFixBattery = { Permissions.requestIgnoreBatteryOptimizations(this) },
                onSkip = { screen = Screen.HOME },
            )

            Screen.HOME -> HomeScreen(
                state = state,
                query = query,
                updateState = updateState,
                onQueryChange = vm::onQueryChange,
                onFilterChange = vm::setFilter,
                onToggleLock = vm::toggleLock,
                onOpenSettings = { screen = Screen.SETTINGS },
                onDownloadUpdate = vm::downloadUpdate,
                onInstallUpdate = ::installUpdate,
                onDismissUpdate = vm::dismissUpdate,
            )

            Screen.SETTINGS -> SettingsScreen(
                state = state,
                selfLockEnabled = selfLockEnabled,
                versionName = installedVersionName(),
                permissionsComplete = permissions.allCriticalGranted,
                onBack = { screen = Screen.HOME },
                onRelockDelayChange = vm::setRelockDelay,
                onThemeModeChange = vm::setThemeMode,
                onSelfLockChange = vm::setSelfLockEnabled,
                onCheckForUpdates = { vm.checkForUpdate(userInitiated = true); screen = Screen.HOME },
                onOpenSetup = { screen = Screen.ONBOARDING },
            )
        }
    }

    @Composable
    private fun SelfLockGate(
        themeMode: com.turboo.applock.data.ThemeMode,
        onUnlocked: () -> Unit,
    ) {
        LockScreen(
            appLabel = getString(R.string.app_name),
            appIcon = null,
            themeMode = themeMode,
            onUnlockClick = { promptForSelfUnlock(onUnlocked) },
        )

        // Raise the prompt on resume rather than on composition: when returning from
        // the background this branch is re-composed while the activity is still
        // stopped, and a prompt asked for then is simply swallowed, leaving the user
        // to tap Unlock by hand.
        var prompted by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            if (!prompted) {
                prompted = true
                promptForSelfUnlock(onUnlocked)
            }
            onPauseOrDispose { prompted = false }
        }
    }

    /** Android always confirms a sideloaded install; this only gets us to that dialog. */
    private fun installUpdate(apk: java.io.File) {
        if (UpdateInstaller.canInstall(this)) {
            UpdateInstaller.install(this, apk)
        } else {
            UpdateInstaller.openInstallPermissionSettings(this)
        }
    }

    private fun installedVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private fun promptForSelfUnlock(onUnlocked: () -> Unit) {
        promptForUnlock(
            title = getString(R.string.unlock_title, getString(R.string.app_name)),
            subtitle = getString(R.string.unlock_subtitle),
            onSuccess = onUnlocked,
            // Close rather than go home: the user came from somewhere, send them back.
            onCancelled = { finish() },
            onUnavailable = onUnlocked,
        )
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
