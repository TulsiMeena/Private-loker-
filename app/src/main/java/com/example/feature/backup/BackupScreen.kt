package com.example.feature.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val historyList by viewModel.historyList.collectAsState()
    val latestBackup by viewModel.latestBackup.collectAsState()
    val latestVerified by viewModel.latestVerifiedBackup.collectAsState()
    val isDue by viewModel.isBackupDue.collectAsState()
    val reminderSchedule by viewModel.reminderSchedule.collectAsState()
    val creationState by viewModel.creationState.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()
    val restorePreview by viewModel.restorePreview.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val showRestoreSecretDialog by viewModel.showRestoreSecretDialog.collectAsState()
    val showRestorePreviewDialog by viewModel.showRestorePreviewDialog.collectAsState()
    val showVerificationDialog by viewModel.showVerificationDialog.collectAsState()
    val pendingVerifySecretDialog by viewModel.pendingVerifySecretDialog.collectAsState()
    val uiToast by viewModel.uiToast.collectAsState()

    // Transient state for pending backup creation parameters before SAF launch
    var pendingProtectionType by remember { mutableStateOf<BackupProtectionType?>(null) }
    var pendingSecretChars by remember { mutableStateOf<CharArray?>(null) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var verifySecretInput by remember { mutableStateOf("") }

    // SAF Document Creator launcher
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && pendingProtectionType != null && pendingSecretChars != null) {
            val fileName = uri.lastPathSegment ?: "PrivateVault_Backup"
            viewModel.executeBackupCreation(
                destinationUri = uri,
                protectionType = pendingProtectionType!!,
                secretChars = pendingSecretChars!!,
                destinationLabel = fileName
            )
        }
        pendingProtectionType = null
        pendingSecretChars = null
    }

    // SAF Document Picker launcher for Restore
    val restorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onRestoreFileSelected(uri)
        }
    }

    // SAF Document Picker launcher for Verification
    val verifyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onVerifyFileSelected(uri)
        }
    }

    LaunchedEffect(uiToast) {
        uiToast?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUiToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "BACKUP & RECOVERY",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Protect your encrypted vault and recover it when you need it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_backup_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Badges Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecurityPill(text = "ENCLAVE")
                    SecurityPill(text = "AES-256-GCM")
                    SecurityPill(text = "AIR-GAPPED")
                }
            }

            // 1. Backup Status Card
            item {
                BackupStatusCard(
                    latestBackup = latestBackup,
                    latestVerified = latestVerified,
                    isDue = isDue,
                    reminderSchedule = reminderSchedule,
                    onOpenReminderConfig = { showReminderPicker = true }
                )
            }

            // 2. Action Cards Section
            item {
                Text(
                    text = "VAULT ACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            // Create Backup Card
            item {
                ActionCard(
                    title = "Create Encrypted Backup",
                    subtitle = "Export your encrypted vault data into an authenticated .pvault archive via Storage Access Framework.",
                    icon = Icons.Default.FileUpload,
                    buttonLabel = "Create Backup",
                    testTag = "btn_create_backup_action",
                    onClick = { viewModel.openCreateBackupDialog() }
                )
            }

            // Restore Backup Card
            item {
                ActionCard(
                    title = "Restore Vault",
                    subtitle = "Recover vault items from a .pvault container. Supports full replacement or non-destructive merging with conflict resolution.",
                    icon = Icons.Default.FileDownload,
                    buttonLabel = "Select Backup File",
                    testTag = "btn_restore_vault_action",
                    onClick = {
                        restorePickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    }
                )
            }

            // Verify Backup Card
            item {
                ActionCard(
                    title = "Verify Backup File",
                    subtitle = "Perform non-destructive structural, format, and cryptographic integrity checks without exposing plaintext to disk.",
                    icon = Icons.Default.VerifiedUser,
                    buttonLabel = "Verify Integrity",
                    testTag = "btn_verify_backup_action",
                    onClick = {
                        verifyPickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                    }
                )
            }

            // 3. Backup History Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BACKUP HISTORY (${historyList.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (historyList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No backup history recorded yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Create a backup to protect your encrypted data across device resets.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                items(historyList, key = { it.id }) { historyItem ->
                    BackupHistoryRow(
                        item = historyItem,
                        onVerify = { viewModel.verifyHistoryItem(historyItem) },
                        onDelete = { viewModel.deleteHistoryRecord(historyItem.id) }
                    )
                }
            }

            // 4. Recovery Configuration & Privacy Card
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Air-Gapped Privacy Architecture",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "• Zero Cloud: PrivateVault does not operate servers or cloud backups. You hold exclusive control over your backup files.\n" +
                                    "• Envelope Encryption: AES-256-GCM authenticated cipher with 100,000 PBKDF2 iterations.\n" +
                                    "• Zero Plaintext Disk Leaks: Files are streamed between device KeyStore and backup cipher without saving unencrypted copies to disk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Important: If you lose your recovery passphrase or recovery key, encrypted backups cannot be recovered by anyone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // --- Dialogs ---

    if (showCreateDialog) {
        CreateBackupModal(
            onDismiss = { viewModel.dismissCreateDialog() },
            onProceedToSaf = { protectionType, secret ->
                viewModel.dismissCreateDialog()
                pendingProtectionType = protectionType
                pendingSecretChars = secret

                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val timestampStr = sdf.format(Date())
                val defaultName = "${BackupConstants.DEFAULT_BACKUP_FILENAME_PREFIX}$timestampStr${BackupConstants.BACKUP_FILE_EXTENSION}"
                createDocLauncher.launch(defaultName)
            },
            onGenerateRecoveryKey = { viewModel.generateNewRecoveryKey() }
        )
    }

    if (creationState !is BackupCreationState.Idle) {
        BackupProgressModal(
            state = creationState,
            onCancel = { viewModel.cancelActiveOperation() },
            onDismissCompleted = { viewModel.resetCreationState() }
        )
    }

    if (showRestoreSecretDialog) {
        RestoreSecretModal(
            onDismiss = { viewModel.dismissRestoreSecretDialog() },
            onSubmitSecret = { secretChars ->
                viewModel.inspectPendingRestore(secretChars)
            }
        )
    }

    if (showRestorePreviewDialog && restorePreview != null) {
        RestorePreviewModal(
            preview = restorePreview!!,
            onDismiss = { viewModel.dismissRestorePreviewDialog() },
            onConfirmRestore = { mode, conflictResolution ->
                viewModel.executeRestore(mode, conflictResolution)
            }
        )
    }

    if (restoreState !is RestoreProgressState.Idle) {
        RestoreProgressModal(
            state = restoreState,
            onDismiss = { viewModel.resetRestoreState() }
        )
    }

    if (showVerificationDialog) {
        VerificationReportModal(
            result = verificationResult,
            onDismiss = { viewModel.dismissVerificationDialog() }
        )
    }

    if (pendingVerifySecretDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissVerifySecretDialog() },
            title = { Text("Verify Backup Integrity") },
            text = {
                Column {
                    Text(
                        "You can verify container structure immediately, or optionally enter the recovery secret to deeply authenticate manifest digests and all internal objects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = verifySecretInput,
                        onValueChange = { verifySecretInput = it },
                        label = { Text("Recovery Secret (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chars = if (verifySecretInput.trim().isNotEmpty()) verifySecretInput.trim().toCharArray() else null
                        viewModel.executeVerification(chars)
                        verifySecretInput = ""
                    }
                ) {
                    Text("Verify Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissVerifySecretDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReminderPicker) {
        AlertDialog(
            onDismissRequest = { showReminderPicker = false },
            title = { Text("Backup Reminder Schedule") },
            text = {
                Column {
                    BackupReminderSchedule.entries.forEach { schedule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setReminderSchedule(schedule)
                                    showReminderPicker = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = reminderSchedule == schedule,
                                onClick = {
                                    viewModel.setReminderSchedule(schedule)
                                    showReminderPicker = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(schedule.displayName, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonLabel: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}
