package com.example.feature.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.core.designsystem.VaultColors

/**
 * High-performance presentation-only syntax highlighter.
 *
 * Security Invariant:
 * - Presentation ONLY: Highlighting is applied strictly to Compose [AnnotatedString] layouts.
 *   It never mutates, transforms, or writes into the raw source content or encrypted file.
 */
object SyntaxHighlighter {

    // Palette tailored for Deep Graphite / Dark Canvas
    val ColorKeyword = Color(0xFF38BDF8) // Sky Cyan
    val ColorString = Color(0xFF4ADE80) // Emerald Green
    val ColorComment = Color(0xFF64748B) // Slate Muted
    val ColorNumber = Color(0xFFFB923C) // Amber / Orange
    val ColorType = Color(0xFFA78BFA) // Violet / Purple
    val ColorTag = Color(0xFFF43F5E) // Rose / Crimson
    val ColorProperty = Color(0xFF38BDF8) // Bright Cyan
    val ColorSearchHighlight = Color(0x770284C7) // Semi-transparent Cyan
    val ColorSearchHighlightText = Color(0xFFFFFFFF)

    private val commonKeywords = setOf(
        "if", "else", "for", "while", "do", "return", "break", "continue", "switch", "case", "default"
    )

    private val kotlinKeywords = commonKeywords + setOf(
        "fun", "val", "var", "class", "interface", "object", "package", "import", "public", "private",
        "protected", "internal", "override", "open", "abstract", "final", "data", "sealed", "enum",
        "companion", "constructor", "init", "this", "super", "is", "as", "in", "out", "by", "suspend",
        "inline", "noinline", "crossinline", "reified", "tailrec", "operator", "infix", "typealias",
        "try", "catch", "finally", "throw", "null", "true", "false"
    )

    private val javaKeywords = commonKeywords + setOf(
        "public", "private", "protected", "static", "final", "void", "class", "interface", "extends",
        "implements", "new", "this", "super", "package", "import", "throws", "throw", "try", "catch",
        "finally", "boolean", "int", "long", "float", "double", "char", "byte", "short", "null", "true", "false",
        "synchronized", "volatile", "transient", "native", "strictfp", "enum", "instanceof"
    )

    private val pythonKeywords = commonKeywords + setOf(
        "def", "class", "import", "from", "as", "return", "yield", "lambda", "try", "except",
        "finally", "raise", "with", "pass", "assert", "global", "nonlocal", "True", "False", "None",
        "and", "or", "not", "is", "in", "elif", "async", "await"
    )

    private val jsKeywords = commonKeywords + setOf(
        "function", "var", "let", "const", "class", "extends", "super", "this", "import", "export",
        "from", "default", "try", "catch", "finally", "throw", "new", "delete", "typeof", "instanceof",
        "void", "yield", "async", "await", "null", "true", "false", "undefined", "NaN"
    )

    private val tsKeywords = jsKeywords + setOf(
        "type", "interface", "enum", "implements", "namespace", "declare", "abstract", "as", "keyof",
        "readonly", "private", "public", "protected", "never", "unknown", "any", "string", "number", "boolean"
    )

    private val sqlKeywords = setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "JOIN",
        "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "ON", "GROUP", "BY", "ORDER", "ASC", "DESC",
        "HAVING", "LIMIT", "OFFSET", "CREATE", "TABLE", "DATABASE", "INDEX", "DROP", "ALTER", "ADD",
        "COLUMN", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "NOT", "NULL", "UNIQUE", "CHECK", "DEFAULT",
        "AND", "OR", "IN", "BETWEEN", "LIKE", "IS", "DISTINCT", "UNION", "ALL", "EXISTS", "CASE", "WHEN", "THEN", "END"
    )

    private val cFamilyKeywords = commonKeywords + setOf(
        "int", "char", "float", "double", "void", "long", "short", "signed", "unsigned", "struct",
        "union", "typedef", "enum", "sizeof", "static", "extern", "const", "volatile", "register",
        "goto", "auto", "class", "public", "private", "protected", "virtual", "template", "typename",
        "namespace", "using", "new", "delete", "this", "friend", "inline", "explicit", "operator",
        "try", "catch", "throw", "bool", "true", "false", "nullptr", "include", "define", "ifdef", "ifndef", "endif"
    )

    private val phpKeywords = commonKeywords + setOf(
        "function", "echo", "print", "class", "interface", "trait", "extends", "implements", "new",
        "public", "private", "protected", "static", "final", "abstract", "try", "catch", "finally",
        "throw", "namespace", "use", "global", "var", "const", "null", "true", "false", "array"
    )

    private val swiftKeywords = commonKeywords + setOf(
        "func", "var", "let", "class", "struct", "enum", "protocol", "extension", "init", "deinit",
        "import", "public", "private", "fileprivate", "internal", "open", "override", "static", "self",
        "super", "mutating", "guard", "defer", "try", "catch", "throw", "throws", "nil", "true", "false", "async", "await"
    )

    /**
     * Highlights code syntax for presentation in the editor or viewer.
     */
    fun highlight(
        code: String,
        language: CodeLanguage,
        searchQuery: String = "",
        matchCase: Boolean = false
    ): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")

        // Large file safety check (>100,000 chars): return plain annotated string with search highlights
        if (code.length > 100_000 || language == CodeLanguage.PLAIN_TEXT || language == CodeLanguage.CSV) {
            return highlightPlainWithSearch(code, searchQuery, matchCase)
        }

        return buildAnnotatedString {
            when (language) {
                CodeLanguage.KOTLIN -> formatCodeWithKeywords(code, kotlinKeywords, "//", searchQuery, matchCase)
                CodeLanguage.JAVA -> formatCodeWithKeywords(code, javaKeywords, "//", searchQuery, matchCase)
                CodeLanguage.PYTHON -> formatCodeWithKeywords(code, pythonKeywords, "#", searchQuery, matchCase)
                CodeLanguage.JAVASCRIPT -> formatCodeWithKeywords(code, jsKeywords, "//", searchQuery, matchCase)
                CodeLanguage.TYPESCRIPT -> formatCodeWithKeywords(code, tsKeywords, "//", searchQuery, matchCase)
                CodeLanguage.SQL -> formatSql(code, searchQuery, matchCase)
                CodeLanguage.HTML, CodeLanguage.XML -> formatMarkup(code, searchQuery, matchCase)
                CodeLanguage.JSON -> formatJson(code, searchQuery, matchCase)
                CodeLanguage.CSS -> formatCss(code, searchQuery, matchCase)
                CodeLanguage.MARKDOWN -> formatMarkdown(code, searchQuery, matchCase)
                CodeLanguage.C, CodeLanguage.CPP, CodeLanguage.CSHARP -> formatCodeWithKeywords(code, cFamilyKeywords, "//", searchQuery, matchCase)
                CodeLanguage.PHP -> formatCodeWithKeywords(code, phpKeywords, "//", searchQuery, matchCase)
                CodeLanguage.SWIFT -> formatCodeWithKeywords(code, swiftKeywords, "//", searchQuery, matchCase)
                CodeLanguage.YAML, CodeLanguage.TOML, CodeLanguage.INI -> formatConfig(code, searchQuery, matchCase)
                else -> formatPlainWithSearch(code, searchQuery, matchCase)
            }
        }
    }

    private fun AnnotatedString.Builder.formatCodeWithKeywords(
        code: String,
        keywords: Set<String>,
        commentPrefix: String,
        searchQuery: String,
        matchCase: Boolean
    ) {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith(commentPrefix)) {
                pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                append(line)
                pop()
            } else if (trimmed.startsWith("/*")) {
                pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                append(line)
                pop()
            } else {
                // Tokenize words, symbols, and strings
                tokenizeAndStyleCodeLine(line, keywords, commentPrefix)
            }

            if (lineIdx < lines.size - 1) {
                append("\n")
            }
        }

        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun AnnotatedString.Builder.tokenizeAndStyleCodeLine(
        line: String,
        keywords: Set<String>,
        commentPrefix: String
    ) {
        var i = 0
        val len = line.length
        var inString = false
        var stringChar = ' '
        var stringStart = -1

        while (i < len) {
            val c = line[i]

            // Handle string literal
            if (inString) {
                if (c == stringChar && (i == 0 || line[i - 1] != '\\')) {
                    inString = false
                    val str = line.substring(stringStart, i + 1)
                    pushStyle(SpanStyle(color = ColorString))
                    append(str)
                    pop()
                }
                i++
                continue
            } else if (c == '"' || c == '\'') {
                inString = true
                stringChar = c
                stringStart = i
                i++
                continue
            }

            // Inline comment check
            if (i + commentPrefix.length <= len && line.substring(i, i + commentPrefix.length) == commentPrefix) {
                pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                append(line.substring(i))
                pop()
                return
            }

            // Word / Identifier / Number
            if (c.isLetter() || c == '_' || c == '@' || c == '$') {
                val start = i
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_' || line[i] == '$')) {
                    i++
                }
                val word = line.substring(start, i)
                when {
                    word in keywords -> {
                        pushStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold))
                        append(word)
                        pop()
                    }
                    word.startsWith("@") -> {
                        pushStyle(SpanStyle(color = ColorType, fontWeight = FontWeight.SemiBold))
                        append(word)
                        pop()
                    }
                    word.firstOrNull()?.isUpperCase() == true -> {
                        pushStyle(SpanStyle(color = ColorType))
                        append(word)
                        pop()
                    }
                    else -> {
                        pushStyle(SpanStyle(color = VaultColors.TextPrimary))
                        append(word)
                        pop()
                    }
                }
                continue
            } else if (c.isDigit()) {
                val start = i
                while (i < len && (line[i].isDigit() || line[i] == '.' || line[i] in "xXbBfFlL")) {
                    i++
                }
                pushStyle(SpanStyle(color = ColorNumber))
                append(line.substring(start, i))
                pop()
                continue
            } else {
                pushStyle(SpanStyle(color = VaultColors.TextSecondary))
                append(c.toString())
                pop()
                i++
            }
        }

        // Unclosed string fallback
        if (inString && stringStart >= 0 && stringStart < len) {
            pushStyle(SpanStyle(color = ColorString))
            append(line.substring(stringStart))
            pop()
        }
    }

    private fun AnnotatedString.Builder.formatSql(code: String, searchQuery: String, matchCase: Boolean) {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("--") || trimmed.startsWith("/*")) {
                pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                append(line)
                pop()
            } else {
                val tokens = line.split(Regex("(?<=[\\s,();=+\\-*/<>])|(?=[\\s,();=+\\-*/<>])"))
                tokens.forEach { token ->
                    val upper = token.uppercase()
                    when {
                        upper in sqlKeywords -> {
                            pushStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold))
                            append(token)
                            pop()
                        }
                        token.startsWith("'") && token.endsWith("'") -> {
                            pushStyle(SpanStyle(color = ColorString))
                            append(token)
                            pop()
                        }
                        token.toIntOrNull() != null -> {
                            pushStyle(SpanStyle(color = ColorNumber))
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
            if (lineIdx < lines.size - 1) append("\n")
        }
        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun AnnotatedString.Builder.formatMarkup(code: String, searchQuery: String, matchCase: Boolean) {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("<!--")) {
                pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                append(line)
                pop()
            } else {
                var i = 0
                val len = line.length
                while (i < len) {
                    val c = line[i]
                    if (c == '<') {
                        val end = line.indexOf('>', i)
                        if (end != -1) {
                            val tagContent = line.substring(i, end + 1)
                            pushStyle(SpanStyle(color = ColorTag, fontWeight = FontWeight.SemiBold))
                            append(tagContent)
                            pop()
                            i = end + 1
                            continue
                        }
                    }
                    pushStyle(SpanStyle(color = VaultColors.TextPrimary))
                    append(c.toString())
                    pop()
                    i++
                }
            }
            if (lineIdx < lines.size - 1) append("\n")
        }
        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun AnnotatedString.Builder.formatJson(code: String, searchQuery: String, matchCase: Boolean) {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            var i = 0
            val len = line.length
            while (i < len) {
                val c = line[i]
                if (c == '"') {
                    val end = line.indexOf('"', i + 1)
                    if (end != -1) {
                        val str = line.substring(i, end + 1)
                        val afterColon = line.substring(end + 1).trimStart().startsWith(":")
                        if (afterColon) {
                            // Key
                            pushStyle(SpanStyle(color = ColorProperty, fontWeight = FontWeight.SemiBold))
                            append(str)
                            pop()
                        } else {
                            // Value string
                            pushStyle(SpanStyle(color = ColorString))
                            append(str)
                            pop()
                        }
                        i = end + 1
                        continue
                    }
                }
                if (c.isDigit() || c == '-' || c == '.') {
                    val start = i
                    while (i < len && (line[i].isDigit() || line[i] == '.' || line[i] == '-' || line[i] == 'e' || line[i] == 'E')) {
                        i++
                    }
                    pushStyle(SpanStyle(color = ColorNumber))
                    append(line.substring(start, i))
                    pop()
                    continue
                }
                if (line.startsWith("true", i) || line.startsWith("false", i) || line.startsWith("null", i)) {
                    val word = if (line.startsWith("true", i)) "true" else if (line.startsWith("false", i)) "false" else "null"
                    pushStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold))
                    append(word)
                    pop()
                    i += word.length
                    continue
                }
                pushStyle(SpanStyle(color = VaultColors.TextSecondary))
                append(c.toString())
                pop()
                i++
            }
            if (lineIdx < lines.size - 1) append("\n")
        }
        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun AnnotatedString.Builder.formatCss(code: String, searchQuery: String, matchCase: Boolean) {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("/*")) {
                pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                append(line)
                pop()
            } else if (trimmed.contains(":")) {
                val parts = line.split(":", limit = 2)
                pushStyle(SpanStyle(color = ColorProperty))
                append(parts[0])
                pop()
                pushStyle(SpanStyle(color = VaultColors.TextSecondary))
                append(":")
                pop()
                if (parts.size > 1) {
                    pushStyle(SpanStyle(color = ColorString))
                    append(parts[1])
                    pop()
                }
            } else {
                pushStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold))
                append(line)
                pop()
            }
            if (lineIdx < lines.size - 1) append("\n")
        }
        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun AnnotatedString.Builder.formatConfig(code: String, searchQuery: String, matchCase: Boolean) {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#") || trimmed.startsWith(";") || trimmed.startsWith("//")) {
                pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                append(line)
                pop()
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                pushStyle(SpanStyle(color = ColorType, fontWeight = FontWeight.Bold))
                append(line)
                pop()
            } else if (line.contains("=") || line.contains(":")) {
                val delim = if (line.contains("=")) "=" else ":"
                val parts = line.split(delim, limit = 2)
                pushStyle(SpanStyle(color = ColorProperty, fontWeight = FontWeight.SemiBold))
                append(parts[0])
                pop()
                pushStyle(SpanStyle(color = VaultColors.TextSecondary))
                append(delim)
                pop()
                if (parts.size > 1) {
                    pushStyle(SpanStyle(color = ColorString))
                    append(parts[1])
                    pop()
                }
            } else {
                pushStyle(SpanStyle(color = VaultColors.TextPrimary))
                append(line)
                pop()
            }
            if (lineIdx < lines.size - 1) append("\n")
        }
        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun AnnotatedString.Builder.formatMarkdown(code: String, searchQuery: String, matchCase: Boolean) {
        val lines = code.lines()
        lines.forEachIndexed { lineIdx, line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> {
                    pushStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold))
                    append(line)
                    pop()
                }
                trimmed.startsWith("```") -> {
                    pushStyle(SpanStyle(color = ColorTag, fontWeight = FontWeight.Bold))
                    append(line)
                    pop()
                }
                trimmed.startsWith(">") -> {
                    pushStyle(SpanStyle(color = ColorComment, fontStyle = FontStyle.Italic))
                    append(line)
                    pop()
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    pushStyle(SpanStyle(color = ColorProperty, fontWeight = FontWeight.Bold))
                    append(line.take(2))
                    pop()
                    pushStyle(SpanStyle(color = VaultColors.TextPrimary))
                    append(line.drop(2))
                    pop()
                }
                else -> {
                    pushStyle(SpanStyle(color = VaultColors.TextPrimary))
                    append(line)
                    pop()
                }
            }
            if (lineIdx < lines.size - 1) append("\n")
        }
        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun AnnotatedString.Builder.formatPlainWithSearch(code: String, searchQuery: String, matchCase: Boolean) {
        pushStyle(SpanStyle(color = VaultColors.TextPrimary))
        append(code)
        pop()
        applySearchHighlights(code, searchQuery, matchCase)
    }

    private fun highlightPlainWithSearch(code: String, searchQuery: String, matchCase: Boolean): AnnotatedString {
        return buildAnnotatedString {
            pushStyle(SpanStyle(color = VaultColors.TextPrimary))
            append(code)
            pop()
            applySearchHighlights(code, searchQuery, matchCase)
        }
    }

    private fun AnnotatedString.Builder.applySearchHighlights(
        code: String,
        searchQuery: String,
        matchCase: Boolean
    ) {
        if (searchQuery.isBlank()) return
        var startIndex = 0
        while (startIndex < code.length) {
            val found = code.indexOf(searchQuery, startIndex, ignoreCase = !matchCase)
            if (found == -1) break
            addStyle(
                style = SpanStyle(background = ColorSearchHighlight, color = ColorSearchHighlightText),
                start = found,
                end = found + searchQuery.length
            )
            startIndex = found + searchQuery.length
        }
    }
}
