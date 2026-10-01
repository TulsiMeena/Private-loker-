package com.example.feature.vault

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.database.VaultItemEntity
import com.example.core.designsystem.VaultColors
import com.example.core.storage.VaultCategory
import com.example.core.ui.SecurityPill
import com.example.feature.settings.SettingsScreen
import com.example.feature.vault.import_feature.SecureImportController
import com.example.feature.vault.import_feature.SecureImportDialog
import com.example.feature.vault.trash.TrashScreen
import com.example.feature.viewer.formatBytes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHomeScreen(
    viewModel: VaultHomeViewModel,
    sessionManager: com.example.core.security.SessionSecurityManager? = null,
    onCategoryClick: (VaultCategory) -> Unit,
    onSettingsClick: () -> Unit,
    onTrashClick: () -> Unit,
    onOpenFile: (itemId: Long) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToOrganize: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    val effectiveSessionManager = remember(sessionManager, context, scope) {
        sessionManager ?: com.example.core.security.SessionSecurityManager(
            context = context,
            secureKeyManager = com.example.core.security.SecureKeyManager(),
            coroutineScope = scope
        )
    }

    val isDecoySession by effectiveSessionManager.isDecoySession.collectAsState()

    val decoyItems = remember {
        listOf(
            VaultItemEntity(
                id = -901L,
                title = "Weekly Grocery List & Recipes.txt",
                category = VaultCategory.TEXT.name,
                encryptedPath = "decoy_1.enc",
                mimeType = "text/plain",
                sizeBytes = 2450L,
                createdAt = System.currentTimeMillis() - 86400000L,
                modifiedAt = System.currentTimeMillis() - 86400000L,
                isFavorite = true
            ),
            VaultItemEntity(
                id = -902L,
                title = "Vacation Travel Itinerary 2026.pdf",
                category = VaultCategory.DOCUMENT.name,
                encryptedPath = "decoy_2.enc",
                mimeType = "application/pdf",
                sizeBytes = 48200L,
                createdAt = System.currentTimeMillis() - 172800000L,
                modifiedAt = System.currentTimeMillis() - 172800000L,
                isFavorite = false
            ),
            VaultItemEntity(
                id = -903L,
                title = "Home Maintenance Checklist.txt",
                category = VaultCategory.TEXT.name,
                encryptedPath = "decoy_3.enc",
                mimeType = "text/plain",
                sizeBytes = 5120L,
                createdAt = System.currentTimeMillis() - 259200000L,
                modifiedAt = System.currentTimeMillis() - 259200000L,
                isFavorite = false
            )
        )
    }

    val effectiveUiState = if (isDecoySession) {
        uiState.copy(
            recentItems = decoyItems,
            favoriteItems = decoyItems.filter { it.isFavorite },
            totalItemCount = 3,
            totalSizeBytes = 55770L,
            categoryStats = mapOf(
                VaultCategory.TEXT to CategoryStat(VaultCategory.TEXT, 2, 7570L),
                VaultCategory.DOCUMENT to CategoryStat(VaultCategory.DOCUMENT, 1, 48200L)
            )
        )
    } else {
        uiState
    }

    val safeOpenFile: (Long) -> Unit = { id ->
        if (id < 0L) {
            Toast.makeText(context, "Decoy Document: Safe preview mode", Toast.LENGTH_SHORT).show()
        } else {
            onOpenFile(id)
        }
    }

    // File Browser ViewModel instance
    val browserViewModel: VaultFileBrowserViewModel = remember {
        VaultFileBrowserViewModel(
            repository = viewModel.repository,
            sessionManager = effectiveSessionManager
        )
    }

    // Direct Import Controller
    val importController = remember {
        SecureImportController(
            context = context,
            repository = viewModel.repository,
            coroutineScope = scope
        )
    }
    val importState by importController.importState.collectAsState()

    // SAF Document Picker (Files, Photos, PDF, All)
    val safMultiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        effectiveSessionManager.setSuppressAutoLock(false)
        if (uris.isNotEmpty()) {
            importController.startImport(uris)
        }
    }

    // Import Options Chooser Bottom Sheet State
    var showImportChooserSheet by remember { mutableStateOf(false) }

    // Modal creation dialogs
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var showCreateCodeDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(VaultColors.SurfaceGraphite)
                                .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PrivateVault",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VaultColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "ENCLAVE",
                                        color = VaultColors.AccentCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                    }
                },
                actions = {
                    // Quick Import Button
                    IconButton(
                        onClick = { showImportChooserSheet = true },
                        modifier = Modifier.testTag("home_top_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Import Files / Media / PDF",
                            tint = VaultColors.AccentCyan
                        )
                    }

                    // Universal Search Button
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.testTag("home_top_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Universal Search",
                            tint = VaultColors.TextPrimary
                        )
                    }

                    // Smart Organization Button
                    IconButton(
                        onClick = onNavigateToOrganize,
                        modifier = Modifier.testTag("home_top_organize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesomeMosaic,
                            contentDescription = "Smart Organization",
                            tint = VaultColors.AccentAmber
                        )
                    }

                    // Quick Lock Button
                    IconButton(
                        onClick = { viewModel.lockVault() },
                        modifier = Modifier.testTag("home_quick_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault Now",
                            tint = VaultColors.AccentCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultColors.SurfaceElevated
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = VaultColors.SurfaceElevated,
                modifier = Modifier.border(1.dp, VaultColors.GlassBorderSubtle)
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == WorkspaceTab.WORKSPACE,
                    onClick = { viewModel.selectTab(WorkspaceTab.WORKSPACE) },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Workspace") },
                    label = { Text("Workspace", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultColors.AccentCyan,
                        selectedTextColor = VaultColors.AccentCyan,
                        indicatorColor = VaultColors.SurfaceGraphite,
                        unselectedIconColor = VaultColors.TextSecondary,
                        unselectedTextColor = VaultColors.TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == WorkspaceTab.FILES,
                    onClick = { viewModel.selectTab(WorkspaceTab.FILES) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                    label = { Text("Files", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultColors.AccentCyan,
                        selectedTextColor = VaultColors.AccentCyan,
                        indicatorColor = VaultColors.SurfaceGraphite,
                        unselectedIconColor = VaultColors.TextSecondary,
                        unselectedTextColor = VaultColors.TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == WorkspaceTab.FAVORITES,
                    onClick = { viewModel.selectTab(WorkspaceTab.FAVORITES) },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                    label = { Text("Favorites", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultColors.AccentAmber,
                        selectedTextColor = VaultColors.AccentAmber,
                        indicatorColor = VaultColors.SurfaceGraphite,
                        unselectedIconColor = VaultColors.TextSecondary,
                        unselectedTextColor = VaultColors.TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == WorkspaceTab.TRASH,
                    onClick = { viewModel.selectTab(WorkspaceTab.TRASH) },
                    icon = {
                        if (uiState.trashCount > 0) {
                            BadgedBox(badge = { Badge { Text(uiState.trashCount.toString()) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Trash")
                            }
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = "Trash")
                        }
                    },
                    label = { Text("Trash", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultColors.AccentCrimson,
                        selectedTextColor = VaultColors.AccentCrimson,
                        indicatorColor = VaultColors.SurfaceGraphite,
                        unselectedIconColor = VaultColors.TextSecondary,
                        unselectedTextColor = VaultColors.TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == WorkspaceTab.SETTINGS,
                    onClick = { viewModel.selectTab(WorkspaceTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VaultColors.AccentCyan,
                        selectedTextColor = VaultColors.AccentCyan,
                        indicatorColor = VaultColors.SurfaceGraphite,
                        unselectedIconColor = VaultColors.TextSecondary,
                        unselectedTextColor = VaultColors.TextSecondary
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportChooserSheet = true },
                containerColor = VaultColors.AccentCyan,
                contentColor = Color.Black,
                modifier = Modifier.testTag("home_import_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = "Upload Files or Media"
                )
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
            AnimatedContent(
                targetState = uiState.currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "workspace_tab_switch"
            ) { tab ->
                when (tab) {
                    WorkspaceTab.WORKSPACE -> {
                        WorkspaceDashboardContent(
                            uiState = effectiveUiState,
                            onImportClick = { showImportChooserSheet = true },
                            onCreateNoteClick = { onCategoryClick(VaultCategory.TEXT) },
                            onCreateCodeClick = { onCategoryClick(VaultCategory.CODE) },
                            onCreateFolderClick = { showCreateFolderDialog = true },
                            onMediaCenterClick = { onCategoryClick(VaultCategory.IMAGE) },
                            onCategoryClick = { cat ->
                                onCategoryClick(cat)
                            },
                            onOpenFile = safeOpenFile,
                            onClearRecent = { viewModel.clearRecentHistory() },
                            onNavigateToSearch = onNavigateToSearch,
                            onNavigateToOrganize = onNavigateToOrganize
                        )
                    }

                    WorkspaceTab.FILES -> {
                        VaultFileBrowserScreen(
                            viewModel = browserViewModel,
                            onOpenFile = safeOpenFile
                        )
                    }

                    WorkspaceTab.FAVORITES -> {
                        FavoritesTabContent(
                            favoriteItems = effectiveUiState.favoriteItems,
                            onOpenFile = safeOpenFile
                        )
                    }

                    WorkspaceTab.TRASH -> {
                        val currentContext = androidx.compose.ui.platform.LocalContext.current
                        val trashViewModel = remember {
                            val db = com.example.core.database.VaultDatabase.getInstance(currentContext.applicationContext)
                            val lifecycleMgr = com.example.core.lifecycle.DataLifecycleManager(
                                context = currentContext.applicationContext,
                                vaultDao = db.vaultDao(),
                                vaultFolderDao = db.vaultFolderDao(),
                                securityAuditDao = db.securityAuditDao(),
                                storageManager = viewModel.repository.storageManager
                            )
                            com.example.feature.vault.trash.TrashViewModel(
                                repository = viewModel.repository,
                                lifecycleManager = lifecycleMgr,
                                sessionManager = viewModel.sessionManager
                            )
                        }
                        TrashScreen(
                            viewModel = trashViewModel,
                            onBackClick = { viewModel.selectTab(WorkspaceTab.WORKSPACE) }
                        )
                    }

                    WorkspaceTab.SETTINGS -> {
                        val auditLogs by viewModel.repository.auditLogs.collectAsState(initial = emptyList())
                        SettingsScreen(
                            sessionManager = viewModel.sessionManager,
                            auditLogs = auditLogs,
                            onBackClick = { viewModel.selectTab(WorkspaceTab.WORKSPACE) },
                            onNavigateToSecurityCenter = onSettingsClick,
                            onNavigateToTrash = onTrashClick,
                            onNavigateToIntruderLogs = onSettingsClick,
                            storageManager = viewModel.repository.storageManager,
                            onPurgeVault = {
                                scope.launch {
                                    viewModel.repository.purgeAllVaultData()
                                    viewModel.lockVault()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Direct Import Dialog
    SecureImportDialog(
        state = importState,
        onCancel = { importController.cancelImport() },
        onDismiss = { importController.dismiss() }
    )

    // Creation Dialogs
    if (showCreateNoteDialog) {
        CreateTextFileDialog(
            onDismiss = { showCreateNoteDialog = false },
            onConfirm = { title, content ->
                showCreateNoteDialog = false
                viewModel.createNote(title, content) { created ->
                    Toast.makeText(context, "Secure note created", Toast.LENGTH_SHORT).show()
                    onOpenFile(created.id)
                }
            }
        )
    }

    if (showCreateCodeDialog) {
        CreateCodeFileDialog(
            onDismiss = { showCreateCodeDialog = false },
            onConfirm = { title, content ->
                showCreateCodeDialog = false
                viewModel.createCode(title, content) { created ->
                    Toast.makeText(context, "Secure source file created", Toast.LENGTH_SHORT).show()
                    onOpenFile(created.id)
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                showCreateFolderDialog = false
                viewModel.createFolder(folderName)
                Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Import Options Modal Bottom Sheet
    if (showImportChooserSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportChooserSheet = false },
            containerColor = VaultColors.SurfaceElevated,
            contentColor = VaultColors.TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Import Secure Media & Files",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select what you want to encrypt and store inside your vault",
                    fontSize = 13.sp,
                    color = VaultColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Option 1: Images & Photos (Media Import)
                Card(
                    onClick = {
                        showImportChooserSheet = false
                        effectiveSessionManager.setSuppressAutoLock(true)
                        safMultiPicker.launch(arrayOf("image/*"))
                    },
                    colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceGraphite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                .background(VaultColors.AccentEmerald.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = VaultColors.AccentEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Photos & Images",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = VaultColors.TextPrimary
                            )
                            Text(
                                text = "Encrypt PNG, JPG, JPEG photos from gallery",
                                fontSize = 12.sp,
                                color = VaultColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: PDF & Documents
                Card(
                    onClick = {
                        showImportChooserSheet = false
                        effectiveSessionManager.setSuppressAutoLock(true)
                        safMultiPicker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "text/plain",
                                "text/csv",
                                "*/*"
                            )
                        )
                    },
                    colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceGraphite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                .background(VaultColors.AccentAmber.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = VaultColors.AccentAmber,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PDFs & Documents",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = VaultColors.TextPrimary
                            )
                            Text(
                                text = "Import encrypted PDF, doc, txt, and papers",
                                fontSize = 12.sp,
                                color = VaultColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 3: Any File or Media
                Card(
                    onClick = {
                        showImportChooserSheet = false
                        effectiveSessionManager.setSuppressAutoLock(true)
                        safMultiPicker.launch(arrayOf("*/*"))
                    },
                    colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceGraphite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
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
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All Files & Storage",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = VaultColors.TextPrimary
                            )
                            Text(
                                text = "Browse system storage for any file format",
                                fontSize = 12.sp,
                                color = VaultColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WorkspaceDashboardContent(
    uiState: VaultHomeUiState,
    onImportClick: () -> Unit,
    onCreateNoteClick: () -> Unit,
    onCreateCodeClick: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onMediaCenterClick: () -> Unit,
    onCategoryClick: (VaultCategory) -> Unit,
    onOpenFile: (itemId: Long) -> Unit,
    onClearRecent: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToOrganize: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uninstallManager = remember(context) { com.example.core.security.UninstallProtectionManager.getInstance(context) }
    val isProtected by uninstallManager.isProtected.collectAsState()
    val hasDismissedBanner by uninstallManager.hasDismissedBanner.collectAsState()

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        uninstallManager.notifyStateChanged()
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Anti-Tamper & Uninstall Protection Alert Banner
        if (!isProtected && !hasDismissedBanner) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF261A13)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, VaultColors.AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = VaultColors.AccentAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Anti-Uninstall Protection Inactive",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = VaultColors.TextPrimary
                                )
                            }
                            IconButton(
                                onClick = { uninstallManager.dismissBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = VaultColors.TextTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Anyone can delete this app and lose your encrypted files. Enable Android Device Admin to lock down uninstallation.",
                            fontSize = 12.sp,
                            color = VaultColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    deviceAdminLauncher.launch(uninstallManager.getActivationIntent())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Turn On Protection",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        // 0. Universal Quick Search Entry Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultColors.SurfaceElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
                    .clickable { onNavigateToSearch() }
                    .testTag("dashboard_quick_search_card")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Universal Search",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Search files, content, ZIPs, tags...",
                        color = VaultColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VaultColors.AccentCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LOCAL SEARCH",
                            color = VaultColors.AccentCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 1. Storage Overview Card with Segmented Progress Bar
        item {
            StorageOverviewCard(
                totalSizeBytes = uiState.totalSizeBytes,
                availableDeviceBytes = uiState.availableDeviceBytes,
                totalItemCount = uiState.totalItemCount,
                categoryStats = uiState.categoryStats
            )
        }

        // 2. Quick Action Shortcuts Bar
        item {
            QuickActionsBar(
                onSearchClick = onNavigateToSearch,
                onOrganizeClick = onNavigateToOrganize,
                onImportClick = onImportClick,
                onCreateNoteClick = onCreateNoteClick,
                onCreateCodeClick = onCreateCodeClick,
                onCreateFolderClick = onCreateFolderClick,
                onMediaCenterClick = onMediaCenterClick,
                onArchiveCenterClick = { onCategoryClick(VaultCategory.ZIP) }
            )
        }

        // 3. Category Hub Header & Grid
        item {
            Text(
                text = "Secure Partitions",
                color = VaultColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            CategoryHubGrid(
                categoryStats = uiState.categoryStats,
                onCategoryClick = onCategoryClick
            )
        }

        // 4. Recent Files Section
        if (uiState.recentItems.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Recent Activity",
                        color = VaultColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClearRecent) {
                        Text("Clear History", color = VaultColors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            items(uiState.recentItems.take(5), key = { "recent_${it.id}" }) { item ->
                RecentItemRow(
                    item = item,
                    onClick = { onOpenFile(item.id) }
                )
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    totalSizeBytes: Long,
    availableDeviceBytes: Long,
    totalItemCount: Int,
    categoryStats: Map<VaultCategory, CategoryStat>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VaultColors.SurfaceElevated)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Encrypted Vault Storage",
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatBytes(totalSizeBytes),
                        color = VaultColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                SecurityPill(
                    text = "$totalItemCount PROTECTED",
                    dotColor = VaultColors.AccentEmerald
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-segment storage bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(VaultColors.SurfaceGraphite)
            ) {
                if (totalSizeBytes > 0) {
                    VaultCategory.entries.forEach { cat ->
                        val catSize = categoryStats[cat]?.sizeBytes ?: 0L
                        if (catSize > 0) {
                            val weight = (catSize.toFloat() / totalSizeBytes).coerceAtLeast(0.01f)
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxSize()
                                    .background(getCategoryColor(cat.name))
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(VaultColors.SurfaceHighlight)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Available storage note
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Hardware Enclave AES-256",
                    color = VaultColors.AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatBytes(availableDeviceBytes)} Free on Device",
                    color = VaultColors.TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun QuickActionsBar(
    onSearchClick: () -> Unit = {},
    onOrganizeClick: () -> Unit = {},
    onImportClick: () -> Unit,
    onCreateNoteClick: () -> Unit,
    onCreateCodeClick: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onMediaCenterClick: () -> Unit,
    onArchiveCenterClick: () -> Unit = {}
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        QuickActionButton(
            icon = Icons.Default.Search,
            label = "Search",
            accentColor = VaultColors.AccentCyan,
            onClick = onSearchClick
        )
        QuickActionButton(
            icon = Icons.Default.AutoAwesomeMosaic,
            label = "Organize",
            accentColor = VaultColors.AccentAmber,
            onClick = onOrganizeClick
        )
        QuickActionButton(
            icon = Icons.Default.Archive,
            label = "Archive Center",
            accentColor = VaultColors.AccentCyan,
            onClick = onArchiveCenterClick
        )
        QuickActionButton(
            icon = Icons.Default.PermMedia,
            label = "Media Center",
            accentColor = VaultColors.AccentCyan,
            onClick = onMediaCenterClick
        )
        QuickActionButton(
            icon = Icons.Default.UploadFile,
            label = "Import Files",
            accentColor = VaultColors.AccentEmerald,
            onClick = onImportClick
        )
        QuickActionButton(
            icon = Icons.Default.NoteAdd,
            label = "Text Studio",
            accentColor = VaultColors.AccentCyan,
            onClick = onCreateNoteClick
        )
        QuickActionButton(
            icon = Icons.Default.Code,
            label = "Code Studio",
            accentColor = Color(0xFF60A5FA),
            onClick = onCreateCodeClick
        )
        QuickActionButton(
            icon = Icons.Default.CreateNewFolder,
            label = "New Folder",
            accentColor = VaultColors.AccentAmber,
            onClick = onCreateFolderClick
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(VaultColors.SurfaceElevated)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = VaultColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CategoryHubGrid(
    categoryStats: Map<VaultCategory, CategoryStat>,
    onCategoryClick: (VaultCategory) -> Unit
) {
    val categories = VaultCategory.entries

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in categories.indices step 2) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val cat1 = categories[i]
                CategoryTile(
                    category = cat1,
                    stat = categoryStats[cat1],
                    onClick = { onCategoryClick(cat1) },
                    modifier = Modifier.weight(1f)
                )

                if (i + 1 < categories.size) {
                    val cat2 = categories[i + 1]
                    CategoryTile(
                        category = cat2,
                        stat = categoryStats[cat2],
                        onClick = { onCategoryClick(cat2) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: VaultCategory,
    stat: CategoryStat?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val count = stat?.count ?: 0
    val sizeBytes = stat?.sizeBytes ?: 0L
    val color = getCategoryColor(category.name)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(VaultColors.SurfaceElevated)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("category_tile_${category.name.lowercase()}")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.name),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "$count",
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = category.title,
                color = VaultColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Text(
                text = if (count > 0) formatBytes(sizeBytes) else "0 items",
                color = VaultColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun RecentItemRow(
    item: VaultItemEntity,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VaultColors.SurfaceElevated)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VaultColors.SurfaceGraphite)
        ) {
            Icon(
                imageVector = getCategoryIcon(item.category),
                contentDescription = null,
                tint = getCategoryColor(item.category),
                modifier = Modifier.size(18.dp)
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
                text = "${formatBytes(item.sizeBytes)} · ${dateFormat.format(Date(item.lastAccessedAt ?: item.modifiedAt))}",
                color = VaultColors.TextTertiary,
                fontSize = 11.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Open file",
            tint = VaultColors.TextTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun FavoritesTabContent(
    favoriteItems: List<VaultItemEntity>,
    onOpenFile: (itemId: Long) -> Unit
) {
    if (favoriteItems.isEmpty()) {
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
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = VaultColors.AccentAmber,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No Starred Files",
                    color = VaultColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Star critical documents, keys, or photos to keep them instantly accessible in your primary enclave.",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "${favoriteItems.size} Starred Items",
                    color = VaultColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(favoriteItems, key = { "fav_${it.id}" }) { item ->
                RecentItemRow(
                    item = item,
                    onClick = { onOpenFile(item.id) }
                )
            }
        }
    }
}
