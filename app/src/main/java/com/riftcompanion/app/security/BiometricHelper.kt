package com.riftcompanion.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

/**
 * Wraps BiometricPrompt for app-lock and credential-access gating.
 * Uses Class 3 (strong) biometrics only.
 */
object BiometricHelper {

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            else -> false
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Use your biometric to access RiftCompanion",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFail: () -> Unit,
    ) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                onFail()
            }
        }

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
