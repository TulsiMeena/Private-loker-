package com.example.feature.files.archive

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.feature.files.operations.FileOperation
import com.example.feature.files.operations.FileOperationStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveCenterScreen(
    viewModel: ArchiveCenterViewModel,
    repository: VaultRepository,
    onNavigateBack: () -> Unit,
    onOpenArchive: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentSection by viewModel.currentSection.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedItemIds by viewModel.selectedItemIds.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val archives by viewModel.filteredArchives.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val operations by viewModel.operations.collectAsState()

    // Dialog and sheet states
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateArchiveDialog by remember { mutableStateOf(false) }
    var archiveToExtract by remember { mutableStateOf<VaultItemEntity?>(null) }
    var archiveForInfo by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemToExport by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showBulkExtractDialog by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // SAF Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val contentResolver = context.contentResolver
                    val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else "imported.zip"
                    } ?: "imported.zip"

                    val size = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                    contentResolver.openInputStream(uri)?.use { stream ->
                        viewModel.importArchiveStream(fileName, stream, size)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // SAF Export Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null && itemToExport != null) {
            val item = itemToExport!!
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val res = repository.exportItemToStream(item, os)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Archive exported successfully", Toast.LENGTH_SHORT).show()
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

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VaultColors.Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultColors.SurfaceGraphite,
                    titleContentColor = VaultColors.TextPrimary,
                    navigationIconContentColor = VaultColors.TextPrimary,
                    actionIconContentColor = VaultColors.TextPrimary
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("archive_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (isSearchExpanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search archives...", color = VaultColors.TextTertiary, fontSize = 14.sp) },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    isSearchExpanded = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = VaultColors.TextSecondary)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .testTag("archive_search_input")
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = null,
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Archive Center",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = VaultColors.TextPrimary
                                )
                                Text(
                                    text = "${stats.totalArchives} Archives • Hardware Encrypted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VaultColors.AccentCyan
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchExpanded) {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = VaultColors.TextSecondary)
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                if (viewMode == ArchiveViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle View",
                                tint = VaultColors.TextSecondary
                            )
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = VaultColors.TextSecondary)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            ArchiveSortOption.values().forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.title) },
                                    trailingIcon = {
                                        if (sortOption == opt) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = VaultColors.AccentCyan)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSortOption(opt)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleMultiSelect() }) {
                            Icon(
                                if (isMultiSelectMode) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = "Select",
                                tint = if (isMultiSelectMode) VaultColors.AccentAmber else VaultColors.TextSecondary
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                        containerColor = VaultColors.SurfaceElevated,
                        contentColor = VaultColors.AccentCyan,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("fab_import_archive")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import Archive")
                    }

                    FloatingActionButton(
                        onClick = { showCreateArchiveDialog = true },
                        containerColor = VaultColors.AccentCyan,
                        contentColor = VaultColors.Canvas,
                        modifier = Modifier.testTag("fab_create_archive")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New Archive", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Active operations banner (e.g. running extraction / compression)
            val runningOp = operations.firstOrNull { it.status == FileOperationStatus.RUNNING }
            if (runningOp != null) {
                ActiveOperationBanner(
                    operation = runningOp,
                    onCancel = { viewModel.operationManager.cancelOperation(runningOp.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Section Filter Chips
            SectionFilterRow(
                currentSection = currentSection,
                onSelectSection = { viewModel.selectSection(it) }
            )

            // Statistics Strip
            ArchiveStatsStrip(stats = stats)

            // Batch selection action bar
            AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                BatchActionBar(
                    selectedCount = selectedItemIds.size,
                    onSelectAll = { viewModel.selectAll() },
                    onClearSelection = { viewModel.clearSelection() },
                    onExtract = { showBulkExtractDialog = true },
                    onArchiveTogether = {
                        viewModel.bulkArchiveSelected("Archive_Batch_${System.currentTimeMillis()}.zip", ArchiveCompressionLevel.NORMAL)
                    },
                    onFavorite = { viewModel.bulkFavoriteSelected(true) },
                    onDelete = { viewModel.bulkDeleteSelected() }
                )
            }

            // Main Content: List or Grid of Archives
            if (archives.isEmpty()) {
                EmptyArchivePlaceholder(
                    isSearch = searchQuery.isNotBlank(),
                    onImportClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) },
                    onCreateClick = { showCreateArchiveDialog = true }
                )
            } else {
                if (viewMode == ArchiveViewMode.LIST) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(archives, key = { it.id }) { item ->
                            val isSelected = selectedItemIds.contains(item.id)
                            ArchiveListItemCard(
                                item = item,
                                isMultiSelect = isMultiSelectMode,
                                isSelected = isSelected,
                                onSelectToggle = { viewModel.toggleItemSelection(item.id) },
                                onClick = {
                                    if (isMultiSelectMode) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        onOpenArchive(item.id)
                                    }
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(item) },
                                onExtractClick = { archiveToExtract = item },
                                onExportClick = { itemToExport = item },
                                onInfoClick = { archiveForInfo = item },
                                onDeleteClick = { viewModel.deleteArchive(item) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(archives, key = { it.id }) { item ->
                            val isSelected = selectedItemIds.contains(item.id)
                            ArchiveGridItemCard(
                                item = item,
                                isMultiSelect = isMultiSelectMode,
                                isSelected = isSelected,
                                onSelectToggle = { viewModel.toggleItemSelection(item.id) },
                                onClick = {
                                    if (isMultiSelectMode) {
                                        viewModel.toggleItemSelection(item.id)
                                    } else {
                                        onOpenArchive(item.id)
                                    }
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(item) },
                                onExtractClick = { archiveToExtract = item },
                                onExportClick = { itemToExport = item },
                                onInfoClick = { archiveForInfo = item },
                                onDeleteClick = { viewModel.deleteArchive(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Archive from Vault Items
    if (showCreateArchiveDialog) {
        CreateArchiveDialog(
            repository = repository,
            onDismiss = { showCreateArchiveDialog = false },
            onCreate = { items, name, compression, conflict ->
                showCreateArchiveDialog = false
                viewModel.createArchive(
                    items = items,
                    archiveName = name,
                    targetFolderId = viewModel.selectedFolderId.value,
                    compressionLevel = compression,
                    conflictOption = conflict
                )
            }
        )
    }

    // Dialog: Extract Archive
    if (archiveToExtract != null) {
        val item = archiveToExtract!!
        ExtractArchiveDialog(
            archiveItem = item,
            folders = folders,
            onDismiss = { archiveToExtract = null },
            onConfirmExtract = { option, folderId, customName ->
                archiveToExtract = null
                viewModel.extractArchive(
                    item = item,
                    option = option,
                    customFolderName = customName,
                    targetFolderId = folderId
                )
            }
        )
    }

    // Dialog: Bulk Extract
    if (showBulkExtractDialog) {
        AlertDialog(
            onDismissRequest = { showBulkExtractDialog = false },
            containerColor = VaultColors.SurfaceElevated,
            title = { Text("Bulk Extract Archives", color = VaultColors.TextPrimary) },
            text = {
                Text(
                    "Extract ${selectedItemIds.size} archives into PrivateVault? Each archive will be placed in a dedicated virtual folder.",
                    color = VaultColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkExtractDialog = false
                        viewModel.bulkExtractSelected(extractSeparately = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
                ) {
                    Text("Extract All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkExtractDialog = false }) {
                    Text("Cancel", color = VaultColors.TextTertiary)
                }
            }
        )
    }

    // Dialog: Archive Info Metadata Sheet
    if (archiveForInfo != null) {
        ArchiveInfoDialog(
            item = archiveForInfo!!,
            onDismiss = { archiveForInfo = null }
        )
    }

    // Protection Boundary Export Dialog
    if (itemToExport != null) {
        val item = itemToExport!!
        ProtectionBoundaryExportDialog(
            fileName = item.title,
            onDismiss = { itemToExport = null },
            onConfirm = {
                exportLauncher.launch(item.title)
            }
        )
    }
}

@Composable
private fun ActiveOperationBanner(
    operation: FileOperation,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { operation.progress },
                        modifier = Modifier.size(18.dp),
                        color = VaultColors.AccentCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = operation.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = VaultColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = VaultColors.AccentCrimson)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { operation.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = VaultColors.AccentCyan,
                trackColor = VaultColors.SurfaceHighlight
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${operation.currentStep} (${(operation.progress * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionFilterRow(
    currentSection: ArchiveCenterSection,
    onSelectSection: (ArchiveCenterSection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArchiveCenterSection.values().forEach { sec ->
            val isSelected = currentSection == sec
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.15f) else VaultColors.SurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelectSection(sec) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = sec.title,
                    color = if (isSelected) VaultColors.AccentCyan else VaultColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ArchiveStatsStrip(stats: ArchiveDashboardStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatPill(title = "Total", value = "${stats.totalArchives}", modifier = Modifier.weight(1f))
        StatPill(title = "Vault Size", value = formatBytes(stats.totalEncryptedSizeBytes), modifier = Modifier.weight(1.3f))
        StatPill(title = "Favorites", value = "${stats.totalFavorites}", modifier = Modifier.weight(1f))
        StatPill(title = "Saved", value = formatBytes(stats.storageSavedBytes), modifier = Modifier.weight(1.1f))
    }
}

@Composable
private fun StatPill(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VaultColors.SurfaceElevated,
        modifier = modifier.border(0.5.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = VaultColors.TextTertiary)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VaultColors.TextPrimary)
        }
    }
}

@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExtract: () -> Unit,
    onArchiveTogether: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = VaultColors.SurfaceOverlay,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = VaultColors.TextSecondary)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$selectedCount selected",
                    color = VaultColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSelectAll) {
                    Text("Select All", fontSize = 12.sp, color = VaultColors.AccentCyan)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onExtract, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Unarchive, contentDescription = "Extract All", tint = VaultColors.AccentCyan)
                }
                IconButton(onClick = onArchiveTogether, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive Together", tint = VaultColors.AccentEmerald)
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Star, contentDescription = "Favorite", tint = VaultColors.AccentAmber)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Trash", tint = VaultColors.AccentCrimson)
                }
            }
        }
    }
}

@Composable
private fun ArchiveListItemCard(
    item: VaultItemEntity,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onExtractClick: () -> Unit,
    onExportClick: () -> Unit,
    onInfoClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.1f) else VaultColors.SurfaceElevated
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // ZIP Icon Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                    .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = null,
                    tint = VaultColors.AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = VaultColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Encrypted: ${formatBytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultColors.TextSecondary
                    )
                    Text(" • ", color = VaultColors.TextTertiary, fontSize = 10.sp)
                    Text(
                        text = formatDate(item.modifiedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = VaultColors.TextTertiary
                    )
                }
            }

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (item.isFavorite) VaultColors.AccentAmber else VaultColors.TextTertiary
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = VaultColors.TextSecondary)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Browse Contents") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Extract...") },
                        leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null, tint = VaultColors.AccentCyan) },
                        onClick = {
                            showMenu = false
                            onExtractClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Outside Vault...") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onExportClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive Details") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onInfoClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Trash", color = VaultColors.AccentCrimson) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VaultColors.AccentCrimson) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveGridItemCard(
    item: VaultItemEntity,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onExtractClick: () -> Unit,
    onExportClick: () -> Unit,
    onInfoClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.1f) else VaultColors.SurfaceElevated
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelect) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(Modifier.width(24.dp))
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) VaultColors.AccentAmber else VaultColors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VaultColors.AccentCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = null,
                    tint = VaultColors.AccentCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = VaultColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = VaultColors.TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyArchivePlaceholder(
    isSearch: Boolean,
    onImportClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(VaultColors.SurfaceElevated)
                    .border(1.dp, VaultColors.GlassBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = null,
                    tint = VaultColors.AccentCyan,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isSearch) "No Matching Archives" else "No Archives in Vault",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = VaultColors.TextPrimary
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (isSearch) "Try adjusting your search criteria" else "Import an existing ZIP archive or create a new compressed archive from your vault items.",
                style = MaterialTheme.typography.bodySmall,
                color = VaultColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (!isSearch) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onImportClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import ZIP")
                    }

                    Button(
                        onClick = onCreateClick,
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create ZIP")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateArchiveDialog(
    repository: VaultRepository,
    onDismiss: () -> Unit,
    onCreate: (List<VaultItemEntity>, String, ArchiveCompressionLevel, DuplicateConflictOption) -> Unit
) {
    val scope = rememberCoroutineScope()
    val allItems by repository.allItems.collectAsState(initial = emptyList())

    var archiveName by remember { mutableStateOf("Archive_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.zip") }
    var selectedCompression by remember { mutableStateOf(ArchiveCompressionLevel.NORMAL) }
    var conflictOption by remember { mutableStateOf(DuplicateConflictOption.KEEP_BOTH) }
    var selectedItemIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filteredItems = allItems.filter {
        !it.isTrash && (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Text("Create Encrypted ZIP Archive", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = {
                        archiveName = it
                        errorMessage = null
                    },
                    label = { Text("Archive Name") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let { Text(it, color = VaultColors.AccentCrimson) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Text("Compression Level:", style = MaterialTheme.typography.labelMedium, color = VaultColors.TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ArchiveCompressionLevel.values().forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedCompression = level }
                        ) {
                            RadioButton(
                                selected = selectedCompression == level,
                                onClick = { selectedCompression = level },
                                colors = RadioButtonDefaults.colors(selectedColor = VaultColors.AccentCyan)
                            )
                            Text(level.name, fontSize = 12.sp, color = VaultColors.TextPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Select Vault Items (${selectedItemIds.size} selected):", style = MaterialTheme.typography.labelMedium, color = VaultColors.TextSecondary)

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter items...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isChecked = selectedItemIds.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val set = selectedItemIds.toMutableSet()
                                    if (isChecked) set.remove(item.id) else set.add(item.id)
                                    selectedItemIds = set
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    val set = selectedItemIds.toMutableSet()
                                    if (it) set.add(item.id) else set.remove(item.id)
                                    selectedItemIds = set
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(item.title, fontSize = 13.sp, color = VaultColors.TextPrimary, maxLines = 1)
                                Text("${item.category} • ${formatBytes(item.sizeBytes)}", fontSize = 11.sp, color = VaultColors.TextTertiary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (archiveName.isBlank()) {
                        errorMessage = "Archive name cannot be empty"
                        return@Button
                    }
                    if (selectedItemIds.isEmpty()) {
                        errorMessage = "Select at least one vault item to compress"
                        return@Button
                    }
                    val chosenItems = allItems.filter { selectedItemIds.contains(it.id) }
                    onCreate(chosenItems, archiveName, selectedCompression, conflictOption)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
            ) {
                Text("Create Archive")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextTertiary)
            }
        }
    )
}

@Composable
internal fun ExtractArchiveDialog(
    archiveItem: VaultItemEntity,
    folders: List<com.example.core.database.VaultFolderEntity>,
    onDismiss: () -> Unit,
    onConfirmExtract: (ArchiveExtractionOption, Long?, String?) -> Unit
) {
    var selectedOption by remember { mutableStateOf(ArchiveExtractionOption.EXTRACT_HERE) }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var customFolderName by remember { mutableStateOf(archiveItem.title.substringBeforeLast('.')) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Text("Extract Archive", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose extraction destination within PrivateVault's protected encrypted storage:",
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultColors.TextSecondary
                )

                Spacer(Modifier.height(12.dp))

                ExtractionOptionItem(
                    title = "Extract Here",
                    subtitle = "Extracts all files directly into the current folder",
                    selected = selectedOption == ArchiveExtractionOption.EXTRACT_HERE,
                    onClick = { selectedOption = ArchiveExtractionOption.EXTRACT_HERE }
                )

                ExtractionOptionItem(
                    title = "Create New Folder and Extract",
                    subtitle = "Creates a dedicated virtual folder for the extracted contents",
                    selected = selectedOption == ArchiveExtractionOption.CREATE_NEW_FOLDER_AND_EXTRACT,
                    onClick = { selectedOption = ArchiveExtractionOption.CREATE_NEW_FOLDER_AND_EXTRACT }
                )

                if (selectedOption == ArchiveExtractionOption.CREATE_NEW_FOLDER_AND_EXTRACT) {
                    OutlinedTextField(
                        value = customFolderName,
                        onValueChange = { customFolderName = it },
                        label = { Text("New Folder Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 6.dp)
                    )
                }

                ExtractionOptionItem(
                    title = "Extract to Specific Folder",
                    subtitle = "Select an existing vault folder",
                    selected = selectedOption == ArchiveExtractionOption.EXTRACT_TO_FOLDER,
                    onClick = { selectedOption = ArchiveExtractionOption.EXTRACT_TO_FOLDER }
                )

                if (selectedOption == ArchiveExtractionOption.EXTRACT_TO_FOLDER && folders.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(start = 24.dp, top = 6.dp)
                            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(6.dp))
                    ) {
                        items(folders) { folder ->
                            val isFSelected = selectedFolderId == folder.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFolderId = folder.id }
                                    .background(if (isFSelected) VaultColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(folder.name, fontSize = 12.sp, color = VaultColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmExtract(selectedOption, selectedFolderId, customFolderName)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
            ) {
                Text("Extract Safely")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextTertiary)
            }
        }
    )
}

@Composable
private fun ExtractionOptionItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = VaultColors.AccentCyan)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = VaultColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = VaultColors.TextSecondary)
        }
    }
}

@Composable
private fun ArchiveInfoDialog(
    item: VaultItemEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = VaultColors.AccentCyan)
                Spacer(Modifier.width(8.dp))
                Text("Archive Information", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoRow("Archive Name", item.title)
                InfoRow("Category", item.category)
                InfoRow("Encrypted Vault Size", formatBytes(item.sizeBytes))
                InfoRow("Created", formatDate(item.createdAt))
                InfoRow("Last Modified", formatDate(item.modifiedAt))
                InfoRow("Security Storage", "Hardware-backed AES-256-GCM (.pvault)")
                InfoRow("Checksum (SHA-256)", item.checksumSha256.take(16) + "...", isMonospace = true)
                InfoRow("Tags", if (item.tags.isNotBlank()) item.tags else "None")
                InfoRow("Favorite", if (item.isFavorite) "Yes" else "No")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)) {
                Text("Close")
            }
        }
    )
}

@Composable
internal fun InfoRow(label: String, value: String, isMonospace: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, color = VaultColors.TextTertiary)
        Text(
            text = value,
            fontSize = 13.sp,
            color = VaultColors.TextPrimary,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun ProtectionBoundaryExportDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = VaultColors.AccentAmber)
                Spacer(Modifier.width(8.dp))
                Text("PrivateVault Protection Boundary", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = "The selected archive will leave PrivateVault's hardware-encrypted protected storage.",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "File: $fileName",
                    color = VaultColors.AccentCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Once exported outside PrivateVault, other apps or device backups may access the decrypted data.",
                    color = VaultColors.TextTertiary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber, contentColor = VaultColors.Canvas)
            ) {
                Text("Continue Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextTertiary)
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
