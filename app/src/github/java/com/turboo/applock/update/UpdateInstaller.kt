package com.turboo.applock.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads an update's APK and hands it to the system installer.
 *
 * Android always shows its own confirmation for a sideloaded install, and that
 * cannot be bypassed — "auto" here means the app fetches and offers the update,
 * not that it installs silently.
 */
object UpdateInstaller {

    /** Downloads to the cache directory, replacing any half-finished attempt. */
    suspend fun download(context: Context, update: AvailableUpdate): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply {
                deleteRecursively()
                mkdirs()
            }
            val target = File(dir, "applock-${update.versionName}.apk")

            runCatching {
                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        error("HTTP ${connection.responseCode}")
                    }
                    connection.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                target
            }.getOrElse {
                target.delete()
                null
            }
        }

    /** True once the user has allowed this app to install APKs. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
