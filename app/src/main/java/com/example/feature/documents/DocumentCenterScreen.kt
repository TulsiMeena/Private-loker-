package com.example.feature.documents

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import com.example.feature.vault.CreateFolderDialog
import com.example.feature.vault.DeleteConfirmationDialog
import com.example.feature.vault.EditTagsDialog
import com.example.feature.vault.ExportConfirmationDialog
import com.example.feature.vault.MoveToFolderDialog
import com.example.feature.vault.RenameDialog
import com.example.feature.vault.import_feature.SecureImportController
import com.example.feature.vault.import_feature.SecureImportDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentCenterScreen(
    viewModel: DocumentCenterViewModel,
    sessionManager: SessionSecurityManager,
    onNavigateBack: () -> Unit,
    onOpenDocument: (itemId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Import controller
    val importController = remember {
        SecureImportController(context, viewModel.repository, scope)
    }
    val importState by importController.importState.collectAsState()

    // Document Picker launcher for bulk or single document import
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            importController.startImport(
                uris = uris,
                targetCategory = null,
                targetFolderId = uiState.currentFolder?.id
            )
        }
    }

    // Export Document Launcher
    var itemToExport by remember { mutableStateOf<VaultItemEntity?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(itemToExport?.mimeType ?: "*/*")
    ) { uri: Uri? ->
        if (uri != null && itemToExport != null) {
            val exportTarget = itemToExport!!
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val result = viewModel.repository.exportItemToStream(exportTarget, os)
                        if (result.isSuccess) {
                            Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    itemToExport = null
                }
            }
        } else {
            itemToExport = null
        }
    }

    // UI Dialog States
    var selectedItemForAction by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showExportWarningDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPrintConfirmDialog by remember { mutableStateOf(false) }
    var showCreateDocDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showBatchTagsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DOCUMENTS",
                            color = VaultColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(VaultColors.AccentEmerald)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VAULT UNSEALED",
                                color = VaultColors.AccentEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.currentTab == DocumentCenterTab.FOLDERS && viewModel.navigateUpFolder()) {
                                // Handled in folder breadcrumbs
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("doc_center_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultColors.TextPrimary
                        )
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(
                        onClick = { viewModel.setSearchActive(!uiState.isSearchActive) },
                        modifier = Modifier.testTag("doc_search_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (uiState.isSearchActive) VaultColors.AccentCyan else VaultColors.TextPrimary
                        )
                    }

                    // Filter action with badge
                    Box {
                        IconButton(
                            onClick = { viewModel.setFilterSheetOpen(true) },
                            modifier = Modifier.testTag("doc_filter_button")
                        ) {
                            if (uiState.filterState.hasActiveFilter) {
                                BadgedBox(badge = { Badge { Text("!") } }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filters",
                                        tint = VaultColors.AccentCyan
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filters",
                                    tint = VaultColors.TextPrimary
                                )
                            }
                        }
                    }

                    // Sort dropdown
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Options",
                                tint = VaultColors.TextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(VaultColors.SurfaceElevated)
                        ) {
                            DocumentSortOption.values().forEach { sort ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sort.displayName,
                                            color = if (sort == uiState.sortOption) VaultColors.AccentCyan else VaultColors.TextPrimary,
                                            fontWeight = if (sort == uiState.sortOption) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOption(sort)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Grid / List Toggle
                    IconButton(
                        onClick = {
                            viewModel.setViewMode(
                                if (uiState.viewMode == DocumentViewMode.GRID) DocumentViewMode.LIST else DocumentViewMode.GRID
                            )
                        },
                        modifier = Modifier.testTag("doc_view_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == DocumentViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View Mode",
                            tint = VaultColors.TextPrimary
                        )
                    }

                    // Multi-select toggle
                    IconButton(
                        onClick = { viewModel.toggleMultiSelect() },
                        modifier = Modifier.testTag("doc_multi_select_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Select Multiple",
                            tint = if (uiState.isMultiSelectActive) VaultColors.AccentCyan else VaultColors.TextSecondary
                        )
                    }

                    // Import document or file button
                    IconButton(
                        onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("doc_top_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = "Import Document or File",
                            tint = VaultColors.AccentCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.SurfaceElevated)
            )
        },
        floatingActionButton = {
            if (!uiState.isMultiSelectActive) {
                FloatingActionButton(
                    onClick = { showCreateDocDialog = true },
                    containerColor = VaultColors.AccentCyan,
                    contentColor = VaultColors.Canvas,
                    modifier = Modifier.testTag("doc_center_create_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Document")
                }
            }
        },
        containerColor = VaultColors.Canvas,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Search Bar (when active)
            if (uiState.isSearchActive) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by name, extension, tag...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = VaultColors.AccentCyan)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = VaultColors.TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                        cursorColor = VaultColors.AccentCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("doc_search_field")
                )
            }

            // 2. Navigation Tabs Row (All, Recent, Favorites, Folders, Tags)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(DocumentCenterTab.values()) { tab ->
                    val isSelected = tab == uiState.currentTab
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = VaultColors.AccentCyan,
                            containerColor = VaultColors.SurfaceElevated,
                            labelColor = VaultColors.TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                            enabled = true,
                            selected = isSelected
                        ),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }

            // 3. Folder Breadcrumbs (When in Folders Tab)
            if (uiState.currentTab == DocumentCenterTab.FOLDERS) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Root",
                        color = if (uiState.currentFolder == null) VaultColors.AccentCyan else VaultColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (uiState.currentFolder == null) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(null) }
                    )

                    uiState.folderBreadcrumbs.forEach { crumb ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = VaultColors.TextTertiary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = crumb.name,
                            color = if (crumb.id == uiState.currentFolder?.id) VaultColors.AccentCyan else VaultColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (crumb.id == uiState.currentFolder?.id) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.clickable { viewModel.navigateToBreadcrumb(crumb) }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 4. Multi-Select Sticky Bar
            if (uiState.isMultiSelectActive) {
                Surface(
                    color = VaultColors.SurfaceGraphite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${uiState.selectedItemIds.size} selected",
                            color = VaultColors.AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Select All",
                                fontSize = 11.sp,
                                color = VaultColors.TextPrimary,
                                modifier = Modifier
                                    .clickable { viewModel.selectAll() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            if (uiState.selectedItemIds.isNotEmpty()) {
                                IconButton(
                                    onClick = { showBatchMoveDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DriveFileMove, contentDescription = "Move", tint = VaultColors.AccentCyan, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { viewModel.batchFavoriteSelected(true) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = "Favorite", tint = VaultColors.AccentAmber, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { showBatchTagsDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Label, contentDescription = "Tag", tint = VaultColors.AccentCyan, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { viewModel.batchDeleteSelected() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = VaultColors.AccentCrimson, modifier = Modifier.size(18.dp))
                                }
                            }

                            IconButton(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Selection", tint = VaultColors.TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // 5. Folders List (When in Folders Tab)
            if (uiState.currentTab == DocumentCenterTab.FOLDERS && uiState.folders.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.folders) { folder ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VaultColors.SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                            modifier = Modifier
                                .clickable { viewModel.openFolder(folder) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = VaultColors.AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = folder.name,
                                    fontSize = 12.sp,
                                    color = VaultColors.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 6. Documents Content Area
            if (uiState.isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = VaultColors.AccentCyan)
                }
            } else if (uiState.documents.isEmpty()) {
                // Empty state
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(VaultColors.SurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (uiState.filterState.hasActiveFilter || uiState.searchQuery.isNotEmpty()) {
                                "No Matching Documents"
                            } else {
                                "Encrypted Document Center"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultColors.TextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (uiState.filterState.hasActiveFilter || uiState.searchQuery.isNotEmpty()) {
                                "Try resetting filters or adjusting search keywords."
                            } else {
                                "Store, read, and edit confidential documents securely inside your private fortress."
                            },
                            fontSize = 12.sp,
                            color = VaultColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (uiState.filterState.hasActiveFilter || uiState.searchQuery.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearFilters()
                                        viewModel.setSearchQuery("")
                                    }
                                ) {
                                    Text("Reset Filters")
                                }
                            } else {
                                Button(
                                    onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                                    modifier = Modifier.testTag("doc_center_empty_import_button")
                                ) {
                                    Text("Import PDF / Files", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showCreateDocDialog = true },
                                    modifier = Modifier.testTag("doc_center_empty_create_button")
                                ) {
                                    Text("Create Document")
                                }
                            }
                        }
                    }
                }
            } else {
                // Documents Grid or List
                if (uiState.viewMode == DocumentViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.documents, key = { it.id }) { item ->
                            val isSelected = uiState.selectedItemIds.contains(item.id)
                            DocumentGridCard(
                                item = item,
                                repository = viewModel.repository,
                                isMultiSelectActive = uiState.isMultiSelectActive,
                                isSelected = isSelected,
                                onClick = {
                                    if (uiState.isMultiSelectActive) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        onOpenDocument(item.id)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleItemSelection(item.id)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onRename = {
                                    selectedItemForAction = item
                                    showRenameDialog = true
                                },
                                onMove = {
                                    selectedItemForAction = item
                                    showMoveDialog = true
                                },
                                onCopy = { viewModel.copyDocument(item) },
                                onDuplicate = { viewModel.duplicateDocument(item) },
                                onTags = {
                                    selectedItemForAction = item
                                    showTagsDialog = true
                                },
                                onInfo = {
                                    selectedItemForAction = item
                                    showInfoSheet = true
                                },
                                onExport = {
                                    selectedItemForAction = item
                                    showExportWarningDialog = true
                                },
                                onPrint = {
                                    selectedItemForAction = item
                                    showPrintConfirmDialog = true
                                },
                                onDelete = {
                                    selectedItemForAction = item
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.documents, key = { it.id }) { item ->
                            val isSelected = uiState.selectedItemIds.contains(item.id)
                            DocumentListRow(
                                item = item,
                                isMultiSelectActive = uiState.isMultiSelectActive,
                                isSelected = isSelected,
                                onClick = {
                                    if (uiState.isMultiSelectActive) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        onOpenDocument(item.id)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleItemSelection(item.id)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onRename = {
                                    selectedItemForAction = item
                                    showRenameDialog = true
                                },
                                onMove = {
                                    selectedItemForAction = item
                                    showMoveDialog = true
                                },
                                onCopy = { viewModel.copyDocument(item) },
                                onDuplicate = { viewModel.duplicateDocument(item) },
                                onTags = {
                                    selectedItemForAction = item
                                    showTagsDialog = true
                                },
                                onInfo = {
                                    selectedItemForAction = item
                                    showInfoSheet = true
                                },
                                onExport = {
                                    selectedItemForAction = item
                                    showExportWarningDialog = true
                                },
                                onPrint = {
                                    selectedItemForAction = item
                                    showPrintConfirmDialog = true
                                },
                                onDelete = {
                                    selectedItemForAction = item
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (uiState.isFilterSheetOpen) {
        DocumentFilterBottomSheet(
            currentFilter = uiState.filterState,
            availableTags = uiState.availableTags,
            onApplyFilters = { viewModel.updateFilters(it) },
            onResetFilters = { viewModel.clearFilters() },
            onDismiss = { viewModel.setFilterSheetOpen(false) }
        )
    }

    // Document Info Sheet
    if (showInfoSheet && selectedItemForAction != null) {
        DocumentInfoSheet(
            item = selectedItemForAction!!,
            folderName = uiState.currentFolder?.name ?: "Root",
            repository = viewModel.repository,
            onDismiss = {
                showInfoSheet = false
                selectedItemForAction = null
            }
        )
    }

    // Create Document Dialog
    if (showCreateDocDialog) {
        CreateDocumentDialog(
            onDismiss = { showCreateDocDialog = false },
            onConfirm = { fileName, mimeType, template ->
                showCreateDocDialog = false
                viewModel.createDocument(fileName, mimeType, template) { created ->
                    Toast.makeText(context, "Document created in vault", Toast.LENGTH_SHORT).show()
                    onOpenDocument(created.id)
                }
            }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                showCreateFolderDialog = false
                viewModel.createFolder(folderName)
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && selectedItemForAction != null) {
        RenameDialog(
            currentName = selectedItemForAction!!.title,
            onDismiss = {
                showRenameDialog = false
                selectedItemForAction = null
            },
            onConfirm = { newName ->
                viewModel.renameDocument(selectedItemForAction!!, newName)
                showRenameDialog = false
                selectedItemForAction = null
            }
        )
    }

    // Move to Folder Dialog
    if (showMoveDialog && selectedItemForAction != null) {
        val allFolders by viewModel.repository.getAllFolders().collectAsState(initial = emptyList())
        MoveToFolderDialog(
            folders = allFolders,
            currentFolderId = selectedItemForAction!!.folderId,
            onDismiss = {
                showMoveDialog = false
                selectedItemForAction = null
            },
            onFolderSelected = { targetFolderId ->
                viewModel.moveDocument(selectedItemForAction!!, targetFolderId)
                showMoveDialog = false
                selectedItemForAction = null
            }
        )
    }

    // Batch Move Dialog
    if (showBatchMoveDialog) {
        val allFolders by viewModel.repository.getAllFolders().collectAsState(initial = emptyList())
        MoveToFolderDialog(
            folders = allFolders,
            currentFolderId = uiState.currentFolder?.id,
            onDismiss = { showBatchMoveDialog = false },
            onFolderSelected = { targetFolderId ->
                viewModel.batchMoveSelected(targetFolderId)
                showBatchMoveDialog = false
            }
        )
    }

    // Edit Tags Dialog
    if (showTagsDialog && selectedItemForAction != null) {
        EditTagsDialog(
            currentTags = selectedItemForAction!!.tags,
            onDismiss = {
                showTagsDialog = false
                selectedItemForAction = null
            },
            onSave = { newTags ->
                viewModel.updateTags(selectedItemForAction!!, newTags)
                showTagsDialog = false
                selectedItemForAction = null
            }
        )
    }

    // Export Confirmation Warning Dialog
    if (showExportWarningDialog && selectedItemForAction != null) {
        ExportConfirmationDialog(
            fileName = selectedItemForAction!!.title,
            onDismiss = {
                showExportWarningDialog = false
                selectedItemForAction = null
            },
            onConfirm = {
                val target = selectedItemForAction
                showExportWarningDialog = false
                itemToExport = target
                exportLauncher.launch(target?.title ?: "document")
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && selectedItemForAction != null) {
        DeleteConfirmationDialog(
            itemCount = 1,
            onDismiss = {
                showDeleteConfirmDialog = false
                selectedItemForAction = null
            },
            onConfirm = {
                viewModel.deleteDocument(selectedItemForAction!!)
                showDeleteConfirmDialog = false
                selectedItemForAction = null
                Toast.makeText(context, "Moved to Secure Trash", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Print Confirmation Warning Dialog
    if (showPrintConfirmDialog && selectedItemForAction != null) {
        val printTarget = selectedItemForAction!!
        AlertDialog(
            onDismissRequest = {
                showPrintConfirmDialog = false
                selectedItemForAction = null
            },
            title = {
                Text("Print Security Notice", fontWeight = FontWeight.Bold, color = VaultColors.TextPrimary)
            },
            text = {
                Text(
                    "Printing will decrypt and transfer '${printTarget.title}' to Android's system Print Spooler. This creates an unencrypted output stream outside PrivateVault. Do you wish to continue?",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrintConfirmDialog = false
                        selectedItemForAction = null
                        scope.launch {
                            try {
                                val ext = printTarget.title.substringAfterLast('.', "").lowercase()
                                if (ext == "pdf") {
                                    val previewRes = viewModel.repository.createTransientPreview(printTarget)
                                    val tempFile = previewRes.getOrNull()
                                    if (tempFile != null) {
                                        DocumentPrintHelper.printPdfDocument(context, tempFile, printTarget.title)
                                    }
                                } else {
                                    Toast.makeText(context, "Direct printing is optimized for PDF format", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                ) {
                    Text("Proceed to Print", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPrintConfirmDialog = false
                        selectedItemForAction = null
                    }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // Secure Import Dialog (Bulk & Single Import)
    SecureImportDialog(
        state = importState,
        onCancel = { importController.cancelImport() },
        onDismiss = { importController.dismiss() }
    )
}
