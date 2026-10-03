package com.example.core.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Anti-Uninstall & Anti-Tamper Protection for Private Vault using Android's
 * DevicePolicyManager framework.
 *
 * When enabled, the OS prevents accidental or unauthorized deletion of the app from
 * the home screen launcher or system application settings.
 */
class UninstallProtectionManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private fun getDpm(): DevicePolicyManager? =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    val componentName = ComponentName(appContext, VaultDeviceAdminReceiver::class.java)

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isProtected = MutableStateFlow(isDeviceAdminActive())
    val isProtected: StateFlow<Boolean> = _isProtected.asStateFlow()

    private val _hasDismissedBanner = MutableStateFlow(prefs.getBoolean(KEY_BANNER_DISMISSED, false))
    val hasDismissedBanner: StateFlow<Boolean> = _hasDismissedBanner.asStateFlow()

    /**
     * Checks with the Android OS whether Private Vault is currently an active Device Administrator.
     */
    fun isDeviceAdminActive(): Boolean {
        return try {
            getDpm()?.isAdminActive(componentName) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Re-queries the system status and updates StateFlow.
     */
    fun notifyStateChanged() {
        val active = isDeviceAdminActive()
        _isProtected.value = active
    }

    /**
     * Creates an Intent to prompt the user with Android's system Device Administrator activation screen.
     * Note: Do NOT add FLAG_ACTIVITY_NEW_TASK when using with ActivityResultLauncher, as Android
     * will immediately cancel the result before the prompt can be completed.
     */
    fun getActivationIntent(asNewTask: Boolean = false): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate Device Admin for Private Vault to protect against unauthorized app uninstallation, accidental deletion, and data loss."
            )
            if (asNewTask) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Fallback intent to open Android Security & Privacy settings in case direct activation is blocked.
     */
    fun getSecuritySettingsIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Deactivates Device Admin protection.
     * Note: In UI this should only be called after Master PIN confirmation.
     */
    fun deactivateProtection(): Boolean {
        return try {
            getDpm()?.removeActiveAdmin(componentName)
            notifyStateChanged()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Dismisses the home screen prompt banner if user chose to dismiss it.
     */
    fun dismissBanner() {
        prefs.edit().putBoolean(KEY_BANNER_DISMISSED, true).apply()
        _hasDismissedBanner.value = true
    }

    companion object {
        private const val PREFS_NAME = "uninstall_protection_prefs"
        private const val KEY_BANNER_DISMISSED = "uninstall_banner_dismissed"

        @Volatile
        private var INSTANCE: UninstallProtectionManager? = null

        fun getInstance(context: Context): UninstallProtectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UninstallProtectionManager(context).also { INSTANCE = it }
            }
        }
    }
}
