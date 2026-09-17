package com.example.feature.vault.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultItemEntity
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.lifecycle.AutoCleanRetention
import com.example.core.lifecycle.RestoreConflictResolution
import com.example.core.ui.VaultGlassCard
import com.example.core.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.testTag("trash_screen"),
        containerColor = VaultColors.Canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Encrypted Secure Trash",
                            style = typography.title,
                            color = VaultColors.TextPrimary
                        )
                        Text(
                            text = if (uiState.items.isEmpty()) {
                                "Trash Partition Empty"
                            } else {
                                "${uiState.items.size} Item(s) • ${Formatters.formatBytes(uiState.totalTrashBytes)} Pending Deletion"
                            },
                            style = typography.caption,
                            color = VaultColors.TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("trash_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultColors.TextPrimary
                        )
                    }
                },
                actions = {
                    // 1. Orphan Detection / Integrity scan button
                    IconButton(
                        onClick = { viewModel.showOrphanReview() },
                        modifier = Modifier.testTag("trash_integrity_button")
                    ) {
                        if (uiState.orphanScanResult.hasIssues) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = VaultColors.AccentCrimson,
                                        contentColor = VaultColors.TextPrimary
                                    ) {
                                        Text("${uiState.orphanScanResult.totalIssuesCount}")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Integrity Issues Detected",
                                    tint = VaultColors.AccentAmber
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = "Integrity & Orphans",
                                tint = VaultColors.TextSecondary
                            )
                        }
                    }

                    // 2. Auto-Clean retention settings button
                    IconButton(
                        onClick = { viewModel.showAutoCleanSettings() },
                        modifier = Modifier.testTag("trash_auto_clean_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoDelete,
                            contentDescription = "Auto-Clean Settings",
                            tint = VaultColors.AccentCyan
                        )
                    }

                    // 3. Empty Trash button
                    if (uiState.items.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.requestEmptyTrash() },
                            modifier = Modifier.testTag("empty_trash_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Empty Trash",
                                tint = VaultColors.AccentCrimson
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = VaultColors.Canvas)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            // 1. Storage Accounting & Auto-Clean Policy Header Card
            item {
                StorageHeaderCard(
                    storageSummary = uiState.storageSummary,
                    autoCleanPolicy = uiState.autoCleanPolicy,
                    onPolicyClick = { viewModel.showAutoCleanSettings() }
                )
            }

            // 2. Integrity Review Warning (Review Required)
            if (uiState.orphanScanResult.hasIssues) {
                item {
                    OrphanWarningCard(
                        issuesCount = uiState.orphanScanResult.totalIssuesCount,
                        onReviewClick = { viewModel.showOrphanReview() }
                    )
                }
            }

            // 3. Multi-Select Actions Bar
            if (uiState.items.isNotEmpty()) {
                item {
                    TrashSelectionBar(
                        totalCount = uiState.items.size,
                        selectedCount = uiState.selectedItemIds.size,
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        onBatchRestore = { viewModel.batchRestoreSelected() },
                        onBatchDelete = {
                            val selected = uiState.items
                                .filter { uiState.selectedItemIds.contains(it.item.id) }
                                .map { it.item }
                            viewModel.requestPermanentDelete(selected)
                        }
                    )
                }
            }

            // 4. Trash Items List or Empty Card
            if (uiState.items.isEmpty()) {
                item {
                    TrashEmptyCard()
                }
            } else {
                items(uiState.items, key = { it.item.id }) { trashItem ->
                    val isSelected = uiState.selectedItemIds.contains(trashItem.item.id)
                    TrashItemCard(
                        trashItem = trashItem,
                        isSelected = isSelected,
                        onToggleSelect = { viewModel.toggleSelection(trashItem.item.id) },
                        onRestore = { viewModel.restoreItem(trashItem.item) },
                        onPermanentDelete = { viewModel.requestPermanentDelete(listOf(trashItem.item)) }
                    )
                }
            }

            // Bottom space
            item {
                Spacer(modifier = Modifier.height(spacing.xl))
            }
        }
    }

    // --- Dialogs ---

    // 1. Conflict Resolution Dialog
    uiState.conflictPair?.let { pair ->
        val itemToRestore = pair.first
        val existingItem = pair.second

        AlertDialog(
            onDismissRequest = { viewModel.dismissConflictDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = VaultColors.AccentAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Text(
                        text = "Filename Conflict Detected",
                        color = VaultColors.TextPrimary,
                        style = typography.title
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(
                        text = "An active file named \"${itemToRestore.title}\" already exists in the target directory.",
                        color = VaultColors.TextSecondary,
                        style = typography.bodySmall
                    )
                    Text(
                        text = "Conflicting Active File: ${Formatters.formatBytes(existingItem.sizeBytes)} • Modified ${Formatters.formatTimestamp(existingItem.modifiedAt)}",
                        color = VaultColors.TextTertiary,
                        style = typography.caption
                    )
                    Text(
                        text = "Please select how you wish to resolve this conflict. PrivateVault never silently overwrites your files.",
                        color = VaultColors.TextSecondary,
                        style = typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    Button(
                        onClick = { viewModel.resolveConflictAndRestore(RestoreConflictResolution.KEEP_BOTH) },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("conflict_keep_both")
                    ) {
                        Text("Keep Both (Restore as '${itemToRestore.title.substringBeforeLast('.')} (restored)...')")
                    }
                    Button(
                        onClick = { viewModel.resolveConflictAndRestore(RestoreConflictResolution.REPLACE) },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("conflict_replace")
                    ) {
                        Text("Replace (Move Existing Active File to Trash)")
                    }
                    OutlinedButton(
                        onClick = { viewModel.resolveConflictAndRestore(RestoreConflictResolution.KEEP_EXISTING) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("conflict_keep_existing")
                    ) {
                        Text("Keep Existing (Do Not Restore)")
                    }
                }
            },
            dismissButton = null,
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // 2. Permanent Delete Confirmation Dialog
    uiState.itemsToPermanentDelete?.let { items ->
        val count = items.size
        val totalSize = items.sumOf { it.sizeBytes }

        AlertDialog(
            onDismissRequest = { viewModel.dismissPermanentDeleteDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = VaultColors.AccentCrimson,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Text(
                        text = if (count == 1) "Permanently Delete Item?" else "Permanently Delete $count Items?",
                        color = VaultColors.TextPrimary,
                        style = typography.title
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    if (count == 1) {
                        Text(
                            text = "\"${items.first().title}\" (${Formatters.formatBytes(totalSize)})",
                            color = VaultColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = typography.body
                        )
                    } else {
                        Text(
                            text = "$count files (${Formatters.formatBytes(totalSize)}) selected for permanent deletion.",
                            color = VaultColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = typography.body
                        )
                    }

                    Text(
                        text = "Encrypted objects will be purged from the vault partition and all related database records, bookmarks, and thumbnail caches will be permanently deleted.",
                        color = VaultColors.TextSecondary,
                        style = typography.bodySmall
                    )

                    Surface(
                        color = VaultColors.Canvas,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(spacing.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = VaultColors.TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Text(
                                text = "Notice: Wear-leveling on modern flash storage cannot physically guarantee sector-level overwrites. Files are cryptographically rendered unrecoverable.",
                                color = VaultColors.TextTertiary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executePermanentDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson),
                    modifier = Modifier.testTag("confirm_perm_delete_button")
                ) {
                    Text("Permanently Delete", color = VaultColors.TextPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissPermanentDeleteDialog() }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // 3. Empty Trash Dialog
    if (uiState.showEmptyTrashDialog) {
        val count = uiState.items.size
        val freedSize = uiState.totalTrashBytes

        AlertDialog(
            onDismissRequest = { viewModel.dismissEmptyTrashDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = VaultColors.AccentCrimson,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Text(
                        text = "Empty Secure Trash?",
                        color = VaultColors.TextPrimary,
                        style = typography.title
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(
                        text = "Permanently delete all $count item(s) currently stored in the encrypted trash partition?",
                        color = VaultColors.TextSecondary,
                        style = typography.bodySmall
                    )
                    Text(
                        text = "Estimated storage to release: ${Formatters.formatBytes(freedSize)}",
                        color = VaultColors.AccentCyan,
                        fontWeight = FontWeight.Bold,
                        style = typography.body
                    )
                    Text(
                        text = "If any item fails during deletion, its reference will be preserved safely so data is never silently lost.",
                        color = VaultColors.TextTertiary,
                        style = typography.caption
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeEmptyTrash() },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson),
                    modifier = Modifier.testTag("confirm_empty_trash_button")
                ) {
                    Text("Empty & Shred All", color = VaultColors.TextPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissEmptyTrashDialog() }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // 4. Auto-Clean Retention Policy Settings Dialog
    if (uiState.showAutoCleanDialog) {
        var selectedPolicy by remember { mutableStateOf(uiState.autoCleanPolicy) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissAutoCleanSettings() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AutoDelete,
                        contentDescription = null,
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Text(
                        text = "Auto-Clean Retention Policy",
                        color = VaultColors.TextPrimary,
                        style = typography.title
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(
                        text = "Choose how long deleted items remain recoverable in Secure Trash before being permanently shredded. Runs purely on-device.",
                        color = VaultColors.TextSecondary,
                        style = typography.bodySmall
                    )

                    AutoCleanRetention.entries.forEach { policy ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPolicy = policy }
                                .padding(vertical = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPolicy == policy,
                                onClick = { selectedPolicy = policy },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = VaultColors.AccentCyan,
                                    unselectedColor = VaultColors.TextTertiary
                                )
                            )
                            Spacer(modifier = Modifier.width(spacing.s))
                            Text(
                                text = policy.label,
                                color = if (selectedPolicy == policy) VaultColors.TextPrimary else VaultColors.TextSecondary,
                                style = typography.body
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.updateAutoCleanPolicy(selectedPolicy) },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                    modifier = Modifier.testTag("save_auto_clean_button")
                ) {
                    Text("Save Policy", color = VaultColors.TextPrimary)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissAutoCleanSettings() }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // 5. Storage Integrity & Orphan Review Dialog ("Review Required")
    if (uiState.showOrphanReviewDialog) {
        val orphans = uiState.orphanScanResult

        AlertDialog(
            onDismissRequest = { viewModel.dismissOrphanReview() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (orphans.hasIssues) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (orphans.hasIssues) VaultColors.AccentAmber else VaultColors.AccentEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Text(
                        text = if (orphans.hasIssues) "Review Required" else "Integrity Verified",
                        color = VaultColors.TextPrimary,
                        style = typography.title
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(
                        text = "PrivateVault performs offline consistency verification between encrypted containers, metadata tables, and transient caches.",
                        color = VaultColors.TextSecondary,
                        style = typography.bodySmall
                    )

                    // Unlinked Containers
                    IntegrityItemRow(
                        title = "Unlinked Encrypted Containers",
                        count = orphans.unlinkedContainers.size,
                        description = "Containers on disk with missing database entries. May contain recoverable files.",
                        actionButton = if (orphans.unlinkedContainers.isNotEmpty()) {
                            {
                                Button(
                                    onClick = { viewModel.recoverOrphanContainers() },
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentEmerald),
                                    modifier = Modifier.testTag("recover_orphans_button")
                                ) {
                                    Text("Recover to Root", fontSize = 12.sp)
                                }
                            }
                        } else null
                    )

                    // Incomplete Operations (.tmp_enc)
                    IntegrityItemRow(
                        title = "Incomplete Operations (.tmp_enc)",
                        count = orphans.incompleteOperations.size,
                        description = "Interrupted encryption streams from crashes or cancelled imports.",
                        actionButton = if (orphans.incompleteOperations.isNotEmpty()) {
                            {
                                Button(
                                    onClick = { viewModel.purgeIncompleteOperations() },
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson),
                                    modifier = Modifier.testTag("purge_incomplete_button")
                                ) {
                                    Text("Purge Temp", fontSize = 12.sp)
                                }
                            }
                        } else null
                    )

                    // Missing Physical Files
                    IntegrityItemRow(
                        title = "Missing Physical Files",
                        count = orphans.missingPhysicalFiles.size,
                        description = "Metadata records whose underlying encrypted container was removed.",
                        actionButton = if (orphans.missingPhysicalFiles.isNotEmpty()) {
                            {
                                Button(
                                    onClick = { viewModel.purgeMissingMetadata() },
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber),
                                    modifier = Modifier.testTag("purge_missing_meta_button")
                                ) {
                                    Text("Clean Records", fontSize = 12.sp)
                                }
                            }
                        } else null
                    )

                    // Abandoned Transients
                    IntegrityItemRow(
                        title = "Transient Working Files",
                        count = orphans.abandonedTransientFiles.size,
                        description = "Cached decrypted streams in app-private transient directory.",
                        actionButton = null
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissOrphanReview() },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceGraphite)
                ) {
                    Text("Close / Keep Untouched", color = VaultColors.TextPrimary)
                }
            },
            dismissButton = null,
            containerColor = VaultColors.SurfaceElevated
        )
    }
}

@Composable
private fun StorageHeaderCard(
    storageSummary: com.example.core.lifecycle.StorageSummary,
    autoCleanPolicy: AutoCleanRetention,
    onPolicyClick: () -> Unit
) {
    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trash_storage_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.m),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = VaultColors.Titanium,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = "Encrypted Lifecycle & Storage",
                        style = typography.bodySmall,
                        color = VaultColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Auto-Clean Policy Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VaultColors.SurfaceElevated,
                    modifier = Modifier
                        .clickable { onPolicyClick() }
                        .testTag("auto_clean_policy_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.s, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoDelete,
                            contentDescription = null,
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Auto-Clean: ${autoCleanPolicy.label.substringBefore(" ")}",
                            fontSize = 11.sp,
                            color = VaultColors.AccentCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Storage Breakdown Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                StoragePill(
                    label = "Vault",
                    value = Formatters.formatBytes(storageSummary.vaultBytes),
                    color = VaultColors.AccentCyan,
                    modifier = Modifier.weight(1f)
                )
                StoragePill(
                    label = "Trash",
                    value = Formatters.formatBytes(storageSummary.trashBytes),
                    color = VaultColors.AccentCrimson,
                    modifier = Modifier.weight(1f)
                )
                StoragePill(
                    label = "Cache",
                    value = Formatters.formatBytes(storageSummary.temporaryBytes),
                    color = VaultColors.AccentAmber,
                    modifier = Modifier.weight(1f)
                )
                StoragePill(
                    label = "Free",
                    value = Formatters.formatBytes(storageSummary.availableDeviceBytes),
                    color = VaultColors.AccentEmerald,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StoragePill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VaultColors.SurfaceElevated,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = VaultColors.TextTertiary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OrphanWarningCard(
    issuesCount: Int,
    onReviewClick: () -> Unit
) {
    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VaultColors.AccentCrimson.copy(alpha = 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VaultColors.AccentCrimson.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onReviewClick() }
            .testTag("orphan_warning_card")
    ) {
        Row(
            modifier = Modifier.padding(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = VaultColors.AccentAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(spacing.s))
                Column {
                    Text(
                        text = "Review Required ($issuesCount Anomalies Detected)",
                        style = typography.body,
                        color = VaultColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Inconsistencies detected. Tap to inspect and recover data safely.",
                        style = typography.caption,
                        color = VaultColors.TextSecondary
                    )
                }
            }

            Button(
                onClick = onReviewClick,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber),
                modifier = Modifier.testTag("review_required_button")
            ) {
                Text("Review", color = VaultColors.TextPrimary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TrashSelectionBar(
    totalCount: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchRestore: () -> Unit,
    onBatchDelete: () -> Unit
) {
    val spacing = LocalVaultSpacing.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = VaultColors.SurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trash_selection_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.m, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selectedCount == totalCount && totalCount > 0,
                    onCheckedChange = { isChecked ->
                        if (isChecked) onSelectAll() else onClearSelection()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = VaultColors.AccentCyan,
                        uncheckedColor = VaultColors.TextTertiary
                    ),
                    modifier = Modifier.testTag("trash_select_all_checkbox")
                )
                Text(
                    text = if (selectedCount > 0) "$selectedCount Selected" else "Select All",
                    fontSize = 12.sp,
                    color = VaultColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (selectedCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Button(
                        onClick = onBatchRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentEmerald),
                        modifier = Modifier.testTag("batch_restore_button")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onBatchDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson),
                        modifier = Modifier.testTag("batch_perm_delete_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shred", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashItemCard(
    trashItem: VaultTrashItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val item = trashItem.item
    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trash_item_${item.id}")
            .clickable { onToggleSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = VaultColors.AccentCyan,
                    uncheckedColor = VaultColors.TextTertiary
                ),
                modifier = Modifier.testTag("trash_checkbox_${item.id}")
            )

            Spacer(modifier = Modifier.width(spacing.xs))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = typography.body,
                    color = VaultColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Metadata details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.category,
                        fontSize = 11.sp,
                        color = VaultColors.AccentCyan,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = VaultColors.TextTertiary
                    )
                    Text(
                        text = Formatters.formatBytes(item.sizeBytes),
                        fontSize = 11.sp,
                        color = VaultColors.TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Deleted timestamp and original folder
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = VaultColors.TextTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "From: ${trashItem.originalFolderName}",
                        fontSize = 11.sp,
                        color = VaultColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                item.deletedAt?.let { deletedTime ->
                    Text(
                        text = "Deleted: ${Formatters.formatTimestamp(deletedTime)}",
                        fontSize = 10.sp,
                        color = VaultColors.TextTertiary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                IconButton(
                    onClick = onRestore,
                    modifier = Modifier.testTag("restore_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = "Restore Item",
                        tint = VaultColors.AccentEmerald
                    )
                }

                IconButton(
                    onClick = onPermanentDelete,
                    modifier = Modifier.testTag("perm_delete_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = "Permanently Delete",
                        tint = VaultColors.AccentCrimson
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashEmptyCard() {
    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trash_empty_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = VaultColors.AccentEmerald,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(spacing.m))
            Text(
                text = "Trash is Empty",
                style = typography.title,
                color = VaultColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                text = "No deleted items currently stored in the encrypted trash partition. Deleted files will remain recoverable here until auto-cleaned or manually shredded.",
                style = typography.bodySmall,
                color = VaultColors.TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IntegrityItemRow(
    title: String,
    count: Int,
    description: String,
    actionButton: (@Composable () -> Unit)?
) {
    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = VaultColors.Canvas,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = typography.bodySmall,
                    color = VaultColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$count item(s)",
                    fontSize = 12.sp,
                    color = if (count > 0) VaultColors.AccentAmber else VaultColors.TextTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = description,
                style = typography.caption,
                color = VaultColors.TextSecondary
            )
            if (actionButton != null) {
                Spacer(modifier = Modifier.height(spacing.xs))
                actionButton()
            }
        }
    }
}
