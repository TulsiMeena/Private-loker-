package com.example.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Production clipboard protection service for PrivateVault.
 *
 * Security Features:
 * - Marks sensitive clipboard data with [ClipDescription.EXTRA_IS_SENSITIVE] on Android 13+ (API 33+)
 *   so system clipboard previews / overlays mask the text.
 * - Automatic background timer to clear sensitive clips after a configurable interval (30s, 60s, 300s).
 * - Only wipes clipboard contents if the active clip matches the hash of what PrivateVault placed there.
 * - Immediate shredding / wiping on Emergency Lock or Vault Lock.
 */
class SecureClipboardManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val clipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private var activeClearJob: Job? = null
    private var lastManagedContentHash: Int? = null

    /**
     * Copies sensitive text to the clipboard with sensitive tagging and scheduled auto-purge.
     */
    fun copySensitiveText(label: String, text: String, autoClearSeconds: Int = 60) {
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard?.setPrimaryClip(clip)
        lastManagedContentHash = text.hashCode()

        if (autoClearSeconds > 0) {
            activeClearJob?.cancel()
            activeClearJob = coroutineScope.launch {
                delay(autoClearSeconds * 1000L)
                clearIfOwned()
            }
        }
    }

    /**
     * Purges clipboard if the text currently held matches what PrivateVault placed.
     */
    fun clearIfOwned(): Boolean {
        return try {
            val primaryClip = clipboard?.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val currentText = primaryClip.getItemAt(0).text?.toString()
                if (currentText != null && currentText.hashCode() == lastManagedContentHash) {
                    clearSystemClipboard()
                    lastManagedContentHash = null
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Immediately clears the system clipboard during emergency lockdown or explicit purge.
     */
    fun clearImmediately() {
        try {
            clearSystemClipboard()
            lastManagedContentHash = null
            activeClearJob?.cancel()
        } catch (_: Exception) {
            // Ignore system security restrictions
        }
    }

    private fun clearSystemClipboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard?.clearPrimaryClip()
        } else {
            clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
