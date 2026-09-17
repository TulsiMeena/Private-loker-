package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.core.database.VaultDatabase
import com.example.core.database.VaultRepository
import com.example.core.security.AndroidVaultKeyManager
import com.example.core.security.DeviceBiometricManager
import com.example.core.security.LockState
import com.example.core.security.PrivacyProtectionManager
import com.example.core.security.SecureKeyManager
import com.example.core.security.SessionSecurityManager
import com.example.core.security.VaultAccessGate
import com.example.core.lifecycle.DataLifecycleManager
import com.example.core.storage.SecureThumbnailProvider
import com.example.core.storage.VaultStorageEngine
import com.example.core.storage.VaultStorageManager
import com.example.core.storage.VaultTempFileManager
import com.example.feature.documents.DocumentThumbnailHelper
import com.example.feature.media.MediaThumbnailHelper
import com.example.core.settings.AppSettingsManager
import com.example.feature.navigation.VaultNavGraph
import com.example.feature.vault.VaultHomeViewModel
import com.example.ui.theme.PrivateVaultTheme
import com.example.ui.theme.VaultCanvas
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var secureKeyManager: SecureKeyManager
    private lateinit var sessionSecurityManager: SessionSecurityManager
    private lateinit var biometricManager: DeviceBiometricManager
    private lateinit var storageManager: VaultStorageManager
    private lateinit var vaultRepository: VaultRepository
    private lateinit var homeViewModel: VaultHomeViewModel
    private lateinit var dataLifecycleManager: DataLifecycleManager
    private lateinit var appSettingsManager: AppSettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Core security architecture initialization
        secureKeyManager = SecureKeyManager()
        sessionSecurityManager = SessionSecurityManager(
            context = applicationContext,
            secureKeyManager = secureKeyManager,
            coroutineScope = lifecycleScope
        )
        biometricManager = DeviceBiometricManager(applicationContext)

        // 2. Cryptographic Vault Engine & Access Gate
        val vaultKeyManager = AndroidVaultKeyManager()
        val accessGate = VaultAccessGate(vaultKeyManager) {
            sessionSecurityManager.lockState.value is LockState.Unlocked
        }
        val tempFileManager = VaultTempFileManager(applicationContext)
        val storageEngine = VaultStorageEngine(applicationContext, accessGate, tempFileManager)
        storageManager = VaultStorageManager(
            context = applicationContext,
            keyManager = vaultKeyManager,
            accessGate = accessGate,
            engine = storageEngine
        )

        // 3. Database & Repository
        val db = VaultDatabase.getInstance(applicationContext)
        vaultRepository = VaultRepository(
            vaultDao = db.vaultDao(),
            vaultFolderDao = db.vaultFolderDao(),
            securityAuditDao = db.securityAuditDao(),
            storageManager = storageManager,
            documentBookmarkDao = db.documentBookmarkDao(),
            backupHistoryDao = db.backupHistoryDao()
        )

        // 4. Data Lifecycle & Recovery Engine
        dataLifecycleManager = DataLifecycleManager(
            context = applicationContext,
            vaultDao = db.vaultDao(),
            vaultFolderDao = db.vaultFolderDao(),
            securityAuditDao = db.securityAuditDao(),
            storageManager = storageManager
        )

        lifecycleScope.launch {
            // Reconcile and purge abandoned transient files from previous crashes or ungraceful exits
            dataLifecycleManager.performCrashRecovery()
            // Auto-clean expired items based on user retention setting
            dataLifecycleManager.executeAutoClean()
        }

        // Clean caches and temporary working files immediately whenever vault transitions to locked
        lifecycleScope.launch {
            sessionSecurityManager.lockState.collect { lockState ->
                if (lockState !is LockState.Unlocked) {
                    if (::storageManager.isInitialized) {
                        storageManager.purgeTemporaryFiles()
                    }
                    SecureThumbnailProvider.clearCache()
                    MediaThumbnailHelper.clearCache()
                    DocumentThumbnailHelper.clearCache()
                }
            }
        }

        // 5. ViewModels
        homeViewModel = VaultHomeViewModel(
            repository = vaultRepository,
            sessionManager = sessionSecurityManager
        )

        // 6. Settings Manager
        appSettingsManager = AppSettingsManager.getInstance(applicationContext)

        // 7. Screen privacy protection (FLAG_SECURE)
        PrivacyProtectionManager.applyWindowProtection(
            activity = this,
            enabled = sessionSecurityManager.isScreenProtectionEnabled()
        )

        setContent {
            val screenProtection by sessionSecurityManager.screenProtectionEnabled.collectAsState()
            androidx.compose.runtime.LaunchedEffect(screenProtection) {
                PrivacyProtectionManager.applyWindowProtection(
                    activity = this@MainActivity,
                    enabled = screenProtection
                )
            }

            val themeMode by appSettingsManager.themeMode.collectAsState()
            val accentColor by appSettingsManager.accentColor.collectAsState()
            val glassIntensity by appSettingsManager.glassIntensity.collectAsState()
            val reducedMotion by appSettingsManager.reducedMotion.collectAsState()

            PrivateVaultTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                glassIntensity = glassIntensity,
                reducedMotion = reducedMotion
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VaultCanvas
                ) {
                    VaultNavGraph(
                        activity = this,
                        sessionManager = sessionSecurityManager,
                        vaultRepository = vaultRepository,
                        biometricAuthenticator = biometricManager,
                        homeViewModel = homeViewModel,
                        lifecycleManager = dataLifecycleManager
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sessionSecurityManager.onAppForegrounded()
        PrivacyProtectionManager.applyWindowProtection(
            activity = this,
            enabled = sessionSecurityManager.isScreenProtectionEnabled()
        )
    }

    override fun onPause() {
        super.onPause()
        sessionSecurityManager.onAppBackgrounded()
        // Shred temporary files and clear thumbnail caches when leaving app
        if (::storageManager.isInitialized) {
            storageManager.purgeTemporaryFiles()
        }
        SecureThumbnailProvider.clearCache()
        MediaThumbnailHelper.clearCache()
        DocumentThumbnailHelper.clearCache()
    }
}

/**
 * Kept for test and preview backwards compatibility.
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "PrivateVault $name", modifier = modifier)
}
