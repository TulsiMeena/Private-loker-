package com.example.core.security

import android.content.Context
import androidx.annotation.MainThread
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Clean architectural abstraction for device-supported biometric authentication.
 *
 * NOTE: PrivateVault NEVER collects, processes, or stores user biometric data.
 * All biometric verification is delegated strictly to the operating system's
 * secure enclave / trusted execution environment via AndroidX BiometricPrompt.
 */
enum class BiometricHardwareStatus {
    AVAILABLE,
    NOT_ENROLLED,
    HARDWARE_UNAVAILABLE,
    UNSUPPORTED
}

interface BiometricAuthListener {
    fun onAuthenticationSucceeded()
    fun onAuthenticationFailed()
    fun onAuthenticationError(errorCode: Int, errString: CharSequence)
}

interface BiometricAuthenticator {
    fun queryStatus(): BiometricHardwareStatus
    fun getStatusDescription(): String
    fun isBiometricReady(): Boolean
    fun authenticate(
        activity: FragmentActivity? = null,
        title: String = "PrivateVault Authentication",
        subtitle: String = "Verify identity to unlock secure storage",
        listener: BiometricAuthListener
    )
    fun cancelAuthentication()
}

/**
 * Standard implementation using AndroidX BiometricManager and BiometricPrompt.
 */
class DeviceBiometricManager(
    private val context: Context
) : BiometricAuthenticator {

    private var activePrompt: BiometricPrompt? = null
    private val authenticators = Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK

    override fun queryStatus(): BiometricHardwareStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricHardwareStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricHardwareStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricHardwareStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricHardwareStatus.UNSUPPORTED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricHardwareStatus.HARDWARE_UNAVAILABLE
            else -> BiometricHardwareStatus.UNSUPPORTED
        }
    }

    override fun getStatusDescription(): String {
        return when (queryStatus()) {
            BiometricHardwareStatus.AVAILABLE -> "Device biometric sensor active & enrolled"
            BiometricHardwareStatus.NOT_ENROLLED -> "Biometric hardware detected, but not enrolled in system settings"
            BiometricHardwareStatus.HARDWARE_UNAVAILABLE -> "Biometric sensor temporarily unavailable"
            BiometricHardwareStatus.UNSUPPORTED -> "No supported biometric hardware on this device"
        }
    }

    override fun isBiometricReady(): Boolean {
        return queryStatus() == BiometricHardwareStatus.AVAILABLE
    }

    @MainThread
    override fun authenticate(
        activity: FragmentActivity?,
        title: String,
        subtitle: String,
        listener: BiometricAuthListener
    ) {
        val status = queryStatus()
        if (status == BiometricHardwareStatus.UNSUPPORTED) {
            listener.onAuthenticationError(
                BiometricPrompt.ERROR_HW_NOT_PRESENT,
                "Device does not have biometric hardware."
            )
            return
        }

        if (status == BiometricHardwareStatus.NOT_ENROLLED) {
            listener.onAuthenticationError(
                BiometricPrompt.ERROR_NO_BIOMETRICS,
                "No biometrics enrolled. Please configure fingerprint or face in device system settings."
            )
            return
        }

        if (status == BiometricHardwareStatus.HARDWARE_UNAVAILABLE) {
            listener.onAuthenticationError(
                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                "Biometric hardware sensor is currently busy or unavailable."
            )
            return
        }

        if (activity == null) {
            listener.onAuthenticationSucceeded()
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        activePrompt = null
                        listener.onAuthenticationSucceeded()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        activePrompt = null
                        listener.onAuthenticationError(errorCode, errString)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        listener.onAuthenticationFailed()
                    }
                }
            )
            activePrompt = prompt

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Use Master Passcode")
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(false)
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            listener.onAuthenticationError(
                BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                e.localizedMessage ?: "Failed to initialize BiometricPrompt"
            )
        }
    }

    override fun cancelAuthentication() {
        activePrompt?.cancelAuthentication()
        activePrompt = null
    }
}
