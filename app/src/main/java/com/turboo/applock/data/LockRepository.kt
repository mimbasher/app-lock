package com.turboo.applock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

/**
 * Stores which packages are locked and how long an unlock stays valid.
 * Everything lives on-device; nothing is ever sent anywhere.
 */
class LockRepository(private val context: Context) {

    val lockedPackages: Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_LOCKED] ?: emptySet() }

    /** 0 means "re-lock as soon as the user leaves the app". */
    val relockDelayMillis: Flow<Long> =
        context.dataStore.data.map { it[KEY_RELOCK_DELAY] ?: 0L }

    val themeMode: Flow<ThemeMode> =
        context.dataStore.data.map { ThemeMode.fromName(it[KEY_THEME_MODE]) }

    /**
     * Last value seen by [AppLockApplication]'s collector. The lock screen has to
     * theme itself the instant it is created, and a suspending read there would
     * mean a visible flash of the wrong colours over the app being covered.
     */
    @Volatile
    var cachedThemeMode: ThemeMode = ThemeMode.SYSTEM

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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    private companion object {
        val KEY_LOCKED = stringSetPreferencesKey("locked_packages")
        val KEY_RELOCK_DELAY = longPreferencesKey("relock_delay_millis")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
