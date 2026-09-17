package com.example.feature.code.editor

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

enum class EditorFontSize(val label: String, val size: TextUnit, val lineHeight: TextUnit) {
    SMALL("Small", 11.sp, 17.sp),
    MEDIUM("Medium", 13.sp, 20.sp),
    LARGE("Large", 16.sp, 24.sp),
    EXTRA_LARGE("Extra Large", 19.sp, 28.sp);

    companion object {
        fun fromLabel(label: String): EditorFontSize =
            values().find { it.label.equals(label, ignoreCase = true) } ?: MEDIUM
    }
}

enum class IndentOption(val label: String, val text: String, val spaceCount: Int) {
    TWO_SPACES("2 Spaces", "  ", 2),
    FOUR_SPACES("4 Spaces", "    ", 4),
    TAB("Tab", "\t", 4);

    companion object {
        fun fromLabel(label: String): IndentOption =
            values().find { it.label.equals(label, ignoreCase = true) } ?: FOUR_SPACES
    }
}

enum class SaveStatus(val label: String) {
    SAVED("SAVED"),
    UNSAVED("UNSAVED"),
    SAVING("SAVING…"),
    READ_ONLY("READ ONLY"),
    ERROR("SAVE FAILED")
}

data class SearchResultMatch(
    val startIndex: Int,
    val endIndex: Int,
    val lineIndex: Int,
    val snippet: String
)

data class DocumentStats(
    val lines: Int = 1,
    val words: Int = 0,
    val characters: Int = 0,
    val selectedChars: Int = 0,
    val selectedLines: Int = 0
)

data class RegexValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val pattern: Pattern? = null
)

/**
 * Validates and safely executes Regular Expressions avoiding catastrophic backtracking.
 */
object SafeRegexValidator {
    private const val MAX_REGEX_LENGTH = 120

    fun validate(patternStr: String, matchCase: Boolean): RegexValidationResult {
        if (patternStr.isBlank()) {
            return RegexValidationResult(isValid = false, errorMessage = "Empty pattern")
        }
        if (patternStr.length > MAX_REGEX_LENGTH) {
            return RegexValidationResult(
                isValid = false,
                errorMessage = "Expression exceeds maximum safe length ($MAX_REGEX_LENGTH chars)"
            )
        }

        // Detect known dangerous catastrophic patterns like (a+)+ or (x*)*
        if (patternStr.contains("++)") || patternStr.contains("**)") || patternStr.contains("+)*") || patternStr.contains("*)+")) {
            return RegexValidationResult(
                isValid = false,
                errorMessage = "Potential catastrophic backtracking pattern detected"
            )
        }

        return try {
            val flags = if (matchCase) 0 else Pattern.CASE_INSENSITIVE
            val compiled = Pattern.compile(patternStr, flags)
            RegexValidationResult(isValid = true, pattern = compiled)
        } catch (e: PatternSyntaxException) {
            RegexValidationResult(isValid = false, errorMessage = e.description)
        } catch (e: Exception) {
            RegexValidationResult(isValid = false, errorMessage = e.message ?: "Invalid regex")
        }
    }
}
