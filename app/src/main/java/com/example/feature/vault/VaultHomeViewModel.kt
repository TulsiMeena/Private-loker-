package com.example.feature.vault

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WorkspaceTab {
    WORKSPACE, FILES, FAVORITES, TRASH, SETTINGS
}

data class CategoryStat(
    val category: VaultCategory,
    val count: Int,
    val sizeBytes: Long
)

data class VaultHomeUiState(
    val isUnlocked: Boolean = true,
    val totalItemCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val availableDeviceBytes: Long = 0L,
    val categoryStats: Map<VaultCategory, CategoryStat> = emptyMap(),
    val recentItems: List<VaultItemEntity> = emptyList(),
    val favoriteItems: List<VaultItemEntity> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<VaultItemEntity> = emptyList(),
    val selectedCategory: VaultCategory? = null,
    val trashCount: Int = 0,
    val currentTab: WorkspaceTab = WorkspaceTab.WORKSPACE
)

@OptIn(ExperimentalCoroutinesApi::class)
class VaultHomeViewModel(
    val repository: VaultRepository,
    val sessionManager: SessionSecurityManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<VaultCategory?>(null)
    val selectedCategory: StateFlow<VaultCategory?> = _selectedCategory.asStateFlow()

    private val _currentTab = MutableStateFlow(WorkspaceTab.WORKSPACE)
    val currentTab: StateFlow<WorkspaceTab> = _currentTab.asStateFlow()

    val lockState: StateFlow<LockState> = sessionManager.lockState

    private val searchResultsFlow = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList())
        else repository.searchItems(query)
    }

    private val metricsFlow = combine(
        repository.totalItemCount,
        repository.totalSizeBytes,
        repository.trashItemCount
    ) { count, bytes, trash ->
        Triple(count, bytes ?: 0L, trash)
    }

    // Dynamic category breakdown computed from all items
    private val categoryBreakdownFlow = repository.allItems.combine(_searchQuery) { items, _ ->
        val map = mutableMapOf<VaultCategory, CategoryStat>()
        VaultCategory.entries.forEach { cat ->
            val matching = items.filter { it.category == cat.name }
            val count = matching.size
            val size = matching.sumOf { it.sizeBytes }
            map[cat] = CategoryStat(category = cat, count = count, sizeBytes = size)
        }
        map
    }.flowOn(Dispatchers.Default)

    // Calculate free disk space
    private fun getAvailableDiskSpace(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            10L * 1024 * 1024 * 1024 // 10 GB fallback
        }
    }

    val uiState: StateFlow<VaultHomeUiState> = combine(
        sessionManager.lockState,
        metricsFlow,
        repository.recentItems,
        repository.favoriteItems,
        categoryBreakdownFlow,
        _searchQuery,
        searchResultsFlow,
        _currentTab
    ) { args: Array<Any?> ->
        val lockState = args[0] as LockState
        @Suppress("UNCHECKED_CAST")
        val metrics = args[1] as Triple<Int, Long, Int>
        @Suppress("UNCHECKED_CAST")
        val recentList = args[2] as List<VaultItemEntity>
        @Suppress("UNCHECKED_CAST")
        val favorites = args[3] as List<VaultItemEntity>
        @Suppress("UNCHECKED_CAST")
        val catMap = args[4] as Map<VaultCategory, CategoryStat>
        val query = args[5] as String
        @Suppress("UNCHECKED_CAST")
        val searchResults = args[6] as List<VaultItemEntity>
        val tab = args[7] as WorkspaceTab

        VaultHomeUiState(
            isUnlocked = lockState is LockState.Unlocked,
            totalItemCount = metrics.first,
            totalSizeBytes = metrics.second,
            availableDeviceBytes = getAvailableDiskSpace(),
            categoryStats = catMap,
            recentItems = recentList,
            favoriteItems = favorites,
            searchQuery = query,
            isSearching = query.isNotBlank(),
            searchResults = searchResults,
            selectedCategory = _selectedCategory.value,
            trashCount = metrics.third,
            currentTab = tab
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultHomeUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: VaultCategory?) {
        _selectedCategory.value = category
    }

    fun selectTab(tab: WorkspaceTab) {
        _currentTab.value = tab
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            repository.clearRecentHistory()
        }
    }

    fun createNote(title: String, content: String, onComplete: (VaultItemEntity) -> Unit) {
        viewModelScope.launch {
            val result = repository.createNewFile(title, content, VaultCategory.TEXT)
            if (result.isSuccess) {
                result.getOrNull()?.let(onComplete)
            }
        }
    }

    fun createCode(title: String, content: String, onComplete: (VaultItemEntity) -> Unit) {
        viewModelScope.launch {
            val result = repository.createNewFile(title, content, VaultCategory.CODE)
            if (result.isSuccess) {
                result.getOrNull()?.let(onComplete)
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name, null)
        }
    }

    fun lockVault() {
        sessionManager.lockVault()
    }
}
