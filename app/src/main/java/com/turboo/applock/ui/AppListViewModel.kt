package com.turboo.applock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turboo.applock.AppLockApplication
import com.turboo.applock.data.InstalledApp
import com.turboo.applock.data.InstalledApps
import com.turboo.applock.data.ThemeMode
import com.turboo.applock.update.AvailableUpdate
import com.turboo.applock.update.UpdateChecker
import com.turboo.applock.update.UpdateInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppFilter { ALL, LOCKED, UNLOCKED }

/** Where the update flow has got to, so the banner can say something useful. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate) : UpdateState
    data class ReadyToInstall(val update: AvailableUpdate, val apk: File) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

data class AppListUiState(
    val apps: List<InstalledApp> = emptyList(),
    val lockedPackages: Set<String> = emptySet(),
    val relockDelayMillis: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val filter: AppFilter = AppFilter.ALL,
    val loading: Boolean = true,
) {
    val lockedCount: Int get() = lockedPackages.size
}

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppLockApplication.repository()

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)
    private val _filter = MutableStateFlow(AppFilter.ALL)

    /**
     * Read directly rather than folded into [uiState]: the gate in MainActivity has
     * to distinguish "not loaded yet" from "off", or it would flash the settings
     * list before the preference arrives.
     */
    val selfLockEnabled = repository.selfLockEnabled

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private data class Settings(val relockDelay: Long, val theme: ThemeMode, val filter: AppFilter)

    // combine() takes at most five flows, so the settings are folded into one.
    private val settings = combine(
        repository.relockDelayMillis,
        repository.themeMode,
        _filter,
    ) { delay, theme, filter -> Settings(delay, theme, filter) }

    val uiState: StateFlow<AppListUiState> =
        combine(
            allApps,
            repository.lockedPackages,
            settings,
            _query,
            loading,
        ) { apps, locked, (delay, theme, filter), query, isLoading ->
            val matching = apps
                .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .filter {
                    when (filter) {
                        AppFilter.ALL -> true
                        AppFilter.LOCKED -> it.packageName in locked
                        AppFilter.UNLOCKED -> it.packageName !in locked
                    }
                }

            AppListUiState(
                // Locked apps float to the top so the current setup is visible at a glance.
                apps = matching.sortedWith(
                    compareByDescending<InstalledApp> { it.packageName in locked }
                        .thenBy { it.label.lowercase() }
                ),
                lockedPackages = locked,
                relockDelayMillis = delay,
                themeMode = theme,
                filter = filter,
                loading = isLoading,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    init {
        viewModelScope.launch {
            allApps.value = InstalledApps.load(getApplication())
            loading.value = false
        }
        checkForUpdate()
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun setFilter(filter: AppFilter) {
        _filter.value = filter
    }

    fun toggleLock(packageName: String, locked: Boolean) {
        viewModelScope.launch { repository.setLocked(packageName, locked) }
    }

    fun setRelockDelay(millis: Long) {
        viewModelScope.launch { repository.setRelockDelay(millis) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setSelfLockEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setSelfLockEnabled(enabled) }
    }

    /** Silent on failure unless asked explicitly: a failed check on launch is not news. */
    fun checkForUpdate(userInitiated: Boolean = false) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val update = UpdateChecker.check(getApplication())
            _updateState.value = when {
                update != null -> UpdateState.Available(update)
                userInitiated -> UpdateState.Failed("You are on the latest version.")
                else -> UpdateState.Idle
            }
        }
    }

    fun downloadUpdate(update: AvailableUpdate) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(update)
            val apk = UpdateInstaller.download(getApplication(), update)
            _updateState.value = if (apk != null) {
                UpdateState.ReadyToInstall(update, apk)
            } else {
                UpdateState.Failed("Download failed. Check your connection and try again.")
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }
}
