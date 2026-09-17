package com.example.feature.vault

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.storage.VaultCategory
import com.example.core.ui.VaultGlassCard
import com.example.core.util.Formatters
import com.example.feature.vault.export_feature.SecureExportDialog
import com.example.feature.vault.import_feature.SecureImportController
import com.example.feature.vault.import_feature.SecureImportDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    category: VaultCategory,
    items: List<VaultItemEntity>,
    repository: VaultRepository,
    onBackClick: () -> Unit,
    onOpenFile: (itemId: Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalVaultSpacing.current
    val typography = LocalVaultTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importController = remember {
        SecureImportController(
            context = context,
            repository = repository,
            coroutineScope = scope
        )
    }
    val importState by importController.importState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var itemToExport by remember { mutableStateOf<VaultItemEntity?>(null) }
    var itemToRename by remember { mutableStateOf<VaultItemEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var previewTextItem by remember { mutableStateOf<Pair<String, String>?>(null) }

    // SAF Document Picker for multi-file secure import
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            importController.startImport(uris, targetCategory = category)
        }
    }

    // SAF Export Document Creator
    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val targetItem = itemToExport
        if (destUri != null && targetItem != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        val result = repository.exportItemToStream(targetItem, outStream)
                        if (result.isSuccess) {
                            Toast.makeText(
                                context,
                                "Successfully exported ${targetItem.title}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Export failed: ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export stream error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    itemToExport = null
                }
            }
        } else {
            itemToExport = null
        }
    }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.title.contains(searchQuery, ignoreCase = true) || it.tags.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        modifier = modifier.testTag("category_detail_screen"),
        containerColor = VaultColors.Canvas,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = category.title,
                            style = typography.title,
                            color = VaultColors.TextPrimary
                        )
                        Text(
                            text = "${items.size} Items Secured",
                            style = typography.caption,
                            color = VaultColors.TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("category_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultColors.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.testTag("category_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = if (isSearchActive) VaultColors.AccentEmerald else VaultColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = VaultColors.Canvas)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { safPickerLauncher.launch(arrayOf("*/*")) },
                containerColor = VaultColors.AccentEmerald,
                contentColor = VaultColors.Canvas,
                modifier = Modifier.testTag("category_fab_import")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Import Files")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            if (isSearchActive) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .testTag("category_search_input")
                            .fillMaxWidth(),
                        placeholder = { Text("Search by filename or tags...", color = VaultColors.TextTertiary) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = VaultColors.Titanium) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = VaultColors.TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultColors.AccentEmerald,
                            unfocusedBorderColor = VaultColors.GlassBorderSubtle,
                            focusedTextColor = VaultColors.TextPrimary,
                            unfocusedTextColor = VaultColors.TextPrimary
                        )
                    )
                }
            }

            item {
                VaultGlassCard(
                    modifier = Modifier
                        .testTag("category_status_card")
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.m),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = VaultColors.AccentEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.m))
                        Column {
                            Text(
                                text = "ENCRYPTED PARTITION ACTIVE",
                                style = typography.caption.copy(fontWeight = FontWeight.Bold),
                                color = VaultColors.AccentEmerald
                            )
                            Text(
                                text = "Supported: ${category.extensionHint}",
                                style = typography.bodySmall,
                                color = VaultColors.TextSecondary
                            )
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    VaultGlassCard(
                        modifier = Modifier
                            .testTag("category_empty_card")
                            .fillMaxWidth()
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
                                tint = VaultColors.Titanium,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(spacing.m))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No Matching Files" else "No ${category.title} Stored",
                                style = typography.title,
                                color = VaultColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Try adjusting your search terms."
                                else "Import files into this hardware-isolated vault. Files are encrypted with AES-256-GCM directly on import.",
                                style = typography.bodySmall,
                                color = VaultColors.TextTertiary,
                                textAlign = TextAlign.Center
                            )
                            if (searchQuery.isEmpty()) {
                                Spacer(modifier = Modifier.height(spacing.l))
                                Button(
                                    onClick = { safPickerLauncher.launch(arrayOf("*/*")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentEmerald),
                                    modifier = Modifier.testTag("category_empty_import_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = VaultColors.Canvas)
                                    Spacer(modifier = Modifier.width(spacing.xs))
                                    Text("Import ${category.title}", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    VaultGlassCard(
                        modifier = Modifier
                            .testTag("vault_item_card_${item.id}")
                            .fillMaxWidth()
                            .clickable {
                                onOpenFile(item.id)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.m),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = typography.title,
                                    color = VaultColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${Formatters.formatBytes(item.sizeBytes)} • ${Formatters.formatTimestamp(item.createdAt)}",
                                    style = typography.bodySmall,
                                    color = VaultColors.TextTertiary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { scope.launch { repository.toggleFavorite(item) } },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Favorite",
                                        tint = if (item.isFavorite) VaultColors.AccentAmber else VaultColors.Titanium,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier
                                            .testTag("item_menu_${item.id}")
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Options",
                                            tint = VaultColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Secure Export (SAF)") },
                                            onClick = {
                                                menuExpanded = false
                                                itemToExport = item
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Filled.FileDownload, contentDescription = null, tint = VaultColors.AccentAmber)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                menuExpanded = false
                                                renameInput = item.title
                                                itemToRename = item
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("Move to Trash", color = VaultColors.AccentCrimson) },
                                            onClick = {
                                                menuExpanded = false
                                                scope.launch { repository.moveToTrash(item) }
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Delete, contentDescription = null, tint = VaultColors.AccentCrimson)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Secure Import Progress Dialog
    SecureImportDialog(
        state = importState,
        onCancel = { importController.cancelImport() },
        onDismiss = { importController.dismiss() }
    )

    // Secure Export Confirmation Dialog
    itemToExport?.let { exportItem ->
        SecureExportDialog(
            item = exportItem,
            onConfirmExport = {
                safExportLauncher.launch(exportItem.title)
            },
            onDismiss = { itemToExport = null }
        )
    }

    // Rename Dialog
    itemToRename?.let { renameTarget ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename Vault Item", color = VaultColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Filename") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            scope.launch {
                                repository.renameItem(renameTarget, renameInput.trim())
                                itemToRename = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentEmerald)
                ) {
                    Text("Save", color = VaultColors.Canvas)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToRename = null }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // Instant Preview Dialog for Text/Code
    previewTextItem?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { previewTextItem = null },
            title = {
                Text(
                    text = title,
                    color = VaultColors.TextPrimary,
                    style = typography.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = content.take(4000) + if (content.length > 4000) "\n\n[Truncated for security display...]" else "",
                            color = VaultColors.TextSecondary,
                            style = typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewTextItem = null },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentEmerald)
                ) {
                    Text("Close", color = VaultColors.Canvas)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }
}
