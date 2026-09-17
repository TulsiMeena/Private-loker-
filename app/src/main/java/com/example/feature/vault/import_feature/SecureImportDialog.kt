package com.example.feature.vault.import_feature

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.database.DuplicateResolution
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.util.Formatters

@Composable
fun SecureImportDialog(
    state: ImportProgressState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state is ImportProgressState.Idle) return

    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current

    Dialog(
        onDismissRequest = {
            if (state is ImportProgressState.Success || state is ImportProgressState.Error || state is ImportProgressState.Cancelled) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is ImportProgressState.Encrypting && state !is ImportProgressState.DuplicatePrompt,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = modifier
                .testTag("secure_import_dialog")
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = VaultColors.SurfaceElevated,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    is ImportProgressState.Preparing -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(VaultColors.SurfaceHighlight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.m))

                        Text(
                            text = "PREPARING ENCRYPTED STREAMS",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.AccentCyan
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        Text(
                            text = state.fileName,
                            style = typography.title,
                            color = VaultColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "File ${state.currentFileIndex} of ${state.totalFiles}",
                            style = typography.bodySmall,
                            color = VaultColors.TextSecondary
                        )

                        Spacer(modifier = Modifier.height(spacing.l))

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = VaultColors.AccentCyan,
                            trackColor = VaultColors.SurfaceHighlight
                        )
                    }

                    is ImportProgressState.Encrypting -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(VaultColors.AccentEmerald.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = VaultColors.AccentEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.m))

                        Text(
                            text = "HARDWARE AES-256 ENCRYPTION",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.AccentEmerald
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        Text(
                            text = state.fileName,
                            style = typography.title,
                            color = VaultColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "File ${state.currentFileIndex} of ${state.totalFiles}",
                            style = typography.bodySmall,
                            color = VaultColors.TextSecondary
                        )

                        Spacer(modifier = Modifier.height(spacing.m))

                        if (state.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { state.bytesProcessed.toFloat() / state.totalBytes.toFloat() },
                                modifier = Modifier
                                    .testTag("import_progress_bar")
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = VaultColors.AccentEmerald,
                                trackColor = VaultColors.SurfaceHighlight
                            )

                            Spacer(modifier = Modifier.height(spacing.xs))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = Formatters.formatBytes(state.bytesProcessed),
                                    style = typography.caption,
                                    color = VaultColors.TextTertiary
                                )
                                Text(
                                    text = "${(state.bytesProcessed * 100 / state.totalBytes)}%",
                                    style = typography.caption.copy(fontWeight = FontWeight.Bold),
                                    color = VaultColors.TextSecondary
                                )
                                Text(
                                    text = Formatters.formatBytes(state.totalBytes),
                                    style = typography.caption,
                                    color = VaultColors.TextTertiary
                                )
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = VaultColors.AccentEmerald,
                                trackColor = VaultColors.SurfaceHighlight
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.l))

                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("import_cancel_button")
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Text("Abort Import", color = VaultColors.TextSecondary)
                        }
                    }

                    is ImportProgressState.Verifying -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(VaultColors.SurfaceHighlight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.m))

                        Text(
                            text = "VERIFYING CRYPTOGRAPHIC DIGEST",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.AccentCyan
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        Text(
                            text = state.fileName,
                            style = typography.title,
                            color = VaultColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(spacing.m))

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = VaultColors.AccentCyan,
                            trackColor = VaultColors.SurfaceHighlight
                        )
                    }

                    is ImportProgressState.DuplicatePrompt -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(VaultColors.AccentAmber.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = VaultColors.AccentAmber,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.m))

                        Text(
                            text = "DUPLICATE ITEM DETECTED",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.AccentAmber
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        Text(
                            text = state.fileName,
                            style = typography.title,
                            color = VaultColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = state.conflictReason,
                            style = typography.bodySmall,
                            color = VaultColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(spacing.l))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.s)
                        ) {
                            Button(
                                onClick = { state.onResolutionSelected(DuplicateResolution.KEEP_BOTH) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                            ) {
                                Text("Keep Both (Append Copy)", color = VaultColors.Canvas)
                            }

                            Button(
                                onClick = { state.onResolutionSelected(DuplicateResolution.REPLACE) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceHighlight)
                            ) {
                                Text("Replace Existing Record", color = VaultColors.TextPrimary)
                            }

                            OutlinedButton(
                                onClick = { state.onResolutionSelected(DuplicateResolution.SKIP) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Skip This File", color = VaultColors.TextSecondary)
                            }
                        }
                    }

                    is ImportProgressState.Success -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(VaultColors.AccentEmerald.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = VaultColors.AccentEmerald,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.m))

                        Text(
                            text = "IMPORT SECURED",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.AccentEmerald
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        Text(
                            text = "${state.importedCount} File(s) Encrypted",
                            style = typography.title,
                            color = VaultColors.TextPrimary
                        )

                        Text(
                            text = state.summaryText,
                            style = typography.bodySmall,
                            color = VaultColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(spacing.l))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .testTag("import_dismiss_button")
                                .fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentEmerald)
                        ) {
                            Text("Done", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                        }
                    }

                    is ImportProgressState.Error -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(VaultColors.AccentCrimson.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = VaultColors.AccentCrimson,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.m))

                        Text(
                            text = "IMPORT FAILED",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.AccentCrimson
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        Text(
                            text = state.errorMessage,
                            style = typography.bodySmall,
                            color = VaultColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(spacing.l))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceHighlight)
                        ) {
                            Text("Close", color = VaultColors.TextPrimary)
                        }
                    }

                    is ImportProgressState.Cancelled -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(VaultColors.SurfaceHighlight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = VaultColors.Titanium,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.m))

                        Text(
                            text = "IMPORT CANCELLED",
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.Titanium
                        )

                        Spacer(modifier = Modifier.height(spacing.xs))

                        Text(
                            text = "Incomplete files were securely shredded with zero lingering plaintext.",
                            style = typography.bodySmall,
                            color = VaultColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(spacing.l))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceHighlight)
                        ) {
                            Text("Dismiss", color = VaultColors.TextPrimary)
                        }
                    }

                    ImportProgressState.Idle -> {}
                }
            }
        }
    }
}
