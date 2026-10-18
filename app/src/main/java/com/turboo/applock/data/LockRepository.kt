package com.turboo.applock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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

    private companion object {
        val KEY_LOCKED = stringSetPreferencesKey("locked_packages")
        val KEY_RELOCK_DELAY = longPreferencesKey("relock_delay_millis")
    }
}
