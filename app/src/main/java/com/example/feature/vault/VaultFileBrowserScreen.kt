@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.feature.vault

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.designsystem.VaultColors
import com.example.core.storage.SecureThumbnailProvider
import com.example.core.storage.VaultCategory
import com.example.feature.viewer.VaultFileInfoSheet
import com.example.feature.viewer.formatBytes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultFileBrowserScreen(
    viewModel: VaultFileBrowserViewModel,
    onOpenFile: (itemId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Dialog & Sheet states
    var itemForInfo by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemForRename by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemForMove by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemForTags by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemForExport by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemForDelete by remember { mutableStateOf<VaultItemEntity?>(null) }

    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchTagDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    var folderForRename by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var folderForDelete by remember { mutableStateOf<VaultFolderEntity?>(null) }

    // All folders list for Move dialogs
    var allFoldersList by remember { mutableStateOf<List<VaultFolderEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        allFoldersList = viewModel.repository.getAllFoldersList()
    }

    // Export Document Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(itemForExport?.mimeType ?: "*/*")
    ) { uri: Uri? ->
        if (uri != null && itemForExport != null) {
            val target = itemForExport!!
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val res = viewModel.repository.exportItemToStream(target, os)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    itemForExport = null
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VaultColors.Canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header / Breadcrumbs Bar
            BrowserTopBar(
                uiState = uiState,
                onNavigateToFolder = { folderId -> viewModel.navigateToFolder(folderId) },
                onToggleViewMode = { viewModel.toggleViewMode() },
                onToggleFilterSheet = { viewModel.toggleFilterSheet(true) },
                onEnterSelectMode = { viewModel.enterMultiSelectMode() },
                onSelectAll = { viewModel.selectAll() },
                onClearSelection = { viewModel.clearSelection() },
                onCreateFolderClick = { showCreateFolderDialog = true }
            )

            // Search Bar
            SearchBarRow(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                activeFilterCount = countActiveFilters(uiState),
                onFilterClick = { viewModel.toggleFilterSheet(true) }
            )

            // Content Area (Folders + Files)
            if (uiState.folders.isEmpty() && uiState.items.isEmpty()) {
                BrowserEmptyState(
                    searchQuery = uiState.searchQuery,
                    onCreateFolder = { showCreateFolderDialog = true }
                )
            } else {
                if (uiState.viewMode == ViewMode.GRID) {
                    BrowserGridView(
                        folders = uiState.folders,
                        items = uiState.items,
                        isMultiSelectMode = uiState.isMultiSelectMode,
                        selectedItemIds = uiState.selectedItemIds,
                        storageManager = viewModel.repository.storageManager,
                        onFolderClick = { folder -> viewModel.navigateToFolder(folder.id) },
                        onFolderRename = { folder -> folderForRename = folder },
                        onFolderDelete = { folder -> folderForDelete = folder },
                        onItemClick = { item ->
                            if (uiState.isMultiSelectMode) {
                                viewModel.toggleItemSelection(item.id)
                            } else {
                                onOpenFile(item.id)
                            }
                        },
                        onItemLongClick = { item ->
                            viewModel.enterMultiSelectMode(item.id)
                        },
                        onItemAction = { action, item ->
                            handleItemAction(
                                action = action,
                                item = item,
                                onInfo = { itemForInfo = item },
                                onRename = { itemForRename = item },
                                onMove = { itemForMove = item },
                                onCopy = {
                                    scope.launch {
                                        val res = viewModel.repository.copyItem(item)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Copy created", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onFavorite = {
                                    scope.launch { viewModel.repository.toggleFavorite(item) }
                                },
                                onTags = { itemForTags = item },
                                onExport = { itemForExport = item },
                                onDelete = { itemForDelete = item }
                            )
                        }
                    )
                } else {
                    BrowserListView(
                        folders = uiState.folders,
                        items = uiState.items,
                        isMultiSelectMode = uiState.isMultiSelectMode,
                        selectedItemIds = uiState.selectedItemIds,
                        onFolderClick = { folder -> viewModel.navigateToFolder(folder.id) },
                        onFolderRename = { folder -> folderForRename = folder },
                        onFolderDelete = { folder -> folderForDelete = folder },
                        onItemClick = { item ->
                            if (uiState.isMultiSelectMode) {
                                viewModel.toggleItemSelection(item.id)
                            } else {
                                onOpenFile(item.id)
                            }
                        },
                        onItemLongClick = { item ->
                            viewModel.enterMultiSelectMode(item.id)
                        },
                        onItemAction = { action, item ->
                            handleItemAction(
                                action = action,
                                item = item,
                                onInfo = { itemForInfo = item },
                                onRename = { itemForRename = item },
                                onMove = { itemForMove = item },
                                onCopy = {
                                    scope.launch {
                                        val res = viewModel.repository.copyItem(item)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Copy created", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onFavorite = {
                                    scope.launch { viewModel.repository.toggleFavorite(item) }
                                },
                                onTags = { itemForTags = item },
                                onExport = { itemForExport = item },
                                onDelete = { itemForDelete = item }
                            )
                        }
                    )
                }
            }
        }

        // Multi-Select Floating Action Bar at Bottom
        AnimatedVisibility(
            visible = uiState.isMultiSelectMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BatchActionBar(
                selectedCount = uiState.selectedItemIds.size,
                onMove = { showBatchMoveDialog = true },
                onFavorite = { viewModel.batchFavorite(true) },
                onTag = { showBatchTagDialog = true },
                onDelete = { showBatchDeleteDialog = true },
                onCancel = { viewModel.clearSelection() }
            )
        }
    }

    // Filter Bottom Sheet
    if (uiState.isFilterSheetOpen) {
        FilterBottomSheet(
            uiState = uiState,
            onDismiss = { viewModel.toggleFilterSheet(false) },
            onSelectCategory = { viewModel.setSelectedCategory(it) },
            onSelectSize = { viewModel.setSizeFilter(it) },
            onSelectDate = { viewModel.setDateFilter(it) },
            onSelectStatus = { viewModel.setStatusFilter(it) },
            onSelectSort = { viewModel.setSortOption(it) },
            onReset = { viewModel.resetFilters() }
        )
    }

    // Modal dialogs
    itemForInfo?.let { itm ->
        VaultFileInfoSheet(
            item = itm,
            folderName = allFoldersList.find { it.id == itm.folderId }?.name ?: "Vault Root",
            onDismiss = { itemForInfo = null }
        )
    }

    itemForRename?.let { itm ->
        RenameDialog(
            currentName = itm.title,
            onDismiss = { itemForRename = null },
            onConfirm = { newName ->
                scope.launch {
                    viewModel.repository.renameItem(itm, newName)
                    itemForRename = null
                }
            }
        )
    }

    itemForMove?.let { itm ->
        MoveToFolderDialog(
            folders = allFoldersList,
            currentFolderId = itm.folderId,
            onDismiss = { itemForMove = null },
            onFolderSelected = { targetFolderId ->
                scope.launch {
                    viewModel.repository.moveItemToFolder(itm, targetFolderId)
                    itemForMove = null
                    Toast.makeText(context, "Item moved", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    itemForTags?.let { itm ->
        EditTagsDialog(
            currentTags = itm.tags,
            onDismiss = { itemForTags = null },
            onSave = { newTags ->
                scope.launch {
                    viewModel.repository.updateItemTags(itm, newTags)
                    itemForTags = null
                }
            }
        )
    }

    itemForExport?.let { itm ->
        ExportConfirmationDialog(
            fileName = itm.title,
            onDismiss = { itemForExport = null },
            onConfirm = {
                exportLauncher.launch(itm.title)
            }
        )
    }

    itemForDelete?.let { itm ->
        DeleteConfirmationDialog(
            itemCount = 1,
            isPermanent = false,
            onDismiss = { itemForDelete = null },
            onConfirm = {
                scope.launch {
                    viewModel.repository.moveToTrash(itm)
                    itemForDelete = null
                    Toast.makeText(context, "Moved to Encrypted Trash", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Folder Actions Dialogs
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                viewModel.createFolder(name)
            }
        )
    }

    folderForRename?.let { folder ->
        RenameDialog(
            currentName = folder.name,
            onDismiss = { folderForRename = null },
            onConfirm = { newName ->
                scope.launch {
                    viewModel.repository.renameFolder(folder, newName)
                    folderForRename = null
                }
            }
        )
    }

    folderForDelete?.let { folder ->
        DeleteConfirmationDialog(
            itemCount = 1,
            isPermanent = false,
            onDismiss = { folderForDelete = null },
            onConfirm = {
                scope.launch {
                    viewModel.repository.deleteFolder(folder)
                    folderForDelete = null
                    Toast.makeText(context, "Folder deleted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Batch Dialogs
    if (showBatchMoveDialog) {
        MoveToFolderDialog(
            folders = allFoldersList,
            currentFolderId = uiState.currentFolderId,
            onDismiss = { showBatchMoveDialog = false },
            onFolderSelected = { targetId ->
                showBatchMoveDialog = false
                viewModel.batchMove(targetId) {
                    Toast.makeText(context, "Items moved successfully", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        DeleteConfirmationDialog(
            itemCount = uiState.selectedItemIds.size,
            isPermanent = false,
            onDismiss = { showBatchDeleteDialog = false },
            onConfirm = {
                showBatchDeleteDialog = false
                viewModel.batchDelete {
                    Toast.makeText(context, "Items moved to Encrypted Trash", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showBatchTagDialog) {
        EditTagsDialog(
            currentTags = "",
            onDismiss = { showBatchTagDialog = false },
            onSave = { tag ->
                showBatchTagDialog = false
                viewModel.batchAddTag(tag)
                Toast.makeText(context, "Tags applied", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun countActiveFilters(uiState: FileBrowserUiState): Int {
    var count = 0
    if (uiState.selectedCategory != null) count++
    if (uiState.sizeFilter != SizeFilter.ALL) count++
    if (uiState.dateFilter != DateFilter.ALL) count++
    if (uiState.statusFilter != StatusFilter.ALL) count++
    return count
}

enum class ItemAction {
    INFO, RENAME, MOVE, COPY, FAVORITE, TAGS, EXPORT, DELETE
}

private fun handleItemAction(
    action: ItemAction,
    item: VaultItemEntity,
    onInfo: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onFavorite: () -> Unit,
    onTags: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    when (action) {
        ItemAction.INFO -> onInfo()
        ItemAction.RENAME -> onRename()
        ItemAction.MOVE -> onMove()
        ItemAction.COPY -> onCopy()
        ItemAction.FAVORITE -> onFavorite()
        ItemAction.TAGS -> onTags()
        ItemAction.EXPORT -> onExport()
        ItemAction.DELETE -> onDelete()
    }
}

@Composable
private fun BrowserTopBar(
    uiState: FileBrowserUiState,
    onNavigateToFolder: (folderId: Long?) -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleFilterSheet: () -> Unit,
    onEnterSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    if (uiState.isMultiSelectMode) {
        // Multi-select header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(VaultColors.SurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = VaultColors.TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${uiState.selectedItemIds.size} Selected",
                    color = VaultColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSelectAll) {
                    Text("Select All", color = VaultColors.AccentCyan, fontSize = 13.sp)
                }
            }
        }
    } else {
        // Standard header with Breadcrumbs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(VaultColors.SurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Breadcrumbs trail
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Root",
                    color = if (uiState.currentFolderId == null) VaultColors.AccentCyan else VaultColors.TextSecondary,
                    fontWeight = if (uiState.currentFolderId == null) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable { onNavigateToFolder(null) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )

                uiState.breadcrumbs.forEach { folder ->
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = VaultColors.TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    val isLast = folder.id == uiState.currentFolderId
                    Text(
                        text = folder.name,
                        color = if (isLast) VaultColors.AccentCyan else VaultColors.TextSecondary,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .combinedClickable { onNavigateToFolder(folder.id) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Quick actions: New folder, View mode toggle, Select mode
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCreateFolderClick) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New folder",
                        tint = VaultColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onToggleViewMode, modifier = Modifier.testTag("toggle_view_mode_button")) {
                    Icon(
                        imageVector = if (uiState.viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View Mode",
                        tint = VaultColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onEnterSelectMode) {
                    Icon(
                        imageVector = Icons.Default.CheckBox,
                        contentDescription = "Select items",
                        tint = VaultColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilterCount: Int,
    onFilterClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search encrypted items...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = VaultColors.TextSecondary, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = VaultColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = VaultColors.TextPrimary,
                unfocusedTextColor = VaultColors.TextPrimary,
                focusedBorderColor = VaultColors.AccentCyan,
                unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                focusedContainerColor = VaultColors.SurfaceGraphite,
                unfocusedContainerColor = VaultColors.SurfaceGraphite
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("file_browser_search_input")
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Filter button with badge
        Box {
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeFilterCount > 0) VaultColors.SurfaceHighlight else VaultColors.SurfaceGraphite)
                    .border(1.dp, if (activeFilterCount > 0) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
                    .testTag("browser_filter_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter & Sort",
                    tint = if (activeFilterCount > 0) VaultColors.AccentCyan else VaultColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (activeFilterCount > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(VaultColors.AccentCyan)
                        .align(Alignment.TopEnd)
                ) {
                    Text(
                        text = activeFilterCount.toString(),
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserGridView(
    folders: List<VaultFolderEntity>,
    items: List<VaultItemEntity>,
    isMultiSelectMode: Boolean,
    selectedItemIds: Set<Long>,
    storageManager: com.example.core.storage.VaultStorageManager,
    onFolderClick: (VaultFolderEntity) -> Unit,
    onFolderRename: (VaultFolderEntity) -> Unit,
    onFolderDelete: (VaultFolderEntity) -> Unit,
    onItemClick: (VaultItemEntity) -> Unit,
    onItemLongClick: (VaultItemEntity) -> Unit,
    onItemAction: (ItemAction, VaultItemEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Folders first
        items(folders, key = { "folder_${it.id}" }) { folder ->
            FolderGridCard(
                folder = folder,
                onClick = { onFolderClick(folder) },
                onRename = { onFolderRename(folder) },
                onDelete = { onFolderDelete(folder) }
            )
        }

        // Vault items
        items(items, key = { "item_${it.id}" }) { item ->
            val isSelected = selectedItemIds.contains(item.id)
            FileGridCard(
                item = item,
                isSelected = isSelected,
                isMultiSelectMode = isMultiSelectMode,
                storageManager = storageManager,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
                onAction = { action -> onItemAction(action, item) }
            )
        }
    }
}

@Composable
private fun FolderGridCard(
    folder: VaultFolderEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VaultColors.SurfaceElevated)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = VaultColors.AccentAmber,
                    modifier = Modifier.size(28.dp)
                )

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Folder options", tint = VaultColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(VaultColors.SurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = VaultColors.TextPrimary) },
                            text = { Text("Rename Folder", color = VaultColors.TextPrimary) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = VaultColors.AccentCrimson) },
                            text = { Text("Delete Folder", color = VaultColors.AccentCrimson) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = folder.name,
                color = VaultColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Encrypted Directory",
                color = VaultColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    item: VaultItemEntity,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    storageManager: com.example.core.storage.VaultStorageManager,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (ItemAction) -> Unit
) {
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    // Lazy load thumbnail if image
    val isImage = item.category == VaultCategory.IMAGE.name
    LaunchedEffect(item.id) {
        if (isImage) {
            thumbnail = SecureThumbnailProvider.loadThumbnail(item.encryptedPath, storageManager)
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VaultColors.SurfaceElevated)
            .border(
                1.dp,
                if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
            .testTag("file_card_${item.id}")
    ) {
        Column {
            // Preview thumbnail or Icon Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VaultColors.SurfaceGraphite)
            ) {
                if (isImage && thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = getCategoryIcon(item.category),
                        contentDescription = null,
                        tint = getCategoryColor(item.category),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Multi-select checkbox overlay
                if (isMultiSelectMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VaultColors.AccentCyan,
                                uncheckedColor = VaultColors.TextSecondary
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Favorite star indicator
                if (item.isFavorite && !isMultiSelectMode) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorited",
                        tint = VaultColors.AccentAmber,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title and menu row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.title,
                    color = VaultColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = VaultColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    ItemDropdownMenu(
                        expanded = showMenu,
                        item = item,
                        onDismiss = { showMenu = false },
                        onAction = { act ->
                            showMenu = false
                            onAction(act)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Metadata row (Size)
            Text(
                text = formatBytes(item.sizeBytes),
                color = VaultColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BrowserListView(
    folders: List<VaultFolderEntity>,
    items: List<VaultItemEntity>,
    isMultiSelectMode: Boolean,
    selectedItemIds: Set<Long>,
    onFolderClick: (VaultFolderEntity) -> Unit,
    onFolderRename: (VaultFolderEntity) -> Unit,
    onFolderDelete: (VaultFolderEntity) -> Unit,
    onItemClick: (VaultItemEntity) -> Unit,
    onItemLongClick: (VaultItemEntity) -> Unit,
    onItemAction: (ItemAction, VaultItemEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Folders first
        items(folders, key = { "folder_list_${it.id}" }) { folder ->
            var showMenu by remember { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VaultColors.SurfaceElevated)
                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
                    .combinedClickable(onClick = { onFolderClick(folder) })
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = VaultColors.AccentAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        color = VaultColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Encrypted Directory", color = VaultColors.TextTertiary, fontSize = 11.sp)
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Folder options", tint = VaultColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(VaultColors.SurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = VaultColors.TextPrimary) },
                            text = { Text("Rename", color = VaultColors.TextPrimary) },
                            onClick = {
                                showMenu = false
                                onFolderRename(folder)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = VaultColors.AccentCrimson) },
                            text = { Text("Delete", color = VaultColors.AccentCrimson) },
                            onClick = {
                                showMenu = false
                                onFolderDelete(folder)
                            }
                        )
                    }
                }
            }
        }

        // Vault items
        items(items, key = { "item_list_${it.id}" }) { item ->
            val isSelected = selectedItemIds.contains(item.id)
            var showMenu by remember { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VaultColors.SurfaceElevated)
                    .border(
                        1.dp,
                        if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                        RoundedCornerShape(8.dp)
                    )
                    .combinedClickable(
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) }
                    )
                    .padding(12.dp)
                    .testTag("file_row_${item.id}")
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onItemClick(item) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VaultColors.AccentCyan,
                            uncheckedColor = VaultColors.TextSecondary
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(VaultColors.SurfaceGraphite)
                ) {
                    Icon(
                        imageVector = getCategoryIcon(item.category),
                        contentDescription = null,
                        tint = getCategoryColor(item.category),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = VaultColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatBytes(item.sizeBytes)} · ${dateFormat.format(Date(item.modifiedAt))}",
                        color = VaultColors.TextTertiary,
                        fontSize = 11.sp
                    )
                }

                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorited",
                        tint = VaultColors.AccentAmber,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = VaultColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    ItemDropdownMenu(
                        expanded = showMenu,
                        item = item,
                        onDismiss = { showMenu = false },
                        onAction = { act ->
                            showMenu = false
                            onItemAction(act, item)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemDropdownMenu(
    expanded: Boolean,
    item: VaultItemEntity,
    onDismiss: () -> Unit,
    onAction: (ItemAction) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(VaultColors.SurfaceElevated)
    ) {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Info, null, tint = VaultColors.AccentCyan) },
            text = { Text("Details", color = VaultColors.TextPrimary) },
            onClick = { onAction(ItemAction.INFO) }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Edit, null, tint = VaultColors.TextPrimary) },
            text = { Text("Rename", color = VaultColors.TextPrimary) },
            onClick = { onAction(ItemAction.RENAME) }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.DriveFileMove, null, tint = VaultColors.TextPrimary) },
            text = { Text("Move to Folder", color = VaultColors.TextPrimary) },
            onClick = { onAction(ItemAction.MOVE) }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = VaultColors.TextPrimary) },
            text = { Text("Make a Copy", color = VaultColors.TextPrimary) },
            onClick = { onAction(ItemAction.COPY) }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Default.StarBorder else Icons.Default.Star,
                    contentDescription = null,
                    tint = VaultColors.AccentAmber
                )
            },
            text = { Text(if (item.isFavorite) "Unfavorite" else "Favorite", color = VaultColors.TextPrimary) },
            onClick = { onAction(ItemAction.FAVORITE) }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Label, null, tint = VaultColors.TextPrimary) },
            text = { Text("Edit Tags", color = VaultColors.TextPrimary) },
            onClick = { onAction(ItemAction.TAGS) }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.UploadFile, null, tint = VaultColors.AccentAmber) },
            text = { Text("Export Plaintext", color = VaultColors.AccentAmber) },
            onClick = { onAction(ItemAction.EXPORT) }
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = VaultColors.AccentCrimson) },
            text = { Text("Move to Trash", color = VaultColors.AccentCrimson) },
            onClick = { onAction(ItemAction.DELETE) }
        )
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onFavorite: () -> Unit,
    onTag: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = VaultColors.SurfaceOverlay,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BatchActionButton(icon = Icons.Default.DriveFileMove, label = "Move", onClick = onMove)
            BatchActionButton(icon = Icons.Default.Star, label = "Favorite", onClick = onFavorite)
            BatchActionButton(icon = Icons.Default.Label, label = "Tag", onClick = onTag)
            BatchActionButton(icon = Icons.Default.Delete, label = "Trash", tint = VaultColors.AccentCrimson, onClick = onDelete)
        }
    }
}

@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = VaultColors.TextPrimary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    uiState: FileBrowserUiState,
    onDismiss: () -> Unit,
    onSelectCategory: (VaultCategory?) -> Unit,
    onSelectSize: (SizeFilter) -> Unit,
    onSelectDate: (DateFilter) -> Unit,
    onSelectStatus: (StatusFilter) -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onReset: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = VaultColors.SurfaceElevated,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Filter & Sort Workspace",
                    color = VaultColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onReset) {
                    Text("Reset All", color = VaultColors.AccentCyan, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sort Section
            Text("Sort By", color = VaultColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SortOption.entries.forEach { option ->
                    FilterChip(
                        selected = uiState.sortOption == option,
                        onClick = { onSelectSort(option) },
                        label = { Text(option.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Section
            Text("File Category", color = VaultColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = uiState.selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("All Categories", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultColors.AccentCyan,
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                )
                VaultCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = uiState.selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(cat.title, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Size Filter
            Text("File Size", color = VaultColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SizeFilter.entries.forEach { sf ->
                    FilterChip(
                        selected = uiState.sizeFilter == sf,
                        onClick = { onSelectSize(sf) },
                        label = { Text(sf.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Filter
            Text("Attributes", color = VaultColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                StatusFilter.entries.forEach { st ->
                    FilterChip(
                        selected = uiState.statusFilter == st,
                        onClick = { onSelectStatus(st) },
                        label = { Text(st.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Filters")
            }
        }
    }
}

@Composable
private fun BrowserEmptyState(
    searchQuery: String,
    onCreateFolder: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(VaultColors.SurfaceElevated)
                    .border(1.dp, VaultColors.GlassBorderSubtle, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = VaultColors.AccentCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (searchQuery.isNotBlank()) "No Matching Files Found" else "This Secure Folder is Empty",
                color = VaultColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (searchQuery.isNotBlank()) {
                    "No items match '$searchQuery'. Try adjusting search terms or active filters."
                } else {
                    "Import files or create a subfolder to organize your encrypted assets."
                },
                color = VaultColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )

            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCreateFolder,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(VaultColors.GlassBorderSubtle)
                    )
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Folder")
                }
            }
        }
    }
}

fun getCategoryIcon(categoryName: String): ImageVector {
    return when (categoryName) {
        VaultCategory.DOCUMENT.name -> Icons.Default.Description
        VaultCategory.IMAGE.name -> Icons.Default.Image
        VaultCategory.VIDEO.name -> Icons.Default.VideoFile
        VaultCategory.AUDIO.name -> Icons.Default.AudioFile
        VaultCategory.ZIP.name -> Icons.Default.Archive
        VaultCategory.CODE.name -> Icons.Default.Code
        VaultCategory.TEXT.name -> Icons.Default.Description
        else -> Icons.Default.Description
    }
}

fun getCategoryColor(categoryName: String): Color {
    return when (categoryName) {
        VaultCategory.DOCUMENT.name -> Color(0xFF38BDF8) // Cyan
        VaultCategory.IMAGE.name -> Color(0xFFEC4899) // Pink
        VaultCategory.VIDEO.name -> Color(0xFFA855F7) // Purple
        VaultCategory.AUDIO.name -> Color(0xFFF59E0B) // Amber
        VaultCategory.ZIP.name -> Color(0xFFEAB308) // Yellow
        VaultCategory.CODE.name -> Color(0xFF10B981) // Emerald
        VaultCategory.TEXT.name -> Color(0xFF60A5FA) // Blue
        else -> Color(0xFF94A3B8) // Slate
    }
}
