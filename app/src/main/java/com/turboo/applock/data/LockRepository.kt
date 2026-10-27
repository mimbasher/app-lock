package com.turboo.applock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

class LockRepository(private val context: Context) {
    val lockedPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_LOCKED] ?: emptySet() }

    val relockDelayMillis: Flow<Long> =
        context.dataStore.data.map { it[KEY_RELOCK_DELAY] ?: 0L }

    val selfLockEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SELF_LOCK] ?: true }

    val settingsTipDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SETTINGS_TIP] ?: false }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { ThemeMode.fromName(it[KEY_THEME_MODE]) }

    @Volatile
    var cachedThemeMode: ThemeMode = ThemeMode.SYSTEM

    @Volatile
    var cachedLockedPackages: Set<String>? = null

    suspend fun setLocked(packageName: String, locked: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LOCKED]?.toMutableSet() ?: mutableSetOf()
            if (locked) current.add(packageName) else current.remove(packageName)
            prefs[KEY_LOCKED] = current
        }
    }

    suspend fun setRelockDelay(millis: Long) {
        context.dataStore.edit { it[KEY_RELOCK_DELAY] = millis }
    }

    suspend fun setSelfLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SELF_LOCK] = enabled }
    }

    suspend fun setSettingsTipDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[KEY_SETTINGS_TIP] = dismissed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    private companion object {
        val KEY_LOCKED = stringSetPreferencesKey("locked_packages")
        val KEY_RELOCK_DELAY = longPreferencesKey("relock_delay_millis")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SELF_LOCK = booleanPreferencesKey("self_lock_enabled")
        val KEY_SETTINGS_TIP = booleanPreferencesKey("settings_tip_dismissed")
    }
}
