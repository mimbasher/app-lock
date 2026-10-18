package com.turboo.applock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.turboo.applock.AppLockApplication
import com.turboo.applock.data.InstalledApp
import com.turboo.applock.data.InstalledApps
import com.turboo.applock.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class AppListUiState(
    val apps: List<InstalledApp> = emptyList(),
    val lockedPackages: Set<String> = emptySet(),
    val relockDelayMillis: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val loading: Boolean = true,
)

class AppListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppLockApplication.repository()

    private val allApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val loading = MutableStateFlow(true)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val settings = combine(
        repository.relockDelayMillis,
        repository.themeMode,
    ) { delay, theme -> delay to theme }

    val uiState: StateFlow<AppListUiState> =
        combine(
            allApps,
            repository.lockedPackages,
            settings,
            _query,
            loading,
        ) { apps, locked, (delay, theme), query, isLoading ->
            val filtered = if (query.isBlank()) {
                apps
            } else {
                apps.filter { it.label.contains(query, ignoreCase = true) }
            }
            AppListUiState(
                // Locked apps float to the top so the current setup is visible at a glance.
                apps = filtered.sortedWith(
                    compareByDescending<InstalledApp> { it.packageName in locked }
                        .thenBy { it.label.lowercase() }
                ),
                lockedPackages = locked,
                relockDelayMillis = delay,
                themeMode = theme,
                loading = isLoading,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListUiState())

    init {
        viewModelScope.launch {
            allApps.value = InstalledApps.load(getApplication())
            loading.value = false
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
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
}
