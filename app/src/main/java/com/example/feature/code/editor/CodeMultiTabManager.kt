package com.example.feature.code.editor

import com.example.core.database.VaultItemEntity
import com.example.feature.code.CodeLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EditorTab(
    val itemId: Long,
    val title: String,
    val content: String,
    val savedContent: String,
    val language: CodeLanguage,
    val isReadOnly: Boolean = false,
    val isLargeFile: Boolean = false
) {
    val isDirty: Boolean get() = content != savedContent
    val extension: String get() = title.substringAfterLast('.', "")
}

/**
 * Manages open file tabs within the active authenticated session.
 *
 * Security Invariant:
 * - Tabs exist strictly in volatile memory of the active [SessionSecurityManager].
 * - No unencrypted content is ever written to disk or public preferences.
 * - When vault locks, all tabs are purged immediately.
 */
class CodeMultiTabManager(private val maxTabs: Int = 10) {

    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    val currentTab: EditorTab?
        get() {
            val list = _tabs.value
            val idx = _activeTabIndex.value
            return if (idx in list.indices) list[idx] else null
        }

    fun openOrSwitchTab(
        item: VaultItemEntity,
        content: String,
        isReadOnly: Boolean = false,
        isLargeFile: Boolean = false
    ): Int {
        val currentList = _tabs.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.itemId == item.id }

        if (existingIndex != -1) {
            _activeTabIndex.value = existingIndex
            return existingIndex
        }

        // Check if max tabs reached, remove oldest clean tab
        if (currentList.size >= maxTabs) {
            val cleanIdx = currentList.indexOfFirst { !it.isDirty }
            if (cleanIdx != -1) {
                currentList.removeAt(cleanIdx)
            } else {
                currentList.removeAt(0)
            }
        }

        val language = CodeLanguage.detect(item.title)
        val newTab = EditorTab(
            itemId = item.id,
            title = item.title,
            content = content,
            savedContent = content,
            language = language,
            isReadOnly = isReadOnly,
            isLargeFile = isLargeFile
        )
        currentList.add(newTab)
        _tabs.value = currentList
        val newIndex = currentList.size - 1
        _activeTabIndex.value = newIndex
        return newIndex
    }

    fun switchTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
        }
    }

    fun updateCurrentTabContent(newContent: String) {
        val list = _tabs.value.toMutableList()
        val idx = _activeTabIndex.value
        if (idx in list.indices) {
            list[idx] = list[idx].copy(content = newContent)
            _tabs.value = list
        }
    }

    fun markCurrentTabSaved(savedContent: String) {
        val list = _tabs.value.toMutableList()
        val idx = _activeTabIndex.value
        if (idx in list.indices) {
            list[idx] = list[idx].copy(content = savedContent, savedContent = savedContent)
            _tabs.value = list
        }
    }

    fun updateCurrentTabLanguage(language: CodeLanguage) {
        val list = _tabs.value.toMutableList()
        val idx = _activeTabIndex.value
        if (idx in list.indices) {
            list[idx] = list[idx].copy(language = language)
            _tabs.value = list
        }
    }

    fun closeTab(index: Int): Boolean {
        val list = _tabs.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _tabs.value = list

            val cur = _activeTabIndex.value
            if (cur >= list.size) {
                _activeTabIndex.value = (list.size - 1).coerceAtLeast(0)
            }
            return true
        }
        return false
    }

    /**
     * Purges all open tab buffers immediately upon vault lock or logout.
     */
    fun clearAll() {
        _tabs.value = emptyList()
        _activeTabIndex.value = 0
    }
}
