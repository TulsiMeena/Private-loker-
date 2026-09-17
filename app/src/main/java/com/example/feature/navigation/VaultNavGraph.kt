package com.example.feature.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.database.VaultRepository
import com.example.core.security.BiometricAuthListener
import com.example.core.security.BiometricAuthenticator
import com.example.core.security.BiometricHardwareStatus
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.security.VoiceLockSecurityManager
import com.example.core.storage.VaultCategory
import com.example.feature.auth.AuthScreen
import com.example.feature.code.editor.CodeEditorScreen
import com.example.core.security.SecureClipboardManager
import com.example.core.security.SecurityStatusManager
import com.example.feature.code.editor.CodeEditorViewModel
import com.example.feature.code.studio.CodeTextStudioScreen
import com.example.feature.code.studio.CodeTextStudioViewModel
import com.example.feature.documents.DocumentCenterScreen
import com.example.feature.documents.DocumentCenterViewModel
import com.example.feature.launch.LaunchScreen
import com.example.feature.backup.BackupScreen
import com.example.feature.backup.BackupViewModel
import com.example.feature.backup.SecureBackupManager
import com.example.feature.backup.BackupReminderManager
import com.example.core.database.VaultDatabase
import com.example.feature.security.SecurityCenterScreen
import com.example.feature.security.SecurityCenterViewModel
import com.example.feature.settings.SettingsScreen
import com.example.core.lifecycle.DataLifecycleManager
import com.example.feature.vault.trash.TrashViewModel
import com.example.feature.vault.CategoryDetailScreen
import com.example.feature.vault.VaultHomeScreen
import com.example.feature.vault.VaultHomeViewModel
import com.example.feature.vault.trash.TrashScreen
import com.example.feature.viewer.SecureFileViewerScreen
import com.example.feature.files.archive.ArchiveCenterScreen
import com.example.feature.files.archive.ArchiveCenterViewModel
import com.example.feature.files.archive.ArchiveViewerScreen
import com.example.feature.files.archive.ArchiveViewerViewModel
import com.example.feature.files.archive.ZipVaultManager
import com.example.feature.files.operations.FileOperationManager
import com.example.feature.organization.SmartOrganizationScreen
import com.example.feature.organization.SmartOrganizationViewModel
import com.example.feature.search.SearchHistoryManager
import com.example.feature.search.UniversalSearchScreen
import com.example.feature.search.UniversalSearchViewModel
import kotlinx.coroutines.launch

object VaultDestinations {
    const val LAUNCH = "launch"
    const val AUTH = "auth"
    const val HOME = "home"
    const val DOCUMENT_CENTER = "document_center"
    const val MEDIA_CENTER = "media_center"
    const val CODE_STUDIO = "code_studio"
    const val CODE_EDITOR = "code_editor/{itemId}"
    const val CATEGORY_DETAIL = "category/{categoryName}"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val FILE_VIEWER = "file_viewer/{itemId}"
    const val ARCHIVE_CENTER = "archive_center"
    const val ARCHIVE_VIEWER = "archive_viewer/{itemId}"
    const val UNIVERSAL_SEARCH = "universal_search"
    const val SMART_ORGANIZATION = "smart_organization"
    const val SECURITY_CENTER = "security_center"
    const val BACKUP_CENTER = "backup_center"

    fun categoryDetailRoute(category: VaultCategory) = "category/${category.name}"
    fun fileViewerRoute(itemId: Long) = "file_viewer/$itemId"
    fun codeEditorRoute(itemId: Long) = "code_editor/$itemId"
    fun archiveViewerRoute(itemId: Long) = "archive_viewer/$itemId"
}

@Composable
fun VaultNavGraph(
    navController: NavHostController = rememberNavController(),
    activity: FragmentActivity? = null,
    sessionManager: SessionSecurityManager,
    vaultRepository: VaultRepository,
    biometricAuthenticator: BiometricAuthenticator,
    homeViewModel: VaultHomeViewModel,
    backupViewModel: BackupViewModel? = null,
    lifecycleManager: DataLifecycleManager? = null,
    modifier: Modifier = Modifier
) {
    val lockState by sessionManager.lockState.collectAsState()
    val failedAttempts by sessionManager.failedAttempts.collectAsState()
    val auditLogs by vaultRepository.auditLogs.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val dataLifecycleManager = remember {
        lifecycleManager ?: run {
            val db = com.example.core.database.VaultDatabase.getInstance(context.applicationContext)
            DataLifecycleManager(
                context = context.applicationContext,
                vaultDao = db.vaultDao(),
                vaultFolderDao = db.vaultFolderDao(),
                securityAuditDao = db.securityAuditDao(),
                storageManager = vaultRepository.storageManager
            )
        }
    }

    val operationManager = remember {
        FileOperationManager(
            storageManager = vaultRepository.storageManager,
            sessionManager = sessionManager
        )
    }
    val zipVaultManager = remember {
        ZipVaultManager(
            repository = vaultRepository,
            storageManager = vaultRepository.storageManager
        )
    }

    val biometricStatus = remember { biometricAuthenticator.queryStatus() }
    val isBiometricSupported = biometricStatus != BiometricHardwareStatus.UNSUPPORTED
    val isBiometricEnrolled = biometricStatus == BiometricHardwareStatus.AVAILABLE
    val biometricDesc = remember { biometricAuthenticator.getStatusDescription() }

    val voiceLockManager = remember {
        VoiceLockSecurityManager(
            context = context.applicationContext,
            sessionSecurityManager = sessionManager
        )
    }
    val voicePassphrase by sessionManager.voicePassphrase.collectAsState()

    val clipboardManager = remember {
        SecureClipboardManager(context)
    }
    val securityStatusManager = remember {
        SecurityStatusManager(
            context = context,
            sessionManager = sessionManager,
            vaultRepository = vaultRepository,
            biometricAuthenticator = biometricAuthenticator,
            clipboardManager = clipboardManager
        )
    }
    val securityCenterViewModel = remember {
        SecurityCenterViewModel(
            securityStatusManager = securityStatusManager,
            sessionManager = sessionManager,
            vaultRepository = vaultRepository,
            biometricAuthenticator = biometricAuthenticator
        )
    }

    // Global session auto-lock observer: immediately pop to HOME (AuthScreen) on lock
    LaunchedEffect(lockState) {
        if (lockState !is LockState.Unlocked) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != null && currentRoute != VaultDestinations.AUTH && currentRoute != VaultDestinations.LAUNCH && currentRoute != VaultDestinations.HOME) {
                navController.navigate(VaultDestinations.HOME) {
                    popUpTo(VaultDestinations.HOME) { inclusive = false }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = VaultDestinations.LAUNCH,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(280)) },
        exitTransition = { fadeOut(animationSpec = tween(280)) },
        popEnterTransition = { fadeIn(animationSpec = tween(280)) },
        popExitTransition = { fadeOut(animationSpec = tween(280)) }
    ) {
        // 1. Hardware App Launch Experience
        composable(VaultDestinations.LAUNCH) {
            LaunchScreen(
                onLaunchComplete = {
                    navController.navigate(VaultDestinations.AUTH) {
                        popUpTo(VaultDestinations.LAUNCH) { inclusive = true }
                    }
                }
            )
        }

        // 2. Authentication Screen
        composable(VaultDestinations.AUTH) {
            AuthScreen(
                lockState = lockState,
                isSetupMode = lockState is LockState.SetupRequired,
                configuredPinLength = sessionManager.getMasterPinLength(),
                failedAttempts = failedAttempts,
                isBiometricSupported = isBiometricSupported,
                isBiometricEnrolled = isBiometricEnrolled,
                biometricStatusDesc = biometricDesc,
                voiceLockManager = voiceLockManager,
                voicePassphrase = voicePassphrase,
                onVoiceUnlockSuccess = {
                    sessionManager.unlockViaVoiceBiometrics()
                    scope.launch {
                        vaultRepository.logSecurityEvent(
                            action = "VOICE_AUTH_SUCCESS",
                            details = "Voice biometric passphrase verified via acoustic enclave",
                            isSuccess = true
                        )
                    }
                    navController.navigate(VaultDestinations.HOME) {
                        popUpTo(VaultDestinations.AUTH) { inclusive = true }
                    }
                },
                onPinSubmit = { pin ->
                    if (lockState is LockState.SetupRequired) {
                        val ok = sessionManager.setupMasterPin(pin)
                        if (ok) {
                            scope.launch {
                                vaultRepository.logSecurityEvent(
                                    action = "MASTER_PIN_SETUP",
                                    details = "Master vault PIN initialized with PBKDF2 salt (Length: ${pin.length})",
                                    isSuccess = true
                                )
                            }
                        }
                        ok
                    } else {
                        val ok = sessionManager.authenticatePin(pin)
                        scope.launch {
                            vaultRepository.logSecurityEvent(
                                action = if (ok) "AUTH_SUCCESS" else "AUTH_FAILED",
                                details = if (ok) "Master PIN verified successfully" else "Invalid master PIN attempt",
                                isSuccess = ok
                            )
                        }
                        ok
                    }
                },
                onBiometricPreferenceChange = { enabled ->
                    sessionManager.setBiometricEnabled(enabled)
                },
                onBiometricSuccess = {
                    sessionManager.unlockViaBiometrics()
                    scope.launch {
                        vaultRepository.logSecurityEvent(
                            action = "BIOMETRIC_AUTH_SUCCESS",
                            details = "Device biometric verification succeeded",
                            isSuccess = true
                        )
                    }
                    navController.navigate(VaultDestinations.HOME) {
                        popUpTo(VaultDestinations.AUTH) { inclusive = true }
                    }
                },
                onBiometricClick = {
                    biometricAuthenticator.authenticate(
                        activity = activity,
                        title = "PrivateVault Biometric Authentication",
                        subtitle = "Verify with Fingerprint or Face Recognition to unlock",
                        listener = object : BiometricAuthListener {
                            override fun onAuthenticationSucceeded() {
                                sessionManager.unlockViaBiometrics()
                                scope.launch {
                                    vaultRepository.logSecurityEvent(
                                        action = "BIOMETRIC_AUTH_SUCCESS",
                                        details = "Device biometric verification succeeded",
                                        isSuccess = true
                                    )
                                }
                                navController.navigate(VaultDestinations.HOME) {
                                    popUpTo(VaultDestinations.AUTH) { inclusive = true }
                                }
                            }

                            override fun onAuthenticationFailed() {
                                scope.launch {
                                    vaultRepository.logSecurityEvent(
                                        action = "BIOMETRIC_AUTH_FAILED",
                                        details = "Biometric sensor did not match",
                                        isSuccess = false
                                    )
                                }
                            }

                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                scope.launch {
                                    vaultRepository.logSecurityEvent(
                                        action = "BIOMETRIC_ERROR",
                                        details = "Biometric error code $errorCode: $errString",
                                        isSuccess = false
                                    )
                                }
                            }
                        }
                    )
                },
                onAuthenticated = {
                    navController.navigate(VaultDestinations.HOME) {
                        popUpTo(VaultDestinations.AUTH) { inclusive = true }
                    }
                },
                onEmergencyWipe = {
                    scope.launch {
                        vaultRepository.purgeAllVaultData()
                        sessionManager.emergencyWipeAllSecuritySettings()
                        vaultRepository.logSecurityEvent(
                            action = "EMERGENCY_WIPE",
                            details = "Vault shredded and reset from authentication boundary",
                            isSuccess = true
                        )
                        navController.navigate(VaultDestinations.AUTH) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 3. Vault Home Shell
        composable(VaultDestinations.HOME) {
            // Protected route guard
            if (lockState !is LockState.Unlocked) {
                AuthScreen(
                    lockState = lockState,
                    isSetupMode = lockState is LockState.SetupRequired,
                    configuredPinLength = sessionManager.getMasterPinLength(),
                    failedAttempts = failedAttempts,
                    isBiometricSupported = isBiometricSupported,
                    isBiometricEnrolled = isBiometricEnrolled,
                    biometricStatusDesc = biometricDesc,
                    voiceLockManager = voiceLockManager,
                    voicePassphrase = voicePassphrase,
                    onVoiceUnlockSuccess = {
                        sessionManager.unlockViaVoiceBiometrics()
                        scope.launch {
                            vaultRepository.logSecurityEvent(
                                action = "VOICE_AUTH_SUCCESS",
                                details = "Voice biometric passphrase verified via acoustic enclave",
                                isSuccess = true
                            )
                        }
                    },
                    onPinSubmit = { pin -> sessionManager.authenticatePin(pin) },
                    onBiometricPreferenceChange = { sessionManager.setBiometricEnabled(it) },
                    onBiometricSuccess = {
                        sessionManager.unlockViaBiometrics()
                        scope.launch {
                            vaultRepository.logSecurityEvent(
                                action = "BIOMETRIC_AUTH_SUCCESS",
                                details = "Device biometric verification succeeded",
                                isSuccess = true
                            )
                        }
                    },
                    onBiometricClick = {
                        biometricAuthenticator.authenticate(
                            activity = activity,
                            title = "PrivateVault Biometric Authentication",
                            subtitle = "Verify with Fingerprint or Face Recognition to unlock",
                            listener = object : BiometricAuthListener {
                                override fun onAuthenticationSucceeded() {
                                    sessionManager.unlockViaBiometrics()
                                    scope.launch {
                                        vaultRepository.logSecurityEvent(
                                            action = "BIOMETRIC_AUTH_SUCCESS",
                                            details = "Device biometric verification succeeded",
                                            isSuccess = true
                                        )
                                    }
                                }
                                override fun onAuthenticationFailed() {
                                    scope.launch {
                                        vaultRepository.logSecurityEvent(
                                            action = "BIOMETRIC_AUTH_FAILED",
                                            details = "Biometric sensor did not match",
                                            isSuccess = false
                                        )
                                    }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    scope.launch {
                                        vaultRepository.logSecurityEvent(
                                            action = "BIOMETRIC_ERROR",
                                            details = "Biometric error code $errorCode: $errString",
                                            isSuccess = false
                                        )
                                    }
                                }
                            }
                        )
                    },
                    onAuthenticated = { /* Remain in home */ },
                    onEmergencyWipe = {
                        scope.launch {
                            vaultRepository.purgeAllVaultData()
                            sessionManager.emergencyWipeAllSecuritySettings()
                            navController.navigate(VaultDestinations.AUTH) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            } else {
                VaultHomeScreen(
                    viewModel = homeViewModel,
                    onCategoryClick = { category ->
                        if (category == VaultCategory.DOCUMENT) {
                            navController.navigate(VaultDestinations.DOCUMENT_CENTER)
                        } else if (category == VaultCategory.IMAGE || category == VaultCategory.VIDEO || category == VaultCategory.AUDIO) {
                            navController.navigate(VaultDestinations.MEDIA_CENTER)
                        } else if (category == VaultCategory.CODE || category == VaultCategory.TEXT) {
                            navController.navigate(VaultDestinations.CODE_STUDIO)
                        } else if (category == VaultCategory.ZIP) {
                            navController.navigate(VaultDestinations.ARCHIVE_CENTER)
                        } else {
                            navController.navigate(VaultDestinations.categoryDetailRoute(category))
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(VaultDestinations.SETTINGS)
                    },
                    onTrashClick = {
                        navController.navigate(VaultDestinations.TRASH)
                    },
                    onOpenFile = { itemId ->
                        navController.navigate(VaultDestinations.fileViewerRoute(itemId))
                    },
                    onNavigateToSearch = {
                        navController.navigate(VaultDestinations.UNIVERSAL_SEARCH)
                    },
                    onNavigateToOrganize = {
                        navController.navigate(VaultDestinations.SMART_ORGANIZATION)
                    }
                )
            }
        }

        // 4. Advanced Document Center
        composable(VaultDestinations.DOCUMENT_CENTER) {
            val docViewModel = remember {
                DocumentCenterViewModel(
                    repository = vaultRepository,
                    sessionManager = sessionManager
                )
            }
            DocumentCenterScreen(
                viewModel = docViewModel,
                sessionManager = sessionManager,
                onNavigateBack = { navController.popBackStack() },
                onOpenDocument = { itemId ->
                    navController.navigate(VaultDestinations.fileViewerRoute(itemId))
                }
            )
        }

        // 5. Advanced Media Center (Images + Video + Audio)
        composable(VaultDestinations.MEDIA_CENTER) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val mediaViewModel = remember {
                val audioEngine = com.example.feature.media.audio.AudioPlaybackEngine(
                    context = context,
                    repository = vaultRepository,
                    sessionManager = sessionManager
                )
                com.example.feature.media.MediaCenterViewModel(
                    repository = vaultRepository,
                    sessionManager = sessionManager,
                    audioEngine = audioEngine
                )
            }
            com.example.feature.media.MediaCenterScreen(
                viewModel = mediaViewModel,
                repository = vaultRepository,
                sessionManager = sessionManager,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 5. Partition Category Detail
        composable(
            route = VaultDestinations.CATEGORY_DETAIL,
            arguments = listOf(navArgument("categoryName") { type = NavType.StringType })
        ) { backStackEntry ->
            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: VaultCategory.DOCUMENT.name
            val category = try {
                VaultCategory.valueOf(categoryName)
            } catch (_: Exception) {
                VaultCategory.DOCUMENT
            }

            val categoryItems by vaultRepository.getItemsByCategory(category).collectAsState(initial = emptyList())

            CategoryDetailScreen(
                category = category,
                items = categoryItems,
                repository = vaultRepository,
                onBackClick = { navController.popBackStack() },
                onOpenFile = { itemId ->
                    navController.navigate(VaultDestinations.fileViewerRoute(itemId))
                }
            )
        }

        // 5. Secure Universal File Viewer
        composable(
            route = VaultDestinations.FILE_VIEWER,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
            SecureFileViewerScreen(
                itemId = itemId,
                repository = vaultRepository,
                sessionManager = sessionManager,
                onNavigateBack = { navController.popBackStack() },
                onOpenInStudioEditor = { id ->
                    navController.navigate(VaultDestinations.codeEditorRoute(id))
                },
                onOpenInArchiveViewer = { id ->
                    navController.navigate(VaultDestinations.archiveViewerRoute(id))
                }
            )
        }

        // 6. Professional Code & Text Studio Workspace
        composable(VaultDestinations.CODE_STUDIO) {
            val studioViewModel = remember {
                CodeTextStudioViewModel(
                    repository = vaultRepository,
                    sessionManager = sessionManager
                )
            }
            CodeTextStudioScreen(
                viewModel = studioViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenFileEditor = { itemId ->
                    navController.navigate(VaultDestinations.codeEditorRoute(itemId))
                }
            )
        }

        // 7. Full Touch-First Code & Text Editor
        composable(
            route = VaultDestinations.CODE_EDITOR,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
            val context = androidx.compose.ui.platform.LocalContext.current
            val appSettingsManager = remember(context) { com.example.core.settings.AppSettingsManager.getInstance(context) }
            val editorViewModel = remember {
                CodeEditorViewModel(
                    repository = vaultRepository,
                    sessionManager = sessionManager,
                    appSettingsManager = appSettingsManager
                )
            }
            CodeEditorScreen(
                itemId = itemId,
                viewModel = editorViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 8. Advanced ZIP & Archive Operations Center
        composable(VaultDestinations.ARCHIVE_CENTER) {
            val archiveCenterViewModel = remember {
                ArchiveCenterViewModel(
                    repository = vaultRepository,
                    sessionManager = sessionManager,
                    zipManager = zipVaultManager,
                    operationManager = operationManager
                )
            }
            ArchiveCenterScreen(
                viewModel = archiveCenterViewModel,
                repository = vaultRepository,
                onNavigateBack = { navController.popBackStack() },
                onOpenArchive = { itemId ->
                    navController.navigate(VaultDestinations.archiveViewerRoute(itemId))
                }
            )
        }

        // 9. Interactive Secure Archive Viewer & Content Browser
        composable(
            route = VaultDestinations.ARCHIVE_VIEWER,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
            val archiveViewerViewModel = remember(itemId) {
                ArchiveViewerViewModel(
                    itemId = itemId,
                    repository = vaultRepository,
                    sessionManager = sessionManager,
                    zipManager = zipVaultManager,
                    operationManager = operationManager
                )
            }
            ArchiveViewerScreen(
                viewModel = archiveViewerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 5. Encrypted Trash Screen
        composable(VaultDestinations.TRASH) {
            val trashViewModel = remember {
                TrashViewModel(
                    repository = vaultRepository,
                    lifecycleManager = dataLifecycleManager,
                    sessionManager = sessionManager
                )
            }

            TrashScreen(
                viewModel = trashViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // 6. Settings Screen
        composable(VaultDestinations.SETTINGS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val appSettingsManager = remember(context) { com.example.core.settings.AppSettingsManager.getInstance(context) }
            SettingsScreen(
                sessionManager = sessionManager,
                auditLogs = auditLogs,
                onBackClick = { navController.popBackStack() },
                onNavigateToSecurityCenter = {
                    navController.navigate(VaultDestinations.SECURITY_CENTER)
                },
                onNavigateToBackupCenter = {
                    navController.navigate(VaultDestinations.BACKUP_CENTER)
                },
                onNavigateToTrash = {
                    navController.navigate(VaultDestinations.TRASH)
                },
                storageManager = vaultRepository.storageManager,
                lifecycleManager = lifecycleManager,
                appSettingsManager = appSettingsManager,
                voiceLockManager = voiceLockManager,
                onPurgeVault = {
                    scope.launch {
                        vaultRepository.purgeAllVaultData()
                        sessionManager.emergencyWipeAllSecuritySettings()
                        vaultRepository.logSecurityEvent(
                            action = "SETTINGS_PURGE_VAULT",
                            details = "Full vault purge executed from Security Settings",
                            isSuccess = true
                        )
                        navController.navigate(VaultDestinations.AUTH) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 7. Security & Privacy Center
        composable(VaultDestinations.SECURITY_CENTER) {
            SecurityCenterScreen(
                viewModel = securityCenterViewModel,
                activity = activity,
                onBackClick = { navController.popBackStack() },
                onNavigateToLock = {
                    navController.navigate(VaultDestinations.AUTH) {
                        popUpTo(VaultDestinations.HOME) { inclusive = false }
                    }
                },
                onNavigateToBackupCenter = {
                    navController.navigate(VaultDestinations.BACKUP_CENTER)
                }
            )
        }

        // 8. Dedicated Backup & Recovery Center
        composable(VaultDestinations.BACKUP_CENTER) {
            val backupVm = backupViewModel ?: remember {
                val reminderManager = BackupReminderManager(context)
                val backupManager = SecureBackupManager(
                    context = context,
                    vaultRepository = vaultRepository,
                    storageManager = vaultRepository.storageManager,
                    backupHistoryDao = vaultRepository.backupHistoryDao ?: VaultDatabase.getInstance(context).backupHistoryDao(),
                    securityAuditDao = VaultDatabase.getInstance(context).securityAuditDao(),
                    vaultDao = VaultDatabase.getInstance(context).vaultDao(),
                    vaultFolderDao = VaultDatabase.getInstance(context).vaultFolderDao(),
                    sessionManager = sessionManager,
                    reminderManager = reminderManager
                )
                BackupViewModel(
                    backupManager = backupManager,
                    backupHistoryDao = vaultRepository.backupHistoryDao ?: VaultDatabase.getInstance(context).backupHistoryDao()
                )
            }
            BackupScreen(
                viewModel = backupVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 10. Universal Search
        composable(VaultDestinations.UNIVERSAL_SEARCH) {
            val searchHistoryManager = remember {
                SearchHistoryManager(context)
            }
            val searchViewModel = remember {
                UniversalSearchViewModel(
                    repository = vaultRepository,
                    sessionManager = sessionManager,
                    zipVaultManager = zipVaultManager,
                    historyManager = searchHistoryManager
                )
            }
            UniversalSearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { item ->
                    if (item.category == VaultCategory.ZIP.name || item.title.endsWith(".zip", ignoreCase = true)) {
                        navController.navigate(VaultDestinations.archiveViewerRoute(item.id))
                    } else if (item.category == VaultCategory.CODE.name || item.category == VaultCategory.TEXT.name) {
                        navController.navigate(VaultDestinations.codeEditorRoute(item.id))
                    } else {
                        navController.navigate(VaultDestinations.fileViewerRoute(item.id))
                    }
                },
                onLockVault = { sessionManager.lockVault() },
                onNavigateToAuth = {
                    navController.navigate(VaultDestinations.AUTH) {
                        popUpTo(VaultDestinations.HOME) { inclusive = false }
                    }
                }
            )
        }

        // 11. Smart Organization
        composable(VaultDestinations.SMART_ORGANIZATION) {
            val orgViewModel = remember {
                SmartOrganizationViewModel(
                    repository = vaultRepository,
                    sessionManager = sessionManager
                )
            }
            SmartOrganizationScreen(
                viewModel = orgViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenFile = { item ->
                    if (item.category == VaultCategory.ZIP.name || item.title.endsWith(".zip", ignoreCase = true)) {
                        navController.navigate(VaultDestinations.archiveViewerRoute(item.id))
                    } else if (item.category == VaultCategory.CODE.name || item.category == VaultCategory.TEXT.name) {
                        navController.navigate(VaultDestinations.codeEditorRoute(item.id))
                    } else {
                        navController.navigate(VaultDestinations.fileViewerRoute(item.id))
                    }
                },
                onLockVault = { sessionManager.lockVault() },
                onNavigateToAuth = {
                    navController.navigate(VaultDestinations.AUTH) {
                        popUpTo(VaultDestinations.HOME) { inclusive = false }
                    }
                }
            )
        }
    }
}
