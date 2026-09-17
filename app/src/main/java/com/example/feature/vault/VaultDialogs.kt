package com.example.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.designsystem.VaultColors
import com.example.core.storage.VaultCategory

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Text(
                text = "Rename Item",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "The physical encrypted file retains its cryptographic anonymity. Only metadata is updated.",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = when {
                            it.isBlank() -> "Name cannot be empty"
                            it.contains("/") || it.contains("\\") -> "Cannot contain path characters"
                            it.length > 120 -> "Name too long (max 120 chars)"
                            else -> null
                        }
                    },
                    label = { Text("Display Name") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it, color = VaultColors.AccentCrimson) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                        cursorColor = VaultColors.AccentCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_text_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && errorText == null) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank() && errorText == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = VaultColors.AccentCyan
            )
        },
        title = {
            Text(
                text = "New Secure Folder",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Virtual folders organize your encrypted items. Files remain individual AES-256 containers.",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        errorText = when {
                            it.isBlank() -> "Folder name cannot be empty"
                            it.contains("/") || it.contains("\\") -> "Cannot contain slashes"
                            else -> null
                        }
                    },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it, color = VaultColors.AccentCrimson) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                        cursorColor = VaultColors.AccentCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank() && errorText == null) {
                        onConfirm(folderName.trim())
                    }
                },
                enabled = folderName.isNotBlank() && errorText == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("create_folder_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
fun CreateTextFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, initialContent: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.NoteAdd,
                contentDescription = null,
                tint = VaultColors.AccentEmerald
            )
        },
        title = {
            Text(
                text = "New Secure Note",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title (e.g. Passwords, Ideas.txt)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentEmerald,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_note_title")
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Initial Content (Encrypted immediately)") },
                    minLines = 4,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentEmerald,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_note_content")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = if (title.isBlank()) "Untitled Note.txt"
                    else if (!title.contains('.')) "$title.txt" else title
                    onConfirm(finalTitle.trim(), content)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentEmerald,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("new_note_confirm")
            ) {
                Text("Create Note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
fun CreateCodeFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, initialContent: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedExt by remember { mutableStateOf("kt") }
    val extensions = listOf("kt", "java", "py", "js", "ts", "json", "xml", "sql", "html", "css", "md", "sh")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Text(
                text = "New Secure Code File",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Filename (without extension)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Select Language / Extension:",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                    val chunked = extensions.chunked(4)
                    items(chunked) { rowExts ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            rowExts.forEach { ext ->
                                val isSelected = ext == selectedExt
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) VaultColors.AccentCyan else VaultColors.SurfaceGraphite)
                                        .border(
                                            1.dp,
                                            if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedExt = ext }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = ".$ext",
                                        color = if (isSelected) Color.Black else VaultColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = if (title.isBlank()) "snippet" else title.trim().substringBeforeLast('.')
                    val finalName = "$base.$selectedExt"
                    val template = when (selectedExt) {
                        "kt" -> "// Kotlin Source File\n\nfun main() {\n    println(\"Encrypted execution safe\")\n}\n"
                        "py" -> "# Python Script\n\ndef main():\n    pass\n"
                        "json" -> "{\n  \"status\": \"secure\",\n  \"encrypted\": true\n}\n"
                        "sql" -> "-- SQL Query\nSELECT * FROM sensitive_records;\n"
                        else -> "// Source file: $finalName\n\n"
                    }
                    onConfirm(finalName, template)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentCyan,
                    contentColor = Color.Black
                )
            ) {
                Text("Create Code File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<VaultFolderEntity>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onFolderSelected: (targetFolderId: Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.DriveFileMove,
                contentDescription = null,
                tint = VaultColors.AccentCyan
            )
        },
        title = {
            Text(
                text = "Move to Folder",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Moving an item updates metadata atomically without decrypting and re-encrypting the physical object.",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    // Root option
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currentFolderId == null) VaultColors.SurfaceGraphite else Color.Transparent)
                                .clickable { onFolderSelected(null) }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Vault Root (Top Level)",
                                color = VaultColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (currentFolderId == null) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    items(folders) { folder ->
                        val isCurrent = folder.id == currentFolderId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) VaultColors.SurfaceGraphite else Color.Transparent)
                                .clickable { onFolderSelected(folder.id) }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = VaultColors.AccentAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                color = VaultColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrent) {
                                Text(
                                    text = "Current",
                                    color = VaultColors.TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTagsDialog(
    currentTags: String,
    onDismiss: () -> Unit,
    onSave: (newTags: String) -> Unit
) {
    val predefinedSuggestions = listOf("Work", "Personal", "Important", "College", "Projects", "Finance", "Legal")
    val activeTags = remember {
        currentTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
    }
    var tagList by remember { mutableStateOf(activeTags.toList()) }
    var newTagInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Text(
                text = "Manage Tags",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Tags are stored purely in local metadata. No external synchronizers.",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Current active tags
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    tagList.forEach { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(VaultColors.SurfaceGraphite)
                                .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = tag, color = VaultColors.TextPrimary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $tag",
                                tint = VaultColors.AccentCrimson,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        val updated = tagList.toMutableList()
                                        updated.remove(tag)
                                        tagList = updated
                                    }
                            )
                        }
                    }
                }

                // Add new tag row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it.replace(",", "") },
                        placeholder = { Text("Add custom tag...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VaultColors.TextPrimary,
                            unfocusedTextColor = VaultColors.TextPrimary,
                            focusedBorderColor = VaultColors.AccentCyan,
                            unfocusedBorderColor = VaultColors.GlassBorderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = newTagInput.trim()
                            if (trimmed.isNotEmpty() && !tagList.contains(trimmed)) {
                                tagList = tagList + trimmed
                                newTagInput = ""
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(VaultColors.AccentCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add tag",
                            tint = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Suggestions:", color = VaultColors.TextTertiary, fontSize = 11.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    predefinedSuggestions.filter { !tagList.contains(it) }.forEach { suggestion ->
                        Text(
                            text = "+ $suggestion",
                            color = VaultColors.AccentCyan,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(VaultColors.SurfaceGraphite)
                                .clickable { tagList = tagList + suggestion }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(tagList.joinToString(", ")) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentCyan,
                    contentColor = Color.Black
                )
            ) {
                Text("Apply Tags")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
fun ExportConfirmationDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = VaultColors.AccentAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Export Plaintext Copy?",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "Exporting this file will create an unencrypted copy outside PrivateVault on device storage.",
                    color = VaultColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "File: \"$fileName\"\n\nOnce exported, other apps with storage permissions may read it. The encrypted master in PrivateVault remains secure.",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentAmber,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("export_confirm_button")
            ) {
                Text("Export Copy")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.TextPrimary),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(VaultColors.GlassBorderSubtle))
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    itemCount: Int,
    isPermanent: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        icon = {
            Icon(
                imageVector = if (isPermanent) Icons.Default.WarningAmber else Icons.Default.Lock,
                contentDescription = null,
                tint = VaultColors.AccentCrimson
            )
        },
        title = {
            Text(
                text = if (isPermanent) "Permanently Shred File(s)?" else "Move to Encrypted Trash?",
                color = VaultColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = if (isPermanent) {
                    "Are you sure you want to permanently shred $itemCount item(s)? Data will undergo zero-trace cryptographic erasure and cannot be recovered."
                } else {
                    "Move $itemCount item(s) to Encrypted Trash? You can restore them anytime before emptying trash."
                },
                color = VaultColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentCrimson,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text(if (isPermanent) "Shred Permanently" else "Move to Trash")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}
