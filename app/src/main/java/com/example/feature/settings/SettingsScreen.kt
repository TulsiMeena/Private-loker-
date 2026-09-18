package com.example.feature.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.lifecycle.DataLifecycleManager
import com.example.core.lifecycle.StorageSummary
import com.example.core.security.AutoLockTimeout
import com.example.core.security.NotificationPrivacyLevel
import com.example.core.security.SessionSecurityManager
import com.example.core.security.VoiceAuthStatus
import com.example.core.security.VoiceLockSecurityManager
import java.util.Locale
import com.example.core.settings.AnimationIntensity
import com.example.core.settings.AppSettingsManager
import com.example.core.settings.AppThemeMode
import com.example.core.settings.EncodingPreference
import com.example.core.settings.GlassIntensity
import com.example.core.settings.LineEndingPreference
import com.example.core.settings.LockScreenStyle
import com.example.core.settings.ThumbnailQualityPreference
import com.example.core.settings.VaultAccentColor
import com.example.core.storage.SecureThumbnailProvider
import com.example.core.storage.VaultStorageManager
import com.example.core.ui.SecurityPill
import com.example.core.ui.VaultGlassCard
import com.example.core.util.Formatters
import com.example.core.util.HapticFeedbackUtil
import com.example.feature.documents.DocumentThumbnailHelper
import com.example.feature.media.MediaThumbnailHelper
import kotlinx.coroutines.launch

enum class SettingsSection(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    SECURITY("Security & Privacy", Icons.Default.Security, "Biometric, PIN, auto-lock, window protection"),
    APPEARANCE("Appearance & Theme", Icons.Default.Palette, "Theme mode, custom accents, glass intensity"),
    STORAGE("Storage & Integrity", Icons.Default.Storage, "Storage accounting, temp files, integrity check"),
    EDITOR("Code & Text Editor", Icons.Default.Code, "Font size, wrap, tab size, line endings, encoding"),
    MEDIA("Media Player & Cache", Icons.Default.PlayCircle, "Playback speed, auto-play, cache limits"),
    NOTIFICATIONS("Notifications & Privacy", Icons.Default.Notifications, "Minimal privacy mode, lockscreen safety"),
    ACCESSIBILITY("Accessibility & Haptics", Icons.Default.AccessibilityNew, "Reduced motion, haptic feedback, touch targets"),
    BACKUP("Backup & Recovery", Icons.Default.Backup, "Offline encrypted archives & restoration"),
    ABOUT("About PrivateVault", Icons.Default.Info, "Security specs, zero-cloud architecture, licenses")
}

@Composable
fun SettingsScreen(
    sessionManager: SessionSecurityManager,
    auditLogs: List<SecurityAuditLogEntity>,
    onBackClick: () -> Unit,
    onPurgeVault: () -> Unit = {},
    onNavigateToSecurityCenter: () -> Unit = {},
    onNavigateToBackupCenter: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    storageManager: VaultStorageManager? = null,
    lifecycleManager: DataLifecycleManager? = null,
    appSettingsManager: AppSettingsManager? = null,
    voiceLockManager: VoiceLockSecurityManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current
    val scope = rememberCoroutineScope()

    val settingsManager = remember(context) {
        appSettingsManager ?: AppSettingsManager.getInstance(context)
    }

    var selectedSection by remember { mutableStateOf(SettingsSection.SECURITY) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }
    var purgeConfirmInput by remember { mutableStateOf("") }
    var showIntegrityDialog by remember { mutableStateOf(false) }
    var integrityCheckResult by remember { mutableStateOf<String?>(null) }
    var isCheckingIntegrity by remember { mutableStateOf(false) }

    // Live storage stats
    var storageSummary by remember { mutableStateOf<StorageSummary?>(null) }
    LaunchedEffect(Unit) {
        if (lifecycleManager != null) {
            storageSummary = lifecycleManager.getStorageSummary()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .testTag("settings_screen")
            .fillMaxSize()
            .background(VaultColors.Canvas)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .testTag("settings_back_button")
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultColors.TextPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "Settings Center",
                            style = vaultTypography.headline,
                            color = VaultColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Advanced preferences & device integration",
                            style = vaultTypography.bodySmall,
                            color = VaultColors.TextTertiary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    // Quick Emergency Lock
                    IconButton(
                        onClick = { sessionManager.lockVault() },
                        modifier = Modifier
                            .testTag("settings_emergency_lock")
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Emergency Lock",
                            tint = VaultColors.AccentCrimson
                        )
                    }

                    SecurityPill(
                        text = "HARDENED",
                        dotColor = VaultColors.AccentEmerald
                    )
                }
            }

            if (isWideScreen) {
                // Adaptive Two-Pane Layout (Tablet / Foldable / Landscape)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(spacing.l)
                ) {
                    // Left Pane: Navigation Rail / Category List (300dp)
                    Box(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight()
                    ) {
                        VaultGlassCard(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(spacing.m)
                            ) {
                                Text(
                                    text = "CATEGORIES",
                                    style = vaultTypography.caption,
                                    color = VaultColors.TextTertiary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = spacing.s)
                                )

                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                                ) {
                                    items(SettingsSection.values()) { section ->
                                        SettingsCategoryItem(
                                            section = section,
                                            isSelected = selectedSection == section,
                                            onClick = { selectedSection = section }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(spacing.s))

                                // Reset Preferences Button
                                OutlinedButton(
                                    onClick = { showResetDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = VaultColors.AccentAmber
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(spacing.xs))
                                    Text(
                                        text = "Reset App Preferences",
                                        style = vaultTypography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Right Pane: Active Section Content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        VaultGlassCard(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(spacing.l)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(spacing.l)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                                ) {
                                    Icon(
                                        imageVector = selectedSection.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = selectedSection.title,
                                            style = vaultTypography.headline,
                                            color = VaultColors.TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = selectedSection.description,
                                            style = vaultTypography.bodySmall,
                                            color = VaultColors.TextSecondary
                                        )
                                    }
                                }

                                SettingsSectionContent(
                                    section = selectedSection,
                                    sessionManager = sessionManager,
                                    settingsManager = settingsManager,
                                    storageManager = storageManager,
                                    lifecycleManager = lifecycleManager,
                                    voiceLockManager = voiceLockManager,
                                    storageSummary = storageSummary,
                                    onRefreshStorage = {
                                        scope.launch {
                                            storageSummary = lifecycleManager?.getStorageSummary()
                                        }
                                    },
                                    onNavigateToSecurityCenter = onNavigateToSecurityCenter,
                                    onNavigateToBackupCenter = onNavigateToBackupCenter,
                                    onNavigateToTrash = onNavigateToTrash,
                                    onRunIntegrityCheck = {
                                        scope.launch {
                                            isCheckingIntegrity = true
                                            try {
                                                val report = lifecycleManager?.scanForOrphansAndInconsistencies()
                                                integrityCheckResult = if (report == null) {
                                                    "Integrity scanner not available in this context."
                                                } else if (report.hasIssues) {
                                                    "Issues detected: ${report.unlinkedContainers.size} unlinked containers, ${report.incompleteOperations.size} incomplete operations, ${report.missingPhysicalFiles.size} broken metadata references."
                                                } else {
                                                    "Vault integrity verified! All database records match physical encrypted containers perfectly with zero orphaned files."
                                                }
                                            } catch (e: Exception) {
                                                integrityCheckResult = "Integrity check encountered an error: ${e.message}"
                                            } finally {
                                                isCheckingIntegrity = false
                                                showIntegrityDialog = true
                                            }
                                        }
                                    },
                                    onOpenPurgeVault = { showPurgeDialog = true }
                                )
                            }
                        }
                    }
                }
            } else {
                // Compact Screen (Phones)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.screenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(spacing.m),
                    contentPadding = PaddingValues(bottom = spacing.xxl)
                ) {
                    items(SettingsSection.values()) { section ->
                        val isExpanded = selectedSection == section
                        VaultGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedSection = if (isExpanded) selectedSection else section
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.m)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(spacing.m)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (isExpanded) MaterialTheme.colorScheme.primaryContainer
                                                    else VaultColors.SurfaceElevated,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = section.icon,
                                                contentDescription = null,
                                                tint = if (isExpanded) MaterialTheme.colorScheme.primary
                                                else VaultColors.TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = section.title,
                                                style = vaultTypography.title,
                                                color = VaultColors.TextPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = section.description,
                                                style = vaultTypography.bodySmall,
                                                color = VaultColors.TextTertiary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = spacing.l),
                                        verticalArrangement = Arrangement.spacedBy(spacing.l)
                                    ) {
                                        SettingsSectionContent(
                                            section = section,
                                            sessionManager = sessionManager,
                                            settingsManager = settingsManager,
                                            storageManager = storageManager,
                                            lifecycleManager = lifecycleManager,
                                            voiceLockManager = voiceLockManager,
                                            storageSummary = storageSummary,
                                            onRefreshStorage = {
                                                scope.launch {
                                                    storageSummary = lifecycleManager?.getStorageSummary()
                                                }
                                            },
                                            onNavigateToSecurityCenter = onNavigateToSecurityCenter,
                                            onNavigateToBackupCenter = onNavigateToBackupCenter,
                                            onNavigateToTrash = onNavigateToTrash,
                                            onRunIntegrityCheck = {
                                                scope.launch {
                                                    isCheckingIntegrity = true
                                                    try {
                                                        val report = lifecycleManager?.scanForOrphansAndInconsistencies()
                                                        integrityCheckResult = if (report == null) {
                                                            "Integrity scanner not available in this context."
                                                        } else if (report.hasIssues) {
                                                            "Issues detected: ${report.unlinkedContainers.size} unlinked containers, ${report.incompleteOperations.size} incomplete operations."
                                                        } else {
                                                            "Vault integrity verified! All database records match physical encrypted containers."
                                                        }
                                                    } catch (e: Exception) {
                                                        integrityCheckResult = "Integrity check failed: ${e.message}"
                                                    } finally {
                                                        isCheckingIntegrity = false
                                                        showIntegrityDialog = true
                                                    }
                                                }
                                            },
                                            onOpenPurgeVault = { showPurgeDialog = true }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(spacing.s))
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = VaultColors.AccentAmber
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Text(
                                text = "Reset App Preferences",
                                style = vaultTypography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Reset Preferences Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset App Preferences?",
                    style = vaultTypography.title,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(
                        text = "This will restore display, theme, editor, media playback, and accessibility preferences to their initial defaults.",
                        style = vaultTypography.body,
                        color = VaultColors.TextSecondary
                    )
                    Text(
                        text = "• Vault files and folders are NOT deleted.\n• Trash contents are NOT deleted.\n• Master keys, PINs, and security policies are UNTOUCHED.",
                        style = vaultTypography.bodySmall,
                        color = VaultColors.AccentEmerald
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsManager.resetAppPreferences()
                        showResetDialog = false
                        Toast.makeText(context, "Preferences restored to defaults", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber)
                ) {
                    Text("Reset Preferences", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // Integrity Check Result Dialog
    if (showIntegrityDialog) {
        AlertDialog(
            onDismissRequest = { showIntegrityDialog = false },
            title = {
                Text(
                    text = "Vault Integrity Check",
                    style = vaultTypography.title,
                    fontWeight = FontWeight.Bold,
                    color = VaultColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = integrityCheckResult ?: "Scanning vault storage...",
                    style = vaultTypography.body,
                    color = VaultColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showIntegrityDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }

    // Emergency Purge Vault Dialog (Original functionality preserved)
    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = {
                showPurgeDialog = false
                purgeConfirmInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = VaultColors.AccentCrimson,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.s))
                    Text(
                        text = "CRITICAL: Purge Entire Vault",
                        style = vaultTypography.title,
                        fontWeight = FontWeight.Bold,
                        color = VaultColors.AccentCrimson
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                    Text(
                        text = "This will permanently destroy all encrypted containers, wipe database records, delete all security settings, and clear encryption keys.",
                        style = vaultTypography.body,
                        color = VaultColors.TextSecondary
                    )
                    Text(
                        text = "THIS ACTION CANNOT BE UNDONE. Type 'DESTROY' below to confirm:",
                        style = vaultTypography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = VaultColors.AccentCrimson
                    )
                    OutlinedTextField(
                        value = purgeConfirmInput,
                        onValueChange = { purgeConfirmInput = it },
                        placeholder = { Text("DESTROY", color = VaultColors.TextTertiary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultColors.AccentCrimson,
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
                        if (purgeConfirmInput.trim() == "DESTROY") {
                            showPurgeDialog = false
                            purgeConfirmInput = ""
                            onPurgeVault()
                        }
                    },
                    enabled = purgeConfirmInput.trim() == "DESTROY",
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson)
                ) {
                    Text("DESTROY ALL DATA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPurgeDialog = false
                    purgeConfirmInput = ""
                }) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            },
            containerColor = VaultColors.SurfaceElevated
        )
    }
}

@Composable
private fun SettingsCategoryItem(
    section: SettingsSection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.m, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = section.title,
            style = vaultTypography.body,
            color = if (isSelected) VaultColors.TextPrimary else VaultColors.TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsSectionContent(
    section: SettingsSection,
    sessionManager: SessionSecurityManager,
    settingsManager: AppSettingsManager,
    storageManager: VaultStorageManager?,
    lifecycleManager: DataLifecycleManager?,
    voiceLockManager: VoiceLockSecurityManager? = null,
    storageSummary: StorageSummary?,
    onRefreshStorage: () -> Unit,
    onNavigateToSecurityCenter: () -> Unit,
    onNavigateToBackupCenter: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onRunIntegrityCheck: () -> Unit,
    onOpenPurgeVault: () -> Unit
) {
    when (section) {
        SettingsSection.SECURITY -> SecuritySettingsSection(
            sessionManager = sessionManager,
            voiceLockManager = voiceLockManager,
            onNavigateToSecurityCenter = onNavigateToSecurityCenter,
            onOpenPurgeVault = onOpenPurgeVault
        )
        SettingsSection.APPEARANCE -> AppearanceSettingsSection(settingsManager = settingsManager)
        SettingsSection.STORAGE -> StorageSettingsSection(
            storageManager = storageManager,
            storageSummary = storageSummary,
            onRefreshStorage = onRefreshStorage,
            onNavigateToTrash = onNavigateToTrash,
            onRunIntegrityCheck = onRunIntegrityCheck
        )
        SettingsSection.EDITOR -> EditorSettingsSection(settingsManager = settingsManager)
        SettingsSection.MEDIA -> MediaSettingsSection(settingsManager = settingsManager)
        SettingsSection.NOTIFICATIONS -> NotificationSettingsSection(sessionManager = sessionManager)
        SettingsSection.ACCESSIBILITY -> AccessibilitySettingsSection(settingsManager = settingsManager)
        SettingsSection.BACKUP -> BackupSettingsSection(onNavigateToBackupCenter = onNavigateToBackupCenter)
        SettingsSection.ABOUT -> AboutSettingsSection(storageSummary = storageSummary)
    }
}

// -------------------------------------------------------------
// SECTION 1: SECURITY & PRIVACY
// -------------------------------------------------------------
@Composable
private fun SecuritySettingsSection(
    sessionManager: SessionSecurityManager,
    voiceLockManager: VoiceLockSecurityManager? = null,
    onNavigateToSecurityCenter: () -> Unit,
    onOpenPurgeVault: () -> Unit
) {
    val context = LocalContext.current
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    val biometricEnabled by sessionManager.biometricEnabled.collectAsState()
    val faceUnlockEnabled by sessionManager.faceUnlockEnabled.collectAsState()
    val voiceLockEnabled by sessionManager.voiceLockEnabled.collectAsState()
    val voicePassphrase by sessionManager.voicePassphrase.collectAsState()
    val voiceAntiSpoofing by sessionManager.voiceAntiSpoofingEnabled.collectAsState()
    val voiceConfidence by sessionManager.voiceConfidenceThreshold.collectAsState()

    val screenProtection by sessionManager.screenProtectionEnabled.collectAsState()
    val recentAppPrivacy by sessionManager.recentAppPrivacyEnabled.collectAsState()
    val clipboardProtection by sessionManager.clipboardProtectionEnabled.collectAsState()
    val clipboardTimeout by sessionManager.clipboardTimeoutSeconds.collectAsState()
    val autoLockTimeout by sessionManager.autoLockTimeout.collectAsState()

    var autoLockDropdownExpanded by remember { mutableStateOf(false) }
    var passphraseInput by remember(voicePassphrase) { mutableStateOf(voicePassphrase) }
    var isCalibratingVoice by remember { mutableStateOf(false) }

    val voiceStatus by (voiceLockManager?.status ?: kotlinx.coroutines.flow.MutableStateFlow(VoiceAuthStatus.Idle)).collectAsState()
    val voiceDecibels by (voiceLockManager?.decibels ?: kotlinx.coroutines.flow.MutableStateFlow(0f)).collectAsState()
    val voiceFrequencyBands by (voiceLockManager?.frequencyBands ?: kotlinx.coroutines.flow.MutableStateFlow(List(7) { 0.1f })).collectAsState()
    val voiceDetectedText by (voiceLockManager?.detectedText ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        // Fingerprint Unlock
        SettingToggleRow(
            title = "Fingerprint Unlock",
            subtitle = "Unlock vault via enrolled device fingerprint sensor",
            checked = biometricEnabled,
            icon = Icons.Default.Fingerprint,
            onCheckedChange = { sessionManager.setBiometricEnabled(it) }
        )

        // Face Recognition Lock
        SettingToggleRow(
            title = "Face Recognition Lock",
            subtitle = "Unlock vault instantly via facial recognition & biometric camera",
            checked = faceUnlockEnabled,
            icon = Icons.Default.Face,
            onCheckedChange = { sessionManager.setFaceUnlockEnabled(it) }
        )

        // Voice Biometric Lock
        SettingToggleRow(
            title = "Voice Biometric Lock",
            subtitle = "Unlock vault using acoustic speech recognition & voice biometric passphrase",
            checked = voiceLockEnabled,
            icon = Icons.Default.Mic,
            onCheckedChange = { sessionManager.setVoiceLockEnabled(it) }
        )

        // Advanced Voice Lock Configuration Subpanel
        if (voiceLockEnabled) {
            VaultGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.s)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.m),
                    verticalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = VaultColors.AccentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Advanced Voice Enclave Settings",
                            style = vaultTypography.title,
                            fontWeight = FontWeight.Bold,
                            color = VaultColors.AccentAmber
                        )
                    }

                    // Passphrase Configuration
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "Voice Passphrase",
                            style = vaultTypography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = VaultColors.TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.s)
                        ) {
                            OutlinedTextField(
                                value = passphraseInput,
                                onValueChange = { passphraseInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("voice_passphrase_input"),
                                singleLine = true,
                                textStyle = vaultTypography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VaultColors.AccentAmber,
                                    unfocusedBorderColor = VaultColors.GlassBorderMedium
                                )
                            )
                            Button(
                                onClick = {
                                    if (passphraseInput.isNotBlank()) {
                                        sessionManager.setVoicePassphrase(passphraseInput.trim())
                                        Toast.makeText(context, "Voice passphrase updated", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber),
                                modifier = Modifier.testTag("save_voice_passphrase_button")
                            ) {
                                Text("Save", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Anti-Spoofing & Replay Defense Toggle
                    SettingToggleRow(
                        title = "Acoustic Liveness & Anti-Replay Defense",
                        subtitle = "Verifies harmonic dispersion and micro-temporal variation to block playback recordings",
                        checked = voiceAntiSpoofing,
                        icon = Icons.Default.Shield,
                        onCheckedChange = { sessionManager.setVoiceAntiSpoofingEnabled(it) }
                    )

                    // Acoustic Match Sensitivity Slider
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Acoustic Match Sensitivity",
                                style = vaultTypography.bodySmall,
                                color = VaultColors.TextPrimary
                            )
                            Text(
                                text = "${(voiceConfidence * 100).toInt()}% Confidence",
                                style = vaultTypography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = VaultColors.AccentAmber
                            )
                        }
                        Slider(
                            value = voiceConfidence,
                            onValueChange = { sessionManager.setVoiceConfidenceThreshold(it) },
                            valueRange = 0.50f..0.95f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = VaultColors.AccentAmber,
                                activeTrackColor = VaultColors.AccentAmber
                            )
                        )
                    }

                    // Live Acoustic Microphone Calibration & Test
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated)
                            .padding(spacing.m),
                        verticalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = VaultColors.AccentAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Microphone Acoustic Calibration",
                                    style = vaultTypography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = VaultColors.TextPrimary
                                )
                            }
                            Button(
                                onClick = {
                                    if (isCalibratingVoice) {
                                        voiceLockManager?.stopListening()
                                        isCalibratingVoice = false
                                    } else {
                                        isCalibratingVoice = true
                                        if (voiceLockManager?.hasRecordAudioPermission() == true) {
                                            voiceLockManager.startListening()
                                        } else {
                                            voiceLockManager?.startAcousticSimulationSession()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCalibratingVoice) VaultColors.AccentCrimson else VaultColors.SurfaceHighlight
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isCalibratingVoice) "Stop" else "Calibrate",
                                    style = vaultTypography.caption.copy(fontWeight = FontWeight.Bold),
                                    color = if (isCalibratingVoice) Color.White else VaultColors.AccentAmber
                                )
                            }
                        }

                        // Real-time Graphic Equalizer & Decibel Visualizer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(VaultColors.SurfaceGraphite)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            voiceFrequencyBands.forEach { bandVal ->
                                val barHeight = (bandVal.coerceIn(0.1f, 1f) * 24).dp
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(VaultColors.AccentAmber)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Ambient: ${String.format(Locale.US, "%.1f", voiceDecibels)} dB",
                                style = vaultTypography.caption.copy(fontFamily = FontFamily.Monospace),
                                color = VaultColors.TextSecondary
                            )
                            Text(
                                text = when (voiceStatus) {
                                    is VoiceAuthStatus.Success -> "✓ Passphrase Matched!"
                                    is VoiceAuthStatus.Listening -> "Listening..."
                                    is VoiceAuthStatus.Mismatch -> "Mismatch Detected"
                                    else -> if (voiceDetectedText.isNotBlank()) "\"$voiceDetectedText\"" else "Acoustic Enclave Ready"
                                },
                                style = vaultTypography.caption.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = when (voiceStatus) {
                                    is VoiceAuthStatus.Success -> VaultColors.AccentEmerald
                                    is VoiceAuthStatus.Mismatch -> VaultColors.AccentCrimson
                                    else -> VaultColors.TextTertiary
                                }
                            )
                        }
                    }
                }
            }
        }

        // Screen Protection (FLAG_SECURE)
        SettingToggleRow(
            title = "Screenshot & Screen Recording Protection",
            subtitle = "Blocks Android task snapshots, screenshots, and screen mirroring (FLAG_SECURE)",
            checked = screenProtection,
            icon = Icons.Default.VisibilityOff,
            onCheckedChange = { sessionManager.setScreenProtectionEnabled(it) }
        )

        // Recent App Privacy
        SettingToggleRow(
            title = "Recent-Apps Task Snapshot Privacy",
            subtitle = "Hides vault contents when switching apps in the Android Recents overview",
            checked = recentAppPrivacy,
            icon = Icons.Default.Shield,
            onCheckedChange = { sessionManager.setRecentAppPrivacyEnabled(it) }
        )

        // Clipboard Protection
        SettingToggleRow(
            title = "Clipboard Protection",
            subtitle = "Automatically clears sensitive copied data from clipboard after ${clipboardTimeout}s",
            checked = clipboardProtection,
            icon = Icons.Default.Security,
            onCheckedChange = { sessionManager.setClipboardProtectionEnabled(it) }
        )

        // Auto-Lock Timeout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Lock Inactivity Timeout",
                    style = vaultTypography.title,
                    color = VaultColors.TextPrimary
                )
                Text(
                    text = "Locks vault automatically after background or idle state",
                    style = vaultTypography.bodySmall,
                    color = VaultColors.TextTertiary
                )
            }

            Box {
                Button(
                    onClick = { autoLockDropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceElevated)
                ) {
                    Text(
                        text = autoLockTimeout.displayName,
                        color = VaultColors.TextPrimary,
                        style = vaultTypography.bodySmall
                    )
                }

                DropdownMenu(
                    expanded = autoLockDropdownExpanded,
                    onDismissRequest = { autoLockDropdownExpanded = false }
                ) {
                    AutoLockTimeout.values().forEach { timeout ->
                        DropdownMenuItem(
                            text = { Text(timeout.displayName) },
                            onClick = {
                                sessionManager.setAutoLockTimeout(timeout)
                                autoLockDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.s))

        // Navigation to Security Center
        OutlinedButton(
            onClick = onNavigateToSecurityCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan)
        ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Open Full Security Center", fontWeight = FontWeight.SemiBold)
        }

        // Critical Purge Section
        OutlinedButton(
            onClick = onOpenPurgeVault,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCrimson)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Purge Entire Vault Data", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// SECTION 2: APPEARANCE & PERSONALIZATION
// -------------------------------------------------------------
@Composable
private fun AppearanceSettingsSection(settingsManager: AppSettingsManager) {
    val context = LocalContext.current
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    val themeMode by settingsManager.themeMode.collectAsState()
    val lockScreenStyle by settingsManager.lockScreenStyle.collectAsState()
    val accentColor by settingsManager.accentColor.collectAsState()
    val glassIntensity by settingsManager.glassIntensity.collectAsState()
    val animationIntensity by settingsManager.animationIntensity.collectAsState()
    val reducedMotion by settingsManager.reducedMotion.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        // Theme Mode
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Theme Mode", style = vaultTypography.title, color = VaultColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                AppThemeMode.values().forEach { mode ->
                    val isSelected = themeMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.GlassBorderSubtle,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { settingsManager.setThemeMode(mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.title,
                            style = vaultTypography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Lock Screen Visual Style Selection
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lock Screen Style (लॉक स्क्रीन स्टाइल)",
                        style = vaultTypography.title,
                        color = VaultColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose Cyberpunk Sci-Fi HUD or Frosted Glassmorphism",
                        style = vaultTypography.caption,
                        color = VaultColors.TextSecondary
                    )
                }
                SecurityPill(
                    text = lockScreenStyle.badge,
                    dotColor = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                LockScreenStyle.values().forEach { style ->
                    val isSelected = lockScreenStyle == style
                    VaultGlassCard(
                        modifier = Modifier
                            .testTag("lock_style_${style.name.lowercase()}")
                            .fillMaxWidth()
                            .clickable {
                                HapticFeedbackUtil.performTactileTick(context)
                                settingsManager.setLockScreenStyle(style)
                            },
                        borderColor = if (isSelected) {
                            if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)
                        } else VaultColors.GlassBorderSubtle,
                        backgroundColor = if (isSelected) {
                            (if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)).copy(alpha = 0.08f)
                        } else VaultColors.SurfaceElevated.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.m)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(if (style == LockScreenStyle.CYBERPUNK_HUD) RoundedCornerShape(12.dp) else CircleShape)
                                    .background(
                                        if (isSelected) {
                                            (if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)).copy(alpha = 0.18f)
                                        } else VaultColors.SurfaceHighlight
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) {
                                            if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)
                                        } else VaultColors.GlassBorderSubtle,
                                        shape = if (style == LockScreenStyle.CYBERPUNK_HUD) RoundedCornerShape(12.dp) else CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (style == LockScreenStyle.CYBERPUNK_HUD) Icons.Default.Security else Icons.Default.Palette,
                                    contentDescription = style.title,
                                    tint = if (isSelected) {
                                        if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFFC084FC)
                                    } else VaultColors.TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                                ) {
                                    Text(
                                        text = style.title,
                                        style = vaultTypography.body,
                                        color = VaultColors.TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = "[ ${style.badge} ]",
                                        style = vaultTypography.caption.copy(
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isSelected) {
                                            if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFFC084FC)
                                        } else VaultColors.TextTertiary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = style.subtitle,
                                    style = vaultTypography.caption,
                                    color = VaultColors.TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)
                                        } else Color.Transparent
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) {
                                            if (style == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)
                                        } else VaultColors.TextTertiary,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = VaultColors.Canvas,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Accent Color
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Vault Accent Color", style = vaultTypography.title, color = VaultColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VaultAccentColor.values().forEach { accent ->
                    val isSelected = accentColor == accent
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(accent.hexCode))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { settingsManager.setAccentColor(accent) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Glass Effect Intensity
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Glassmorphism Effect Intensity", style = vaultTypography.title, color = VaultColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                GlassIntensity.values().forEach { intensity ->
                    val isSelected = glassIntensity == intensity
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setGlassIntensity(intensity) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = intensity.title.split(" ").first(),
                            style = vaultTypography.caption,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Animation Intensity
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Animation Speed", style = vaultTypography.title, color = VaultColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                AnimationIntensity.values().forEach { intensity ->
                    val isSelected = animationIntensity == intensity
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setAnimationIntensity(intensity) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = intensity.title,
                            style = vaultTypography.caption,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Reduced Motion
        SettingToggleRow(
            title = "Reduced Motion",
            subtitle = "Disables non-essential motion while keeping security transitions instant",
            checked = reducedMotion,
            icon = Icons.Default.AccessibilityNew,
            onCheckedChange = { settingsManager.setReducedMotion(it) }
        )
    }
}

// -------------------------------------------------------------
// SECTION 3: STORAGE SETTINGS
// -------------------------------------------------------------
@Composable
private fun StorageSettingsSection(
    storageManager: VaultStorageManager?,
    storageSummary: StorageSummary?,
    onRefreshStorage: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onRunIntegrityCheck: () -> Unit
) {
    val context = LocalContext.current
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        // Real Storage Cards
        VaultGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Text("REAL STORAGE ACCOUNTING", style = vaultTypography.caption, color = VaultColors.TextTertiary, fontWeight = FontWeight.Bold)

                StorageMetricRow(
                    label = "Encrypted Vault Files",
                    value = Formatters.formatBytes(storageSummary?.vaultBytes ?: 0L),
                    count = "${storageSummary?.activeItemCount ?: 0} active objects"
                )

                StorageMetricRow(
                    label = "Secure Trash Partition",
                    value = Formatters.formatBytes(storageSummary?.trashBytes ?: 0L),
                    count = "${storageSummary?.trashItemCount ?: 0} items"
                )

                StorageMetricRow(
                    label = "Temporary Working Files",
                    value = Formatters.formatBytes(storageSummary?.temporaryBytes ?: 0L),
                    count = "decrypted stream buffers"
                )

                StorageMetricRow(
                    label = "Available Device Storage",
                    value = Formatters.formatBytes(storageSummary?.availableDeviceBytes ?: 0L),
                    count = "verified on-device space"
                )
            }
        }

        // Clean Temporary Files
        OutlinedButton(
            onClick = {
                storageManager?.purgeTemporaryFiles()
                onRefreshStorage()
                Toast.makeText(context, "Temporary decrypted working files cleaned", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Purge Temporary Files")
        }

        // Clear Thumbnail Caches
        OutlinedButton(
            onClick = {
                SecureThumbnailProvider.clearCache()
                MediaThumbnailHelper.clearCache()
                DocumentThumbnailHelper.clearCache()
                onRefreshStorage()
                Toast.makeText(context, "All memory & thumbnail caches cleared", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Clear Thumbnail Caches")
        }

        // Vault Integrity Check
        Button(
            onClick = onRunIntegrityCheck,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Run Vault Integrity Check", fontWeight = FontWeight.Bold)
        }

        // Open Trash
        OutlinedButton(
            onClick = onNavigateToTrash,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentAmber)
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Manage Secure Trash")
        }

        Text(
            text = "GUARANTEE: Encrypted vault files are NEVER deleted through generic cleanup tools. Storage operations only purge transient cache and temporary memory buffers.",
            style = vaultTypography.bodySmall,
            color = VaultColors.TextTertiary
        )
    }
}

@Composable
private fun StorageMetricRow(label: String, value: String, count: String) {
    val vaultTypography = LocalVaultTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = vaultTypography.body, color = VaultColors.TextPrimary)
            Text(count, style = vaultTypography.bodySmall, color = VaultColors.TextTertiary)
        }
        Text(value, style = vaultTypography.title, color = VaultColors.AccentCyan, fontWeight = FontWeight.Bold)
    }
}

// -------------------------------------------------------------
// SECTION 4: CODE & TEXT EDITOR
// -------------------------------------------------------------
@Composable
private fun EditorSettingsSection(settingsManager: AppSettingsManager) {
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    val fontSize by settingsManager.editorFontSize.collectAsState()
    val wordWrap by settingsManager.editorWordWrap.collectAsState()
    val lineNumbers by settingsManager.editorLineNumbers.collectAsState()
    val syntaxHighlighting by settingsManager.editorSyntaxHighlighting.collectAsState()
    val tabSize by settingsManager.editorTabSize.collectAsState()
    val useSpaces by settingsManager.editorUseSpaces.collectAsState()
    val autoIndent by settingsManager.editorAutoIndent.collectAsState()
    val lineEnding by settingsManager.editorLineEnding.collectAsState()
    val encoding by settingsManager.editorEncoding.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        // Font Size Slider
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Editor Font Size", style = vaultTypography.title, color = VaultColors.TextPrimary)
                Text("${fontSize}sp", style = vaultTypography.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { settingsManager.setEditorFontSize(it.toInt()) },
                valueRange = 10f..24f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            // Live code preview sample
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VaultColors.SurfaceElevated, RoundedCornerShape(6.dp))
                    .padding(spacing.s)
            ) {
                Text(
                    text = "val encryptedPayload = cipher.doFinal(plaintext)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    color = VaultColors.AccentEmerald
                )
            }
        }

        SettingToggleRow(
            title = "Word Wrap",
            subtitle = "Wrap long code and text lines instead of horizontal scrolling",
            checked = wordWrap,
            icon = Icons.Default.Code,
            onCheckedChange = { settingsManager.setEditorWordWrap(it) }
        )

        SettingToggleRow(
            title = "Line Numbers",
            subtitle = "Display gutter line numbers along the left edge",
            checked = lineNumbers,
            icon = Icons.Default.Code,
            onCheckedChange = { settingsManager.setEditorLineNumbers(it) }
        )

        SettingToggleRow(
            title = "Syntax Highlighting",
            subtitle = "Colorize tokens for Kotlin, JSON, XML, SQL, Markdown, and Python",
            checked = syntaxHighlighting,
            icon = Icons.Default.Palette,
            onCheckedChange = { settingsManager.setEditorSyntaxHighlighting(it) }
        )

        SettingToggleRow(
            title = "Auto-Indent",
            subtitle = "Automatically match indentation on newlines",
            checked = autoIndent,
            icon = Icons.Default.Code,
            onCheckedChange = { settingsManager.setEditorAutoIndent(it) }
        )

        // Tab Size
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Tab Width", style = vaultTypography.title, color = VaultColors.TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                listOf(2, 4, 8).forEach { size ->
                    val isSelected = tabSize == size
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setEditorTabSize(size) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$size Spaces",
                            style = vaultTypography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        SettingToggleRow(
            title = "Insert Spaces for Tabs",
            subtitle = if (useSpaces) "Inserts space characters" else "Inserts literal \\t tab character",
            checked = useSpaces,
            icon = Icons.Default.Code,
            onCheckedChange = { settingsManager.setEditorUseSpaces(it) }
        )

        // Line Ending Preference
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Default Line Endings", style = vaultTypography.title, color = VaultColors.TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                LineEndingPreference.values().forEach { ending ->
                    val isSelected = lineEnding == ending
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setEditorLineEnding(ending) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ending.title.split(" ").first(),
                            style = vaultTypography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // File Encoding Preference
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("File Character Encoding", style = vaultTypography.title, color = VaultColors.TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                EncodingPreference.values().forEach { enc ->
                    val isSelected = encoding == enc
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setEditorEncoding(enc) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = enc.title.split(" ").first(),
                            style = vaultTypography.caption,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 5: MEDIA SETTINGS
// -------------------------------------------------------------
@Composable
private fun MediaSettingsSection(settingsManager: AppSettingsManager) {
    val context = LocalContext.current
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    val playbackSpeed by settingsManager.mediaPlaybackSpeed.collectAsState()
    val autoPlay by settingsManager.mediaAutoPlay.collectAsState()
    val fullscreen by settingsManager.mediaFullscreen.collectAsState()
    val rememberPosition by settingsManager.mediaRememberPosition.collectAsState()
    val thumbnailQuality by settingsManager.mediaThumbnailQuality.collectAsState()
    val cacheLimitMb by settingsManager.mediaCacheLimitMb.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        // Default Playback Speed
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Default Playback Speed", style = vaultTypography.title, color = VaultColors.TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    val isSelected = playbackSpeed == speed
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setMediaPlaybackSpeed(speed) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${speed}x",
                            style = vaultTypography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        SettingToggleRow(
            title = "Auto-Play Media on Open",
            subtitle = "Automatically begin playback when opening video or audio items",
            checked = autoPlay,
            icon = Icons.Default.PlayCircle,
            onCheckedChange = { settingsManager.setMediaAutoPlay(it) }
        )

        SettingToggleRow(
            title = "Default Fullscreen Video",
            subtitle = "Automatically enter fullscreen landscape mode when launching videos",
            checked = fullscreen,
            icon = Icons.Default.PlayCircle,
            onCheckedChange = { settingsManager.setMediaFullscreen(it) }
        )

        SettingToggleRow(
            title = "Remember Playback Position",
            subtitle = "Resume video and audio where you left off",
            checked = rememberPosition,
            icon = Icons.Default.PlayCircle,
            onCheckedChange = { settingsManager.setMediaRememberPosition(it) }
        )

        // Thumbnail Quality
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Thumbnail Generation Quality", style = vaultTypography.title, color = VaultColors.TextPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                ThumbnailQualityPreference.values().forEach { quality ->
                    val isSelected = thumbnailQuality == quality
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setMediaThumbnailQuality(quality) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = quality.title.split(" ").first(),
                            style = vaultTypography.caption,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Cache Limit
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Media Cache Size Limit", style = vaultTypography.title, color = VaultColors.TextPrimary)
                Text("${cacheLimitMb}MB", style = vaultTypography.title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                listOf(50, 100, 250, 500).forEach { limit ->
                    val isSelected = cacheLimitMb == limit
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else VaultColors.SurfaceElevated
                            )
                            .clickable { settingsManager.setMediaCacheLimitMb(limit) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${limit}MB",
                            style = vaultTypography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                MediaThumbnailHelper.clearCache()
                Toast.makeText(context, "Media thumbnail cache cleared", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Clear Media Cache")
        }

        Text(
            text = "SECURITY GUARANTEE: Media files are never exposed in unencrypted form on the device filesystem. Playback utilizes isolated memory buffers and auto-clears on app exit.",
            style = vaultTypography.bodySmall,
            color = VaultColors.TextTertiary
        )
    }
}

// -------------------------------------------------------------
// SECTION 6: NOTIFICATIONS & PRIVACY
// -------------------------------------------------------------
@Composable
private fun NotificationSettingsSection(sessionManager: SessionSecurityManager) {
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    val privacyLevel by sessionManager.notificationPrivacyLevel.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        Text("Notification Privacy Mode", style = vaultTypography.title, color = VaultColors.TextPrimary, fontWeight = FontWeight.SemiBold)

        NotificationPrivacyLevel.values().forEach { level ->
            val isSelected = privacyLevel == level
            VaultGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { sessionManager.setNotificationPrivacyLevel(level) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else VaultColors.GlassBorderMedium,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (level == NotificationPrivacyLevel.MINIMAL) "Minimal Privacy Mode (Recommended)" else "Standard Notifications",
                            style = vaultTypography.title,
                            color = VaultColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (level == NotificationPrivacyLevel.MINIMAL)
                                "Never reveals file names, types, or counts on Android lock screens or notification center."
                            else
                                "Shows operation progress and status without leaking raw file contents.",
                            style = vaultTypography.bodySmall,
                            color = VaultColors.TextTertiary
                        )
                    }
                }
            }
        }

        VaultGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = VaultColors.AccentEmerald, modifier = Modifier.size(24.dp))
                Text(
                    text = "PrivateVault will never trigger push notifications from external servers. All notifications are 100% on-device local system notifications.",
                    style = vaultTypography.bodySmall,
                    color = VaultColors.TextSecondary
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 7: ACCESSIBILITY & HAPTICS
// -------------------------------------------------------------
@Composable
private fun AccessibilitySettingsSection(settingsManager: AppSettingsManager) {
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    val hapticEnabled by settingsManager.hapticFeedbackEnabled.collectAsState()
    val reducedMotion by settingsManager.reducedMotion.collectAsState()
    val highContrast by settingsManager.highContrastEnabled.collectAsState()
    val largeTouchTargets by settingsManager.largeTouchTargets.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        SettingToggleRow(
            title = "Haptic Tactile Feedback",
            subtitle = "Subtle vibration on keypad PIN entry and security actions",
            checked = hapticEnabled,
            icon = Icons.Default.Fingerprint,
            onCheckedChange = { settingsManager.setHapticFeedbackEnabled(it) }
        )

        SettingToggleRow(
            title = "Reduced Motion",
            subtitle = "Reduces UI transitions and eliminates non-critical animations",
            checked = reducedMotion,
            icon = Icons.Default.AccessibilityNew,
            onCheckedChange = { settingsManager.setReducedMotion(it) }
        )

        SettingToggleRow(
            title = "High Contrast Mode",
            subtitle = "Enhances text legibility and card outline contrast",
            checked = highContrast,
            icon = Icons.Default.AccessibilityNew,
            onCheckedChange = { settingsManager.setHighContrastEnabled(it) }
        )

        SettingToggleRow(
            title = "Enforce Large Touch Targets",
            subtitle = "Maintains minimum 48dp component dimensions across all interactables",
            checked = largeTouchTargets,
            icon = Icons.Default.AccessibilityNew,
            onCheckedChange = { settingsManager.setLargeTouchTargets(it) }
        )

        VaultGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Text("ACCESSIBILITY COMPLIANCE", style = vaultTypography.caption, color = VaultColors.TextTertiary, fontWeight = FontWeight.Bold)
                Text(
                    text = "PrivateVault is engineered for full TalkBack screen reader compatibility, scalable system font typography, and distinct non-color state indicators.",
                    style = vaultTypography.bodySmall,
                    color = VaultColors.TextSecondary
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 8: BACKUP & RECOVERY
// -------------------------------------------------------------
@Composable
private fun BackupSettingsSection(onNavigateToBackupCenter: () -> Unit) {
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        VaultGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Text("ENCRYPTED OFFLINE BACKUPS", style = vaultTypography.caption, color = VaultColors.TextTertiary, fontWeight = FontWeight.Bold)
                Text(
                    text = "Generate password-protected AES-256-GCM backup archives or import previous backups without relying on cloud infrastructure.",
                    style = vaultTypography.body,
                    color = VaultColors.TextSecondary
                )
            }
        }

        Button(
            onClick = onNavigateToBackupCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("Open Backup & Recovery Center", fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------
// SECTION 9: ABOUT PRIVATEVAULT
// -------------------------------------------------------------
@Composable
private fun AboutSettingsSection(storageSummary: StorageSummary?) {
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        VaultGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.s),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.l)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "PrivateVault",
                    style = vaultTypography.headline,
                    color = VaultColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Version 2.4.0 (Hardened Build)",
                    style = vaultTypography.bodySmall,
                    color = VaultColors.TextTertiary
                )

                SecurityPill(
                    text = "LOCAL-FIRST SECURE ENCLAVE",
                    dotColor = VaultColors.AccentEmerald
                )
            }
        }

        // Security Architecture Breakdown
        VaultGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Text("SECURITY ARCHITECTURE", style = vaultTypography.caption, color = VaultColors.TextTertiary, fontWeight = FontWeight.Bold)

                AboutDetailRow("Authenticated Cipher", "AES-256-GCM with 128-bit authentication tag")
                AboutDetailRow("Key Derivation", "PBKDF2 with HMAC-SHA256 (100,000 rounds) + Unique Salt")
                AboutDetailRow("Key Storage", "Android Keystore hardware-backed secure master key")
                AboutDetailRow("Network Transmissions", "0 bytes (Pure local-first, zero cloud, zero ads)")
                AboutDetailRow("Database Storage", "Room SQLite encrypted object registry")
            }
        }

        // Open Source & Legal
        VaultGlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(spacing.m),
                verticalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Text("OPEN SOURCE & LEGAL", style = vaultTypography.caption, color = VaultColors.TextTertiary, fontWeight = FontWeight.Bold)
                Text(
                    text = "Built with AndroidX, Jetpack Compose, Kotlin Coroutines, and Room SQLite under Apache 2.0 and Google Play policy compliance.",
                    style = vaultTypography.bodySmall,
                    color = VaultColors.TextSecondary
                )
                Text(
                    text = "Notice: Flash storage wear-leveling prevents guaranteed physical sector overwrites. Encryption keys and headers are permanently shredded to render data unrecoverable.",
                    style = vaultTypography.bodySmall,
                    color = VaultColors.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun AboutDetailRow(label: String, value: String) {
    val vaultTypography = LocalVaultTypography.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = vaultTypography.bodySmall, color = VaultColors.TextSecondary)
        Text(value, style = vaultTypography.caption, color = VaultColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

// -------------------------------------------------------------
// HELPER TOGGLE ROW
// -------------------------------------------------------------
@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = LocalVaultSpacing.current
    val vaultTypography = LocalVaultTypography.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(VaultColors.SurfaceElevated, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else VaultColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = vaultTypography.title,
                    color = VaultColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = vaultTypography.bodySmall,
                    color = VaultColors.TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.width(spacing.s))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = VaultColors.TextTertiary,
                uncheckedTrackColor = VaultColors.SurfaceElevated
            )
        )
    }
}
