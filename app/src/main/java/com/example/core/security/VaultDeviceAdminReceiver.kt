package com.example.core.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * DeviceAdminReceiver that prevents unauthorized uninstallation of PrivateVault.
 * While active as a Device Administrator, the Android system blocks normal uninstallation
 * from the launcher and Settings until the owner deactivates it using their master PIN.
 */
class VaultDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        UninstallProtectionManager.getInstance(context).notifyStateChanged()
        Toast.makeText(context, "Shield Active: Uninstall Protection Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        UninstallProtectionManager.getInstance(context).notifyStateChanged()
        Toast.makeText(context, "Uninstall Protection Disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "WARNING: Disabling Device Administrator removes Anti-Uninstall Protection. An intruder or unauthorized person will be able to delete Private Vault and all encrypted files."
    }
}
