package com.example.feature.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.core.database.VaultItemEntity
import com.example.core.storage.VaultCategory
import java.util.concurrent.TimeUnit

enum class SearchSortOption(val displayName: String) {
    RELEVANCE("Relevance"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LARGEST("Largest"),
    SMALLEST("Smallest"),
    TYPE("Type")
}

enum class DateFilterOption(val displayName: String) {
    ALL("All Time"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    CUSTOM("Custom Range")
}

enum class FileSizeFilterOption(val displayName: String) {
    ALL("Any Size"),
    SMALL("< 1 MB"),
    MEDIUM("1 MB – 25 MB"),
    LARGE("> 25 MB"),
    CUSTOM("Custom Size")
}

enum class SearchCategoryFilter(val displayName: String, val category: VaultCategory?) {
    ALL("All", null),
    DOCUMENTS("Documents", VaultCategory.DOCUMENT),
    IMAGES("Images", VaultCategory.IMAGE),
    VIDEOS("Videos", VaultCategory.VIDEO),
    AUDIO("Audio", VaultCategory.AUDIO),
    ARCHIVES("Archives", VaultCategory.ZIP),
    CODE("Code", VaultCategory.CODE),
    TEXT("Notes & Text", VaultCategory.TEXT),
    OTHER("Other", VaultCategory.OTHER);

    fun getIcon(): ImageVector = when (this) {
        ALL -> Icons.Default.Layers
        DOCUMENTS -> Icons.Default.Description
        IMAGES -> Icons.Default.Image
        VIDEOS -> Icons.Default.VideoFile
        AUDIO -> Icons.Default.AudioFile
        ARCHIVES -> Icons.Default.Archive
        CODE -> Icons.Default.Code
        TEXT -> Icons.Default.NoteAdd
        OTHER -> Icons.Default.Folder
    }
}

data class SearchFilterState(
    val category: SearchCategoryFilter = SearchCategoryFilter.ALL,
    val extension: String? = null,
    val folderId: Long? = null,
    val tag: String? = null,
    val favoritesOnly: Boolean = false,
    val recentOnly: Boolean = false,
    val dateFilter: DateFilterOption = DateFilterOption.ALL,
    val customDateStart: Long? = null,
    val customDateEnd: Long? = null,
    val sizeFilter: FileSizeFilterOption = FileSizeFilterOption.ALL,
    val customMinBytes: Long? = null,
    val customMaxBytes: Long? = null,
    val contentSearchEnabled: Boolean = false,
    val archiveSearchEnabled: Boolean = false,
    val sortOption: SearchSortOption = SearchSortOption.RELEVANCE,
    val caseSensitive: Boolean = false
) {
    val isDefault: Boolean
        get() = category == SearchCategoryFilter.ALL &&
                extension == null &&
                folderId == null &&
                tag == null &&
                !favoritesOnly &&
                !recentOnly &&
                dateFilter == DateFilterOption.ALL &&
                sizeFilter == FileSizeFilterOption.ALL &&
                !contentSearchEnabled &&
                !archiveSearchEnabled &&
                sortOption == SearchSortOption.RELEVANCE &&
                !caseSensitive

    val activeFilterCount: Int
        get() {
            var count = 0
            if (category != SearchCategoryFilter.ALL) count++
            if (!extension.isNullOrBlank()) count++
            if (folderId != null) count++
            if (!tag.isNullOrBlank()) count++
            if (favoritesOnly) count++
            if (recentOnly) count++
            if (dateFilter != DateFilterOption.ALL) count++
            if (sizeFilter != FileSizeFilterOption.ALL) count++
            if (contentSearchEnabled) count++
            if (archiveSearchEnabled) count++
            if (sortOption != SearchSortOption.RELEVANCE) count++
            if (caseSensitive) count++
            return count
        }
}

data class ContentMatchSnippet(
    val lineIndex: Int,
    val snippet: String,
    val matchCount: Int = 1
)

data class SearchResultItem(
    val item: VaultItemEntity,
    val rankScore: Int = 0,
    val folderName: String? = null,
    val contentSnippet: ContentMatchSnippet? = null,
    val matchedArchiveEntries: List<String> = emptyList(),
    val matchedTags: List<String> = emptyList()
)

enum class SuggestionType {
    RECENT_QUERY,
    FOLDER,
    TAG,
    EXTENSION,
    FILENAME
}

data class SearchSuggestionItem(
    val type: SuggestionType,
    val text: String,
    val subtitle: String? = null
)

/**
 * Deterministic local ranking system for PrivateVault.
 * Transparent, predictable score calculation without any AI, telemetry, or remote services.
 */
object DeterministicSearchRanker {

    fun computeScore(
        query: String,
        item: VaultItemEntity,
        folderName: String?,
        contentSnippet: ContentMatchSnippet?,
        matchedArchiveEntries: List<String>,
        caseSensitive: Boolean
    ): Int {
        if (query.isBlank()) {
            var base = 0
            if (item.isFavorite) base += 20
            if (item.lastAccessedAt != null) base += 10
            return base
        }

        val q = if (caseSensitive) query.trim() else query.trim().lowercase()
        val title = if (caseSensitive) item.title else item.title.lowercase()
        var score = 0

        // 1. Exact filename match
        if (title == q) {
            score += 100
        } else if (title.startsWith(q)) {
            // 2. Filename starts with query
            score += 80
        } else if (title.contains(q)) {
            // 3. Filename contains query
            score += 60
        }

        // 4. Extension match
        val ext = title.substringAfterLast('.', "")
        if (ext.isNotEmpty() && (ext == q.removePrefix(".") || q == ".$ext")) {
            score += 40
        }

        // 5. Tag match
        val itemTags = item.tags.split(",").map { if (caseSensitive) it.trim() else it.trim().lowercase() }
        if (itemTags.any { it == q }) {
            score += 35
        } else if (itemTags.any { it.contains(q) }) {
            score += 25
        }

        // 6. Folder match
        val fName = if (caseSensitive) folderName ?: "" else folderName?.lowercase() ?: ""
        if (fName.isNotEmpty()) {
            if (fName == q) {
                score += 25
            } else if (fName.contains(q)) {
                score += 15
            }
        }

        // 7. Content match (local on-demand text search)
        if (contentSnippet != null) {
            score += 20 + minOf(contentSnippet.matchCount * 2, 10)
        }

        // 8. Archive entry match
        if (matchedArchiveEntries.isNotEmpty()) {
            score += 20
        }

        // Deterministic boosts
        if (item.isFavorite) score += 15
        val now = System.currentTimeMillis()
        if (item.lastAccessedAt != null && (now - item.lastAccessedAt) < TimeUnit.DAYS.toMillis(7)) {
            score += 10
        }

        return score
    }
}
