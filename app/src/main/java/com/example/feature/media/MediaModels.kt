package com.example.feature.media

import androidx.compose.runtime.Immutable
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity

enum class MediaCategoryTab(val displayName: String) {
    OVERVIEW("Overview"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio")
}

enum class MediaViewMode {
    GRID,
    LIST
}

enum class MediaSortOption(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    TYPE("By Media Type")
}

enum class MediaSizeFilter(val displayName: String) {
    ALL("All Sizes"),
    SMALL("< 5 MB"),
    MEDIUM("5 – 50 MB"),
    LARGE("> 50 MB")
}

enum class MediaDateFilter(val displayName: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class MediaStatusFilter(val displayName: String) {
    ALL("All Items"),
    FAVORITES("Favorites"),
    TAGGED("Tagged Only"),
    RECENT("Recently Accessed")
}

@Immutable
data class ImageMetadataInfo(
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val orientationDegrees: Int = 0,
    val colorSpace: String? = null,
    val hasExif: Boolean = false
) {
    val aspectRatio: String?
        get() {
            if (width == null || height == null || width <= 0 || height <= 0) return null
            val gcd = computeGcd(width, height)
            val wRatio = width / gcd
            val hRatio = height / gcd
            return if (wRatio <= 21 && hRatio <= 21) "$wRatio:$hRatio" else String.format("%.2f:1", width.toDouble() / height)
        }

    private fun computeGcd(a: Int, b: Int): Int = if (b == 0) a else computeGcd(b, a % b)
}

@Immutable
data class VideoMetadataInfo(
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long = 0L,
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val rotation: Int = 0,
    val bitrate: Long? = null,
    val frameRate: Float? = null
)

@Immutable
data class AudioMetadataInfo(
    val durationMs: Long = 0L,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val bitrate: Long? = null,
    val mimeType: String = "",
    val sizeBytes: Long = 0L
)

sealed interface BulkImportStatus {
    data object Idle : BulkImportStatus
    data class Preparing(val total: Int) : BulkImportStatus
    data class Importing(val current: Int, val total: Int, val currentFileName: String) : BulkImportStatus
    data class Completed(val importedCount: Int, val failedCount: Int) : BulkImportStatus
    data class Failed(val reason: String) : BulkImportStatus
    data object Cancelled : BulkImportStatus
}

@Immutable
data class MediaUiState(
    val currentTab: MediaCategoryTab = MediaCategoryTab.OVERVIEW,
    val viewMode: MediaViewMode = MediaViewMode.GRID,
    val searchQuery: String = "",
    val sortOption: MediaSortOption = MediaSortOption.NEWEST,
    val sizeFilter: MediaSizeFilter = MediaSizeFilter.ALL,
    val dateFilter: MediaDateFilter = MediaDateFilter.ALL,
    val statusFilter: MediaStatusFilter = MediaStatusFilter.ALL,
    val selectedTag: String? = null,
    val selectedFolderId: Long? = null,
    val isMultiSelectMode: Boolean = false,
    val selectedItemIds: Set<Long> = emptySet(),
    val allMediaItems: List<VaultItemEntity> = emptyList(),
    val displayedItems: List<VaultItemEntity> = emptyList(),
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val totalMediaSizeBytes: Long = 0L,
    val imageTotalSizeBytes: Long = 0L,
    val videoTotalSizeBytes: Long = 0L,
    val audioTotalSizeBytes: Long = 0L,
    val availableTags: List<String> = emptyList(),
    val folders: List<VaultFolderEntity> = emptyList(),
    val recentMediaItems: List<VaultItemEntity> = emptyList(),
    val favoriteMediaItems: List<VaultItemEntity> = emptyList(),
    val isSearching: Boolean = false
)
