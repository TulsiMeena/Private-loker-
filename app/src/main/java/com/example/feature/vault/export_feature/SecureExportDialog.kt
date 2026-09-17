package com.example.feature.vault.export_feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.core.database.VaultItemEntity
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.util.Formatters

@Composable
fun SecureExportDialog(
    item: VaultItemEntity,
    onConfirmExport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .testTag("secure_export_dialog")
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = VaultColors.SurfaceElevated,
            tonalElevation = 8.dp
        ) {
            val spacing = LocalVaultSpacing.current
            val typography = LocalVaultTypography.current

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                    text = "SECURITY BOUNDARY EXPORT",
                    style = typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = VaultColors.AccentAmber
                )

                Spacer(modifier = Modifier.height(spacing.xs))

                Text(
                    text = item.title,
                    style = typography.title,
                    color = VaultColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${Formatters.formatBytes(item.sizeBytes)} • ${item.mimeType}",
                    style = typography.bodySmall,
                    color = VaultColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(spacing.m))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VaultColors.SurfaceGraphite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "WARNING: Exporting this file outside the private vault decrypts the payload into public storage. Once exported, the file will no longer be protected by AES-256 hardware encryption.",
                        style = typography.bodySmall,
                        color = VaultColors.TextSecondary,
                        modifier = Modifier.padding(spacing.m),
                        textAlign = TextAlign.Start
                    )
                }

                Spacer(modifier = Modifier.height(spacing.l))

                Button(
                    onClick = onConfirmExport,
                    modifier = Modifier
                        .testTag("confirm_export_button")
                        .fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber)
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, tint = VaultColors.Canvas)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text("Decrypt & Export via SAF", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(spacing.s))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .testTag("cancel_export_button")
                        .fillMaxWidth()
                ) {
                    Text("Keep Secured in Vault", color = VaultColors.TextSecondary)
                }
            }
        }
    }
}
