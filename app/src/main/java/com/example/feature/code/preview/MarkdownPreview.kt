package com.example.feature.code.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.VaultColors

/**
 * Pure presentation-only native Markdown preview component.
 */
@Composable
fun MarkdownPreview(
    markdownContent: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val lines = markdownContent.lines()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        var inCodeBlock = false
        val codeBlockBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            // Handle fenced code block
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Close code block
                    RenderCodeBlock(code = codeBlockBuffer.toString())
                    codeBlockBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockBuffer.append(line).append("\n")
                continue
            }

            when {
                // Horizontal divider
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = VaultColors.GlassBorderSubtle, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Headings
                trimmed.startsWith("# ") -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = trimmed.removePrefix("# "),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = VaultColors.GlassBorderSubtle, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                trimmed.startsWith("## ") -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = trimmed.removePrefix("## "),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                trimmed.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = trimmed.removePrefix("### "),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultColors.AccentCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                trimmed.startsWith("#### ") -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = trimmed.removePrefix("#### "),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Blockquote
                trimmed.startsWith("> ") -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(22.dp)
                                .background(VaultColors.AccentCyan, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = parseInlineFormatting(trimmed.removePrefix("> ")),
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = VaultColors.TextSecondary
                        )
                    }
                }

                // Unordered list
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .background(VaultColors.AccentCyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = parseInlineFormatting(trimmed.substring(2)),
                            fontSize = 14.sp,
                            color = VaultColors.TextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Ordered list
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val prefix = trimmed.substringBefore(".") + "."
                    val content = trimmed.substringAfter(". ")
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = prefix,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultColors.AccentCyan,
                            modifier = Modifier.width(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = parseInlineFormatting(content),
                            fontSize = 14.sp,
                            color = VaultColors.TextPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Empty line
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Regular Paragraph
                else -> {
                    Text(
                        text = parseInlineFormatting(line),
                        fontSize = 14.sp,
                        color = VaultColors.TextPrimary,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Flush remaining unclosed code block if any
        if (inCodeBlock && codeBlockBuffer.isNotEmpty()) {
            RenderCodeBlock(code = codeBlockBuffer.toString())
        }
    }
}

@Composable
private fun RenderCodeBlock(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0D1117))
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            text = code.trimEnd(),
            color = Color(0xFF58A6FF),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

/**
 * Parses bold, italics, inline code, and links.
 */
private fun parseInlineFormatting(text: String) = buildAnnotatedString {
    var i = 0
    val len = text.length

    while (i < len) {
        // Inline code: `code`
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                pushStyle(
                    SpanStyle(
                        background = Color(0xFF161B22),
                        color = Color(0xFF58A6FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                )
                append(" ${text.substring(i + 1, end)} ")
                pop()
                i = end + 1
                continue
            }
        }

        // Bold: **text**
        if (i + 1 < len && text.substring(i, i + 2) == "**") {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = VaultColors.TextPrimary))
                append(text.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        }

        // Italic: *text* or _text_
        if (text[i] == '*' || text[i] == '_') {
            val mark = text[i]
            val end = text.indexOf(mark, i + 1)
            if (end != -1 && end > i + 1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = VaultColors.TextSecondary))
                append(text.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }

        // Link: [title](url)
        if (text[i] == '[') {
            val titleEnd = text.indexOf(']', i + 1)
            if (titleEnd != -1 && titleEnd + 1 < len && text[titleEnd + 1] == '(') {
                val urlEnd = text.indexOf(')', titleEnd + 2)
                if (urlEnd != -1) {
                    val linkTitle = text.substring(i + 1, titleEnd)
                    pushStyle(SpanStyle(color = VaultColors.AccentCyan, fontWeight = FontWeight.SemiBold))
                    append(linkTitle)
                    pop()
                    i = urlEnd + 1
                    continue
                }
            }
        }

        append(text[i])
        i++
    }
}
