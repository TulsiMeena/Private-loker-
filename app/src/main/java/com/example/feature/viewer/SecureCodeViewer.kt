package com.example.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors

/**
 * Secure Source Code Viewer & Editor.
 *
 * Supports syntax highlighting, line numbers, word-wrap control, and atomic re-encryption.
 */
@Composable
fun SecureCodeViewer(
    initialCode: String,
    fileName: String,
    onSaveContent: (newCode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var codeContent by remember { mutableStateOf(initialCode) }
    var isEditMode by remember { mutableStateOf(false) }
    var isWordWrap by remember { mutableStateOf(false) }
    var fontSizeSp by remember { mutableFloatStateOf(13f) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val hasChanges = codeContent != initialCode
    val lines = remember(codeContent) { codeContent.lines() }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
    ) {
        // Code Toolbar
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Language extension badge & line count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = fileName.substringAfterLast('.', "code").uppercase(),
                    color = VaultColors.AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(VaultColors.SurfaceOverlay)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${lines.size} lines",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Font size toggle (- / +)
                IconButton(onClick = {
                    fontSizeSp = if (fontSizeSp >= 18f) 11f else fontSizeSp + 2f
                }) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = "Adjust font size",
                        tint = VaultColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Word wrap toggle
                IconButton(onClick = { isWordWrap = !isWordWrap }) {
                    Icon(
                        imageVector = Icons.Default.WrapText,
                        contentDescription = "Toggle word wrap",
                        tint = if (isWordWrap) VaultColors.AccentCyan else VaultColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search toggle
                IconButton(onClick = {
                    isSearching = !isSearching
                    if (!isSearching) searchQuery = ""
                }) {
                    Icon(
                        imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search code",
                        tint = if (isSearching) VaultColors.AccentCyan else VaultColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Edit / Read mode
                IconButton(
                    onClick = { isEditMode = !isEditMode },
                    modifier = Modifier.testTag("code_edit_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                        contentDescription = "Toggle edit mode",
                        tint = if (isEditMode) VaultColors.AccentEmerald else VaultColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Save button
                if (hasChanges) {
                    Button(
                        onClick = { onSaveContent(codeContent) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultColors.AccentEmerald,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("save_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Search Bar
        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Find in code...", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Code Editor / Viewer View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF090B0E))
                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                // Line numbers gutter
                Column(
                    modifier = Modifier
                        .background(Color(0xFF0E1116))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    for (i in 1..lines.size) {
                        Text(
                            text = i.toString(),
                            color = VaultColors.TextTertiary,
                            fontSize = fontSizeSp.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = (fontSizeSp + 8f).sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Code Content
                val codeModifier = if (isWordWrap) {
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                } else {
                    Modifier
                        .horizontalScroll(horizontalScrollState)
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                }

                Box(modifier = codeModifier) {
                    if (isEditMode) {
                        BasicTextField(
                            value = codeContent,
                            onValueChange = { codeContent = it },
                            textStyle = TextStyle(
                                color = VaultColors.TextPrimary,
                                fontSize = fontSizeSp.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = (fontSizeSp + 8f).sp
                            ),
                            cursorBrush = SolidColor(VaultColors.AccentCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("code_editor_field")
                        )
                    } else {
                        val syntaxHighlighted = remember(codeContent, searchQuery) {
                            highlightCodeSyntax(codeContent, searchQuery)
                        }

                        Text(
                            text = syntaxHighlighted,
                            fontSize = fontSizeSp.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = (fontSizeSp + 8f).sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lightweight fast syntax highlighter with keywords, strings, comments, numbers.
 */
private fun highlightCodeSyntax(code: String, query: String) = buildAnnotatedString {
    val keywords = setOf(
        "fun", "val", "var", "class", "interface", "object", "return", "if", "else",
        "for", "while", "import", "package", "private", "public", "protected", "override",
        "suspend", "data", "sealed", "enum", "def", "async", "await", "const", "let", "export",
        "function", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "NULL", "TRUE", "FALSE"
    )

    val lines = code.lines()
    lines.forEachIndexed { index, line ->
        var remaining = line
        val trimmed = line.trimStart()

        // Line comment
        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
            pushStyle(SpanStyle(color = Color(0xFF6B7280))) // Comment grey
            append(line)
            pop()
        } else {
            // Tokenize line simply
            val tokens = line.split(Regex("(?<=[\\s(),.:;=+\\-*/<>])|(?=[\\s(),.:;=+\\-*/<>])"))
            tokens.forEach { token ->
                when {
                    token in keywords -> {
                        pushStyle(SpanStyle(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                        append(token)
                        pop()
                    }
                    token.startsWith("\"") && token.endsWith("\"") -> {
                        pushStyle(SpanStyle(color = Color(0xFF34D399))) // String green
                        append(token)
                        pop()
                    }
                    token.toIntOrNull() != null -> {
                        pushStyle(SpanStyle(color = Color(0xFFF59E0B))) // Number amber
                        append(token)
                        pop()
                    }
                    token.startsWith("@") -> {
                        pushStyle(SpanStyle(color = Color(0xFFA78BFA))) // Annotation purple
                        append(token)
                        pop()
                    }
                    query.isNotBlank() && token.contains(query, ignoreCase = true) -> {
                        pushStyle(SpanStyle(background = Color(0x6638BDF8), color = Color.White))
                        append(token)
                        pop()
                    }
                    else -> {
                        pushStyle(SpanStyle(color = VaultColors.TextPrimary))
                        append(token)
                        pop()
                    }
                }
            }
        }
        if (index < lines.size - 1) append("\n")
    }
}
