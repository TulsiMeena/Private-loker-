package com.example.feature.documents

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors

data class DocumentCreationFormat(
    val title: String,
    val extension: String,
    val mimeType: String,
    val icon: ImageVector,
    val defaultTemplate: String
)

val SupportedCreationFormats = listOf(
    DocumentCreationFormat(
        title = "Plain Text",
        extension = "txt",
        mimeType = "text/plain",
        icon = Icons.Default.Description,
        defaultTemplate = ""
    ),
    DocumentCreationFormat(
        title = "PDF Document",
        extension = "pdf",
        mimeType = "application/pdf",
        icon = Icons.Default.PictureAsPdf,
        defaultTemplate = "%PDF-1.4\n% Vault Secure Encrypted PDF Document\n"
    ),
    DocumentCreationFormat(
        title = "Markdown",
        extension = "md",
        mimeType = "text/markdown",
        icon = Icons.Default.EditNote,
        defaultTemplate = "# Title\n\nWrite your private notes in Markdown...\n"
    ),
    DocumentCreationFormat(
        title = "CSV Table",
        extension = "csv",
        mimeType = "text/csv",
        icon = Icons.Default.TableChart,
        defaultTemplate = "ID,Name,Category,Notes\n1,Sample,Confidential,\n"
    ),
    DocumentCreationFormat(
        title = "JSON Document",
        extension = "json",
        mimeType = "application/json",
        icon = Icons.Default.Code,
        defaultTemplate = "{\n  \"title\": \"Confidential\",\n  \"records\": []\n}\n"
    ),
    DocumentCreationFormat(
        title = "XML Document",
        extension = "xml",
        mimeType = "application/xml",
        icon = Icons.Default.Code,
        defaultTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<document>\n  <note></note>\n</document>\n"
    )
)

@Composable
fun CreateDocumentDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileNameWithExt: String, mimeType: String, initialContent: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFormat by remember { mutableStateOf(SupportedCreationFormats[0]) }
    var fileNameInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Encrypted Document",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = VaultColors.TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select Document Format",
                    fontSize = 12.sp,
                    color = VaultColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Format selection grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SupportedCreationFormats.take(3).forEach { format ->
                        val isSelected = format == selectedFormat
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.15f) else VaultColors.SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFormat = format }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = format.icon,
                                    contentDescription = format.title,
                                    tint = if (isSelected) VaultColors.AccentCyan else VaultColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = format.extension.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) VaultColors.AccentCyan else VaultColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SupportedCreationFormats.drop(3).forEach { format ->
                        val isSelected = format == selectedFormat
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.15f) else VaultColors.SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFormat = format }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = format.icon,
                                    contentDescription = format.title,
                                    tint = if (isSelected) VaultColors.AccentCyan else VaultColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = format.extension.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) VaultColors.AccentCyan else VaultColors.TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = {
                        fileNameInput = it
                        errorMessage = null
                    },
                    label = { Text("Document Name") },
                    placeholder = { Text("e.g. Confidential_Report") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let {
                            Text(text = it, color = VaultColors.AccentCrimson, fontSize = 11.sp)
                        } ?: Text(
                            text = "Will be saved as .${selectedFormat.extension}",
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                        cursorColor = VaultColors.AccentCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_document_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawName = fileNameInput.trim()
                    if (rawName.isEmpty()) {
                        errorMessage = "Document name cannot be empty"
                        return@Button
                    }
                    if (rawName.contains("/") || rawName.contains("\\")) {
                        errorMessage = "Document name cannot contain slashes"
                        return@Button
                    }
                    val fullName = if (rawName.endsWith(".${selectedFormat.extension}", ignoreCase = true)) {
                        rawName
                    } else {
                        "$rawName.${selectedFormat.extension}"
                    }
                    onConfirm(fullName, selectedFormat.mimeType, selectedFormat.defaultTemplate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                modifier = Modifier.testTag("create_document_confirm_button")
            ) {
                Text("Create & Open", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.TextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultColors.GlassBorderSubtle)
            ) {
                Text("Cancel")
            }
        },
        containerColor = VaultColors.SurfaceElevated,
        modifier = modifier.testTag("create_document_dialog")
    )
}
