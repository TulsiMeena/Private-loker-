package com.example.core.storage

import java.util.Locale

/**
 * Vault categories supporting full file-type extensibility without artificial restrictions.
 */
enum class VaultCategory(
    val title: String,
    val extensionHint: String,
    val defaultMime: String
) {
    DOCUMENT("Documents", "pdf, doc, docx, txt, xls, xlsx, ppt, pptx, odt, rtf, csv", "application/pdf"),
    IMAGE("Images", "png, jpg, jpeg, webp, gif, svg, bmp, heic", "image/*"),
    VIDEO("Videos", "mp4, mkv, mov, avi, webm, 3gp, flv, wmv", "video/*"),
    AUDIO("Audio", "mp3, wav, m4a, ogg, flac, aac, opus", "audio/*"),
    ZIP("Archives", "zip, tar, gz, 7z, rar, bz2, xz", "application/zip"),
    CODE("Source Code", "kt, java, py, js, ts, html, css, rs, c, cpp, json, xml, sh, sql, md, cs, php, swift, yaml, toml", "text/plain"),
    TEXT("Secure Text", "txt, md, log, note, csv, ini, conf, env", "text/plain"),
    OTHER("All Files", "Any file extension", "*/*");

    companion object {
        fun fromFileNameAndMime(fileName: String, mimeType: String): VaultCategory {
            val lowerMime = mimeType.lowercase(Locale.ROOT)
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

            if (lowerMime.startsWith("image/") || ext in listOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp", "heic", "tiff")) {
                return IMAGE
            }
            if (lowerMime.startsWith("video/") || ext in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v")) {
                return VIDEO
            }
            if (lowerMime.startsWith("audio/") || ext in listOf("mp3", "wav", "m4a", "ogg", "flac", "aac", "opus", "wma")) {
                return AUDIO
            }
            if (lowerMime == "application/zip" || lowerMime.contains("archive") || lowerMime.contains("compressed") ||
                ext in listOf("zip", "tar", "gz", "7z", "rar", "bz2", "xz", "tgz")
            ) {
                return ZIP
            }
            // Code formats
            if (ext in listOf(
                    "kt", "kts", "java", "py", "pyw", "js", "mjs", "cjs", "ts", "tsx",
                    "html", "htm", "css", "scss", "less", "rs", "c", "cpp", "h", "hpp",
                    "cc", "cxx", "json", "xml", "sh", "bash", "zsh", "sql", "yaml",
                    "yml", "gradle", "cs", "php", "swift", "toml", "lua", "go", "rb",
                    "dart", "vue", "svelte", "jsx", "proto", "graphql"
                )
            ) {
                return CODE
            }
            // Document formats (excluding plain text/code)
            if (ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "rtf", "epub")) {
                return DOCUMENT
            }
            // Text formats (including CSV, Markdown, Logs, Configs)
            if (ext in listOf("txt", "md", "markdown", "log", "note", "csv", "tsv", "ini", "properties", "env", "conf") ||
                lowerMime.startsWith("text/")
            ) {
                return TEXT
            }

            return OTHER
        }
    }
}
