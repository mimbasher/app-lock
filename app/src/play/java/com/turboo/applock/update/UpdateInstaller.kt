package com.turboo.applock.update

import android.content.Context
import java.io.File

/**
 * Play build: nothing here installs anything.
 *
 * Unreachable in practice, since UpdateChecker never reports an update, but it
 * has to exist for the shared UI to compile. Kept deliberately empty so the Play
 * APK contains no download or install path at all — the REQUEST_INSTALL_PACKAGES
 * permission and the FileProvider it would need are declared only in src/github.
 */
@Suppress("RedundantSuspendModifier", "UNUSED_PARAMETER")
object UpdateInstaller {

    suspend fun download(context: Context, update: AvailableUpdate): File? = null

    fun canInstall(context: Context): Boolean = false

    fun openInstallPermissionSettings(context: Context) = Unit

    fun install(context: Context, apk: File) = Unit
}
