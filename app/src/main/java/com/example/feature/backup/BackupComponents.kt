package com.example.feature.backup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.BackupHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        "VERIFIED" -> Triple(Color(0xFF1B5E20), Color(0xFF81C784), "VERIFIED")
        "ATTENTION_REQUIRED" -> Triple(Color(0xFFE65100), Color(0xFFFFB74D), "ATTENTION REQUIRED")
        "CORRUPTED" -> Triple(Color(0xFFB71C1C), Color(0xFFE57373), "CORRUPTED")
        "PENDING" -> Triple(Color(0xFF004D40), Color(0xFF80CBC4), "PENDING")
        else -> Triple(Color(0xFF37474F), Color(0xFFB0BEC5), "NOT CONFIGURED")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = 0.25f))
            .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SecurityPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun BackupStatusCard(
    latestBackup: BackupHistoryEntity?,
    latestVerified: BackupHistoryEntity?,
    isDue: Boolean,
    reminderSchedule: BackupReminderSchedule,
    onOpenReminderConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("backup_status_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Backup Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                BackupStatusBadge(
                    status = when {
                        latestVerified != null -> "VERIFIED"
                        latestBackup != null -> latestBackup.verificationStatus
                        else -> "NOT_CONFIGURED"
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Last Backup Timestamp
            val dateStr = if (latestBackup != null) {
                val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
                sdf.format(Date(latestBackup.timestamp))
            } else {
                "Never"
            }

            Text(
                text = "Last Backup: $dateStr",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (latestVerified != null) {
                val verifiedDateStr = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
                    .format(Date(latestVerified.lastVerifiedTimestamp ?: latestVerified.timestamp))
                Text(
                    text = "Last Verified: $verifiedDateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            if (latestBackup != null) {
                Text(
                    text = "Objects Protected: ${latestBackup.objectCount} items (${latestBackup.backupSizeBytes / 1024} KB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Overdue or Schedule Warning
            if (isDue) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Backup reminder due (${reminderSchedule.displayName})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reminder: ${reminderSchedule.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onOpenReminderConfig,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Change", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateBackupModal(
    onDismiss: () -> Unit,
    onProceedToSaf: (protectionType: BackupProtectionType, secret: CharArray) -> Unit,
    onGenerateRecoveryKey: () -> String
) {
    var selectedMethod by remember { mutableStateOf(BackupProtectionType.PASSPHRASE) }
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var isPassphraseVisible by remember { mutableStateOf(false) }
    var generatedKey by remember { mutableStateOf("") }
    var userConfirmedSavedKey by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Encrypted Backup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "All metadata and vault files will be encrypted using AES-256-GCM. Select how you want to protect this backup:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Option 1: Passphrase
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedMethod = BackupProtectionType.PASSPHRASE }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMethod == BackupProtectionType.PASSPHRASE,
                        onClick = { selectedMethod = BackupProtectionType.PASSPHRASE }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Recovery Passphrase", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Enter a strong passphrase to encrypt your backup",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Option 2: Generated Recovery Key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            selectedMethod = BackupProtectionType.RECOVERY_KEY
                            if (generatedKey.isEmpty()) {
                                generatedKey = onGenerateRecoveryKey()
                            }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMethod == BackupProtectionType.RECOVERY_KEY,
                        onClick = {
                            selectedMethod = BackupProtectionType.RECOVERY_KEY
                            if (generatedKey.isEmpty()) {
                                generatedKey = onGenerateRecoveryKey()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Generated Recovery Key", fontWeight = FontWeight.SemiBold)
                        Text(
                            "High-entropy 256-bit cryptographically random key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedMethod == BackupProtectionType.PASSPHRASE) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            errorMessage = null
                        },
                        label = { Text("Backup Passphrase (min 8 chars)") },
                        visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                                Icon(
                                    imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Passphrase Visibility"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_backup_passphrase")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = {
                            confirmPassphrase = it
                            errorMessage = null
                        },
                        label = { Text("Confirm Passphrase") },
                        visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_confirm_backup_passphrase")
                    )
                } else {
                    // Generated Key Display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Your Secure Recovery Key:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = generatedKey.ifEmpty { "Generating..." },
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = userConfirmedSavedKey,
                            onCheckedChange = { userConfirmedSavedKey = it },
                            modifier = Modifier.testTag("checkbox_confirm_saved_key")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "I have written down or safely recorded this recovery key. I understand it cannot be recovered if lost.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "🔒 No cloud. You will select where to save the .pvault file via Android Storage Access Framework.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMethod == BackupProtectionType.PASSPHRASE) {
                        if (passphrase.length < 8) {
                            errorMessage = "Passphrase must be at least 8 characters long."
                            return@Button
                        }
                        if (passphrase != confirmPassphrase) {
                            errorMessage = "Passphrases do not match."
                            return@Button
                        }
                        onProceedToSaf(BackupProtectionType.PASSPHRASE, passphrase.toCharArray())
                    } else {
                        if (!userConfirmedSavedKey) {
                            errorMessage = "You must confirm you recorded your recovery key."
                            return@Button
                        }
                        onProceedToSaf(BackupProtectionType.RECOVERY_KEY, generatedKey.toCharArray())
                    }
                },
                modifier = Modifier.testTag("btn_proceed_to_saf")
            ) {
                Text("Select Location & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BackupProgressModal(
    state: BackupCreationState,
    onCancel: () -> Unit,
    onDismissCompleted: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Modal during active encryption */ },
        title = {
            Text(
                text = when (state) {
                    is BackupCreationState.Preparing -> "Preparing Vault Objects"
                    is BackupCreationState.Encrypting -> "Encrypting Backup"
                    is BackupCreationState.Authenticating -> "Generating Authenticated Manifest"
                    is BackupCreationState.Finalizing -> "Finalizing & Verifying"
                    is BackupCreationState.Completed -> "Backup Complete"
                    is BackupCreationState.Error -> "Backup Failed"
                    else -> "Backup Progress"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                when (state) {
                    is BackupCreationState.Preparing -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing ${state.totalItems} items (${state.totalSizeBytes / 1024} KB)...",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is BackupCreationState.Encrypting -> {
                        val progress = if (state.totalItems > 0) {
                            state.currentItemIndex.toFloat() / state.totalItems.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Item ${state.currentItemIndex} of ${state.totalItems}: ${state.currentFileTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "Streaming AES-256-GCM encryption in-memory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is BackupCreationState.Authenticating -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Computing SHA-256 digests and sealing authenticated manifest...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is BackupCreationState.Finalizing -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Streaming atomic container to target location...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    is BackupCreationState.Completed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Vault backup successfully created and cryptographically verified.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Objects: ${state.objectCount}\n• Size: ${state.backupSizeBytes / 1024} KB\n• Location: ${state.destinationLabel}\n• Elapsed: ${state.durationMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is BackupCreationState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            if (state is BackupCreationState.Completed || state is BackupCreationState.Error) {
                Button(onClick = onDismissCompleted) {
                    Text("Done")
                }
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel Backup")
                }
            }
        }
    )
}

@Composable
fun RestoreSecretModal(
    onDismiss: () -> Unit,
    onSubmitSecret: (secretChars: CharArray) -> Unit
) {
    var secretText by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enter Recovery Secret",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter the Recovery Passphrase or Generated Recovery Key used when creating this backup:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = secretText,
                    onValueChange = {
                        secretText = it
                        error = null
                    },
                    label = { Text("Passphrase or Recovery Key") },
                    visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Secret Visibility"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_restore_secret")
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (secretText.trim().isEmpty()) {
                        error = "Please enter your recovery secret."
                        return@Button
                    }
                    onSubmitSecret(secretText.trim().toCharArray())
                },
                modifier = Modifier.testTag("btn_inspect_backup")
            ) {
                Text("Inspect Backup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RestorePreviewModal(
    preview: RestorePreviewData,
    onDismiss: () -> Unit,
    onConfirmRestore: (mode: RestoreMode, conflictResolution: RestoreConflictResolution) -> Unit
) {
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE_VAULT) }
    var conflictResolution by remember { mutableStateOf(RestoreConflictResolution.KEEP_BOTH) }
    var userAcknowledged by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restore Preview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Summary Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = preview.backupName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val dateStr = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
                            .format(Date(preview.createdAt))
                        Text(
                            text = "Created: $dateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Objects: ${preview.objectCount} items · ${preview.totalSizeBytes / 1024} KB",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Protection: ${preview.protectionType.name} · Format v${preview.formatVersion}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Restore Mode Selection
                Text("Restore Mode:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { restoreMode = RestoreMode.MERGE_VAULT }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = restoreMode == RestoreMode.MERGE_VAULT,
                        onClick = { restoreMode = RestoreMode.MERGE_VAULT }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Merge into Existing Vault", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "Preserves current vault and imports non-duplicate items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { restoreMode = RestoreMode.REPLACE_VAULT }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = restoreMode == RestoreMode.REPLACE_VAULT,
                        onClick = { restoreMode = RestoreMode.REPLACE_VAULT }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Restore as New Vault", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "Replaces existing local vault items with the backup",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (restoreMode == RestoreMode.MERGE_VAULT && preview.conflictCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Potential Conflicts: ${preview.conflictCount} items with matching titles or hashes.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Conflict Strategy:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ConflictOptionChip("Keep Both", conflictResolution == RestoreConflictResolution.KEEP_BOTH) {
                            conflictResolution = RestoreConflictResolution.KEEP_BOTH
                        }
                        ConflictOptionChip("Use Backup", conflictResolution == RestoreConflictResolution.USE_BACKUP) {
                            conflictResolution = RestoreConflictResolution.USE_BACKUP
                        }
                        ConflictOptionChip("Skip", conflictResolution == RestoreConflictResolution.SKIP) {
                            conflictResolution = RestoreConflictResolution.SKIP
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = userAcknowledged,
                        onCheckedChange = { userAcknowledged = it },
                        modifier = Modifier.testTag("checkbox_confirm_restore")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "I confirm restoration into this device's encrypted vault.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRestore(restoreMode, conflictResolution) },
                enabled = userAcknowledged,
                modifier = Modifier.testTag("btn_execute_restore")
            ) {
                Text("Confirm & Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConflictOptionChip(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(6.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RestoreProgressModal(
    state: RestoreProgressState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Modal while active */ },
        title = {
            Text(
                text = when (state) {
                    is RestoreProgressState.Inspecting -> "Inspecting Backup Container"
                    is RestoreProgressState.Authenticating -> "Unwrapping Backup Master Key"
                    is RestoreProgressState.StagingObjects -> "Importing Encrypted Objects"
                    is RestoreProgressState.ReconcilingDatabase -> "Reconciling Metadata Database"
                    is RestoreProgressState.VerifyingRestoredState -> "Verifying Vault Integrity"
                    is RestoreProgressState.Completed -> "Restore Completed"
                    is RestoreProgressState.Error -> "Restore Failed"
                    else -> "Restore Progress"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    is RestoreProgressState.StagingObjects -> {
                        val progress = if (state.totalItems > 0) {
                            state.currentItemIndex.toFloat() / state.totalItems.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Importing item ${state.currentItemIndex} of ${state.totalItems} into device KeyStore",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is RestoreProgressState.Completed -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Vault successfully restored and bound to this device's Android KeyStore.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Restored Items: ${state.restoredCount}\n• Merged Items: ${state.mergedCount}\n• Conflicts Resolved: ${state.conflictsResolved}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is RestoreProgressState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        },
        confirmButton = {
            if (state is RestoreProgressState.Completed || state is RestoreProgressState.Error) {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun VerificationReportModal(
    result: BackupVerificationResult?,
    onDismiss: () -> Unit
) {
    if (result == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Backup Verification Report",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Integrity Status:", fontWeight = FontWeight.SemiBold)
                    BackupStatusBadge(status = result.status.name)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp)
                ) {
                    Column {
                        Text("• Format: PrivateVault v${result.formatVersion}", style = MaterialTheme.typography.bodySmall)
                        Text("• Protection: ${result.protectionType.name}", style = MaterialTheme.typography.bodySmall)
                        Text("• Objects: ${result.objectCount} items (${result.totalSizeBytes / 1024} KB)", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Verification Checklist:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))

                result.details.forEach { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (detail.startsWith("✓")) Color(0xFF2E7D32)
                        else if (detail.startsWith("✗")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (result.errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${result.errorMessage}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun BackupHistoryRow(
    item: BackupHistoryEntity,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateStr = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
                    .format(Date(item.timestamp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                BackupStatusBadge(status = item.verificationStatus)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${item.objectCount} items · ${item.backupSizeBytes / 1024} KB · ${item.protectionType} · ${item.destinationLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onVerify,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verify Integrity", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Backup Record",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
