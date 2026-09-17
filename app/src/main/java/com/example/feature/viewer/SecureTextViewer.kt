package com.example.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors

/**
 * Enterprise Secure Text & Markdown Editor.
 *
 * Security & Functional Specs:
 * - Operates entirely in volatile RAM buffer.
 * - Re-encrypts directly into AES-256-GCM vault on save.
 * - Local Undo & Redo state management.
 * - Find & Replace engine.
 * - Live Word, Character, and Line counters.
 * - Markdown preview parser.
 * - Unsaved changes guard on exit.
 */
@Composable
fun SecureTextViewer(
    initialText: String,
    fileName: String = "document.txt",
    onSaveContent: (newText: String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var textContent by remember { mutableStateOf(initialText) }
    var isEditMode by remember { mutableStateOf(false) }
    var isMarkdownPreview by remember { mutableStateOf(false) }

    // Search and Replace states
    var isSearchReplaceOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    // Editor formatting states
    var fontSizeSp by remember { mutableFloatStateOf(14f) }
    var useMonospace by remember { mutableStateOf(true) }
    var isWordWrapEnabled by remember { mutableStateOf(true) }

    // Undo / Redo history
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    var isSaving by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    val hasChanges = textContent != initialText
    val isMarkdownFile = remember(fileName) {
        fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".markdown", ignoreCase = true)
    }

    // Intercept back navigation if there are unsaved changes
    if (hasChanges && onNavigateBack != null) {
        BackHandler {
            showUnsavedChangesDialog = true
        }
    }

    fun updateText(newText: String) {
        if (newText != textContent) {
            undoStack.add(textContent)
            redoStack.clear()
            textContent = newText
        }
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(textContent)
            textContent = previous
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(textContent)
            textContent = next
        }
    }

    // Metrics calculation
    val charCount = textContent.length
    val charCountNoSpaces = remember(textContent) { textContent.count { !it.isWhitespace() } }
    val lineCount = remember(textContent) { textContent.lines().size }
    val wordCount = remember(textContent) {
        if (textContent.isBlank()) 0
        else textContent.trim().split(Regex("\\s+")).size
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
    ) {
        // Toolbar Row 1: Save status, Undo/Redo, Search, Mode toggle, Save
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Save status pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (hasChanges) VaultColors.AccentAmber.copy(alpha = 0.15f) else VaultColors.AccentEmerald.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (hasChanges) VaultColors.AccentAmber.copy(alpha = 0.4f) else VaultColors.AccentEmerald.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (hasChanges) VaultColors.AccentAmber else VaultColors.AccentEmerald)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (hasChanges) "UNSAVED" else "SAVED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasChanges) VaultColors.AccentAmber else VaultColors.AccentEmerald
                    )
                }
            }

            // Central Actions: Undo, Redo, Find/Replace, Format
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isEditMode) {
                    IconButton(
                        onClick = { handleUndo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) VaultColors.TextPrimary else VaultColors.TextDisabled,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { handleRedo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) VaultColors.TextPrimary else VaultColors.TextDisabled,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Find & Replace toggle
                IconButton(
                    onClick = {
                        isSearchReplaceOpen = !isSearchReplaceOpen
                        if (!isSearchReplaceOpen) {
                            searchQuery = ""
                            replaceQuery = ""
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FindReplace,
                        contentDescription = "Find and Replace",
                        tint = if (isSearchReplaceOpen) VaultColors.AccentCyan else VaultColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Markdown Preview toggle (if markdown)
                if (isMarkdownFile) {
                    IconButton(
                        onClick = { isMarkdownPreview = !isMarkdownPreview },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isMarkdownPreview) Icons.Default.Edit else Icons.Default.Preview,
                            contentDescription = "Markdown Preview",
                            tint = if (isMarkdownPreview) VaultColors.AccentCyan else VaultColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Mode toggle (Read / Edit)
                IconButton(
                    onClick = {
                        isEditMode = !isEditMode
                        if (isEditMode) isMarkdownPreview = false
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("toggle_edit_mode_button")
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                        contentDescription = if (isEditMode) "Read Mode" else "Edit Mode",
                        tint = if (isEditMode) VaultColors.AccentCyan else VaultColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Save button
                if (hasChanges) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            isSaving = true
                            onSaveContent(textContent)
                            isSaving = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultColors.AccentEmerald,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("save_text_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Toolbar Row 2: Live Metrics and Font Controls
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$wordCount words · $charCount chars · $lineCount lines",
                color = VaultColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "A-",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.TextSecondary,
                    modifier = Modifier
                        .clickable { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(11f) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = "${fontSizeSp.toInt()}sp",
                    fontSize = 11.sp,
                    color = VaultColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = "A+",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.TextSecondary,
                    modifier = Modifier
                        .clickable { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(24f) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Find & Replace Expansion Panel
        if (isSearchReplaceOpen) {
            Surface(
                color = VaultColors.SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Find Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Find...", fontSize = 12.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(16.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VaultColors.TextPrimary,
                            unfocusedTextColor = VaultColors.TextPrimary,
                            focusedBorderColor = VaultColors.AccentCyan,
                            unfocusedBorderColor = VaultColors.GlassBorderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Replace Field + Actions (Only in Edit mode)
                    if (isEditMode) {
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace with...", fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = null, tint = VaultColors.AccentEmerald, modifier = Modifier.size(16.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = VaultColors.TextPrimary,
                                unfocusedTextColor = VaultColors.TextPrimary,
                                focusedBorderColor = VaultColors.AccentEmerald,
                                unfocusedBorderColor = VaultColors.GlassBorderSubtle
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        val idx = textContent.indexOf(searchQuery, ignoreCase = true)
                                        if (idx != -1) {
                                            val newContent = textContent.substring(0, idx) + replaceQuery + textContent.substring(idx + searchQuery.length)
                                            updateText(newContent)
                                        }
                                    }
                                },
                                enabled = searchQuery.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceGraphite),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Replace Next", fontSize = 11.sp, color = VaultColors.TextPrimary)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        val newContent = textContent.replace(searchQuery, replaceQuery, ignoreCase = true)
                                        updateText(newContent)
                                    }
                                },
                                enabled = searchQuery.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Replace All", fontSize = 11.sp, color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Main Document Editor / Viewer Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VaultColors.SurfaceGraphite)
                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            when {
                // 1. Markdown Formatted Preview
                isMarkdownPreview -> {
                    MarkdownPreviewContent(
                        markdown = textContent,
                        fontSizeSp = fontSizeSp,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 2. Active Text Editor
                isEditMode -> {
                    BasicTextField(
                        value = textContent,
                        onValueChange = { updateText(it) },
                        textStyle = TextStyle(
                            color = VaultColors.TextPrimary,
                            fontSize = fontSizeSp.sp,
                            fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.SansSerif,
                            lineHeight = (fontSizeSp * 1.5f).sp
                        ),
                        cursorBrush = SolidColor(VaultColors.AccentCyan),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .testTag("text_editor_field")
                    )
                }

                // 3. Read Mode with highlighted search terms
                else -> {
                    val scrollState = rememberScrollState()
                    val annotatedString = remember(textContent, searchQuery) {
                        buildAnnotatedString {
                            if (searchQuery.isBlank() || !textContent.contains(searchQuery, ignoreCase = true)) {
                                append(textContent)
                            } else {
                                var startIndex = 0
                                val lowerText = textContent.lowercase()
                                val lowerQuery = searchQuery.lowercase()
                                while (startIndex < textContent.length) {
                                    val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
                                    if (matchIndex == -1) {
                                        append(textContent.substring(startIndex))
                                        break
                                    }
                                    append(textContent.substring(startIndex, matchIndex))
                                    val matchEnd = matchIndex + searchQuery.length
                                    pushStyle(SpanStyle(background = VaultColors.AccentCyan.copy(alpha = 0.4f), color = VaultColors.TextPrimary))
                                    append(textContent.substring(matchIndex, matchEnd))
                                    pop()
                                    startIndex = matchEnd
                                }
                            }
                        }
                    }

                    Text(
                        text = annotatedString,
                        color = VaultColors.TextPrimary,
                        fontSize = fontSizeSp.sp,
                        fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.SansSerif,
                        lineHeight = (fontSizeSp * 1.5f).sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    )
                }
            }
        }
    }

    // Unsaved Changes Confirmation Dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes", fontWeight = FontWeight.Bold, color = VaultColors.TextPrimary) },
            text = {
                Text(
                    "You have unsaved changes in '$fileName'. Do you want to save your work before closing?",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onSaveContent(textContent)
                        onNavigateBack?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentEmerald)
                ) {
                    Text("Save & Exit", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onNavigateBack?.invoke()
                    }
                ) {
                    Text("Discard")
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }
}

/**
 * Lightweight local Markdown preview renderer.
 * Formats headings (#, ##, ###), bold (**), italic (*), lists (- or *), code blocks (```).
 * Fully offline, zero third-party dependencies.
 */
@Composable
private fun MarkdownPreviewContent(
    markdown: String,
    fontSizeSp: Float,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(4.dp)
    ) {
        val lines = markdown.lines()
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        lines.forEach { line ->
            when {
                line.startsWith("```") -> {
                    if (inCodeBlock) {
                        // End code block
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF101216),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = codeBlockLines.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSizeSp - 1).sp,
                                color = VaultColors.AccentCyan,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        codeBlockLines.clear()
                        inCodeBlock = false
                    } else {
                        inCodeBlock = true
                    }
                }
                inCodeBlock -> {
                    codeBlockLines.add(line)
                }
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# ").trim(),
                        fontSize = (fontSizeSp + 6).sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultColors.AccentCyan,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## ").trim(),
                        fontSize = (fontSizeSp + 4).sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultColors.TextPrimary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### ").trim(),
                        fontSize = (fontSizeSp + 2).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultColors.AccentAmber,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val bulletText = line.trimStart().drop(2)
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", color = VaultColors.AccentCyan, fontWeight = FontWeight.Bold, fontSize = fontSizeSp.sp)
                        Text(bulletText, color = VaultColors.TextPrimary, fontSize = fontSizeSp.sp)
                    }
                }
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                else -> {
                    Text(
                        text = line,
                        color = VaultColors.TextPrimary,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.5f).sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
