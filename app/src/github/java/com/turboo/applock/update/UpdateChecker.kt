package com.turboo.applock.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the repository's latest GitHub release for a newer APK.
 *
 * Deliberately built on HttpURLConnection and org.json rather than a HTTP/JSON
 * library: one request against one endpoint does not justify the dependency.
 */
object UpdateChecker {

    private const val LATEST_RELEASE =
        "https://api.github.com/repos/mimbasher/app-lock/releases/latest"
    private const val TIMEOUT_MILLIS = 10_000

    suspend fun check(context: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        val installed = installedVersion(context) ?: return@withContext null
        val json = runCatching { fetch(LATEST_RELEASE) }.getOrNull() ?: return@withContext null

        val release = runCatching { JSONObject(json) }.getOrNull() ?: return@withContext null
        val tag = release.optString("tag_name").removePrefix("v")
        if (tag.isEmpty() || !isNewer(tag, installed)) return@withContext null

        val apk = (0 until release.optJSONArray("assets")?.length().orZero())
            .asSequence()
            .mapNotNull { release.optJSONArray("assets")?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null

        AvailableUpdate(
            versionName = tag,
            apkUrl = apk.optString("browser_download_url"),
            apkSizeBytes = apk.optLong("size"),
            notes = release.optString("body").trim(),
        )
    }

    private fun Int?.orZero() = this ?: 0

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun installedVersion(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    /**
     * Compares dotted version numbers a component at a time, so 1.10 correctly beats
     * 1.9 where a string comparison would not. Any non-numeric suffix is ignored.
     */
    internal fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.toParts()
        val b = installed.toParts()
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun String.toParts(): List<Int> =
        split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
