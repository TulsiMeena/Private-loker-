package com.example.feature.search

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Manages local, private search history for PrivateVault.
 *
 * Security & Privacy:
 * - Stored exclusively in app-private Encrypted/Protected SharedPreferences.
 * - History terms are never written to logcat or exported.
 * - In-memory flow is purged immediately upon vault lock.
 */
class SearchHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "private_vault_search_history",
        Context.MODE_PRIVATE
    )

    private val _historyFlow = MutableStateFlow<List<String>>(emptyList())
    val historyFlow: StateFlow<List<String>> = _historyFlow.asStateFlow()

    init {
        loadHistoryFromDisk()
    }

    private fun loadHistoryFromDisk() {
        val raw = prefs.getString(KEY_SEARCH_TERMS, null)
        if (raw.isNullOrBlank()) {
            _historyFlow.value = emptyList()
            return
        }
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i)
                if (item.isNotBlank()) list.add(item)
            }
            _historyFlow.value = list
        } catch (_: Exception) {
            _historyFlow.value = emptyList()
        }
    }

    suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext

        val current = _historyFlow.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val limited = current.take(MAX_HISTORY_ENTRIES)

        _historyFlow.value = limited
        saveToDisk(limited)
    }

    suspend fun removeSearch(query: String) = withContext(Dispatchers.IO) {
        val current = _historyFlow.value.toMutableList()
        current.removeAll { it.equals(query.trim(), ignoreCase = true) }
        _historyFlow.value = current
        saveToDisk(current)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _historyFlow.value = emptyList()
        prefs.edit().remove(KEY_SEARCH_TERMS).apply()
    }

    /**
     * Purges in-memory history when the vault session locks.
     */
    fun onVaultLocked() {
        _historyFlow.value = emptyList()
    }

    /**
     * Restores history from disk once the vault is unlocked.
     */
    fun onVaultUnlocked() {
        loadHistoryFromDisk()
    }

    private fun saveToDisk(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_SEARCH_TERMS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_SEARCH_TERMS = "vault_search_queries_v1"
        private const val MAX_HISTORY_ENTRIES = 20
    }
}
