package com.example.feature.viewer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.VaultFolderEntity
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.designsystem.VaultColors
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.VaultCategory
import com.example.feature.vault.DeleteConfirmationDialog
import com.example.feature.vault.EditTagsDialog
import com.example.feature.vault.ExportConfirmationDialog
import com.example.feature.vault.MoveToFolderDialog
import com.example.feature.vault.RenameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal Secure File Viewer Screen.
 *
 * Security Invariants:
 * - Session Lock Guard: If [SessionSecurityManager] locks the vault, this screen immediately terminates.
 * - Hardware Key Protection: All content is loaded exclusively through [VaultRepository] inside an unlocked session.
 * - Zero Disk Residue: In-memory content is never written to disk. Any transient working files are tracked
 *   and destroyed using DoD-standard multi-pass shredding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureFileViewerScreen(
    itemId: Long,
    repository: VaultRepository,
    sessionManager: SessionSecurityManager,
    onNavigateBack: () -> Unit,
    onOpenInStudioEditor: ((Long) -> Unit)? = null,
    onOpenInArchiveViewer: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lockState by sessionManager.lockState.collectAsState()

    // Immediate session lock enforcement
    LaunchedEffect(lockState) {
        if (lockState !is LockState.Unlocked) {
            onNavigateBack()
        }
    }

    var item by remember { mutableStateOf<VaultItemEntity?>(null) }
    var folderName by remember { mutableStateOf("Vault Root") }
    var allFolders by remember { mutableStateOf<List<VaultFolderEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Content decodings
    var inMemoryBytes by remember { mutableStateOf<ByteArray?>(null) }
    var inMemoryText by remember { mutableStateOf<String?>(null) }
    var transientFile by remember { mutableStateOf<File?>(null) }

    // Dialog and sheet states
    var showMenu by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showExportConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Export Document Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(item?.mimeType ?: "*/*")
    ) { uri: Uri? ->
        if (uri != null && item != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val result = repository.exportItemToStream(item!!, os)
                        if (result.isSuccess) {
                            Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Load metadata and load file payload
    fun loadFileData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val currentItem = repository.getItemById(itemId)
                if (currentItem == null) {
                    errorMessage = "Item not found in encrypted vault"
                    isLoading = false
                    return@launch
                }
                item = currentItem
                repository.recordFileAccess(currentItem)

                // Load folder name
                if (currentItem.folderId != null) {
                    val folders = withContext(Dispatchers.IO) { repository.getAllFoldersList() }
                    allFolders = folders
                    val f = folders.find { it.id == currentItem.folderId }
                    folderName = f?.name ?: "Unknown Folder"
                } else {
                    allFolders = withContext(Dispatchers.IO) { repository.getAllFoldersList() }
                    folderName = "Vault Root"
                }

                // Category routing for decryption
                val category = try {
                    VaultCategory.valueOf(currentItem.category)
                } catch (_: Exception) {
                    VaultCategory.OTHER
                }

                val ext = currentItem.title.substringAfterLast('.', "").lowercase()

                when {
                    // Documents (specifically PDF)
                    category == VaultCategory.DOCUMENT && ext == "pdf" -> {
                        val previewResult = repository.createTransientPreview(currentItem)
                        if (previewResult.isSuccess) {
                            transientFile = previewResult.getOrNull()
                        } else {
                            errorMessage = "Decryption failed: ${previewResult.exceptionOrNull()?.message}"
                        }
                    }

                    // Images: Decrypt directly into memory
                    category == VaultCategory.IMAGE -> {
                        val bytesResult = repository.decryptItemBytes(currentItem)
                        if (bytesResult.isSuccess) {
                            inMemoryBytes = bytesResult.getOrNull()
                        } else {
                            errorMessage = "Decryption failed"
                        }
                    }

                    // Text files
                    category == VaultCategory.TEXT || (category == VaultCategory.DOCUMENT && ext in listOf("txt", "md", "csv", "rtf", "log")) -> {
                        val bytesResult = repository.decryptItemBytes(currentItem)
                        if (bytesResult.isSuccess) {
                            val bytes = bytesResult.getOrNull() ?: ByteArray(0)
                            inMemoryText = String(bytes, Charsets.UTF_8)
                        } else {
                            errorMessage = "Decryption failed"
                        }
                    }

                    // Word DOCX files
                    category == VaultCategory.DOCUMENT && ext in listOf("docx", "doc") -> {
                        val bytesResult = repository.decryptItemBytes(currentItem)
                        if (bytesResult.isSuccess) {
                            val bytes = bytesResult.getOrNull() ?: ByteArray(0)
                            inMemoryText = if (ext == "docx") {
                                com.example.feature.documents.DocxTextExtractor.extractText(bytes)
                            } else {
                                String(bytes, Charsets.UTF_8)
                            }
                        } else {
                            errorMessage = "Decryption failed"
                        }
                    }

                    // Code files
                    category == VaultCategory.CODE -> {
                        val bytesResult = repository.decryptItemBytes(currentItem)
                        if (bytesResult.isSuccess) {
                            val bytes = bytesResult.getOrNull() ?: ByteArray(0)
                            inMemoryText = String(bytes, Charsets.UTF_8)
                        } else {
                            errorMessage = "Decryption failed"
                        }
                    }

                    // Archives / ZIP
                    category == VaultCategory.ZIP -> {
                        val bytesResult = repository.decryptItemBytes(currentItem)
                        if (bytesResult.isSuccess) {
                            inMemoryBytes = bytesResult.getOrNull()
                        } else {
                            errorMessage = "Decryption failed"
                        }
                    }

                    // Audio
                    category == VaultCategory.AUDIO -> {
                        val previewResult = repository.createTransientPreview(currentItem)
                        if (previewResult.isSuccess) {
                            transientFile = previewResult.getOrNull()
                        } else {
                            errorMessage = "Decryption failed"
                        }
                    }

                    // Video
                    category == VaultCategory.VIDEO -> {
                        val previewResult = repository.createTransientPreview(currentItem)
                        if (previewResult.isSuccess) {
                            transientFile = previewResult.getOrNull()
                        } else {
                            errorMessage = "Decryption failed"
                        }
                    }

                    // Other
                    else -> {
                        // Keep item in memory for fallback viewer
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(itemId) {
        loadFileData()
    }

    // Cleanup ephemeral transient file on screen exit
    DisposableEffect(transientFile) {
        onDispose {
            transientFile?.let {
                // If it wasn't already handled by viewer
                com.example.core.storage.FileShredder.shredAndPurge(it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = item?.title ?: "Secure Viewer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VaultColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item?.let { "${it.category} · ${formatBytes(it.sizeBytes)}" } ?: "Encrypted",
                            fontSize = 11.sp,
                            color = VaultColors.TextSecondary
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
                    item?.let { currentItem ->
                        // Favorite toggle
                        IconButton(onClick = {
                            scope.launch {
                                repository.toggleFavorite(currentItem)
                                item = currentItem.copy(isFavorite = !currentItem.isFavorite)
                            }
                        }) {
                            Icon(
                                imageVector = if (currentItem.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Toggle favorite",
                                tint = if (currentItem.isFavorite) VaultColors.AccentAmber else VaultColors.TextSecondary
                            )
                        }

                        // Open in Studio Editor for code and text files
                        if ((currentItem.category == VaultCategory.CODE.name || currentItem.category == VaultCategory.TEXT.name) && onOpenInStudioEditor != null) {
                            IconButton(onClick = { onOpenInStudioEditor(currentItem.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Open in Studio Editor",
                                    tint = VaultColors.AccentCyan
                                )
                            }
                        }

                        // Open in Archive Studio for ZIP archives
                        if (currentItem.category == VaultCategory.ZIP.name && onOpenInArchiveViewer != null) {
                            IconButton(onClick = { onOpenInArchiveViewer(currentItem.id) }) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = "Open in Archive Browser",
                                    tint = VaultColors.AccentCyan
                                )
                            }
                        }

                        // Quick Lock
                        IconButton(onClick = { sessionManager.lockVault() }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Vault",
                                tint = VaultColors.AccentCyan
                            )
                        }

                        // Overflow menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = VaultColors.TextPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(VaultColors.SurfaceElevated)
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = VaultColors.AccentCyan) },
                                    text = { Text("File Details", color = VaultColors.TextPrimary) },
                                    onClick = {
                                        showMenu = false
                                        showInfoSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = VaultColors.TextPrimary) },
                                    text = { Text("Rename", color = VaultColors.TextPrimary) },
                                    onClick = {
                                        showMenu = false
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null, tint = VaultColors.TextPrimary) },
                                    text = { Text("Move to Folder", color = VaultColors.TextPrimary) },
                                    onClick = {
                                        showMenu = false
                                        showMoveDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = VaultColors.TextPrimary) },
                                    text = { Text("Make a Copy", color = VaultColors.TextPrimary) },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            val res = repository.copyItem(currentItem)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Encrypted copy created", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed creating copy", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Label, null, tint = VaultColors.TextPrimary) },
                                    text = { Text("Edit Tags", color = VaultColors.TextPrimary) },
                                    onClick = {
                                        showMenu = false
                                        showTagsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = VaultColors.AccentAmber) },
                                    text = { Text("Export Plaintext", color = VaultColors.AccentAmber) },
                                    onClick = {
                                        showMenu = false
                                        showExportConfirmDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = VaultColors.AccentCrimson) },
                                    text = { Text("Move to Trash", color = VaultColors.AccentCrimson) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultColors.SurfaceElevated
                )
            )
        },
        containerColor = VaultColors.Canvas,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = VaultColors.AccentCyan)
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "Error opening file",
                    color = VaultColors.AccentCrimson,
                    fontSize = 14.sp
                )
            } else {
                val current = item
                if (current == null) {
                    Text("Item not found", color = VaultColors.TextSecondary)
                } else {
                    val category = try {
                        VaultCategory.valueOf(current.category)
                    } catch (_: Exception) {
                        VaultCategory.OTHER
                    }
                    val ext = current.title.substringAfterLast('.', "").lowercase()

                    when {
                        // PDF
                        category == VaultCategory.DOCUMENT && ext == "pdf" -> {
                            transientFile?.let { file ->
                                SecurePdfViewer(
                                    transientPdfFile = file,
                                    itemId = current.id,
                                    documentTitle = current.title,
                                    repository = repository
                                )
                            } ?: Text("Failed initializing PDF viewer", color = VaultColors.AccentCrimson)
                        }

                        // Images
                        category == VaultCategory.IMAGE -> {
                            inMemoryBytes?.let { bytes ->
                                SecureImageViewer(imageBytes = bytes)
                            } ?: Text("Failed loading image bytes", color = VaultColors.AccentCrimson)
                        }

                        // Text & Documents
                        category == VaultCategory.TEXT || (category == VaultCategory.DOCUMENT && ext in listOf("txt", "md", "csv", "rtf", "log", "docx", "doc")) -> {
                            inMemoryText?.let { txt ->
                                SecureTextViewer(
                                    initialText = txt,
                                    fileName = current.title,
                                    onSaveContent = { updatedText ->
                                        scope.launch {
                                            val res = repository.updateFileContent(current, updatedText)
                                            if (res.isSuccess) {
                                                item = res.getOrNull()
                                                Toast.makeText(context, "Document securely encrypted and saved", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed saving changes", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    onNavigateBack = onNavigateBack
                                )
                            } ?: Text("Failed loading text content", color = VaultColors.AccentCrimson)
                        }

                        // Code
                        category == VaultCategory.CODE -> {
                            inMemoryText?.let { code ->
                                SecureCodeViewer(
                                    initialCode = code,
                                    fileName = current.title,
                                    onSaveContent = { updatedCode ->
                                        scope.launch {
                                            val res = repository.updateFileContent(current, updatedCode)
                                            if (res.isSuccess) {
                                                item = res.getOrNull()
                                                Toast.makeText(context, "Code securely encrypted and saved", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed saving code", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )
                            } ?: Text("Failed loading code content", color = VaultColors.AccentCrimson)
                        }

                        // Archive
                        category == VaultCategory.ZIP -> {
                            inMemoryBytes?.let { bytes ->
                                SecureArchiveViewer(
                                    archiveBytes = bytes,
                                    onExtractEntryToVault = { entryName, entryBytes ->
                                        scope.launch {
                                            val subCategory = VaultCategory.fromFileNameAndMime(entryName, "application/octet-stream")
                                            val res = repository.importItem(
                                                title = entryName,
                                                category = subCategory,
                                                mimeType = subCategory.defaultMime,
                                                inputStream = java.io.ByteArrayInputStream(entryBytes),
                                                folderId = current.folderId,
                                                expectedSizeBytes = entryBytes.size.toLong()
                                            )
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Extracted '$entryName' into vault", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Extraction failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onOpenFullBrowser = onOpenInArchiveViewer?.let { cb -> { cb(current.id) } }
                                )
                            } ?: Text("Failed loading archive payload", color = VaultColors.AccentCrimson)
                        }

                        // Audio
                        category == VaultCategory.AUDIO -> {
                            transientFile?.let { file ->
                                SecureAudioPlayer(transientAudioFile = file, fileName = current.title)
                            } ?: Text("Failed initializing audio playback", color = VaultColors.AccentCrimson)
                        }

                        // Video
                        category == VaultCategory.VIDEO -> {
                            transientFile?.let { file ->
                                SecureVideoPlayer(transientVideoFile = file)
                            } ?: Text("Failed initializing video playback", color = VaultColors.AccentCrimson)
                        }

                        // Unsupported / fallback
                        else -> {
                            SecureUnsupportedViewer(
                                item = current,
                                onExport = { showExportConfirmDialog = true },
                                onShowInfo = { showInfoSheet = true },
                                onDelete = { showDeleteConfirmDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheets & Dialogs
    item?.let { currentItem ->
        if (showInfoSheet) {
            VaultFileInfoSheet(
                item = currentItem,
                folderName = folderName,
                onDismiss = { showInfoSheet = false }
            )
        }

        if (showRenameDialog) {
            RenameDialog(
                currentName = currentItem.title,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    showRenameDialog = false
                    scope.launch {
                        repository.renameItem(currentItem, newName)
                        item = currentItem.copy(title = newName)
                    }
                }
            )
        }

        if (showMoveDialog) {
            MoveToFolderDialog(
                folders = allFolders,
                currentFolderId = currentItem.folderId,
                onDismiss = { showMoveDialog = false },
                onFolderSelected = { targetFolderId ->
                    showMoveDialog = false
                    scope.launch {
                        repository.moveItemToFolder(currentItem, targetFolderId)
                        item = currentItem.copy(folderId = targetFolderId)
                        folderName = allFolders.find { it.id == targetFolderId }?.name ?: "Vault Root"
                        Toast.makeText(context, "Item moved", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        if (showTagsDialog) {
            EditTagsDialog(
                currentTags = currentItem.tags,
                onDismiss = { showTagsDialog = false },
                onSave = { updatedTags ->
                    showTagsDialog = false
                    scope.launch {
                        repository.updateItemTags(currentItem, updatedTags)
                        item = currentItem.copy(tags = updatedTags)
                    }
                }
            )
        }

        if (showExportConfirmDialog) {
            ExportConfirmationDialog(
                fileName = currentItem.title,
                onDismiss = { showExportConfirmDialog = false },
                onConfirm = {
                    showExportConfirmDialog = false
                    exportLauncher.launch(currentItem.title)
                }
            )
        }

        if (showDeleteConfirmDialog) {
            DeleteConfirmationDialog(
                itemCount = 1,
                isPermanent = false,
                onDismiss = { showDeleteConfirmDialog = false },
                onConfirm = {
                    showDeleteConfirmDialog = false
                    scope.launch {
                        repository.moveToTrash(currentItem)
                        Toast.makeText(context, "Moved to Encrypted Trash", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                }
            )
        }
    }
}
