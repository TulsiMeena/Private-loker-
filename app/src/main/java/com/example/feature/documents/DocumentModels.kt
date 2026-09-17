package com.example.feature.documents

import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity

enum class DocumentViewMode {
    GRID,
    LIST
}

enum class DocumentSortOption(val displayName: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_NEWEST("Newest First"),
    DATE_OLDEST("Oldest First"),
    SIZE_LARGEST("Largest First"),
    SIZE_SMALLEST("Smallest First"),
    FILE_TYPE("File Extension")
}

enum class DocumentTypeFilter(val displayName: String, val extensions: List<String>) {
    ALL("All Types", emptyList()),
    PDF("PDF", listOf("pdf")),
    DOC_DOCX("Word (DOC/DOCX)", listOf("doc", "docx")),
    TXT("Plain Text (TXT)", listOf("txt")),
    MARKDOWN("Markdown (MD)", listOf("md", "markdown")),
    CSV("Spreadsheet (CSV)", listOf("csv")),
    JSON("JSON", listOf("json")),
    XML("XML", listOf("xml")),
    RTF("RTF", listOf("rtf")),
    OTHER("Other Documents", listOf("odt", "epub", "log", "note", "yaml", "yml"))
}

enum class DocumentStatusFilter(val displayName: String) {
    ALL("All"),
    FAVORITE("Favorites"),
    RECENT("Recently Accessed"),
    TAGGED("Tagged")
}

enum class DocumentSizeFilter(val displayName: String, val minBytes: Long, val maxBytes: Long) {
    ALL("Any Size", 0L, Long.MAX_VALUE),
    SMALL("Small (< 1 MB)", 0L, 1_048_576L),
    MEDIUM("Medium (1 - 10 MB)", 1_048_576L, 10_485_760L),
    LARGE("Large (> 10 MB)", 10_485_760L, Long.MAX_VALUE)
}

enum class DocumentDateFilter(val displayName: String) {
    ALL("Any Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class DocumentCenterTab(val displayName: String) {
    ALL("All Documents"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    FOLDERS("Folders"),
    TAGS("Tags")
}

data class DocumentFilterState(
    val typeFilter: DocumentTypeFilter = DocumentTypeFilter.ALL,
    val statusFilter: DocumentStatusFilter = DocumentStatusFilter.ALL,
    val sizeFilter: DocumentSizeFilter = DocumentSizeFilter.ALL,
    val dateFilter: DocumentDateFilter = DocumentDateFilter.ALL,
    val selectedTag: String? = null
) {
    val hasActiveFilter: Boolean
        get() = typeFilter != DocumentTypeFilter.ALL ||
                statusFilter != DocumentStatusFilter.ALL ||
                sizeFilter != DocumentSizeFilter.ALL ||
                dateFilter != DocumentDateFilter.ALL ||
                selectedTag != null
}

data class DocumentCenterUiState(
    val documents: List<VaultItemEntity> = emptyList(),
    val folders: List<VaultFolderEntity> = emptyList(),
    val currentFolder: VaultFolderEntity? = null,
    val folderBreadcrumbs: List<VaultFolderEntity> = emptyList(),
    val currentTab: DocumentCenterTab = DocumentCenterTab.ALL,
    val viewMode: DocumentViewMode = DocumentViewMode.GRID,
    val sortOption: DocumentSortOption = DocumentSortOption.DATE_NEWEST,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val filterState: DocumentFilterState = DocumentFilterState(),
    val isFilterSheetOpen: Boolean = false,
    val isMultiSelectActive: Boolean = false,
    val selectedItemIds: Set<Long> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
