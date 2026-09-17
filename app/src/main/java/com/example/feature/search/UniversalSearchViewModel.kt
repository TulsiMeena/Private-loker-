package com.example.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import com.example.feature.files.archive.ZipVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

sealed interface UniversalSearchUiState {
    data object Locked : UniversalSearchUiState
    data class Success(
        val query: String = "",
        val filterState: SearchFilterState = SearchFilterState(),
        val results: List<SearchResultItem> = emptyList(),
        val folders: List<VaultFolderEntity> = emptyList(),
        val allTags: List<String> = emptyList(),
        val suggestions: List<SearchSuggestionItem> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        val selectedItemIds: Set<Long> = emptySet(),
        val isSearchingContent: Boolean = false,
        val contentSearchProgress: Pair<Int, Int>? = null, // (current, total)
        val errorMessage: String? = null
    ) : UniversalSearchUiState
}

class UniversalSearchViewModel(
    val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager,
    private val zipVaultManager: ZipVaultManager,
    private val historyManager: SearchHistoryManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _debouncedQuery = MutableStateFlow("")

    private val _filterState = MutableStateFlow(SearchFilterState())
    val filterState: StateFlow<SearchFilterState> = _filterState.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    private val _isSearchingContent = MutableStateFlow(false)
    val isSearchingContent: StateFlow<Boolean> = _isSearchingContent.asStateFlow()

    private val _contentSearchProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val contentSearchProgress: StateFlow<Pair<Int, Int>?> = _contentSearchProgress.asStateFlow()

    private val _contentSnippets = MutableStateFlow<Map<Long, ContentMatchSnippet>>(emptyMap())
    private val _archiveMatches = MutableStateFlow<Map<Long, List<String>>>(emptyMap())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var contentSearchJob: Job? = null
    private var archiveSearchJob: Job? = null
    private var debounceJob: Job? = null

    // Monitor vault lock state
    init {
        sessionManager.lockState.onEach { state ->
            if (state is LockState.Locked) {
                // Instantly wipe all temporary search data, snippets and cancel running jobs
                cancelContentSearch()
                cancelArchiveSearch()
                _contentSnippets.value = emptyMap()
                _archiveMatches.value = emptyMap()
                _selectedItemIds.value = emptySet()
                _query.value = ""
                _debouncedQuery.value = ""
                historyManager.onVaultLocked()
            } else if (state is LockState.Unlocked) {
                historyManager.onVaultUnlocked()
            }
        }.launchIn(viewModelScope)
    }

    // Combine all metadata sources reactively
    val uiState: StateFlow<UniversalSearchUiState> = combine(
        sessionManager.lockState,
        _debouncedQuery,
        _filterState,
        repository.allItems,
        repository.getAllFolders(),
        repository.getAllTags(),
        historyManager.historyFlow,
        _selectedItemIds,
        _contentSnippets,
        _archiveMatches,
        _isSearchingContent,
        _contentSearchProgress,
        _errorMessage
    ) { args: Array<Any?> ->
        val lockState = args[0] as LockState
        if (lockState is LockState.Locked) {
            return@combine UniversalSearchUiState.Locked
        }

        val q = args[1] as String
        val filters = args[2] as SearchFilterState
        @Suppress("UNCHECKED_CAST")
        val allItems = args[3] as List<VaultItemEntity>
        @Suppress("UNCHECKED_CAST")
        val allFolders = args[4] as List<VaultFolderEntity>
        @Suppress("UNCHECKED_CAST")
        val rawTags = args[5] as List<String>
        @Suppress("UNCHECKED_CAST")
        val recentSearches = args[6] as List<String>
        @Suppress("UNCHECKED_CAST")
        val selectedIds = args[7] as Set<Long>
        @Suppress("UNCHECKED_CAST")
        val contentSnippets = args[8] as Map<Long, ContentMatchSnippet>
        @Suppress("UNCHECKED_CAST")
        val archiveMatches = args[9] as Map<Long, List<String>>
        val isSearching = args[10] as Boolean
        @Suppress("UNCHECKED_CAST")
        val progress = args[11] as Pair<Int, Int>?
        val errorMsg = args[12] as String?

        val folderMap = allFolders.associateBy { it.id }

        // Process unique tags
        val uniqueTags = rawTags.flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        // Filter and Rank Results
        val filteredResults = filterAndRank(
            allItems = allItems,
            query = q,
            filters = filters,
            folderMap = folderMap,
            contentSnippets = contentSnippets,
            archiveMatches = archiveMatches
        )

        // Generate suggestions if typing
        val suggestions = generateSuggestions(
            query = q,
            recentSearches = recentSearches,
            folders = allFolders,
            tags = uniqueTags,
            allItems = allItems
        )

        UniversalSearchUiState.Success(
            query = _query.value,
            filterState = filters,
            results = filteredResults,
            folders = allFolders,
            allTags = uniqueTags,
            suggestions = suggestions,
            recentSearches = recentSearches,
            selectedItemIds = selectedIds,
            isSearchingContent = isSearching,
            contentSearchProgress = progress,
            errorMessage = errorMsg
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UniversalSearchUiState.Success()
    )

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(250) // 250ms debounce
            _debouncedQuery.value = newQuery

            // Trigger on-demand content search if enabled and query has enough characters
            if (_filterState.value.contentSearchEnabled && newQuery.trim().length >= 2) {
                startContentSearch(newQuery.trim())
            } else if (!_filterState.value.contentSearchEnabled) {
                _contentSnippets.value = emptyMap()
            }

            // Trigger on-demand archive search if enabled
            if (_filterState.value.archiveSearchEnabled && newQuery.trim().length >= 2) {
                startArchiveSearch(newQuery.trim())
            } else if (!_filterState.value.archiveSearchEnabled) {
                _archiveMatches.value = emptyMap()
            }
        }
    }

    fun submitSearch(term: String = _query.value) {
        val trimmed = term.trim()
        if (trimmed.isNotBlank()) {
            _query.value = trimmed
            _debouncedQuery.value = trimmed
            viewModelScope.launch {
                historyManager.recordSearch(trimmed)
            }
        }
    }

    fun updateFilters(update: (SearchFilterState) -> SearchFilterState) {
        val newState = update(_filterState.value)
        _filterState.value = newState

        if (newState.contentSearchEnabled && _query.value.trim().length >= 2) {
            startContentSearch(_query.value.trim())
        } else if (!newState.contentSearchEnabled) {
            cancelContentSearch()
            _contentSnippets.value = emptyMap()
        }

        if (newState.archiveSearchEnabled && _query.value.trim().length >= 2) {
            startArchiveSearch(_query.value.trim())
        } else if (!newState.archiveSearchEnabled) {
            cancelArchiveSearch()
            _archiveMatches.value = emptyMap()
        }
    }

    fun clearAllFilters() {
        cancelContentSearch()
        cancelArchiveSearch()
        _contentSnippets.value = emptyMap()
        _archiveMatches.value = emptyMap()
        _filterState.value = SearchFilterState()
    }

    fun removeRecentSearch(term: String) {
        viewModelScope.launch {
            historyManager.removeSearch(term)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            historyManager.clearHistory()
        }
    }

    // ====================================================================
    // Safe In-Memory Content Search
    // ====================================================================

    fun startContentSearch(searchQuery: String) {
        cancelContentSearch()
        contentSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearchingContent.value = true
            _errorMessage.value = null

            try {
                val candidateItems = repository.getAllItemsList().filter { item ->
                    !item.isTrash && isTextOrCode(item)
                }

                val total = candidateItems.size
                val resultsMap = mutableMapOf<Long, ContentMatchSnippet>()

                candidateItems.forEachIndexed { index, item ->
                    if (!isActive) return@launch
                    _contentSearchProgress.value = Pair(index + 1, total)

                    try {
                        val bytes = repository.storageManager.decryptToMemory(item.encryptedPath)
                        val text = String(bytes, Charsets.UTF_8)
                        val lines = text.lines()
                        var matchCount = 0
                        var firstMatchLine = -1
                        var firstSnippet = ""

                        lines.forEachIndexed { lineIdx, line ->
                            val matches = if (_filterState.value.caseSensitive) {
                                line.contains(searchQuery)
                            } else {
                                line.contains(searchQuery, ignoreCase = true)
                            }

                            if (matches) {
                                matchCount++
                                if (firstMatchLine == -1) {
                                    firstMatchLine = lineIdx + 1
                                    firstSnippet = line.trim().take(120)
                                }
                            }
                        }

                        if (matchCount > 0) {
                            resultsMap[item.id] = ContentMatchSnippet(
                                lineIndex = firstMatchLine,
                                snippet = firstSnippet,
                                matchCount = matchCount
                            )
                        }
                    } catch (_: Exception) {
                        // Skip corrupted/non-text items gracefully
                    }
                }

                _contentSnippets.value = resultsMap
            } catch (e: Exception) {
                _errorMessage.value = "Content search interrupted"
            } finally {
                _isSearchingContent.value = false
                _contentSearchProgress.value = null
            }
        }
    }

    fun cancelContentSearch() {
        contentSearchJob?.cancel()
        contentSearchJob = null
        _isSearchingContent.value = false
        _contentSearchProgress.value = null
    }

    // ====================================================================
    // Safe Archive Search (ZIP File Entries)
    // ====================================================================

    private fun startArchiveSearch(searchQuery: String) {
        cancelArchiveSearch()
        archiveSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val archives = repository.getAllItemsList().filter {
                    !it.isTrash && (it.category == VaultCategory.ZIP.name || it.mimeType.contains("zip"))
                }

                val matchesMap = mutableMapOf<Long, List<String>>()
                archives.forEach { archiveItem ->
                    if (!isActive) return@launch
                    val result = zipVaultManager.openArchive(archiveItem)
                    if (result.isSuccess) {
                        val (_, entries) = result.getOrThrow()
                        val matchingEntryPaths = entries.filter { entry ->
                            if (_filterState.value.caseSensitive) {
                                entry.fullPath.contains(searchQuery) || entry.name.contains(searchQuery)
                            } else {
                                entry.fullPath.contains(searchQuery, ignoreCase = true) || entry.name.contains(searchQuery, ignoreCase = true)
                            }
                        }.map { it.fullPath }.take(5)

                        if (matchingEntryPaths.isNotEmpty()) {
                            matchesMap[archiveItem.id] = matchingEntryPaths
                        }
                    }
                }
                _archiveMatches.value = matchesMap
            } catch (_: Exception) {
                // Ignore archive search errors gracefully
            }
        }
    }

    fun cancelArchiveSearch() {
        archiveSearchJob?.cancel()
        archiveSearchJob = null
    }

    // ====================================================================
    // Multi-Selection & Batch Actions
    // ====================================================================

    fun toggleSelection(itemId: Long) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItemIds.value = current
    }

    fun selectAll(visibleItems: List<SearchResultItem>) {
        _selectedItemIds.value = visibleItems.map { it.item.id }.toSet()
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun batchMove(targetFolderId: Long?) {
        val ids = _selectedItemIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val allItems = repository.getAllItemsList().filter { ids.contains(it.id) }
            repository.batchMove(allItems, targetFolderId)
            clearSelection()
        }
    }

    fun batchDelete() {
        val ids = _selectedItemIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val allItems = repository.getAllItemsList().filter { ids.contains(it.id) }
            repository.batchDelete(allItems)
            clearSelection()
        }
    }

    fun batchFavorite(isFavorite: Boolean) {
        val ids = _selectedItemIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val allItems = repository.getAllItemsList().filter { ids.contains(it.id) }
            repository.batchFavorite(allItems, isFavorite)
            clearSelection()
        }
    }

    fun batchAddTag(tag: String) {
        val ids = _selectedItemIds.value
        if (ids.isEmpty() || tag.isBlank()) return
        viewModelScope.launch {
            val allItems = repository.getAllItemsList().filter { ids.contains(it.id) }
            repository.batchAddTag(allItems, tag)
            clearSelection()
        }
    }

    fun batchArchive(archiveTitle: String, onComplete: (VaultItemEntity) -> Unit) {
        val ids = _selectedItemIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val allItems = repository.getAllItemsList().filter { ids.contains(it.id) }
            val cleanTitle = if (archiveTitle.endsWith(".zip", ignoreCase = true)) archiveTitle else "$archiveTitle.zip"
            val result = zipVaultManager.createArchiveFromItems(
                items = allItems,
                archiveName = cleanTitle,
                targetFolderId = null
            )
            if (result.isSuccess) {
                result.getOrNull()?.let(onComplete)
                clearSelection()
            }
        }
    }

    // ====================================================================
    // Quick Item Actions
    // ====================================================================

    fun toggleFavorite(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.setFavorite(item, !item.isFavorite)
        }
    }

    fun moveToTrash(item: VaultItemEntity) {
        viewModelScope.launch {
            repository.moveToTrash(item)
        }
    }

    fun renameItem(item: VaultItemEntity, newTitle: String) {
        viewModelScope.launch {
            repository.renameItem(item, newTitle)
        }
    }

    fun moveItem(item: VaultItemEntity, targetFolderId: Long?) {
        viewModelScope.launch {
            repository.moveItemToFolder(item, targetFolderId)
        }
    }

    fun updateItemTags(item: VaultItemEntity, tags: String) {
        viewModelScope.launch {
            repository.updateItemTags(item.id, tags)
        }
    }

    // ====================================================================
    // Filtering & Ranking Logic
    // ====================================================================

    private fun filterAndRank(
        allItems: List<VaultItemEntity>,
        query: String,
        filters: SearchFilterState,
        folderMap: Map<Long, VaultFolderEntity>,
        contentSnippets: Map<Long, ContentMatchSnippet>,
        archiveMatches: Map<Long, List<String>>
    ): List<SearchResultItem> {
        val now = System.currentTimeMillis()
        val q = query.trim()
        val hasQuery = q.isNotEmpty()

        val matchingItems = allItems.filter { item ->
            if (item.isTrash) return@filter false

            // 1. Category Filter
            if (filters.category != SearchCategoryFilter.ALL) {
                val matchesCategory = when (filters.category) {
                    SearchCategoryFilter.DOCUMENTS -> item.category == VaultCategory.DOCUMENT.name ||
                            item.title.endsWith(".pdf", ignoreCase = true) ||
                            item.title.endsWith(".doc", ignoreCase = true) ||
                            item.title.endsWith(".docx", ignoreCase = true)
                    SearchCategoryFilter.IMAGES -> item.category == VaultCategory.IMAGE.name
                    SearchCategoryFilter.VIDEOS -> item.category == VaultCategory.VIDEO.name
                    SearchCategoryFilter.AUDIO -> item.category == VaultCategory.AUDIO.name
                    SearchCategoryFilter.ARCHIVES -> item.category == VaultCategory.ZIP.name || item.title.endsWith(".zip", ignoreCase = true)
                    SearchCategoryFilter.CODE -> item.category == VaultCategory.CODE.name
                    SearchCategoryFilter.TEXT -> item.category == VaultCategory.TEXT.name
                    SearchCategoryFilter.OTHER -> item.category == VaultCategory.OTHER.name
                    else -> true
                }
                if (!matchesCategory) return@filter false
            }

            // 2. Extension Filter
            if (!filters.extension.isNullOrBlank()) {
                val cleanExt = filters.extension.removePrefix(".").lowercase()
                val itemExt = item.title.substringAfterLast('.', "").lowercase()
                if (itemExt != cleanExt) return@filter false
            }

            // 3. Folder Filter
            if (filters.folderId != null) {
                if (item.folderId != filters.folderId) return@filter false
            }

            // 4. Tag Filter
            if (!filters.tag.isNullOrBlank()) {
                val itemTags = item.tags.split(",").map { it.trim().lowercase() }
                if (!itemTags.contains(filters.tag.trim().lowercase())) return@filter false
            }

            // 5. Favorites Filter
            if (filters.favoritesOnly && !item.isFavorite) {
                return@filter false
            }

            // 6. Recent Filter
            if (filters.recentOnly && item.lastAccessedAt == null) {
                return@filter false
            }

            // 7. Date Filter
            if (filters.dateFilter != DateFilterOption.ALL) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val todayStart = cal.timeInMillis

                val matchesDate = when (filters.dateFilter) {
                    DateFilterOption.TODAY -> item.modifiedAt >= todayStart
                    DateFilterOption.YESTERDAY -> item.modifiedAt in (todayStart - TimeUnit.DAYS.toMillis(1)) until todayStart
                    DateFilterOption.LAST_7_DAYS -> item.modifiedAt >= (now - TimeUnit.DAYS.toMillis(7))
                    DateFilterOption.LAST_30_DAYS -> item.modifiedAt >= (now - TimeUnit.DAYS.toMillis(30))
                    DateFilterOption.CUSTOM -> {
                        val start = filters.customDateStart ?: 0L
                        val end = filters.customDateEnd ?: Long.MAX_VALUE
                        item.modifiedAt in start..end
                    }
                    else -> true
                }
                if (!matchesDate) return@filter false
            }

            // 8. Size Filter
            if (filters.sizeFilter != FileSizeFilterOption.ALL) {
                val matchesSize = when (filters.sizeFilter) {
                    FileSizeFilterOption.SMALL -> item.sizeBytes < 1024 * 1024 // < 1 MB
                    FileSizeFilterOption.MEDIUM -> item.sizeBytes in (1024 * 1024)..(25 * 1024 * 1024) // 1-25 MB
                    FileSizeFilterOption.LARGE -> item.sizeBytes > 25 * 1024 * 1024 // > 25 MB
                    FileSizeFilterOption.CUSTOM -> {
                        val min = filters.customMinBytes ?: 0L
                        val max = filters.customMaxBytes ?: Long.MAX_VALUE
                        item.sizeBytes in min..max
                    }
                    else -> true
                }
                if (!matchesSize) return@filter false
            }

            // 9. Query Matching (if query is present)
            if (hasQuery) {
                val titleMatches = if (filters.caseSensitive) {
                    item.title.contains(q)
                } else {
                    item.title.contains(q, ignoreCase = true)
                }

                val tagMatches = item.tags.split(",").any {
                    if (filters.caseSensitive) it.trim().contains(q) else it.trim().contains(q, ignoreCase = true)
                }

                val folderName = folderMap[item.folderId]?.name
                val folderMatches = folderName != null && (
                    if (filters.caseSensitive) folderName.contains(q) else folderName.contains(q, ignoreCase = true)
                )

                val contentMatch = contentSnippets.containsKey(item.id)
                val archiveMatch = archiveMatches.containsKey(item.id)

                val extensionMatches = item.title.substringAfterLast('.', "").equals(q.removePrefix("."), ignoreCase = true)

                titleMatches || tagMatches || folderMatches || contentMatch || archiveMatch || extensionMatches
            } else {
                true
            }
        }

        // Map to SearchResultItem with deterministic score
        val scoredItems = matchingItems.map { item ->
            val folder = folderMap[item.folderId]
            val snippet = contentSnippets[item.id]
            val matchedEntries = archiveMatches[item.id] ?: emptyList()
            val matchedTags = item.tags.split(",")
                .map { it.trim() }
                .filter { tag ->
                    tag.isNotEmpty() && (hasQuery && (if (filters.caseSensitive) tag.contains(q) else tag.contains(q, ignoreCase = true)))
                }

            val score = DeterministicSearchRanker.computeScore(
                query = q,
                item = item,
                folderName = folder?.name,
                contentSnippet = snippet,
                matchedArchiveEntries = matchedEntries,
                caseSensitive = filters.caseSensitive
            )

            SearchResultItem(
                item = item,
                rankScore = score,
                folderName = folder?.name,
                contentSnippet = snippet,
                matchedArchiveEntries = matchedEntries,
                matchedTags = matchedTags
            )
        }

        // Apply Sorting
        return when (filters.sortOption) {
            SearchSortOption.RELEVANCE -> scoredItems.sortedWith(
                compareByDescending<SearchResultItem> { it.rankScore }
                    .thenByDescending { it.item.modifiedAt }
            )
            SearchSortOption.NAME_ASC -> scoredItems.sortedBy { it.item.title.lowercase() }
            SearchSortOption.NAME_DESC -> scoredItems.sortedByDescending { it.item.title.lowercase() }
            SearchSortOption.NEWEST -> scoredItems.sortedByDescending { it.item.modifiedAt }
            SearchSortOption.OLDEST -> scoredItems.sortedBy { it.item.modifiedAt }
            SearchSortOption.LARGEST -> scoredItems.sortedByDescending { it.item.sizeBytes }
            SearchSortOption.SMALLEST -> scoredItems.sortedBy { it.item.sizeBytes }
            SearchSortOption.TYPE -> scoredItems.sortedWith(
                compareBy<SearchResultItem> { it.item.category }
                    .thenBy { it.item.title.lowercase() }
            )
        }
    }

    private fun generateSuggestions(
        query: String,
        recentSearches: List<String>,
        folders: List<VaultFolderEntity>,
        tags: List<String>,
        allItems: List<VaultItemEntity>
    ): List<SearchSuggestionItem> {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            return recentSearches.take(5).map {
                SearchSuggestionItem(SuggestionType.RECENT_QUERY, it, "Recent Search")
            }
        }

        val suggestions = mutableListOf<SearchSuggestionItem>()

        // 1. Matching Recent Searches
        recentSearches.filter { it.lowercase().contains(q) && !it.equals(q, ignoreCase = true) }
            .take(3).forEach {
                suggestions.add(SearchSuggestionItem(SuggestionType.RECENT_QUERY, it, "Recent search"))
            }

        // 2. Matching Tags
        tags.filter { it.lowercase().contains(q) }
            .take(3).forEach {
                suggestions.add(SearchSuggestionItem(SuggestionType.TAG, it, "Tag"))
            }

        // 3. Matching Folders
        folders.filter { it.name.lowercase().contains(q) }
            .take(3).forEach {
                suggestions.add(SearchSuggestionItem(SuggestionType.FOLDER, it.name, "Folder"))
            }

        // 4. Matching Extensions
        val commonExtensions = listOf("pdf", "jpg", "png", "mp4", "mp3", "zip", "kt", "txt", "md", "json", "py")
        commonExtensions.filter { it.contains(q.removePrefix(".")) }
            .take(2).forEach {
                suggestions.add(SearchSuggestionItem(SuggestionType.EXTENSION, ".$it", "Extension filter"))
            }

        // 5. Matching filenames
        allItems.filter { it.title.lowercase().startsWith(q) }
            .take(3).forEach {
                suggestions.add(SearchSuggestionItem(SuggestionType.FILENAME, it.title, "File"))
            }

        return suggestions.distinctBy { it.text }
    }

    private fun isTextOrCode(item: VaultItemEntity): Boolean {
        if (item.category == VaultCategory.TEXT.name || item.category == VaultCategory.CODE.name) return true
        val ext = item.title.substringAfterLast('.', "").lowercase()
        val textExtensions = setOf(
            "txt", "md", "json", "xml", "csv", "kt", "kts", "java", "py",
            "js", "ts", "html", "css", "sql", "c", "cpp", "h", "hpp", "yaml", "yml", "log"
        )
        return textExtensions.contains(ext) || item.mimeType.startsWith("text/")
    }
}
