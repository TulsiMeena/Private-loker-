package com.example.feature.code.formatters

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class JsonValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val line: Int? = null,
    val column: Int? = null
)

object JsonFormatter {

    /**
     * Validates JSON string syntax and reports error location if invalid.
     */
    fun validate(rawJson: String): JsonValidationResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return JsonValidationResult(isValid = false, errorMessage = "JSON content is empty")
        }

        return try {
            val tokener = JSONTokener(trimmed)
            val firstChar = tokener.nextClean()
            tokener.back()

            if (firstChar == '{') {
                JSONObject(tokener)
            } else if (firstChar == '[') {
                JSONArray(tokener)
            } else {
                return JsonValidationResult(
                    isValid = false,
                    errorMessage = "Root element must be an Object '{' or Array '['"
                )
            }
            JsonValidationResult(isValid = true)
        } catch (e: Exception) {
            val msg = e.message ?: "Invalid JSON format"
            val lineRegex = Regex("at character (\\d+)")
            val match = lineRegex.find(msg)
            val charPos = match?.groupValues?.getOrNull(1)?.toIntOrNull()

            var lineNum: Int? = null
            var colNum: Int? = null
            if (charPos != null && charPos <= trimmed.length) {
                val prefix = trimmed.substring(0, charPos)
                val lines = prefix.lines()
                lineNum = lines.size
                colNum = lines.last().length + 1
            }

            JsonValidationResult(
                isValid = false,
                errorMessage = msg,
                line = lineNum,
                column = colNum
            )
        }
    }

    /**
     * Formats JSON with specified indentation spaces.
     */
    fun prettyPrint(rawJson: String, indentSpaces: Int = 2): Result<String> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return Result.success("")

        return try {
            val tokener = JSONTokener(trimmed)
            val firstChar = tokener.nextClean()
            tokener.back()

            val formatted = if (firstChar == '{') {
                JSONObject(tokener).toString(indentSpaces)
            } else if (firstChar == '[') {
                JSONArray(tokener).toString(indentSpaces)
            } else {
                return Result.failure(IllegalArgumentException("Root must be object or array"))
            }
            Result.success(formatted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Minifies JSON by stripping extraneous whitespaces.
     */
    fun minify(rawJson: String): Result<String> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return Result.success("")

        return try {
            val tokener = JSONTokener(trimmed)
            val firstChar = tokener.nextClean()
            tokener.back()

            val minified = if (firstChar == '{') {
                JSONObject(tokener).toString()
            } else if (firstChar == '[') {
                JSONArray(tokener).toString()
            } else {
                return Result.failure(IllegalArgumentException("Root must be object or array"))
            }
            Result.success(minified)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
