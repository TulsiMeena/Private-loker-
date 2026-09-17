package com.example.feature.code.studio

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.CodeContentSearchResult
import com.example.core.database.VaultItemEntity
import com.example.core.designsystem.VaultColors
import com.example.feature.code.CodeLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeTextStudioScreen(
    viewModel: CodeTextStudioViewModel,
    onNavigateBack: () -> Unit,
    onOpenFileEditor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isContentSearchEnabled by viewModel.isContentSearchEnabled.collectAsStateWithLifecycle()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
    val selectedItemIds by viewModel.selectedItemIds.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val contentSearchResults by viewModel.contentSearchResults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    // Observe newly created file to navigate to editor immediately
    LaunchedEffect(Unit) {
        viewModel.navigateToFileEditor.collect { newId ->
            onOpenFileEditor(newId)
        }
    }

    // Import file picker
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(context, uris)
        }
    }

    // Dialog states
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var selectedItemForAction by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showExportWarningDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(VaultColors.SurfaceDark)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "CODE & TEXT STUDIO",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultColors.TextPrimary,
                            letterSpacing = 1.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("studio_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = VaultColors.TextPrimary)
                        }
                    },
                    actions = {
                        // View mode toggle
                        IconButton(
                            onClick = {
                                viewModel.setViewMode(
                                    if (viewMode == StudioViewMode.LIST) StudioViewMode.GRID else StudioViewMode.LIST
                                )
                            },
                            modifier = Modifier.testTag("studio_view_mode_btn")
                        ) {
                            Icon(
                                if (viewMode == StudioViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle Grid/List",
                                tint = VaultColors.TextSecondary
                            )
                        }

                        // Import button
                        IconButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("studio_import_btn")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = "Import Files", tint = VaultColors.TextSecondary)
                        }

                        // Multi-select toggle
                        IconButton(
                            onClick = { viewModel.toggleMultiSelect() },
                            modifier = Modifier.testTag("studio_multiselect_btn")
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Select Mode",
                                tint = if (isMultiSelectMode) VaultColors.AccentCyan else VaultColors.TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultColors.SurfaceDark)
                )

                // Workspace Section Tabs
                ScrollableTabRow(
                    selectedTabIndex = currentSection.ordinal,
                    containerColor = VaultColors.SurfaceDark,
                    contentColor = VaultColors.AccentCyan,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[currentSection.ordinal]),
                            color = VaultColors.AccentCyan
                        )
                    }
                ) {
                    StudioSection.values().forEach { section ->
                        Tab(
                            selected = currentSection == section,
                            onClick = { viewModel.setSection(section) },
                            text = {
                                Text(
                                    text = section.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (currentSection == section) FontWeight.Bold else FontWeight.Normal,
                                    color = if (currentSection == section) VaultColors.AccentCyan else VaultColors.TextSecondary
                                )
                            }
                        )
                    }
                }

                // Search & Filter Box (when Search tab is active or search query present)
                if (currentSection == StudioSection.SEARCH || searchQuery.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF11151C))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search filename, tag, or content...", fontSize = 13.sp, color = VaultColors.TextTertiary) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VaultColors.AccentCyan) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = VaultColors.TextTertiary)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VaultColors.AccentCyan,
                                unfocusedBorderColor = VaultColors.GlassBorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("studio_search_input")
                        )

                        // On-demand content search toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Switch(
                                checked = isContentSearchEnabled,
                                onCheckedChange = { viewModel.toggleContentSearch() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = VaultColors.AccentCyan,
                                    checkedTrackColor = VaultColors.AccentCyan.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "On-demand local content search",
                                fontSize = 11.sp,
                                color = if (isContentSearchEnabled) VaultColors.AccentCyan else VaultColors.TextSecondary
                            )
                        }
                    }
                }

                // Tags Filter Strip (when on TAGS tab)
                if (currentSection == StudioSection.TAGS && availableTags.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        items(availableTags) { tag ->
                            FilterChip(
                                selected = tag == viewModel.selectedTag.collectAsStateWithLifecycle().value,
                                onClick = { viewModel.setSelectedTag(tag) },
                                label = { Text("#$tag", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = VaultColors.AccentCyan,
                                    containerColor = VaultColors.SurfaceOverlay,
                                    labelColor = VaultColors.TextSecondary
                                ),
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }

                // Multi-select Action Bar
                if (isMultiSelectMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                            .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${selectedItemIds.size} selected",
                            color = VaultColors.AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text("All", fontSize = 11.sp, color = VaultColors.TextPrimary)
                            }
                            IconButton(onClick = { viewModel.batchFavorite(true) }, enabled = selectedItemIds.isNotEmpty()) {
                                Icon(Icons.Default.Favorite, contentDescription = "Batch Favorite", tint = VaultColors.AccentAmber)
                            }
                            IconButton(onClick = { viewModel.batchDelete() }, enabled = selectedItemIds.isNotEmpty()) {
                                Icon(Icons.Default.Delete, contentDescription = "Batch Trash", tint = VaultColors.AccentRed)
                            }
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = VaultColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Folder button
                OutlinedButton(
                    onClick = { showNewFolderDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = VaultColors.SurfaceDark,
                        contentColor = VaultColors.TextPrimary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Folder", fontSize = 12.sp)
                }

                // New File Primary FAB
                ExtendedFloatingActionButton(
                    onClick = { showNewFileDialog = true },
                    containerColor = VaultColors.AccentCyan,
                    contentColor = VaultColors.TextInverse,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New File", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("studio_new_file_fab")
                )
            }
        },
        containerColor = VaultColors.Canvas,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VaultColors.AccentCyan)
                }
            } else if (isContentSearchEnabled && contentSearchResults.isNotEmpty()) {
                // Content search results view
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    item {
                        Text(
                            text = "Content Matches (${contentSearchResults.size} files)",
                            color = VaultColors.AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(contentSearchResults) { result ->
                        ContentSearchResultCard(
                            result = result,
                            onClick = { onOpenFileEditor(result.item.id) }
                        )
                    }
                }
            } else if (items.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = VaultColors.TextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Files in this Section",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create a code or text file, or import from device into PrivateVault.",
                            fontSize = 13.sp,
                            color = VaultColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showNewFileDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Create New File", color = VaultColors.TextInverse)
                        }
                    }
                }
            } else if (viewMode == StudioViewMode.LIST) {
                // List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = selectedItemIds.contains(item.id)
                        StudioFileListItem(
                            item = item,
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelectMode,
                            onClick = {
                                if (isMultiSelectMode) {
                                    viewModel.toggleSelectItem(item.id)
                                } else {
                                    onOpenFileEditor(item.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleMultiSelect(item)
                            },
                            onFavoriteClick = { viewModel.toggleFavorite(item) },
                            onActionClick = {
                                selectedItemForAction = item
                            }
                        )
                    }
                }
            } else {
                // Grid View
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val isSelected = selectedItemIds.contains(item.id)
                        StudioFileGridItem(
                            item = item,
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelectMode,
                            onClick = {
                                if (isMultiSelectMode) {
                                    viewModel.toggleSelectItem(item.id)
                                } else {
                                    onOpenFileEditor(item.id)
                                }
                            },
                            onLongClick = { viewModel.toggleMultiSelect(item) },
                            onFavoriteClick = { viewModel.toggleFavorite(item) },
                            onActionClick = { selectedItemForAction = item }
                        )
                    }
                }
            }
        }
    }

    // --- Action Dropdown & Dialogs ---

    // File Action Dialog / Menu
    selectedItemForAction?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForAction = null },
            title = {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    ActionRow(Icons.Default.Edit, "Rename") {
                        selectedItemForAction = null
                        showRenameDialog = true
                    }
                    ActionRow(Icons.Default.DriveFileMove, "Move to Folder") {
                        selectedItemForAction = null
                        showMoveDialog = true
                    }
                    ActionRow(Icons.Default.ContentCopy, "Duplicate (Encrypted Copy)") {
                        viewModel.duplicateItem(item)
                        selectedItemForAction = null
                    }
                    ActionRow(Icons.Default.Label, "Edit Tags") {
                        selectedItemForAction = null
                        showTagsDialog = true
                    }
                    ActionRow(Icons.Default.Info, "Properties / Checksum") {
                        selectedItemForAction = null
                        showDetailsDialog = true
                    }
                    ActionRow(Icons.Default.Share, "Export (Leaves Vault Boundary)") {
                        selectedItemForAction = null
                        showExportWarningDialog = true
                    }
                    HorizontalDivider(color = VaultColors.GlassBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                    ActionRow(Icons.Default.Delete, "Move to Trash", color = VaultColors.AccentRed) {
                        viewModel.deleteItem(item)
                        selectedItemForAction = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemForAction = null }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { filename, language, template ->
                viewModel.createNewFile(filename, language, initialTemplate = template)
                showNewFileDialog = false
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            viewModel.createFolder(folderName.trim())
                            showNewFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                ) {
                    Text("Create", color = VaultColors.TextInverse)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }

    // Status / Notice Alert
    statusMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearStatusMessage() },
            title = { Text("Studio Message", color = VaultColors.AccentCyan, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = VaultColors.TextPrimary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearStatusMessage() }) {
                    Text("OK", color = VaultColors.AccentCyan)
                }
            },
            containerColor = VaultColors.SurfaceDark
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioFileListItem(
    item: VaultItemEntity,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onActionClick: () -> Unit
) {
    val ext = item.title.substringAfterLast('.', "TXT").uppercase(Locale.ROOT)
    val extColor = when (ext) {
        "KT", "KTS" -> Color(0xFF7F52FF)
        "JAVA" -> Color(0xFFE76F00)
        "PY" -> Color(0xFF3776AB)
        "JS", "JSX" -> Color(0xFFF7DF1E)
        "TS", "TSX" -> Color(0xFF3178C6)
        "HTML" -> Color(0xFFE34F26)
        "CSS" -> Color(0xFF1572B6)
        "JSON" -> Color(0xFF00B4D8)
        "XML" -> Color(0xFFE06C75)
        "SQL" -> Color(0xFF00758F)
        "MD" -> Color(0xFF58A6FF)
        "CSV" -> Color(0xFF2EA043)
        else -> VaultColors.AccentCyan
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.12f) else Color(0xFF10131A))
            .border(
                1.dp,
                if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        // Multi-select Checkbox or Extension Badge
        if (isMultiSelectMode) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) VaultColors.AccentCyan else VaultColors.TextTertiary,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 6.dp)
            )
        }

        // Extension Badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(extColor.copy(alpha = 0.15f))
                .border(1.dp, extColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        ) {
            Text(
                text = ext.take(4),
                color = extColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = VaultColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val sizeStr = Formatter.formatFileSize(LocalContext.current, item.sizeBytes)
            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.modifiedAt))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$sizeStr · $dateStr",
                    color = VaultColors.TextTertiary,
                    fontSize = 11.sp
                )

                if (item.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#${item.tags.split(",").firstOrNull()?.trim() ?: ""}",
                        color = VaultColors.AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Star Favorite
        IconButton(onClick = onFavoriteClick, modifier = Modifier.size(32.dp)) {
            Icon(
                if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (item.isFavorite) VaultColors.AccentAmber else VaultColors.TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        // More Menu
        IconButton(onClick = onActionClick, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = VaultColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioFileGridItem(
    item: VaultItemEntity,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onActionClick: () -> Unit
) {
    val ext = item.title.substringAfterLast('.', "TXT").uppercase(Locale.ROOT)
    val extColor = VaultColors.AccentCyan

    Column(
        modifier = Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.15f) else Color(0xFF10131A))
            .border(
                1.dp,
                if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(extColor.copy(alpha = 0.15f))
            ) {
                Text(text = ext.take(3), color = extColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(onClick = onActionClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = VaultColors.TextTertiary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = VaultColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        val sizeStr = Formatter.formatFileSize(LocalContext.current, item.sizeBytes)
        Text(text = sizeStr, color = VaultColors.TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun ContentSearchResultCard(
    result: CodeContentSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = result.item.title,
                color = VaultColors.AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${result.matchCount} match(es) · Line ${result.lineIndex}",
                color = VaultColors.TextTertiary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF090B0E))
                .padding(6.dp)
        ) {
            Text(
                text = result.snippet,
                color = VaultColors.TextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (filename: String, language: CodeLanguage, template: String) -> Unit
) {
    var filename by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(CodeLanguage.PLAIN_TEXT) }
    var includeTemplate by remember { mutableStateOf(false) }

    val languages = remember { CodeLanguage.getCreatableLanguages() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New File", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Filename", color = VaultColors.TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    placeholder = { Text("e.g. script, notes, config") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_file_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Format / Language", color = VaultColors.TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(modifier = Modifier.fillMaxWidth()) {
                    items(languages) { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang.displayName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VaultColors.AccentCyan.copy(alpha = 0.2f),
                                selectedLabelColor = VaultColors.AccentCyan
                            ),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeTemplate,
                        onCheckedChange = { includeTemplate = it },
                        colors = CheckboxDefaults.colors(checkedColor = VaultColors.AccentCyan)
                    )
                    Text("Add starter boilerplate code", fontSize = 12.sp, color = VaultColors.TextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (filename.isNotBlank()) {
                        val template = if (!includeTemplate) "" else when (selectedLanguage) {
                            CodeLanguage.JSON -> "{\n  \"name\": \"PrivateVault\",\n  \"version\": \"1.0.0\"\n}"
                            CodeLanguage.HTML -> "<!DOCTYPE html>\n<html>\n<head>\n  <title>Document</title>\n</head>\n<body>\n\n</body>\n</html>"
                            CodeLanguage.MARKDOWN -> "# Document Title\n\n- Point 1\n- Point 2\n"
                            CodeLanguage.CSV -> "id,name,value\n1,Alpha,100\n2,Beta,200\n"
                            CodeLanguage.KOTLIN -> "fun main() {\n    // Code here\n}\n"
                            CodeLanguage.PYTHON -> "#!/usr/bin/env python3\n\ndef main():\n    pass\n\nif __name__ == '__main__':\n    main()\n"
                            else -> ""
                        }
                        onCreate(filename.trim(), selectedLanguage, template)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                modifier = Modifier.testTag("new_file_confirm_btn")
            ) {
                Text("Create in Vault", color = VaultColors.TextInverse)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        },
        containerColor = VaultColors.SurfaceDark
    )
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color = VaultColors.TextPrimary,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, color = color, fontSize = 14.sp)
    }
}
