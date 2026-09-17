package com.example.feature.media

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.feature.media.audio.AdvancedAudioPlayerDialog
import com.example.feature.media.audio.AudioPlaybackEngine
import com.example.feature.media.audio.MiniAudioPlayer
import com.example.feature.media.image.AdvancedImageViewerDialog
import com.example.feature.media.video.AdvancedVideoPlayerDialog

/**
 * Advanced Media Center Screen.
 * Complete encrypted workspace for Images, Videos, and Audio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCenterScreen(
    viewModel: MediaCenterViewModel,
    repository: VaultRepository,
    sessionManager: SessionSecurityManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val bulkStatus by viewModel.bulkImportStatus.collectAsState()
    val lockState by sessionManager.lockState.collectAsState()

    // Auto navigate back if vault locks
    LaunchedEffect(lockState) {
        if (lockState !is LockState.Unlocked) {
            onNavigateBack()
        }
    }

    // Modal viewers & sheets states
    var activeViewerImageIndex by remember { mutableStateOf<Int?>(null) }
    var activeVideoItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showFullAudioPlayer by remember { mutableStateOf(false) }
    var activeInfoItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var pendingExportItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }

    // Multi-select Photo Picker launcher for images & videos
    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(context, uris)
        }
    }

    // Generic OpenMultipleDocuments for audio and other formats
    val openDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(context, uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Media Center",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultColors.TextPrimary
                        )
                        Text(
                            text = "Hardware-Encrypted Workspace",
                            fontSize = 11.sp,
                            color = VaultColors.AccentCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultColors.TextPrimary
                        )
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = { viewModel.toggleSearching() }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Media",
                            tint = if (uiState.isSearching) VaultColors.AccentCyan else VaultColors.TextSecondary
                        )
                    }

                    // View Mode Toggle (Grid vs List)
                    IconButton(
                        onClick = {
                            viewModel.setViewMode(
                                if (uiState.viewMode == MediaViewMode.GRID) MediaViewMode.LIST else MediaViewMode.GRID
                            )
                        }
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == MediaViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle View Mode",
                            tint = VaultColors.TextSecondary
                        )
                    }

                    // Multi-select toggle
                    IconButton(onClick = { viewModel.toggleMultiSelectMode() }) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = "Multi Select",
                            tint = if (uiState.isMultiSelectMode) VaultColors.AccentCyan else VaultColors.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.Canvas)
            )
        },
        floatingActionButton = {
            if (!uiState.isMultiSelectMode) {
                FloatingActionButton(
                    onClick = {
                        if (uiState.currentTab == MediaCategoryTab.AUDIO) {
                            openDocumentsLauncher.launch(arrayOf("audio/*"))
                        } else {
                            visualMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    },
                    containerColor = VaultColors.AccentCyan,
                    contentColor = VaultColors.Canvas,
                    modifier = Modifier.testTag("media_import_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import Media"
                    )
                }
            }
        },
        containerColor = VaultColors.Canvas,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Category Tabs Bar
                PrimaryTabRow(
                    selectedTabIndex = uiState.currentTab.ordinal,
                    containerColor = VaultColors.Canvas,
                    contentColor = VaultColors.AccentCyan,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            color = VaultColors.AccentCyan,
                            modifier = Modifier.tabIndicatorOffset(uiState.currentTab.ordinal)
                        )
                    }
                ) {
                    MediaCategoryTab.entries.forEach { tab ->
                        Tab(
                            selected = uiState.currentTab == tab,
                            onClick = { viewModel.setTab(tab) },
                            text = {
                                Text(
                                    text = tab.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (uiState.currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    color = if (uiState.currentTab == tab) VaultColors.AccentCyan else VaultColors.TextSecondary
                                )
                            }
                        )
                    }
                }

                // Filter & Sort Bar
                MediaFilterSortBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    isSearching = uiState.isSearching,
                    onToggleSearch = { viewModel.toggleSearching() },
                    sortOption = uiState.sortOption,
                    onSortOptionSelected = { viewModel.setSortOption(it) },
                    sizeFilter = uiState.sizeFilter,
                    onSizeFilterSelected = { viewModel.setSizeFilter(it) },
                    dateFilter = uiState.dateFilter,
                    onDateFilterSelected = { viewModel.setDateFilter(it) },
                    statusFilter = uiState.statusFilter,
                    onStatusFilterSelected = { viewModel.setStatusFilter(it) },
                    selectedTag = uiState.selectedTag,
                    availableTags = uiState.availableTags,
                    onTagSelected = { viewModel.setSelectedTag(it) }
                )

                // Tab Specific Content
                if (uiState.currentTab == MediaCategoryTab.OVERVIEW) {
                    // Overview Category Cards & Recent Section
                    MediaOverviewCategoryCards(
                        imageCount = uiState.imageCount,
                        imageSizeBytes = uiState.imageTotalSizeBytes,
                        videoCount = uiState.videoCount,
                        videoSizeBytes = uiState.videoTotalSizeBytes,
                        audioCount = uiState.audioCount,
                        audioSizeBytes = uiState.audioTotalSizeBytes,
                        onCategoryClick = { viewModel.setTab(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Media Items Presentation (Grid or List)
                if (uiState.displayedItems.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No media files found",
                                color = VaultColors.TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the + button below to encrypt and import media safely.",
                                color = VaultColors.TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    if (uiState.viewMode == MediaViewMode.GRID) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(uiState.displayedItems, key = { it.id }) { item ->
                                val isSelected = uiState.selectedItemIds.contains(item.id)
                                MediaGridTile(
                                    item = item,
                                    repository = repository,
                                    isSelected = isSelected,
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        if (uiState.isMultiSelectMode) {
                                            viewModel.toggleItemSelection(item.id)
                                        } else {
                                            handleItemClick(
                                                item = item,
                                                displayedList = uiState.displayedItems,
                                                onOpenImage = { idx -> activeViewerImageIndex = idx },
                                                onOpenVideo = { v -> activeVideoItem = v },
                                                onPlayAudio = { a ->
                                                    val audioPlaylist = uiState.displayedItems.filter { it.category == "AUDIO" }
                                                    viewModel.audioEngine.playTrack(a, audioPlaylist)
                                                    showFullAudioPlayer = true
                                                }
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!uiState.isMultiSelectMode) {
                                            viewModel.toggleMultiSelectMode()
                                        }
                                        viewModel.toggleItemSelection(item.id)
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(uiState.displayedItems, key = { it.id }) { item ->
                                val isSelected = uiState.selectedItemIds.contains(item.id)
                                MediaListRow(
                                    item = item,
                                    repository = repository,
                                    isSelected = isSelected,
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        if (uiState.isMultiSelectMode) {
                                            viewModel.toggleItemSelection(item.id)
                                        } else {
                                            handleItemClick(
                                                item = item,
                                                displayedList = uiState.displayedItems,
                                                onOpenImage = { idx -> activeViewerImageIndex = idx },
                                                onOpenVideo = { v -> activeVideoItem = v },
                                                onPlayAudio = { a ->
                                                    val audioPlaylist = uiState.displayedItems.filter { it.category == "AUDIO" }
                                                    viewModel.audioEngine.playTrack(a, audioPlaylist)
                                                    showFullAudioPlayer = true
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Mini Audio Player at bottom of screen if audio is playing or queued
                MiniAudioPlayer(
                    engine = viewModel.audioEngine,
                    onExpand = { showFullAudioPlayer = true }
                )
            }

            // Floating Multi-Select Bottom Bar
            AnimatedVisibility(
                visible = uiState.isMultiSelectMode && uiState.selectedItemIds.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                MediaMultiSelectActionBar(
                    selectedCount = uiState.selectedItemIds.size,
                    onMoveClick = { showFolderDialog = true },
                    onFavoriteToggle = { viewModel.batchToggleFavorite() },
                    onAddTagClick = { showTagDialog = true },
                    onDeleteClick = { viewModel.batchDeleteToTrash() },
                    onExportClick = {
                        val firstItem = uiState.displayedItems.firstOrNull { it.id == uiState.selectedItemIds.firstOrNull() }
                        if (firstItem != null) {
                            pendingExportItem = firstItem
                        }
                    },
                    onCancelSelection = { viewModel.clearSelection() }
                )
            }
        }
    }

    // Fullscreen Image Gallery Viewer Dialog
    activeViewerImageIndex?.let { index ->
        val imageList = uiState.displayedItems.filter { it.category == "IMAGE" }
        if (imageList.isNotEmpty()) {
            val validIndex = index.coerceIn(0, imageList.size - 1)
            AdvancedImageViewerDialog(
                initialIndex = validIndex,
                images = imageList,
                repository = repository,
                sessionManager = sessionManager,
                onDismiss = { activeViewerImageIndex = null },
                onShowInfo = { activeInfoItem = it },
                onExportRequested = { pendingExportItem = it },
                onDeleteRequested = {
                    viewModel.deleteItemToTrash(it)
                    activeViewerImageIndex = null
                }
            )
        }
    }

    // Fullscreen Video Player Dialog
    activeVideoItem?.let { video ->
        val videoPlaylist = uiState.displayedItems.filter { it.category == "VIDEO" }
        AdvancedVideoPlayerDialog(
            item = video,
            playlist = videoPlaylist,
            repository = repository,
            sessionManager = sessionManager,
            onDismiss = { activeVideoItem = null },
            onShowInfo = { activeInfoItem = it }
        )
    }

    // Fullscreen Audio Player Dialog
    if (showFullAudioPlayer && viewModel.audioEngine.currentTrack.collectAsState().value != null) {
        AdvancedAudioPlayerDialog(
            engine = viewModel.audioEngine,
            repository = repository,
            onDismiss = { showFullAudioPlayer = false }
        )
    }

    // Media Info Sheet
    activeInfoItem?.let { infoItem ->
        MediaInfoSheet(
            item = infoItem,
            folders = uiState.folders,
            repository = repository,
            onDismiss = { activeInfoItem = null }
        )
    }

    // Privacy Export Confirmation Dialog
    pendingExportItem?.let { exportItem ->
        MediaExportConfirmDialog(
            itemTitle = exportItem.title,
            onConfirm = {
                viewModel.exportItem(context, exportItem) { success, msg ->
                    Toast.makeText(
                        context,
                        if (success) "Exported copy: $msg" else "Export failed: $msg",
                        Toast.LENGTH_LONG
                    ).show()
                }
                pendingExportItem = null
            },
            onDismiss = { pendingExportItem = null }
        )
    }

    // Bulk Import Progress Dialog
    BulkMediaImportProgressDialog(
        status = bulkStatus,
        onCancel = { viewModel.cancelImport() },
        onDismiss = { viewModel.dismissImportStatus() }
    )

    // Batch Tag Dialog
    if (showTagDialog) {
        var tagInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add Tag to Selected Media", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("e.g. Vacation, Receipt, Studio") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultColors.AccentCyan,
                        focusedContainerColor = VaultColors.SurfaceElevated,
                        unfocusedContainerColor = VaultColors.SurfaceElevated,
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchAddTag(tagInput)
                        showTagDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = VaultColors.AccentCyan)
                ) {
                    Text("Add Tag", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // Batch Move to Folder Dialog
    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Move to Folder", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.batchMoveToFolder(null)
                                    showFolderDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Vault Root (No Folder)", color = VaultColors.TextPrimary)
                        }
                    }
                    items(uiState.folders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.batchMoveToFolder(folder.id)
                                    showFolderDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(folder.name, color = VaultColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }
}

private fun handleItemClick(
    item: VaultItemEntity,
    displayedList: List<VaultItemEntity>,
    onOpenImage: (Int) -> Unit,
    onOpenVideo: (VaultItemEntity) -> Unit,
    onPlayAudio: (VaultItemEntity) -> Unit
) {
    when (item.category) {
        "IMAGE" -> {
            val imageList = displayedList.filter { it.category == "IMAGE" }
            val idx = imageList.indexOfFirst { it.id == item.id }
            if (idx != -1) onOpenImage(idx)
        }
        "VIDEO" -> onOpenVideo(item)
        "AUDIO" -> onPlayAudio(item)
    }
}
