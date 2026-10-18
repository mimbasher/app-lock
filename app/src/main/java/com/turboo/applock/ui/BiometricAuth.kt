package com.turboo.applock.ui

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` is explicitly unsupported on API 28-29 and
 * throws there, so those two versions get the weak-biometric combination instead.
 */
private fun allowedAuthenticators(): Int = when (Build.VERSION.SDK_INT) {
    Build.VERSION_CODES.P, Build.VERSION_CODES.Q -> BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    else -> BIOMETRIC_STRONG or DEVICE_CREDENTIAL
}

/**
 * Raises the system prompt: fingerprint or face, with the device PIN, pattern or
 * password as the fallback. The system owns the whole exchange, so no secret ever
 * passes through this app.
 *
 * [onUnavailable] covers the case where there is no enrolled biometric *and* no
 * screen lock. There is then nothing to check against, so refusing would lock the
 * user out of their own device; both callers treat it as success.
 *
 * Errors other than an explicit cancel (lockout, for instance) deliberately do
 * nothing, leaving the caller's screen up so the user can retry.
 */
fun FragmentActivity.promptForUnlock(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onCancelled: () -> Unit,
    onUnavailable: () -> Unit = onSuccess,
) {
    val authenticators = allowedAuthenticators()

    if (BiometricManager.from(this).canAuthenticate(authenticators) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        onUnavailable()
        return
    }

    BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED,
                    -> onCancelled()
                    else -> Unit
                }
            }
        },
    ).authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
    )
}
