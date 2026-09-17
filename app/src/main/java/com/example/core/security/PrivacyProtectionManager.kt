package com.example.core.security

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * Privacy protection controller.
 * Enforces FLAG_SECURE on the active Activity window to prevent
 * screen capture, screen recording, and exposure in recent-apps switcher.
 *
 * NOTE: In streaming emulator environments (such as AI Studio web browser preview),
 * FLAG_SECURE causes the WebRTC/virtual display mirror to capture pure black frames
 * because Android OS security blocks window mirroring.
 * Window protection is automatically bypassed in emulator/preview mode so the UI is visible.
 */
object PrivacyProtectionManager {

    /**
     * Check if running in an Android Emulator or browser streaming preview environment.
     */
    fun isEmulatorOrPreview(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()

        return fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                fingerprint.contains("test-keys") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for") ||
                manufacturer.contains("genymotion") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                hardware.contains("vbox86") ||
                product.contains("sdk") ||
                product.contains("google_sdk") ||
                product.contains("sdk_gphone") ||
                brand.startsWith("generic") ||
                device.startsWith("generic") ||
                board.contains("goldfish")
    }

    fun applyWindowProtection(activity: Activity, enabled: Boolean) {
        // Only apply FLAG_SECURE if enabled AND NOT running on emulator / preview stream.
        // On emulator/preview stream, FLAG_SECURE blacks out the entire window stream.
        if (enabled && !isEmulatorOrPreview()) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

