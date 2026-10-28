package com.turboo.applock.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.turboo.applock.AppLockApplication
import com.turboo.applock.R
import com.turboo.applock.data.PauseState
import com.turboo.applock.ui.PauseAuthActivity

/**
 * Quick Settings tile: suspend locking for [PauseState.PAUSE_MINUTES] minutes, or
 * resume early.
 *
 * Pausing is authenticated, resuming is not. A tile that switched locking off on one
 * tap would hand anyone holding the phone a bypass of the entire app, reachable from
 * the shade without ever opening AppLock — so the tap only opens
 * [PauseAuthActivity], and the pause begins once the system prompt has passed.
 * Turning protection back on can never need permission.
 *
 * The pause expires by itself. Nothing here cancels it, and nothing persists it.
 */
class LockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()

        if (PauseState.isPaused) {
            PauseState.resume()
            LockGuardService.refresh(applicationContext)
            render()
            return
        }

        // On the keyguard, let the system take its own unlock first; our prompt
        // would otherwise queue behind it invisibly.
        if (isLocked) unlockAndRun(::openPauseAuth) else openPauseAuth()
    }

    private fun openPauseAuth() {
        val intent = Intent(this, PauseAuthActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            // The PendingIntent overload only exists from 34; this one only throws
            // from 34. The branch above is the whole reason this is safe.
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val locked = AppLockApplication.repository().cachedLockedPackages

        val subtitle = when {
            locked != null && locked.isEmpty() -> {
                tile.state = Tile.STATE_UNAVAILABLE
                getString(R.string.tile_nothing_locked)
            }

            PauseState.isPaused -> {
                tile.state = Tile.STATE_INACTIVE
                getString(R.string.tile_paused, PauseState.remainingMinutes)
            }

            else -> {
                tile.state = Tile.STATE_ACTIVE
                getString(R.string.tile_watching)
            }
        }

        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(
            this,
            if (PauseState.isPaused) R.drawable.ic_tile_lock_open else R.drawable.ic_tile_lock,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    companion object {
        /**
         * Ask the system to call [onStartListening] again, which is the only way to
         * repaint a tile from outside it. A no-op when the user has never added it.
         */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, LockTileService::class.java),
                )
            }
        }
    }
}
