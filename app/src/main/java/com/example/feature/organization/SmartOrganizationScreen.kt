package com.example.feature.organization

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.designsystem.VaultColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartOrganizationScreen(
    viewModel: SmartOrganizationViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (VaultItemEntity) -> Unit,
    onLockVault: () -> Unit,
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderForSubfolder by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var folderForRename by remember { mutableStateOf<VaultFolderEntity?>(null) }
    var folderForMove by remember { mutableStateOf<VaultFolderEntity?>(null) }

    var tagForRename by remember { mutableStateOf<TagSummary?>(null) }
    var tagForMerge by remember { mutableStateOf<TagSummary?>(null) }
    var tagForDelete by remember { mutableStateOf<TagSummary?>(null) }

    var duplicateItemToDelete by remember { mutableStateOf<VaultItemEntity?>(null) }

    when (val state = uiState) {
        is SmartOrganizationUiState.Locked -> {
            OrganizationLockedView(
                onAuthenticate = onNavigateToAuth,
                onNavigateBack = onNavigateBack,
                modifier = modifier
            )
        }

        is SmartOrganizationUiState.Success -> {
            // Toast feedback
            LaunchedEffect(state.statusMessage) {
                state.statusMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearStatusMessage()
                }
            }

            Scaffold(
                containerColor = VaultColors.Canvas,
                topBar = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VaultColors.SurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (state.activeCollection != null) {
                                        viewModel.closeCollection()
                                    } else {
                                        onNavigateBack()
                                    }
                                },
                                modifier = Modifier.testTag("org_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = VaultColors.TextPrimary
                                )
                            }
                            Column {
                                Text(
                                    text = if (state.activeCollection != null) state.activeCollection.displayName else "Smart Organization",
                                    color = VaultColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.activeCollection != null) state.activeCollection.description else "Rule-based Vault Architecture",
                                    color = VaultColors.TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.canUndo) {
                                IconButton(
                                    onClick = { viewModel.rollbackLastBatchAction() },
                                    modifier = Modifier.testTag("org_undo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = "Undo Last Organize",
                                        tint = VaultColors.AccentAmber
                                    )
                                }
                            }
                            IconButton(
                                onClick = onLockVault,
                                modifier = Modifier.testTag("org_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Vault",
                                    tint = VaultColors.AccentCyan
                                )
                            }
                        }
                    }
                },
                modifier = modifier.fillMaxSize()
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (state.activeCollection != null) {
                        // Viewing an active smart collection
                        ActiveCollectionView(
                            collectionType = state.activeCollection,
                            items = state.collectionItems,
                            allFolders = state.allFolders,
                            selectedItemIds = state.selectedItemIds,
                            onToggleSelect = { viewModel.toggleItemSelection(it) },
                            onSelectAll = { viewModel.selectAll(state.collectionItems) },
                            onClearSelection = { viewModel.clearSelection() },
                            onOpenFile = onOpenFile,
                            onBatchMove = { folderId -> viewModel.batchMoveSelected(folderId) },
                            onBatchTrash = { viewModel.batchTrashSelected() },
                            onBatchFavorite = { fav -> viewModel.batchFavoriteSelected(fav) }
                        )
                    } else {
                        // Main Tab Navigation
                        val tabs = listOf("Insights", "Collections", "Bulk Tools", "Tags", "Folders", "Duplicates")
                        ScrollableTabRow(
                            selectedTabIndex = state.selectedTab,
                            containerColor = VaultColors.SurfaceElevated,
                            contentColor = VaultColors.AccentCyan,
                            edgePadding = 16.dp,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                                    color = VaultColors.AccentCyan,
                                    height = 2.dp
                                )
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = state.selectedTab == index,
                                    onClick = { viewModel.selectTab(index) },
                                    text = {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if (state.selectedTab == index) VaultColors.AccentCyan else VaultColors.TextSecondary
                                        )
                                    }
                                )
                            }
                        }

                        // Tab Content
                        when (state.selectedTab) {
                            0 -> InsightsTabContent(
                                insights = state.insights,
                                onActionClick = { insight ->
                                    when (insight.type) {
                                        InsightType.UNTAGGED -> viewModel.openCollection(SmartCollectionType.UNTAGGED)
                                        InsightType.UNFILED -> viewModel.openCollection(SmartCollectionType.UNFILED)
                                        InsightType.LARGE_FILES -> viewModel.openCollection(SmartCollectionType.LARGE_FILES)
                                        InsightType.DUPLICATES -> viewModel.selectTab(5) // Duplicates tab
                                        InsightType.RECENT_IMPORT -> viewModel.openCollection(SmartCollectionType.RECENTLY_ADDED)
                                    }
                                },
                                onBulkOrganizeClick = { viewModel.selectTab(2) }
                            )

                            1 -> CollectionsTabContent(
                                currentThresholdBytes = state.largeFileThresholdBytes,
                                onSelectCollection = { col -> viewModel.openCollection(col) },
                                onThresholdChange = { bytes -> viewModel.setLargeFileThreshold(bytes) }
                            )

                            2 -> BulkToolsTabContent(
                                canUndo = state.canUndo,
                                onOrganizeByType = { viewModel.generateOrganizeByTypeProposal() },
                                onOrganizeByDate = { viewModel.generateOrganizeByDateProposal() },
                                onRollback = { viewModel.rollbackLastBatchAction() }
                            )

                            3 -> TagsTabContent(
                                tagSummaries = state.tagSummaries,
                                onRenameTag = { tagForRename = it },
                                onMergeTag = { tagForMerge = it },
                                onDeleteTag = { tagForDelete = it }
                            )

                            4 -> FoldersTabContent(
                                folderTree = state.folderTree,
                                showEmpty = state.showEmptyFolders,
                                onToggleShowEmpty = { viewModel.toggleShowEmptyFolders() },
                                onCreateRootFolder = {
                                    folderForSubfolder = null
                                    showCreateFolderDialog = true
                                },
                                onCreateSubfolder = { folder ->
                                    folderForSubfolder = folder
                                    showCreateFolderDialog = true
                                },
                                onRenameFolder = { folder -> folderForRename = folder },
                                onMoveFolder = { folder -> folderForMove = folder },
                                onDeleteFolder = { folder -> viewModel.deleteFolder(folder.id) }
                            )

                            5 -> DuplicatesTabContent(
                                duplicateGroups = state.duplicateGroups,
                                onDeleteDuplicate = { item -> duplicateItemToDelete = item }
                            )
                        }
                    }
                }
            }

            // Safe Preview & Confirmation Dialog for Bulk Organization Proposals
            state.activeProposal?.let { proposal ->
                OrganizationProposalDialog(
                    proposal = proposal,
                    onDismiss = { viewModel.dismissProposal() },
                    onConfirm = { viewModel.applyProposal(proposal) }
                )
            }

            // Create Folder Dialog
            if (showCreateFolderDialog) {
                var folderName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreateFolderDialog = false },
                    title = {
                        Text(
                            text = if (folderForSubfolder == null) "Create Root Folder" else "Create Subfolder in '${folderForSubfolder?.name}'",
                            color = VaultColors.TextPrimary
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            label = { Text("Folder Name") },
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
                                if (folderName.isNotBlank()) {
                                    viewModel.createFolder(folderName.trim(), folderForSubfolder?.id)
                                    showCreateFolderDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Create", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateFolderDialog = false }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Rename Folder Dialog
            folderForRename?.let { folder ->
                var newName by remember { mutableStateOf(folder.name) }
                AlertDialog(
                    onDismissRequest = { folderForRename = null },
                    title = { Text("Rename Folder", color = VaultColors.TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Folder Name") },
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
                                    viewModel.renameFolder(folder.id, newName.trim())
                                    folderForRename = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Save", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { folderForRename = null }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Move Folder Dialog (with circular validation)
            folderForMove?.let { folder ->
                AlertDialog(
                    onDismissRequest = { folderForMove = null },
                    title = { Text("Move '${folder.name}' to Folder", color = VaultColors.TextPrimary) },
                    text = {
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.moveFolder(folder.id, null)
                                            folderForMove = null
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vault Root (Top Level)", color = VaultColors.TextPrimary)
                                }
                            }
                            items(state.allFolders.filter { it.id != folder.id }) { candidate ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.moveFolder(folder.id, candidate.id)
                                            folderForMove = null
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(candidate.name, color = VaultColors.TextPrimary)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { folderForMove = null }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Rename Tag Dialog
            tagForRename?.let { tagSummary ->
                var newTagName by remember { mutableStateOf(tagSummary.tag) }
                AlertDialog(
                    onDismissRequest = { tagForRename = null },
                    title = { Text("Rename Tag '#${tagSummary.tag}'", color = VaultColors.TextPrimary) },
                    text = {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            label = { Text("New Tag Name") },
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
                                if (newTagName.isNotBlank()) {
                                    viewModel.renameTag(tagSummary.tag, newTagName.trim())
                                    tagForRename = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Rename", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { tagForRename = null }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Merge Tag Dialog
            tagForMerge?.let { tagSummary ->
                var targetTagName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { tagForMerge = null },
                    title = { Text("Merge '#${tagSummary.tag}' into Another Tag", color = VaultColors.TextPrimary) },
                    text = {
                        Column {
                            Text(
                                "All ${tagSummary.count} files tagged '#${tagSummary.tag}' will be updated to the target tag.",
                                fontSize = 12.sp,
                                color = VaultColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = targetTagName,
                                onValueChange = { targetTagName = it },
                                label = { Text("Target Tag Name") },
                                singleLine = true,
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
                                if (targetTagName.isNotBlank()) {
                                    viewModel.mergeTags(tagSummary.tag, targetTagName.trim())
                                    tagForMerge = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
                        ) {
                            Text("Merge", color = VaultColors.Canvas)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { tagForMerge = null }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Delete Tag Confirmation Dialog
            tagForDelete?.let { tagSummary ->
                AlertDialog(
                    onDismissRequest = { tagForDelete = null },
                    title = { Text("Delete Tag '#${tagSummary.tag}'?", color = VaultColors.TextPrimary) },
                    text = {
                        Text(
                            "This will remove the tag from all ${tagSummary.count} files.\n\nYour actual files will NOT be deleted.",
                            color = VaultColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteTag(tagSummary.tag)
                                tagForDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson)
                        ) {
                            Text("Delete Tag", color = VaultColors.TextPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { tagForDelete = null }) {
                            Text("Cancel", color = VaultColors.TextSecondary)
                        }
                    },
                    containerColor = VaultColors.SurfaceElevated
                )
            }

            // Delete Duplicate Confirmation Dialog
            duplicateItemToDelete?.let { item ->
                AlertDialog(
                    onDismissRequest = { duplicateItemToDelete = null },
                    title = { Text("Move Duplicate to Trash?", color = VaultColors.TextPrimary) },
                    text = {
                        Text(
                            "Are you sure you want to move '${item.title}' (${formatFileSize(item.sizeBytes)}) to trash?\n\nYou can restore it later from Trash if needed.",
                            color = VaultColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteDuplicateItem(item)
                                duplicateItemToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson)
                        ) {
                            Text("Move to Trash", color = VaultColors.TextPrimary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { duplicateItemToDelete = null }) {
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
// Tab 0: Insights Tab
// ====================================================================

@Composable
private fun InsightsTabContent(
    insights: List<OrganizationInsight>,
    onActionClick: (OrganizationInsight) -> Unit,
    onBulkOrganizeClick: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Default.AutoAwesomeMosaic, contentDescription = null, tint = VaultColors.AccentCyan)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vault Organization Health", color = VaultColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Local metadata rule engines analyze untagged, unfiled, duplicate, and large files without external servers.",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (insights.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = VaultColors.AccentEmerald, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Vault is Perfectly Organized", color = VaultColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("All items are filed into folders and tagged.", color = VaultColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(insights) { insight ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val tint = when (insight.type) {
                                    InsightType.UNTAGGED -> VaultColors.AccentAmber
                                    InsightType.UNFILED -> VaultColors.AccentCyan
                                    InsightType.LARGE_FILES -> VaultColors.AccentCrimson
                                    InsightType.DUPLICATES -> Color(0xFFA855F7)
                                    InsightType.RECENT_IMPORT -> VaultColors.AccentEmerald
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(tint)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(insight.title, color = VaultColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(insight.description, color = VaultColors.TextSecondary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onActionClick(insight) },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceHighlight),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(insight.actionLabel, color = VaultColors.AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBulkOrganizeClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch Bulk Organization Tools")
                }
            }
        }
    }
}

// ====================================================================
// Tab 1: Smart Collections
// ====================================================================

@Composable
private fun CollectionsTabContent(
    currentThresholdBytes: Long,
    onSelectCollection: (SmartCollectionType) -> Unit,
    onThresholdChange: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("Virtual Collections (Zero File Duplication)", color = VaultColors.TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Threshold chips for Large Files
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text("Large Files:", color = VaultColors.TextTertiary, fontSize = 11.sp)
                val thresholds = listOf(
                    10L * 1024 * 1024 to "10 MB",
                    25L * 1024 * 1024 to "25 MB",
                    50L * 1024 * 1024 to "50 MB",
                    100L * 1024 * 1024 to "100 MB"
                )
                thresholds.forEach { (bytes, label) ->
                    val isSelected = currentThresholdBytes == bytes
                    FilterChip(
                        selected = isSelected,
                        onClick = { onThresholdChange(bytes) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VaultColors.AccentCrimson.copy(alpha = 0.2f),
                            selectedLabelColor = VaultColors.AccentCrimson
                        )
                    )
                }
            }
        }

        items(SmartCollectionType.entries) { col ->
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onSelectCollection(col) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceHighlight)
                    ) {
                        Icon(col.getIcon(), contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(col.displayName, color = VaultColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(col.description, color = VaultColors.TextSecondary, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = VaultColors.TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ====================================================================
// Tab 2: Bulk Tools Tab
// ====================================================================

@Composable
private fun BulkToolsTabContent(
    canUndo: Boolean,
    onOrganizeByType: () -> Unit,
    onOrganizeByDate: () -> Unit,
    onRollback: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = VaultColors.AccentCyan)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Organize by File Type", color = VaultColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Sorts loose root files into dedicated category folders: Documents, Images, Videos, Audio, Code, Archives, Notes, and Other. Shows a safe preview dialog prior to execution.",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOrganizeByType,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Preview Organization by Type", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = VaultColors.AccentAmber)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Organize by Date Created", color = VaultColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Groups loose root files into Year/Month folders (e.g. 2026/September) based on their creation timestamp.",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOrganizeByDate,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Preview Organization by Date", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (canUndo) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VaultColors.AccentCrimson.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rollback Last Organization", color = VaultColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Restore previous folder assignments.", color = VaultColors.TextSecondary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = onRollback,
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Undo", color = VaultColors.TextPrimary)
                    }
                }
            }
        }
    }
}

// ====================================================================
// Tab 3: Tag Manager Tab
// ====================================================================

@Composable
private fun TagsTabContent(
    tagSummaries: List<TagSummary>,
    onRenameTag: (TagSummary) -> Unit,
    onMergeTag: (TagSummary) -> Unit,
    onDeleteTag: (TagSummary) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "${tagSummaries.size} Active Tags Across Vault",
                color = VaultColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (tagSummaries.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LabelOff, contentDescription = null, tint = VaultColors.TextTertiary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Tags Created Yet", color = VaultColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Add tags from search results or file cards.", color = VaultColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(tagSummaries) { tagSummary ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VaultColors.AccentAmber.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("#${tagSummary.tag}", color = VaultColors.AccentAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "${tagSummary.count} file${if (tagSummary.count != 1) "s" else ""}",
                                color = VaultColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onRenameTag(tagSummary) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename Tag", tint = VaultColors.TextSecondary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onMergeTag(tagSummary) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.CallMerge, contentDescription = "Merge Tag", tint = VaultColors.AccentCyan, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDeleteTag(tagSummary) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Tag", tint = VaultColors.AccentCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// Tab 4: Folders Hierarchy Tab
// ====================================================================

@Composable
private fun FoldersTabContent(
    folderTree: List<FolderTreeNode>,
    showEmpty: Boolean,
    onToggleShowEmpty: () -> Unit,
    onCreateRootFolder: () -> Unit,
    onCreateSubfolder: (VaultFolderEntity) -> Unit,
    onRenameFolder: (VaultFolderEntity) -> Unit,
    onMoveFolder: (VaultFolderEntity) -> Unit,
    onDeleteFolder: (VaultFolderEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleShowEmpty() }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (showEmpty) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = VaultColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (showEmpty) "Showing Empty Folders" else "Hiding Empty Folders",
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onCreateRootFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Folder", color = VaultColors.Canvas, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (folderTree.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = VaultColors.TextTertiary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Folders", color = VaultColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Create a folder to begin organizing your files.", color = VaultColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            folderTree.forEach { rootNode ->
                renderFolderNode(
                    node = rootNode,
                    onCreateSubfolder = onCreateSubfolder,
                    onRenameFolder = onRenameFolder,
                    onMoveFolder = onMoveFolder,
                    onDeleteFolder = onDeleteFolder
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderFolderNode(
    node: FolderTreeNode,
    onCreateSubfolder: (VaultFolderEntity) -> Unit,
    onRenameFolder: (VaultFolderEntity) -> Unit,
    onMoveFolder: (VaultFolderEntity) -> Unit,
    onDeleteFolder: (VaultFolderEntity) -> Unit
) {
    item(key = "folder_${node.folder.id}") {
        FolderNodeCard(
            node = node,
            onCreateSubfolder = onCreateSubfolder,
            onRenameFolder = onRenameFolder,
            onMoveFolder = onMoveFolder,
            onDeleteFolder = onDeleteFolder
        )
    }

    node.children.forEach { child ->
        renderFolderNode(
            node = child,
            onCreateSubfolder = onCreateSubfolder,
            onRenameFolder = onRenameFolder,
            onMoveFolder = onMoveFolder,
            onDeleteFolder = onDeleteFolder
        )
    }
}

@Composable
private fun FolderNodeCard(
    node: FolderTreeNode,
    onCreateSubfolder: (VaultFolderEntity) -> Unit,
    onRenameFolder: (VaultFolderEntity) -> Unit,
    onMoveFolder: (VaultFolderEntity) -> Unit,
    onDeleteFolder: (VaultFolderEntity) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (node.level * 16).dp)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = VaultColors.AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        node.folder.name,
                        color = VaultColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${node.totalItemCount} files • ${formatFileSize(node.totalSizeBytes)}",
                        color = VaultColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = VaultColors.TextSecondary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(VaultColors.SurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("New Subfolder", color = VaultColors.TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = VaultColors.AccentCyan) },
                        onClick = {
                            showMenu = false
                            onCreateSubfolder(node.folder)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename", color = VaultColors.TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = VaultColors.TextPrimary) },
                        onClick = {
                            showMenu = false
                            onRenameFolder(node.folder)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move Folder", color = VaultColors.TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = VaultColors.TextPrimary) },
                        onClick = {
                            showMenu = false
                            onMoveFolder(node.folder)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Folder", color = VaultColors.AccentCrimson) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VaultColors.AccentCrimson) },
                        onClick = {
                            showMenu = false
                            onDeleteFolder(node.folder)
                        }
                    )
                }
            }
        }
    }
}

// ====================================================================
// Tab 5: Duplicates Tab
// ====================================================================

@Composable
private fun DuplicatesTabContent(
    duplicateGroups: List<DuplicateFileGroup>,
    onDeleteDuplicate: (VaultItemEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFA855F7))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Cryptographic Duplicate Detection", color = VaultColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Groups files with identical local SHA-256 hashes. PrivateVault never deletes files automatically; you must review and confirm each deletion.",
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        if (duplicateGroups.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = VaultColors.AccentEmerald, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Duplicates Found", color = VaultColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("All files in your vault have unique cryptographic signatures.", color = VaultColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(duplicateGroups) { group ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${group.items.size} Identical Copies",
                                color = VaultColors.AccentAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatFileSize(group.sizeBytes),
                                color = VaultColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Text(
                            text = "SHA-256: ${group.checksumSha256.take(16)}…",
                            color = VaultColors.TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // List copies in this group
                        group.items.forEachIndexed { index, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = VaultColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Modified: ${formatDate(item.modifiedAt)}",
                                        color = VaultColors.TextTertiary,
                                        fontSize = 10.sp
                                    )
                                }

                                if (index > 0) {
                                    Button(
                                        onClick = { onDeleteDuplicate(item) },
                                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Delete Copy", color = VaultColors.AccentCrimson, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(VaultColors.AccentEmerald.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Primary", color = VaultColors.AccentEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
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
// Active Virtual Collection View
// ====================================================================

@Composable
private fun ActiveCollectionView(
    collectionType: SmartCollectionType,
    items: List<VaultItemEntity>,
    allFolders: List<VaultFolderEntity>,
    selectedItemIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenFile: (VaultItemEntity) -> Unit,
    onBatchMove: (Long?) -> Unit,
    onBatchTrash: () -> Unit,
    onBatchFavorite: (Boolean) -> Unit
) {
    var showMoveDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("${items.size} items in collection", color = VaultColors.TextSecondary, fontSize = 12.sp)

            if (selectedItemIds.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All", color = VaultColors.AccentCyan, fontSize = 11.sp)
                    }
                    TextButton(onClick = onClearSelection) {
                        Text("Clear", color = VaultColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(collectionType.getIcon(), contentDescription = null, tint = VaultColors.TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Files in Collection", color = VaultColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(items, key = { "col_${it.id}" }) { item ->
                    val isSelected = selectedItemIds.contains(item.id)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.15f) else VaultColors.SurfaceElevated
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSelected) VaultColors.AccentCyan.copy(alpha = 0.6f) else VaultColors.GlassBorderSubtle,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (selectedItemIds.isNotEmpty()) {
                                    onToggleSelect(item.id)
                                } else {
                                    onOpenFile(item)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelect(item.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = VaultColors.AccentCyan,
                                    checkmarkColor = VaultColors.Canvas
                                ),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = VaultColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(formatFileSize(item.sizeBytes), color = VaultColors.TextSecondary, fontSize = 11.sp)
                                    Text("•", color = VaultColors.TextTertiary, fontSize = 11.sp)
                                    Text(formatDate(item.modifiedAt), color = VaultColors.TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection action bar if items selected
        if (selectedItemIds.isNotEmpty()) {
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
                    Text("${selectedItemIds.size} selected", color = VaultColors.AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { showMoveDialog = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Move", tint = VaultColors.TextPrimary)
                        }
                        IconButton(onClick = { onBatchFavorite(true) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Star, contentDescription = "Favorite", tint = VaultColors.AccentAmber)
                        }
                        IconButton(onClick = onBatchTrash, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Trash", tint = VaultColors.AccentCrimson)
                        }
                    }
                }
            }
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move Selected Files", color = VaultColors.TextPrimary) },
            text = {
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBatchMove(null)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vault Root (No Folder)", color = VaultColors.TextPrimary)
                        }
                    }
                    items(allFolders) { folder ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onBatchMove(folder.id)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = VaultColors.AccentCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(folder.name, color = VaultColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }
}

// ====================================================================
// Safe Preview Dialog for Bulk Proposals
// ====================================================================

@Composable
private fun OrganizationProposalDialog(
    proposal: OrganizationProposal,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(proposal.title, color = VaultColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(proposal.description, color = VaultColors.TextSecondary, fontSize = 12.sp)
            }
        },
        text = {
            Column {
                Text("Safe Preview of Proposed Moves:", color = VaultColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    items(proposal.moves) { move ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                move.item.title,
                                color = VaultColors.TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    move.targetFolderName,
                                    color = VaultColors.AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
            ) {
                Text("Apply Moves (${proposal.moves.size})", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        },
        containerColor = VaultColors.SurfaceElevated
    )
}

// ====================================================================
// Vault Locked View
// ====================================================================

@Composable
private fun OrganizationLockedView(
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
                Text("Smart Organization", color = VaultColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VaultColors.AccentAmber, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Vault Locked", color = VaultColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Authenticate to access Smart Organization.", color = VaultColors.TextSecondary, fontSize = 13.sp)
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
