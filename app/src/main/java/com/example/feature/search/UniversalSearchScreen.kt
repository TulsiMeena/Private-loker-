package com.example.feature.search

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.designsystem.VaultColors
import com.example.core.storage.VaultCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSearchScreen(
    viewModel: UniversalSearchViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (VaultItemEntity) -> Unit,
    onLockVault: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onOpenFolder: ((folderId: Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedItemForAction by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showBatchTagDialog by remember { mutableStateOf(false) }
    var showBatchArchiveDialog by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is UniversalSearchUiState.Locked -> {
            VaultLockedSearchView(
                onAuthenticate = onNavigateToAuth,
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }

        is UniversalSearchUiState.Success -> {
            Scaffold(
                containerColor = VaultColors.Canvas,
                topBar = {
                    SearchTopBar(
                        query = state.query,
                        activeFilterCount = state.filterState.activeFilterCount,
                        caseSensitive = state.filterState.caseSensitive,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onClearQuery = { viewModel.onQueryChange("") },
                        onSearchSubmit = {
                            keyboardController?.hide()
                            viewModel.submitSearch(it)
                        },
                        onToggleFilterSheet = { showFilterSheet = true },
                        onToggleCaseSensitivity = {
                            viewModel.updateFilters { it.copy(caseSensitive = !it.caseSensitive) }
                        },
                        onNavigateBack = onNavigateBack,
                        onLockClick = onLockVault
                    )
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = state.selectedItemIds.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SearchBatchActionBar(
                            selectedCount = state.selectedItemIds.size,
                            totalCount = state.results.size,
                            onSelectAll = { viewModel.selectAll(state.results) },
                            onClearSelection = { viewModel.clearSelection() },
                            onBatchMove = { showBatchMoveDialog = true },
                            onBatchFavorite = { viewModel.batchFavorite(true) },
                            onBatchTag = { showBatchTagDialog = true },
                            onBatchArchive = { showBatchArchiveDialog = true },
                            onBatchDelete = {
                                viewModel.batchDelete()
                                Toast.makeText(context, "Moved ${state.selectedItemIds.size} items to trash", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                modifier = modifier.fillMaxSize()
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Filter Chips Bar
                    SearchFilterChipsBar(
                        filterState = state.filterState,
                        allTags = state.allTags,
                        folders = state.folders,
                        onCategorySelect = { cat ->
                            viewModel.updateFilters { it.copy(category = cat) }
                        },
                        onToggleFavorites = {
                            viewModel.updateFilters { it.copy(favoritesOnly = !it.favoritesOnly) }
                        },
                        onToggleRecent = {
                            viewModel.updateFilters { it.copy(recentOnly = !it.recentOnly) }
                        },
                        onToggleContentSearch = {
                            viewModel.updateFilters { it.copy(contentSearchEnabled = !it.contentSearchEnabled) }
                        },
                        onToggleArchiveSearch = {
                            viewModel.updateFilters { it.copy(archiveSearchEnabled = !it.archiveSearchEnabled) }
                        },
                        onOpenFilterSheet = { showFilterSheet = true },
                        onClearAllFilters = { viewModel.clearAllFilters() }
                    )

                    // Content Search In-Progress Indicator
                    if (state.isSearchingContent) {
                        ContentSearchProgressBar(
                            progress = state.contentSearchProgress,
                            onCancel = { viewModel.cancelContentSearch() }
                        )
                    }

                    // Main Body: Idle/Suggestions or Results List
                    if (state.query.isBlank() && state.filterState.isDefault) {
                        IdleSearchDashboard(
                            recentSearches = state.recentSearches,
                            allTags = state.allTags,
                            folders = state.folders,
                            onSelectSearch = { term ->
                                viewModel.onQueryChange(term)
                                viewModel.submitSearch(term)
                            },
                            onRemoveRecentSearch = { term -> viewModel.removeRecentSearch(term) },
                            onClearRecentHistory = { viewModel.clearRecentSearches() },
                            onSelectTag = { tag ->
                                viewModel.updateFilters { it.copy(tag = tag) }
                            },
                            onSelectFolder = { folderId ->
                                viewModel.updateFilters { it.copy(folderId = folderId) }
                            }
                        )
                    } else {
                        // Header Result Count & Sort Button
                        SearchResultsHeader(
                            resultCount = state.results.size,
                            currentSort = state.filterState.sortOption,
                            onSortSelected = { sort ->
                                viewModel.updateFilters { it.copy(sortOption = sort) }
                            }
                        )

                        // Suggestions Row (while typing)
                        if (state.suggestions.isNotEmpty() && state.query.isNotBlank()) {
                            SuggestionsRow(
                                suggestions = state.suggestions,
                                onSelectSuggestion = { suggestion ->
                                    when (suggestion.type) {
                                        SuggestionType.RECENT_QUERY, SuggestionType.FILENAME -> {
                                            viewModel.onQueryChange(suggestion.text)
                                            viewModel.submitSearch(suggestion.text)
                                        }
                                        SuggestionType.TAG -> {
                                            viewModel.updateFilters { it.copy(tag = suggestion.text) }
                                        }
                                        SuggestionType.FOLDER -> {
                                            val f = state.folders.firstOrNull { it.name == suggestion.text }
                                            if (f != null) viewModel.updateFilters { it.copy(folderId = f.id) }
                                        }
                                        SuggestionType.EXTENSION -> {
                                            viewModel.updateFilters { it.copy(extension = suggestion.text) }
                                        }
                                    }
                                }
                            )
                        }

                        if (state.results.isEmpty()) {
                            SearchEmptyState(
                                query = state.query,
                                filterState = state.filterState,
                                onClearFilters = { viewModel.clearAllFilters() }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.results, key = { "search_${it.item.id}" }) { resultItem ->
                                    val isSelected = state.selectedItemIds.contains(resultItem.item.id)
                                    val isMultiSelectActive = state.selectedItemIds.isNotEmpty()

                                    SearchResultCard(
                                        result = resultItem,
                                        query = state.query,
                                        caseSensitive = state.filterState.caseSensitive,
                                        isSelected = isSelected,
                                        isMultiSelectMode = isMultiSelectActive,
                                        onToggleSelect = { viewModel.toggleSelection(resultItem.item.id) },
                                        onClick = {
                                            if (isMultiSelectActive) {
                                                viewModel.toggleSelection(resultItem.item.id)
                                            } else {
                                                onOpenFile(resultItem.item)
                                            }
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(resultItem.item) },
                                        onMoreClick = { selectedItemForAction = resultItem.item }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Advanced Filters Modal Bottom Sheet
            if (showFilterSheet) {
                SearchFilterBottomSheet(
                    filterState = state.filterState,
                    folders = state.folders,
                    allTags = state.allTags,
                    onDismiss = { showFilterSheet = false },
                    onApplyFilters = { newFilters ->
                        viewModel.updateFilters { newFilters }
                        showFilterSheet = false
                    },
                    onResetFilters = {
                        viewModel.clearAllFilters()
                        showFilterSheet = false
                    }
                )
            }

            // Action dialog for single item
            selectedItemForAction?.let { item ->
                SearchResultActionMenu(
                    item = item,
                    onDismiss = { selectedItemForAction = null },
                    onOpenFile = {
                        selectedItemForAction = null
                        onOpenFile(item)
                    },
                    onToggleFavorite = {
                        viewModel.toggleFavorite(item)
                        selectedItemForAction = null
                    },
                    onRename = {
                        showRenameDialog = true
                    },
                    onMove = {
                        showMoveDialog = true
                    },
                    onTag = {
                        showTagDialog = true
                    },
                    onArchive = {
                        showArchiveDialog = true
                    },
                    onDelete = {
                        viewModel.moveToTrash(item)
                        selectedItemForAction = null
                        Toast.makeText(context, "Moved to trash", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Single Item Rename Dialog
            if (showRenameDialog && selectedItemForAction != null) {
                val item = selectedItemForAction!!
                var newName by remember { mutableStateOf(item.title) }
                AlertDialog(
                    onDismissRequest = {
                        showRenameDialog = false
                        selectedItemForAction = null
                    },
                    title = { Text("Rename File", color = VaultColors.TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Filename") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultColors.AccentCyan,
                                unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                                focusedTextColor = VaultColors.TextPrimary,
                                unfocusedTextColor = VaultColors.TextPrimary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    viewModel.renameItem(item, newName.trim())
                                    showRenameDialog = false
                                    selectedItemForAction = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Save", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showRenameDialog = false
                            selectedItemForAction = null
                        }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Single Item Move Dialog
            if (showMoveDialog && selectedItemForAction != null) {
                val item = selectedItemForAction!!
                MoveFolderDialog(
                    folders = state.folders,
                    currentFolderId = item.folderId,
                    onDismiss = {
                        showMoveDialog = false
                        selectedItemForAction = null
                    },
                    onFolderSelected = { targetFolderId ->
                        viewModel.moveItem(item, targetFolderId)
                        showMoveDialog = false
                        selectedItemForAction = null
                        Toast.makeText(context, "Moved file", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Single Item Tag Dialog
            if (showTagDialog && selectedItemForAction != null) {
                val item = selectedItemForAction!!
                var tagsInput by remember { mutableStateOf(item.tags) }
                AlertDialog(
                    onDismissRequest = {
                        showTagDialog = false
                        selectedItemForAction = null
                    },
                    title = { Text("Edit Tags", color = VaultColors.TextPrimary) },
                    text = {
                        Column {
                            Text(
                                "Enter comma-separated tags:",
                                fontSize = 12.sp,
                                color = VaultColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = tagsInput,
                                onValueChange = { tagsInput = it },
                                label = { Text("Tags (e.g. Work, Important)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VaultColors.AccentCyan,
                                    unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                                    focusedTextColor = VaultColors.TextPrimary,
                                    unfocusedTextColor = VaultColors.TextPrimary
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.updateItemTags(item, tagsInput.trim())
                                showTagDialog = false
                                selectedItemForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Update", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showTagDialog = false
                            selectedItemForAction = null
                        }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Single Item Create Archive Dialog
            if (showArchiveDialog && selectedItemForAction != null) {
                val item = selectedItemForAction!!
                var archiveTitle by remember { mutableStateOf("${item.title.substringBeforeLast('.')}_archive.zip") }
                AlertDialog(
                    onDismissRequest = {
                        showArchiveDialog = false
                        selectedItemForAction = null
                    },
                    title = { Text("Create Encrypted ZIP", color = VaultColors.TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = archiveTitle,
                            onValueChange = { archiveTitle = it },
                            label = { Text("Archive Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultColors.AccentCyan,
                                unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                                focusedTextColor = VaultColors.TextPrimary,
                                unfocusedTextColor = VaultColors.TextPrimary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (archiveTitle.isNotBlank()) {
                                    viewModel.toggleSelection(item.id)
                                    viewModel.batchArchive(archiveTitle) {
                                        Toast.makeText(context, "Archive created", Toast.LENGTH_SHORT).show()
                                    }
                                    showArchiveDialog = false
                                    selectedItemForAction = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Create", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showArchiveDialog = false
                            selectedItemForAction = null
                        }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Batch Move Dialog
            if (showBatchMoveDialog) {
                MoveFolderDialog(
                    folders = state.folders,
                    currentFolderId = null,
                    onDismiss = { showBatchMoveDialog = false },
                    onFolderSelected = { targetFolderId ->
                        viewModel.batchMove(targetFolderId)
                        showBatchMoveDialog = false
                        Toast.makeText(context, "Moved items", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Batch Tag Dialog
            if (showBatchTagDialog) {
                var newTag by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showBatchTagDialog = false },
                    title = { Text("Add Tag to Selected", color = VaultColors.TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            label = { Text("Tag Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultColors.AccentCyan,
                                unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                                focusedTextColor = VaultColors.TextPrimary,
                                unfocusedTextColor = VaultColors.TextPrimary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTag.isNotBlank()) {
                                    viewModel.batchAddTag(newTag.trim())
                                    showBatchTagDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Apply", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatchTagDialog = false }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Batch Archive Dialog
            if (showBatchArchiveDialog) {
                var archiveTitle by remember { mutableStateOf("selection_archive.zip") }
                AlertDialog(
                    onDismissRequest = { showBatchArchiveDialog = false },
                    title = { Text("Create Encrypted Archive", color = VaultColors.TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = archiveTitle,
                            onValueChange = { archiveTitle = it },
                            label = { Text("Archive Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultColors.AccentCyan,
                                unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                                focusedTextColor = VaultColors.TextPrimary,
                                unfocusedTextColor = VaultColors.TextPrimary
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (archiveTitle.isNotBlank()) {
                                    viewModel.batchArchive(archiveTitle) {
                                        Toast.makeText(context, "Archive created", Toast.LENGTH_SHORT).show()
                                    }
                                    showBatchArchiveDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Create ZIP", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatchArchiveDialog = false }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }
        }
    }
}

// ====================================================================
// Top App Bar & Search Input
// ====================================================================

@Composable
private fun SearchTopBar(
    query: String,
    activeFilterCount: Int,
    caseSensitive: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    onToggleFilterSheet: () -> Unit,
    onToggleCaseSensitivity: () -> Unit,
    onNavigateBack: () -> Unit,
    onLockClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultColors.SurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("search_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = VaultColors.TextPrimary
            )
        }

        // Sleek Search Field
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(VaultColors.SurfaceGraphite)
                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(24.dp))
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search PrivateVault…",
                        color = VaultColors.TextTertiary,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = onClearQuery,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                    tint = VaultColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = VaultColors.TextPrimary,
                    unfocusedTextColor = VaultColors.TextPrimary,
                    cursorColor = VaultColors.AccentCyan
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("universal_search_input")
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Filter button with active count badge
        IconButton(
            onClick = onToggleFilterSheet,
            modifier = Modifier.testTag("search_filter_button")
        ) {
            if (activeFilterCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = VaultColors.AccentCyan,
                            contentColor = VaultColors.Canvas
                        ) {
                            Text(activeFilterCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = VaultColors.AccentCyan
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filters",
                    tint = VaultColors.TextSecondary
                )
            }
        }

        // Quick Lock Button
        IconButton(
            onClick = onLockClick,
            modifier = Modifier.testTag("search_quick_lock_button")
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock Vault",
                tint = VaultColors.AccentCyan
            )
        }
    }
}

// ====================================================================
// Filter Chips Bar
// ====================================================================

@Composable
private fun SearchFilterChipsBar(
    filterState: SearchFilterState,
    allTags: List<String>,
    folders: List<VaultFolderEntity>,
    onCategorySelect: (SearchCategoryFilter) -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleRecent: () -> Unit,
    onToggleContentSearch: () -> Unit,
    onToggleArchiveSearch: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    onClearAllFilters: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultColors.SurfaceElevated)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Clear all if filters active
        if (!filterState.isDefault) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(VaultColors.AccentCrimson.copy(alpha = 0.15f))
                    .border(1.dp, VaultColors.AccentCrimson.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { onClearAllFilters() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Filters",
                        tint = VaultColors.AccentCrimson,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", color = VaultColors.AccentCrimson, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Quick Category Filters
        SearchCategoryFilter.entries.forEach { cat ->
            val isSelected = filterState.category == cat
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelect(cat) },
                label = { Text(cat.displayName, fontSize = 11.sp) },
                leadingIcon = {
                    Icon(cat.getIcon(), contentDescription = null, modifier = Modifier.size(14.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                    selectedLabelColor = VaultColors.AccentCyan,
                    selectedLeadingIconColor = VaultColors.AccentCyan,
                    containerColor = VaultColors.SurfaceGraphite,
                    labelColor = VaultColors.TextSecondary,
                    iconColor = VaultColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                    enabled = true,
                    selected = isSelected
                )
            )
        }

        // Favorites toggle chip
        FilterChip(
            selected = filterState.favoritesOnly,
            onClick = onToggleFavorites,
            label = { Text("Favorites", fontSize = 11.sp) },
            leadingIcon = {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VaultColors.AccentAmber.copy(alpha = 0.2f),
                selectedLabelColor = VaultColors.AccentAmber,
                selectedLeadingIconColor = VaultColors.AccentAmber,
                containerColor = VaultColors.SurfaceGraphite,
                labelColor = VaultColors.TextSecondary,
                iconColor = VaultColors.TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = if (filterState.favoritesOnly) VaultColors.AccentAmber.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                enabled = true,
                selected = filterState.favoritesOnly
            )
        )

        // Content Search toggle chip
        FilterChip(
            selected = filterState.contentSearchEnabled,
            onClick = onToggleContentSearch,
            label = { Text("Content Search", fontSize = 11.sp) },
            leadingIcon = {
                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VaultColors.AccentEmerald.copy(alpha = 0.2f),
                selectedLabelColor = VaultColors.AccentEmerald,
                selectedLeadingIconColor = VaultColors.AccentEmerald,
                containerColor = VaultColors.SurfaceGraphite,
                labelColor = VaultColors.TextSecondary,
                iconColor = VaultColors.TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = if (filterState.contentSearchEnabled) VaultColors.AccentEmerald.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                enabled = true,
                selected = filterState.contentSearchEnabled
            )
        )

        // Archive Search toggle chip
        FilterChip(
            selected = filterState.archiveSearchEnabled,
            onClick = onToggleArchiveSearch,
            label = { Text("ZIP Contents", fontSize = 11.sp) },
            leadingIcon = {
                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                selectedLabelColor = VaultColors.AccentCyan,
                selectedLeadingIconColor = VaultColors.AccentCyan,
                containerColor = VaultColors.SurfaceGraphite,
                labelColor = VaultColors.TextSecondary,
                iconColor = VaultColors.TextSecondary
            ),
            border = FilterChipDefaults.filterChipBorder(
                borderColor = if (filterState.archiveSearchEnabled) VaultColors.AccentCyan.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                enabled = true,
                selected = filterState.archiveSearchEnabled
            )
        )
    }
}

// ====================================================================
// Content Search Progress Bar
// ====================================================================

@Composable
private fun ContentSearchProgressBar(
    progress: Pair<Int, Int>?,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultColors.SurfaceGraphite)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val text = if (progress != null) {
                    "Searching memory text files (${progress.first} / ${progress.second})…"
                } else {
                    "Searching in-memory candidate text files…"
                }
                Text(text, color = VaultColors.AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                val fraction = if (progress != null && progress.second > 0) {
                    progress.first.toFloat() / progress.second.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    color = VaultColors.AccentEmerald,
                    trackColor = VaultColors.SurfaceOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Cancel", color = VaultColors.AccentCrimson, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ====================================================================
// Idle State Dashboard (Recent searches, tags, quick folders)
// ====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleSearchDashboard(
    recentSearches: List<String>,
    allTags: List<String>,
    folders: List<VaultFolderEntity>,
    onSelectSearch: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentHistory: () -> Unit,
    onSelectTag: (String) -> Unit,
    onSelectFolder: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Recent Searches
        if (recentSearches.isNotEmpty()) {
            item {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recent Searches", color = VaultColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = onClearRecentHistory,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Clear All", color = VaultColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recentSearches.forEach { term ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(VaultColors.SurfaceElevated)
                                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(16.dp))
                                    .clickable { onSelectSearch(term) }
                                    .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(term, color = VaultColors.TextPrimary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = VaultColors.TextTertiary,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onRemoveRecentSearch(term) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tags Discovery
        if (allTags.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Label, contentDescription = null, tint = VaultColors.AccentAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search by Tag", color = VaultColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allTags.forEach { tag ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(VaultColors.SurfaceElevated)
                                    .border(1.dp, VaultColors.AccentAmber.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .clickable { onSelectTag(tag) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("#$tag", color = VaultColors.AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // Folders Quick Discovery
        if (folders.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Vault Folders", color = VaultColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        folders.forEach { folder ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(VaultColors.SurfaceElevated)
                                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(16.dp))
                                    .clickable { onSelectFolder(folder.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(folder.name, color = VaultColors.TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// Suggestions Pill Row
// ====================================================================

@Composable
private fun SuggestionsRow(
    suggestions: List<SearchSuggestionItem>,
    onSelectSuggestion: (SearchSuggestionItem) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        suggestions.forEach { item ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(VaultColors.SurfaceElevated)
                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(14.dp))
                    .clickable { onSelectSuggestion(item) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (item.type) {
                        SuggestionType.RECENT_QUERY -> Icons.Default.History
                        SuggestionType.TAG -> Icons.Default.Label
                        SuggestionType.FOLDER -> Icons.Default.Folder
                        SuggestionType.EXTENSION -> Icons.Default.TextFields
                        SuggestionType.FILENAME -> Icons.Default.Search
                    }
                    Icon(icon, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(item.text, color = VaultColors.TextPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ====================================================================
// Results Header & Sort Dropdown
// ====================================================================

@Composable
private fun SearchResultsHeader(
    resultCount: Int,
    currentSort: SearchSortOption,
    onSortSelected: (SearchSortOption) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Found $resultCount item${if (resultCount != 1) "s" else ""}",
            color = VaultColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSortMenu = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Sort",
                    tint = VaultColors.AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = currentSort.displayName,
                    color = VaultColors.AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier.background(VaultColors.SurfaceElevated)
            ) {
                SearchSortOption.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sort == currentSort) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(sort.displayName, color = VaultColors.TextPrimary, fontSize = 13.sp)
                            }
                        },
                        onClick = {
                            onSortSelected(sort)
                            showSortMenu = false
                        }
                    )
                }
            }
        }
    }
}

// ====================================================================
// Result Card with Highlighting
// ====================================================================

@Composable
private fun SearchResultCard(
    result: SearchResultItem,
    query: String,
    caseSensitive: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit
) {
    val item = result.item

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.12f) else VaultColors.SurfaceElevated
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .testTag("search_result_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Checkbox if multi-select mode
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VaultColors.AccentCyan,
                            checkmarkColor = VaultColors.Canvas
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Category Icon
                val categoryColor = when (item.category) {
                    VaultCategory.DOCUMENT.name -> VaultColors.AccentCyan
                    VaultCategory.IMAGE.name -> VaultColors.AccentAmber
                    VaultCategory.VIDEO.name -> VaultColors.AccentCrimson
                    VaultCategory.AUDIO.name -> VaultColors.AccentEmerald
                    VaultCategory.ZIP.name -> Color(0xFFA855F7)
                    VaultCategory.CODE.name -> Color(0xFF38BDF8)
                    VaultCategory.TEXT.name -> VaultColors.Platinum
                    else -> VaultColors.TextSecondary
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                ) {
                    val icon = when (item.category) {
                        VaultCategory.IMAGE.name -> Icons.Default.Image
                        VaultCategory.VIDEO.name -> Icons.Default.VideoFile
                        VaultCategory.AUDIO.name -> Icons.Default.AudioFile
                        VaultCategory.ZIP.name -> Icons.Default.Archive
                        VaultCategory.CODE.name -> Icons.Default.Code
                        VaultCategory.TEXT.name -> Icons.Default.NoteAdd
                        else -> Icons.Default.Description
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildHighlightedText(item.title, query, caseSensitive),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatFileSize(item.sizeBytes),
                            fontSize = 11.sp,
                            color = VaultColors.TextSecondary
                        )

                        Text("•", fontSize = 11.sp, color = VaultColors.TextTertiary)

                        Text(
                            text = formatDate(item.modifiedAt),
                            fontSize = 11.sp,
                            color = VaultColors.TextSecondary
                        )

                        if (!result.folderName.isNullOrBlank()) {
                            Text("•", fontSize = 11.sp, color = VaultColors.TextTertiary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = result.folderName,
                                    fontSize = 11.sp,
                                    color = VaultColors.AccentCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Actions: Favorite & Overflow
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (item.isFavorite) "Unstar" else "Star",
                        tint = if (item.isFavorite) VaultColors.AccentAmber else VaultColors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = VaultColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tags Row
            if (item.tags.isNotBlank()) {
                val tagsList = item.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        tagsList.forEach { tag ->
                            val matchesQuery = query.isNotBlank() && (
                                if (caseSensitive) tag.contains(query) else tag.contains(query, ignoreCase = true)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (matchesQuery) VaultColors.AccentAmber.copy(alpha = 0.25f)
                                        else VaultColors.SurfaceHighlight
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    color = if (matchesQuery) VaultColors.AccentAmber else VaultColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (matchesQuery) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Content Snippet if content search matched
            result.contentSnippet?.let { snippet ->
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(VaultColors.Canvas.copy(alpha = 0.6f))
                        .border(1.dp, VaultColors.AccentEmerald.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Line ${snippet.lineIndex}:",
                                color = VaultColors.AccentEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "(${snippet.matchCount} match${if (snippet.matchCount > 1) "es" else ""})",
                                color = VaultColors.TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = buildHighlightedText(snippet.snippet, query, caseSensitive),
                            color = VaultColors.Platinum,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Archive Entries matches if matched inside ZIP
            if (result.matchedArchiveEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(VaultColors.Canvas.copy(alpha = 0.6f))
                        .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column {
                        Text(
                            "Inside ZIP archive:",
                            color = VaultColors.AccentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        result.matchedArchiveEntries.take(2).forEach { entry ->
                            Text(
                                text = "• $entry",
                                color = VaultColors.Platinum,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// Empty State
// ====================================================================

@Composable
private fun SearchEmptyState(
    query: String,
    filterState: SearchFilterState,
    onClearFilters: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VaultColors.SurfaceElevated)
                    .border(1.dp, VaultColors.GlassBorderSubtle, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = VaultColors.TextTertiary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No matching files",
                color = VaultColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (query.isNotBlank()) {
                    "No files found matching \"$query\". Check spelling or adjust active filters."
                } else {
                    "No files match the currently selected filter combination."
                },
                color = VaultColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!filterState.isDefault) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onClearFilters,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan)
                ) {
                    Text("Clear All Filters")
                }
            }
        }
    }
}

// ====================================================================
// Vault Locked View (Guarantees zero search data leakage)
// ====================================================================

@Composable
private fun VaultLockedSearchView(
    onAuthenticate: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = VaultColors.Canvas,
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VaultColors.TextPrimary
                    )
                }
                Text("Universal Search", color = VaultColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(VaultColors.SurfaceElevated)
                        .border(1.dp, VaultColors.AccentAmber.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = VaultColors.AccentAmber,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Vault Locked",
                    color = VaultColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Authenticate to search PrivateVault.",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onAuthenticate,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                ) {
                    Text("Unlock Vault", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ====================================================================
// Batch Selection Bottom Bar
// ====================================================================

@Composable
private fun SearchBatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchMove: () -> Unit,
    onBatchFavorite: () -> Unit,
    onBatchTag: () -> Unit,
    onBatchArchive: () -> Unit,
    onBatchDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VaultColors.GlassBorderFocus, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = VaultColors.TextSecondary)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "$selectedCount selected",
                    color = VaultColors.AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Move button
                IconButton(onClick = onBatchMove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = "Move to folder", tint = VaultColors.TextPrimary)
                }
                // Tag button
                IconButton(onClick = onBatchTag, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Label, contentDescription = "Add Tag", tint = VaultColors.AccentAmber)
                }
                // Favorite button
                IconButton(onClick = onBatchFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Star, contentDescription = "Star", tint = VaultColors.AccentAmber)
                }
                // Archive button
                IconButton(onClick = onBatchArchive, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Create ZIP", tint = VaultColors.AccentCyan)
                }
                // Trash button
                IconButton(onClick = onBatchDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Move to Trash", tint = VaultColors.AccentCrimson)
                }
            }
        }
    }
}

// ====================================================================
// Action Menu for Individual Search Result
// ====================================================================

@Composable
private fun SearchResultActionMenu(
    item: VaultItemEntity,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onTag: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(VaultColors.SurfaceElevated)
    ) {
        DropdownMenuItem(
            text = { Text("Open File", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VaultColors.AccentCyan) },
            onClick = onOpenFile
        )
        DropdownMenuItem(
            text = { Text(if (item.isFavorite) "Remove from Favorites" else "Add to Favorites", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = VaultColors.AccentAmber) },
            onClick = onToggleFavorite
        )
        DropdownMenuItem(
            text = { Text("Edit Tags", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, tint = VaultColors.AccentAmber) },
            onClick = onTag
        )
        DropdownMenuItem(
            text = { Text("Move to Folder", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = VaultColors.TextPrimary) },
            onClick = onMove
        )
        DropdownMenuItem(
            text = { Text("Rename", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = VaultColors.TextPrimary) },
            onClick = onRename
        )
        DropdownMenuItem(
            text = { Text("Create ZIP Archive", color = VaultColors.TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, tint = VaultColors.AccentCyan) },
            onClick = onArchive
        )
        DropdownMenuItem(
            text = { Text("Move to Trash", color = VaultColors.AccentCrimson) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VaultColors.AccentCrimson) },
            onClick = onDelete
        )
    }
}

// ====================================================================
// Move Folder Dialog
// ====================================================================

@Composable
private fun MoveFolderDialog(
    folders: List<VaultFolderEntity>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onFolderSelected: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Destination Folder", color = VaultColors.TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.height(240.dp)) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFolderSelected(null) }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Root (No Folder)",
                            color = if (currentFolderId == null) VaultColors.AccentCyan else VaultColors.TextPrimary,
                            fontWeight = if (currentFolderId == null) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                items(folders) { folder ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFolderSelected(folder.id) }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            folder.name,
                            color = if (currentFolderId == folder.id) VaultColors.AccentCyan else VaultColors.TextPrimary,
                            fontWeight = if (currentFolderId == folder.id) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        },
        containerColor = VaultColors.SurfaceElevated
    )
}

// ====================================================================
// Deep Filter Bottom Sheet
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterBottomSheet(
    filterState: SearchFilterState,
    folders: List<VaultFolderEntity>,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onApplyFilters: (SearchFilterState) -> Unit,
    onResetFilters: () -> Unit
) {
    var draftState by remember { mutableStateOf(filterState) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VaultColors.SurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Search Filters", color = VaultColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    draftState = SearchFilterState()
                    onResetFilters()
                }) {
                    Text("Reset All", color = VaultColors.AccentCrimson, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(380.dp)
            ) {
                // Date Filter Section
                item {
                    Text("Date Modified", color = VaultColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DateFilterOption.entries.forEach { opt ->
                            FilterChip(
                                selected = draftState.dateFilter == opt,
                                onClick = { draftState = draftState.copy(dateFilter = opt) },
                                label = { Text(opt.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // File Size Filter Section
                item {
                    Text("File Size", color = VaultColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FileSizeFilterOption.entries.forEach { opt ->
                            FilterChip(
                                selected = draftState.sizeFilter == opt,
                                onClick = { draftState = draftState.copy(sizeFilter = opt) },
                                label = { Text(opt.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Folder Filter
                if (folders.isNotEmpty()) {
                    item {
                        Text("Limit to Folder", color = VaultColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = draftState.folderId == null,
                                onClick = { draftState = draftState.copy(folderId = null) },
                                label = { Text("All Folders", fontSize = 11.sp) }
                            )
                            folders.forEach { folder ->
                                FilterChip(
                                    selected = draftState.folderId == folder.id,
                                    onClick = { draftState = draftState.copy(folderId = folder.id) },
                                    label = { Text(folder.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Tag Filter
                if (allTags.isNotEmpty()) {
                    item {
                        Text("Filter by Tag", color = VaultColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = draftState.tag == null,
                                onClick = { draftState = draftState.copy(tag = null) },
                                label = { Text("Any Tag", fontSize = 11.sp) }
                            )
                            allTags.forEach { tag ->
                                FilterChip(
                                    selected = draftState.tag == tag,
                                    onClick = { draftState = draftState.copy(tag = tag) },
                                    label = { Text("#$tag", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Extension input
                item {
                    Text("File Extension", color = VaultColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = draftState.extension ?: "",
                        onValueChange = { draftState = draftState.copy(extension = it.ifBlank { null }) },
                        placeholder = { Text("e.g. pdf, kt, zip, png", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultColors.AccentCyan,
                            unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                            focusedTextColor = VaultColors.TextPrimary,
                            unfocusedTextColor = VaultColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onApplyFilters(draftState) },
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Filters", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ====================================================================
// Text Highlighting Helper
// ====================================================================

private fun buildHighlightedText(text: String, query: String, caseSensitive: Boolean) = buildAnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }

    var startIndex = 0
    while (startIndex < text.length) {
        val foundIndex = text.indexOf(q, startIndex, ignoreCase = !caseSensitive)
        if (foundIndex == -1) {
            append(text.substring(startIndex))
            break
        }
        if (foundIndex > startIndex) {
            append(text.substring(startIndex, foundIndex))
        }
        withStyle(
            SpanStyle(
                background = VaultColors.AccentCyan.copy(alpha = 0.35f),
                color = VaultColors.AccentCyan,
                fontWeight = FontWeight.Bold
            )
        ) {
            append(text.substring(foundIndex, foundIndex + q.length))
        }
        startIndex = foundIndex + q.length
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}
