package com.example.feature.code

import java.util.Locale

/**
 * Supported programming, markup, and structured text languages for Private Code & Text Studio.
 */
enum class CodeLanguage(
    val id: String,
    val displayName: String,
    val extensions: List<String>,
    val defaultExtension: String,
    val commentPrefix: String?,
    val defaultWordWrap: Boolean,
    val isCode: Boolean,
    val supportsPreview: Boolean = false
) {
    KOTLIN(
        id = "kotlin",
        displayName = "Kotlin",
        extensions = listOf("kt", "kts"),
        defaultExtension = "kt",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    JAVA(
        id = "java",
        displayName = "Java",
        extensions = listOf("java"),
        defaultExtension = "java",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    PYTHON(
        id = "python",
        displayName = "Python",
        extensions = listOf("py", "pyw"),
        defaultExtension = "py",
        commentPrefix = "#",
        defaultWordWrap = false,
        isCode = true
    ),
    JAVASCRIPT(
        id = "javascript",
        displayName = "JavaScript",
        extensions = listOf("js", "mjs", "cjs", "jsx"),
        defaultExtension = "js",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    TYPESCRIPT(
        id = "typescript",
        displayName = "TypeScript",
        extensions = listOf("ts", "tsx"),
        defaultExtension = "ts",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    HTML(
        id = "html",
        displayName = "HTML",
        extensions = listOf("html", "htm"),
        defaultExtension = "html",
        commentPrefix = "<!--",
        defaultWordWrap = false,
        isCode = true
    ),
    CSS(
        id = "css",
        displayName = "CSS",
        extensions = listOf("css", "scss", "less"),
        defaultExtension = "css",
        commentPrefix = "/*",
        defaultWordWrap = false,
        isCode = true
    ),
    JSON(
        id = "json",
        displayName = "JSON",
        extensions = listOf("json"),
        defaultExtension = "json",
        commentPrefix = null,
        defaultWordWrap = false,
        isCode = true,
        supportsPreview = true
    ),
    XML(
        id = "xml",
        displayName = "XML",
        extensions = listOf("xml", "svg", "xaml"),
        defaultExtension = "xml",
        commentPrefix = "<!--",
        defaultWordWrap = false,
        isCode = true,
        supportsPreview = true
    ),
    SQL(
        id = "sql",
        displayName = "SQL",
        extensions = listOf("sql"),
        defaultExtension = "sql",
        commentPrefix = "--",
        defaultWordWrap = false,
        isCode = true
    ),
    MARKDOWN(
        id = "markdown",
        displayName = "Markdown",
        extensions = listOf("md", "markdown"),
        defaultExtension = "md",
        commentPrefix = null,
        defaultWordWrap = true,
        isCode = false,
        supportsPreview = true
    ),
    C(
        id = "c",
        displayName = "C",
        extensions = listOf("c", "h"),
        defaultExtension = "c",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    CPP(
        id = "cpp",
        displayName = "C++",
        extensions = listOf("cpp", "hpp", "cc", "cxx"),
        defaultExtension = "cpp",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    CSHARP(
        id = "csharp",
        displayName = "C#",
        extensions = listOf("cs"),
        defaultExtension = "cs",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    PHP(
        id = "php",
        displayName = "PHP",
        extensions = listOf("php"),
        defaultExtension = "php",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    SWIFT(
        id = "swift",
        displayName = "Swift",
        extensions = listOf("swift"),
        defaultExtension = "swift",
        commentPrefix = "//",
        defaultWordWrap = false,
        isCode = true
    ),
    YAML(
        id = "yaml",
        displayName = "YAML",
        extensions = listOf("yaml", "yml"),
        defaultExtension = "yaml",
        commentPrefix = "#",
        defaultWordWrap = false,
        isCode = true
    ),
    TOML(
        id = "toml",
        displayName = "TOML",
        extensions = listOf("toml"),
        defaultExtension = "toml",
        commentPrefix = "#",
        defaultWordWrap = false,
        isCode = true
    ),
    INI(
        id = "ini",
        displayName = "INI / Config",
        extensions = listOf("ini", "conf", "properties", "env"),
        defaultExtension = "ini",
        commentPrefix = "#",
        defaultWordWrap = false,
        isCode = true
    ),
    CSV(
        id = "csv",
        displayName = "CSV / Data",
        extensions = listOf("csv", "tsv"),
        defaultExtension = "csv",
        commentPrefix = null,
        defaultWordWrap = false,
        isCode = false,
        supportsPreview = true
    ),
    PLAIN_TEXT(
        id = "text",
        displayName = "Plain Text",
        extensions = listOf("txt", "log", "note"),
        defaultExtension = "txt",
        commentPrefix = null,
        defaultWordWrap = true,
        isCode = false
    );

    companion object {
        fun detect(fileName: String, explicitLanguage: String? = null): CodeLanguage {
            if (!explicitLanguage.isNullOrBlank()) {
                val matched = values().find {
                    it.id.equals(explicitLanguage, ignoreCase = true) ||
                            it.displayName.equals(explicitLanguage, ignoreCase = true)
                }
                if (matched != null) return matched
            }

            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return values().find { it.extensions.contains(ext) } ?: PLAIN_TEXT
        }

        fun getCreatableLanguages(): List<CodeLanguage> = listOf(
            PLAIN_TEXT,
            MARKDOWN,
            JSON,
            XML,
            CSV,
            KOTLIN,
            JAVA,
            PYTHON,
            JAVASCRIPT,
            TYPESCRIPT,
            HTML,
            CSS,
            SQL,
            C,
            CPP,
            YAML,
            CSHARP,
            PHP,
            SWIFT,
            TOML,
            INI
        )
    }
}
