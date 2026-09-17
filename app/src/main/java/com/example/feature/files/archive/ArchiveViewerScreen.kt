package com.example.feature.files.archive

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.core.storage.VaultCategory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    viewModel: ArchiveViewerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val archiveItem by viewModel.archiveItem.collectAsState()
    val metadata by viewModel.metadata.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isUnsupportedFormat by viewModel.isUnsupportedFormat.collectAsState()
    val isPasswordProtected by viewModel.isPasswordProtected.collectAsState()
    val displayedEntries by viewModel.displayedEntries.collectAsState()
    val breadcrumbs by viewModel.breadcrumbs.collectAsState()
    val previewEntry by viewModel.previewEntry.collectAsState()
    val previewFile by viewModel.previewFile.collectAsState()
    val isPreviewLoading by viewModel.isPreviewLoading.collectAsState()
    val previewTextContent by viewModel.previewTextContent.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allVaultItems by viewModel.allVaultItems.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showAddFilesDialog by remember { mutableStateOf(false) }
    var entryToRename by remember { mutableStateOf<ArchiveEntryItem?>(null) }
    var entryToDelete by remember { mutableStateOf<ArchiveEntryItem?>(null) }

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
                        modifier = Modifier.testTag("archive_viewer_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = archiveItem?.title ?: "Archive Browser",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = VaultColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        metadata?.let { meta ->
                            Text(
                                text = "${meta.totalFiles} files • ${formatBytes(meta.uncompressedTotalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = VaultColors.AccentCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showExtractDialog = true }) {
                        Icon(Icons.Default.Unarchive, contentDescription = "Extract All", tint = VaultColors.AccentCyan)
                    }
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Archive Info", tint = VaultColors.TextSecondary)
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = VaultColors.TextSecondary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Files from Vault...") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = VaultColors.AccentEmerald) },
                            onClick = {
                                showMenu = false
                                showAddFilesDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort Entries...") },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showSortMenu = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reload Archive") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                viewModel.loadArchive()
                            }
                        )
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
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Breadcrumbs Bar
            BreadcrumbRibbon(
                breadcrumbs = breadcrumbs,
                onNavigateTo = { viewModel.navigateTo(it) }
            )

            // Search bar within archive
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Filter items in archive...", fontSize = 13.sp, color = VaultColors.TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VaultColors.TextSecondary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = VaultColors.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(48.dp)
            )

            // Content State
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = VaultColors.AccentCyan)
                            Spacer(Modifier.height(16.dp))
                            Text("Decrypting & Authenticating Archive...", color = VaultColors.TextSecondary, fontSize = 14.sp)
                        }
                    }
                }

                isUnsupportedFormat -> {
                    FormatErrorCard(
                        title = "Unsupported Archive Format",
                        description = errorMessage ?: "This archive format is not supported. PrivateVault supports standard ZIP and ZIP64 archives.",
                        onBack = onNavigateBack
                    )
                }

                isPasswordProtected -> {
                    FormatErrorCard(
                        title = "Password-Protected Archive",
                        description = "This archive is encrypted with an external password. Decryption of proprietary ZIP passwords is not supported locally.",
                        onBack = onNavigateBack
                    )
                }

                errorMessage != null -> {
                    FormatErrorCard(
                        title = "Archive Error",
                        description = errorMessage ?: "Failed to read archive contents.",
                        onBack = onNavigateBack
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Up Directory Item if in a nested path and not searching
                        if (currentPath.isNotEmpty() && searchQuery.isEmpty()) {
                            item {
                                UpDirectoryCard(
                                    onClick = { viewModel.navigateUp() }
                                )
                            }
                        }

                        if (displayedEntries.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) "No entries match '$searchQuery'" else "This folder is empty",
                                        color = VaultColors.TextTertiary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            items(displayedEntries, key = { it.fullPath }) { entry ->
                                ArchiveEntryRow(
                                    entry = entry,
                                    onClick = { viewModel.previewEntry(entry) },
                                    onRename = { entryToRename = entry },
                                    onDelete = { entryToDelete = entry }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Safe Preview
    if (previewEntry != null) {
        val entry = previewEntry!!
        ArchiveEntryPreviewDialog(
            entry = entry,
            previewFile = previewFile,
            textContent = previewTextContent,
            isLoading = isPreviewLoading,
            onDismiss = { viewModel.dismissPreview() },
            onExtractThisFile = {
                viewModel.extractArchive(
                    option = ArchiveExtractionOption.EXTRACT_HERE,
                    targetFolderId = archiveItem?.folderId
                )
                viewModel.dismissPreview()
            }
        )
    }

    // Dialog: Extract Archive
    if (showExtractDialog && archiveItem != null) {
        ExtractArchiveDialog(
            archiveItem = archiveItem!!,
            folders = folders,
            onDismiss = { showExtractDialog = false },
            onConfirmExtract = { option, folderId, customName ->
                showExtractDialog = false
                viewModel.extractArchive(
                    option = option,
                    customFolderName = customName,
                    targetFolderId = folderId
                )
            }
        )
    }

    // Dialog: Metadata Info Sheet
    if (showInfoSheet && metadata != null) {
        ArchiveMetadataSheet(
            metadata = metadata!!,
            archiveItem = archiveItem,
            onDismiss = { showInfoSheet = false }
        )
    }

    // Dialog: Add files from vault
    if (showAddFilesDialog) {
        AddFilesFromVaultDialog(
            vaultItems = allVaultItems,
            onDismiss = { showAddFilesDialog = false },
            onConfirmAdd = { itemsToAdd ->
                showAddFilesDialog = false
                viewModel.addVaultFiles(itemsToAdd)
            }
        )
    }

    // Dialog: Rename Entry
    if (entryToRename != null) {
        val entry = entryToRename!!
        var newName by remember { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { entryToRename = null },
            containerColor = VaultColors.SurfaceElevated,
            title = { Text("Rename Entry", color = VaultColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRename = entryToRename
                        entryToRename = null
                        if (toRename != null && newName.isNotBlank() && newName != toRename.name) {
                            viewModel.renameEntry(toRename.fullPath, newName.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToRename = null }) {
                    Text("Cancel", color = VaultColors.TextTertiary)
                }
            }
        )
    }

    // Dialog: Delete Entry
    if (entryToDelete != null) {
        val entry = entryToDelete!!
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            containerColor = VaultColors.SurfaceElevated,
            title = { Text("Delete Entry", color = VaultColors.AccentCrimson) },
            text = {
                Text("Remove '${entry.name}' from this archive? The archive container will be rebuilt safely.", color = VaultColors.TextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = entryToDelete
                        entryToDelete = null
                        if (toDel != null) {
                            viewModel.deleteEntry(toDel.fullPath)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson, contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel", color = VaultColors.TextTertiary)
                }
            }
        )
    }
}

@Composable
private fun BreadcrumbRibbon(
    breadcrumbs: List<Pair<String, String>>,
    onNavigateTo: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultColors.SurfaceElevated)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        breadcrumbs.forEachIndexed { index, pair ->
            val isLast = index == breadcrumbs.size - 1
            Text(
                text = pair.first,
                fontSize = 13.sp,
                color = if (isLast) VaultColors.AccentCyan else VaultColors.TextSecondary,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { onNavigateTo(pair.second) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (!isLast) {
                Text(" / ", color = VaultColors.TextTertiary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun UpDirectoryCard(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VaultColors.SurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(0.5.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = VaultColors.AccentCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = ".. (Parent Directory)",
                color = VaultColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveEntryItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val iconTint = when {
                entry.isDirectory -> VaultColors.AccentAmber
                entry.isExecutableOrScript -> VaultColors.AccentCrimson
                entry.category == VaultCategory.IMAGE -> VaultColors.AccentCyan
                entry.category == VaultCategory.CODE -> VaultColors.AccentEmerald
                else -> VaultColors.Titanium
            }

            val icon = when {
                entry.isDirectory -> Icons.Default.Folder
                entry.isExecutableOrScript -> Icons.Default.Warning
                entry.category == VaultCategory.IMAGE -> Icons.Default.Image
                entry.category == VaultCategory.AUDIO -> Icons.Default.AudioFile
                entry.category == VaultCategory.VIDEO -> Icons.Default.VideoFile
                entry.category == VaultCategory.DOCUMENT -> Icons.Default.Description
                entry.category == VaultCategory.CODE -> Icons.Default.Code
                else -> Icons.Default.InsertDriveFile
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = VaultColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.isExecutableOrScript) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "DATA ONLY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultColors.AccentCrimson,
                            modifier = Modifier
                                .background(VaultColors.AccentCrimson.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!entry.isDirectory) {
                        Text(
                            text = formatBytes(entry.uncompressedSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = VaultColors.TextSecondary
                        )
                        if (entry.compressedSize > 0) {
                            Text(" (comp: ${formatBytes(entry.compressedSize)})", color = VaultColors.TextTertiary, fontSize = 10.sp)
                        }
                    } else {
                        Text("Folder", style = MaterialTheme.typography.labelSmall, color = VaultColors.TextSecondary)
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = VaultColors.TextTertiary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Preview") },
                            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Entry", color = VaultColors.AccentCrimson) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = VaultColors.AccentCrimson) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveEntryPreviewDialog(
    entry: ArchiveEntryItem,
    previewFile: File?,
    textContent: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onExtractThisFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = VaultColors.AccentCyan)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.name,
                    color = VaultColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(color = VaultColors.AccentCyan)
                    }

                    entry.isExecutableOrScript -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = VaultColors.AccentCrimson, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Executable / Script Protection", fontWeight = FontWeight.Bold, color = VaultColors.TextPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "PrivateVault enforces strict zero-execution boundaries. This file is never executed or interpreted. It is retained strictly as raw data.",
                                fontSize = 12.sp,
                                color = VaultColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    textContent != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(VaultColors.Canvas, RoundedCornerShape(6.dp))
                                .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = textContent,
                                color = VaultColors.TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    previewFile != null && entry.category == VaultCategory.IMAGE -> {
                        val bitmap = remember(previewFile) {
                            try {
                                BitmapFactory.decodeFile(previewFile.absolutePath)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = entry.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Text("Unable to render image bitmap", color = VaultColors.TextTertiary, fontSize = 12.sp)
                        }
                    }

                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = VaultColors.Titanium, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(formatBytes(entry.uncompressedSize), color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("MIME: ${entry.mimeType}", color = VaultColors.TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExtractThisFile,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
            ) {
                Text("Extract to Vault")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = VaultColors.TextTertiary)
            }
        }
    )
}

@Composable
private fun ArchiveMetadataSheet(
    metadata: ArchiveMetadata,
    archiveItem: VaultItemEntity?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = VaultColors.AccentCyan)
                Spacer(Modifier.width(8.dp))
                Text("Archive Structure & Stats", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                InfoRow("Archive Name", metadata.fileName)
                InfoRow("Format", metadata.format)
                InfoRow("Total Entries", "${metadata.totalEntries}")
                InfoRow("Files", "${metadata.totalFiles}")
                InfoRow("Directories", "${metadata.totalDirectories}")
                InfoRow("Uncompressed Total", formatBytes(metadata.uncompressedTotalBytes))
                InfoRow("Encrypted Vault Size", formatBytes(archiveItem?.sizeBytes ?: metadata.encryptedSizeBytes))
                InfoRow("Compression Ratio", String.format(Locale.US, "%.1f : 1", metadata.compressionRatio))
                InfoRow("Password Protected", if (metadata.isPasswordProtected) "Yes (Not Supported)" else "No")
                InfoRow("Security Status", if (metadata.isSafeToExtract) "Verified Safe (Within limits)" else "Exceeds Extraction Limits")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AddFilesFromVaultDialog(
    vaultItems: List<VaultItemEntity>,
    onDismiss: () -> Unit,
    onConfirmAdd: (List<VaultItemEntity>) -> Unit
) {
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var query by remember { mutableStateOf("") }

    val candidates = vaultItems.filter {
        !it.isTrash && it.category != VaultCategory.ZIP.name &&
                (query.isBlank() || it.title.contains(query, ignoreCase = true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceElevated,
        title = { Text("Add Vault Files to Archive", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search files...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(8.dp))
                ) {
                    items(candidates, key = { it.id }) { item ->
                        val isChecked = selectedIds.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val set = selectedIds.toMutableSet()
                                    if (isChecked) set.remove(item.id) else set.add(item.id)
                                    selectedIds = set
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    val set = selectedIds.toMutableSet()
                                    if (it) set.add(item.id) else set.remove(item.id)
                                    selectedIds = set
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
                    val chosen = vaultItems.filter { selectedIds.contains(it.id) }
                    onConfirmAdd(chosen)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan, contentColor = VaultColors.Canvas)
            ) {
                Text("Add Selected (${selectedIds.size})")
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
private fun FormatErrorCard(
    title: String,
    description: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VaultColors.AccentCrimson.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = VaultColors.AccentCrimson,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = VaultColors.TextPrimary
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultColors.TextSecondary
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceHighlight, contentColor = VaultColors.TextPrimary)
                ) {
                    Text("Return to Vault")
                }
            }
        }
    }
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
