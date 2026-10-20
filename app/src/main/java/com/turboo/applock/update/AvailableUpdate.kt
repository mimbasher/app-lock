package com.turboo.applock.update

/**
 * A release newer than the installed one.
 *
 * Shared by both flavours so the banner and view model compile either way; only
 * the `github` flavour ever produces one (see UpdateChecker in src/github).
 */
data class AvailableUpdate(
    val versionName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val notes: String,
)
