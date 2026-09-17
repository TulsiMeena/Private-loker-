package com.example.feature.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.core.database.SecurityAuditLogEntity
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.security.AutoLockTimeout
import com.example.core.security.NotificationPrivacyLevel
import com.example.core.security.SecurityCheckupItem
import com.example.core.security.SecurityCheckupReport
import com.example.core.security.SecuritySeverity
import com.example.core.security.VaultIntegrityReport
import com.example.core.security.VaultSecurityDashboardState
import com.example.core.ui.SecurityPill
import com.example.core.ui.VaultGlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun SecurityCenterScreen(
    viewModel: SecurityCenterViewModel,
    activity: FragmentActivity? = null,
    onBackClick: () -> Unit,
    onNavigateToLock: () -> Unit,
    onNavigateToBackupCenter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = LocalVaultSpacing.current
    val dashboardState by viewModel.dashboardState.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val latestBackup by viewModel.latestBackup.collectAsState()
    val latestVerified by viewModel.latestVerifiedBackup.collectAsState()

    val isCheckingIntegrity by viewModel.isCheckingIntegrity.collectAsState()
    val integrityReport by viewModel.integrityReport.collectAsState()
    val isCheckingSecurity by viewModel.isCheckingSecurity.collectAsState()
    val checkupReport by viewModel.checkupReport.collectAsState()

    val showChangePinDialog by viewModel.changePinDialogVisible.collectAsState()
    val showDisableBiometricDialog by viewModel.disableBiometricDialogVisible.collectAsState()
    val showEmergencyLockDialog by viewModel.emergencyLockDialogVisible.collectAsState()
    val showClearActivityDialog by viewModel.clearActivityDialogVisible.collectAsState()
    val showPurgeTempDialog by viewModel.purgeTempFilesDialogVisible.collectAsState()
    val showIntegrityDialog by viewModel.integrityReportDialogVisible.collectAsState()
    val showCheckupDialog by viewModel.checkupReportDialogVisible.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    // 1. Protected Access Enforcement
    val state = dashboardState
    if (state != null && !state.isSessionUnlocked && !state.isSetupRequired) {
        LaunchedEffect(Unit) {
            onNavigateToLock()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VaultColors.Canvas),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Vault Sealed",
                    tint = VaultColors.AccentCyan,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Vault Sealed",
                    color = VaultColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Authentication required to access Security Center",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    Box(
        modifier = modifier
            .testTag("security_center_screen")
            .fillMaxSize()
            .background(VaultColors.Canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header App Bar
            SecurityCenterHeader(
                onBackClick = onBackClick,
                onEmergencyLockClick = { viewModel.showEmergencyLockDialog(true) },
                onQuickLockClick = { viewModel.manualLockVault(onNavigateToLock) }
            )

            if (state == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = VaultColors.AccentCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("security_center_content_list"),
                    contentPadding = PaddingValues(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = spacing.s,
                        bottom = spacing.xxl * 2
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.l)
                ) {
                    // Top Overview Status Card
                    item {
                        SecurityOverviewStatusCard(
                            state = state,
                            onLockVaultClick = { viewModel.manualLockVault(onNavigateToLock) },
                            onRunCheckupClick = { viewModel.runSecurityCheckup() }
                        )
                    }

                    // Section 1: Authentication
                    item {
                        SectionHeader(title = "AUTHENTICATION", subtitle = "Master PIN and biometric security controls")
                    }
                    item {
                        AuthenticationSectionCard(
                            state = state,
                            onToggleBiometric = { viewModel.toggleBiometric(activity) },
                            onChangePinClick = { viewModel.showChangePinDialog(true) }
                        )
                    }

                    // Section 2: Session Protection
                    item {
                        SectionHeader(title = "SESSION PROTECTION", subtitle = "Auto-lock intervals, failed attempt defense, and lockdown")
                    }
                    item {
                        SessionProtectionSectionCard(
                            state = state,
                            onSelectAutoLock = { viewModel.setAutoLockTimeout(it) },
                            onManualLock = { viewModel.manualLockVault(onNavigateToLock) },
                            onEmergencyLock = { viewModel.showEmergencyLockDialog(true) }
                        )
                    }

                    // Section 3: Screen & App Privacy
                    item {
                        SectionHeader(title = "SCREEN & APP PRIVACY", subtitle = "Prevent screen capture and recent apps leakage")
                    }
                    item {
                        ScreenPrivacySectionCard(
                            state = state,
                            onToggleScreenProtection = { viewModel.setScreenProtectionEnabled(it) },
                            onToggleRecentApps = { viewModel.setRecentAppPrivacyEnabled(it) }
                        )
                    }

                    // Section 4: Data & Storage Privacy
                    item {
                        SectionHeader(title = "DATA & STORAGE PRIVACY", subtitle = "Sensitive clipboard, notification masking, and transient files")
                    }
                    item {
                        DataPrivacySectionCard(
                            state = state,
                            onToggleClipboard = { viewModel.setClipboardProtectionEnabled(it) },
                            onSelectClipboardTimeout = { viewModel.setClipboardTimeoutSeconds(it) },
                            onSelectNotificationPrivacy = { viewModel.setNotificationPrivacyLevel(it) },
                            onPurgeTempFiles = { viewModel.showPurgeTempFilesDialog(true) }
                        )
                    }

                    // Section 5: Vault Health & Integrity
                    item {
                        SectionHeader(title = "VAULT HEALTH & INTEGRITY", subtitle = "Cryptographic AEAD verification and storage consistency")
                    }
                    item {
                        VaultHealthSectionCard(
                            state = state,
                            isCheckingIntegrity = isCheckingIntegrity,
                            isCheckingSecurity = isCheckingSecurity,
                            onRunIntegrityCheck = { viewModel.runIntegrityCheck() },
                            onRunSecurityCheckup = { viewModel.runSecurityCheckup() }
                        )
                    }

                    // Section 6: Backup & Disaster Recovery
                    item {
                        SectionHeader(title = "BACKUP & DISASTER RECOVERY", subtitle = "Encrypted local backup state and disaster recovery readiness")
                    }
                    item {
                        VaultBackupStatusSectionCard(
                            latestBackup = latestBackup,
                            latestVerified = latestVerified,
                            onOpenBackupCenter = onNavigateToBackupCenter
                        )
                    }

                    // Section 7: Security Activity (Audit Logs)
                    item {
                        SectionHeader(
                            title = "SECURITY ACTIVITY",
                            subtitle = "Local tamper-evident security audit trail (${auditLogs.size} events)"
                        )
                    }
                    item {
                        SecurityActivitySectionCard(
                            auditLogs = auditLogs,
                            onClearActivity = { viewModel.showClearActivityDialog(true) }
                        )
                    }

                    // Section 7: Device Security State
                    item {
                        SectionHeader(title = "DEVICE HARDWARE SECURITY", subtitle = "Host operating system and hardware enclave status")
                    }
                    item {
                        DeviceSecuritySectionCard(state = state)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )

        // Dialogs
        if (showChangePinDialog) {
            ChangePinDialog(
                currentPinLength = state?.masterPinLength ?: 4,
                onDismiss = { viewModel.showChangePinDialog(false) },
                onConfirm = { currentPin, newPin, confirmPin ->
                    viewModel.changeMasterPin(currentPin, newPin, confirmPin) {
                        // PIN changed
                    }
                }
            )
        }

        if (showDisableBiometricDialog) {
            DisableBiometricConfirmDialog(
                onDismiss = { viewModel.showDisableBiometricDialog(false) },
                onConfirm = { viewModel.confirmDisableBiometric() }
            )
        }

        if (showEmergencyLockDialog) {
            EmergencyLockConfirmDialog(
                onDismiss = { viewModel.showEmergencyLockDialog(false) },
                onConfirm = { viewModel.executeEmergencyLock(onNavigateToLock) }
            )
        }

        if (showClearActivityDialog) {
            ClearActivityConfirmDialog(
                onDismiss = { viewModel.showClearActivityDialog(false) },
                onConfirm = { viewModel.clearSecurityActivity() }
            )
        }

        if (showPurgeTempDialog) {
            PurgeTempConfirmDialog(
                tempCount = state?.tempFilesCount ?: 0,
                tempSizeBytes = state?.tempFilesSizeBytes ?: 0L,
                onDismiss = { viewModel.showPurgeTempFilesDialog(false) },
                onConfirm = { viewModel.purgeTemporaryFiles() }
            )
        }

        if (showIntegrityDialog && integrityReport != null) {
            IntegrityReportDialog(
                report = integrityReport!!,
                onDismiss = { viewModel.showIntegrityReportDialog(false) }
            )
        }

        if (showCheckupDialog && checkupReport != null) {
            SecurityCheckupDialog(
                report = checkupReport!!,
                onDismiss = { viewModel.showCheckupReportDialog(false) },
                onAction = { actionType ->
                    viewModel.showCheckupReportDialog(false)
                    when (actionType) {
                        "CHANGE_PIN", "SETUP_PIN" -> viewModel.showChangePinDialog(true)
                        "ENABLE_BIOMETRIC" -> viewModel.enableBiometricWithPrompt(activity)
                        "SET_AUTOLOCK_1M" -> viewModel.setAutoLockTimeout(AutoLockTimeout.ONE_MINUTE)
                        "ENABLE_SCREEN_PROT" -> viewModel.setScreenProtectionEnabled(true)
                        "ENABLE_RECENT_APP" -> viewModel.setRecentAppPrivacyEnabled(true)
                        "ENABLE_CLIPBOARD" -> viewModel.setClipboardProtectionEnabled(true)
                    }
                }
            )
        }
    }
}

@Composable
private fun SecurityCenterHeader(
    onBackClick: () -> Unit,
    onEmergencyLockClick: () -> Unit,
    onQuickLockClick: () -> Unit
) {
    val spacing = LocalVaultSpacing.current

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
                modifier = Modifier.testTag("security_center_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = VaultColors.TextPrimary
                )
            }
            Column {
                Text(
                    text = "Security & Privacy",
                    color = VaultColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Fortress Control Center",
                    color = VaultColors.AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            IconButton(
                onClick = onQuickLockClick,
                modifier = Modifier.testTag("security_header_lock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Vault",
                    tint = VaultColors.AccentCyan
                )
            }
            IconButton(
                onClick = onEmergencyLockClick,
                modifier = Modifier.testTag("security_header_emergency_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Emergency Lock",
                    tint = VaultColors.AccentCrimson
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    val spacing = LocalVaultSpacing.current
    Column(modifier = Modifier.padding(top = spacing.xs, bottom = spacing.xxs)) {
        Text(
            text = title,
            color = VaultColors.AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = subtitle,
            color = VaultColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SecurityOverviewStatusCard(
    state: VaultSecurityDashboardState,
    onLockVaultClick: () -> Unit,
    onRunCheckupClick: () -> Unit
) {
    val spacing = LocalVaultSpacing.current
    val isProtected = state.isSessionUnlocked

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_overview_card"),
        borderColor = if (isProtected) VaultColors.AccentEmerald.copy(alpha = 0.3f) else VaultColors.AccentAmber.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.AccentEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Security Shield",
                            tint = VaultColors.AccentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Vault Status",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = state.vaultStatusText,
                            color = VaultColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                SecurityPill(
                    text = if (isProtected) "SESSION ACTIVE" else "SEALED",
                    dotColor = if (isProtected) VaultColors.AccentEmerald else VaultColors.AccentAmber
                )
            }

            Spacer(modifier = Modifier.height(spacing.m))

            // 4 Key Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                StatusMiniTile(
                    modifier = Modifier.weight(1f),
                    label = "Biometric",
                    value = if (state.isBiometricEnabled) "Enabled" else "Disabled",
                    color = if (state.isBiometricEnabled) VaultColors.AccentEmerald else VaultColors.TextSecondary
                )
                StatusMiniTile(
                    modifier = Modifier.weight(1f),
                    label = "Master PIN",
                    value = "${state.masterPinLength} Digits",
                    color = VaultColors.AccentCyan
                )
                StatusMiniTile(
                    modifier = Modifier.weight(1f),
                    label = "Auto-Lock",
                    value = state.autoLockTimeout.displayName.substringBefore(" ("),
                    color = if (state.autoLockTimeout == AutoLockTimeout.NEVER) VaultColors.AccentAmber else VaultColors.Platinum
                )
                StatusMiniTile(
                    modifier = Modifier.weight(1f),
                    label = "Screen Shield",
                    value = if (state.screenProtectionEnabled) "Active" else "Off",
                    color = if (state.screenProtectionEnabled) VaultColors.AccentEmerald else VaultColors.AccentAmber
                )
            }

            Spacer(modifier = Modifier.height(spacing.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Button(
                    onClick = onRunCheckupClick,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceElevated),
                    border = BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overview_run_checkup_button"),
                    contentPadding = PaddingValues(vertical = spacing.s)
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = "Checkup",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Security Checkup",
                        color = VaultColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onLockVaultClick,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, VaultColors.AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overview_lock_vault_button"),
                    contentPadding = PaddingValues(vertical = spacing.s)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lock Vault Now",
                        color = VaultColors.AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMiniTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    val spacing = LocalVaultSpacing.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VaultColors.SurfaceElevated)
            .border(BorderStroke(1.dp, VaultColors.GlassBorderSubtle), RoundedCornerShape(8.dp))
            .padding(vertical = spacing.xs, horizontal = spacing.s)
    ) {
        Column {
            Text(
                text = label,
                color = VaultColors.TextSecondary,
                fontSize = 10.sp
            )
            Text(
                text = value,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AuthenticationSectionCard(
    state: VaultSecurityDashboardState,
    onToggleBiometric: () -> Unit,
    onChangePinClick: () -> Unit
) {
    val spacing = LocalVaultSpacing.current

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_section_card")
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            // Biometric Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Icon",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Biometric Authentication",
                            color = VaultColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.biometricStatusText,
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = state.isBiometricEnabled,
                    onCheckedChange = { onToggleBiometric() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VaultColors.AccentCyan,
                        checkedTrackColor = VaultColors.AccentCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = VaultColors.TextSecondary,
                        uncheckedTrackColor = VaultColors.SurfaceElevated
                    ),
                    modifier = Modifier.testTag("toggle_biometric_switch")
                )
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            // PIN Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "PIN Icon",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Master PIN Protection",
                            color = VaultColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Configured (${state.masterPinLength} digits)",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onChangePinClick,
                    border = BorderStroke(1.dp, VaultColors.AccentCyan.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan),
                    modifier = Modifier.testTag("change_master_pin_button")
                ) {
                    Text("Change PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))
            Text(
                text = "Protected with PBKDF2 cryptographic key derivation and unique per-vault salt. Biometric data is evaluated only by host OS secure enclave.",
                color = VaultColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SessionProtectionSectionCard(
    state: VaultSecurityDashboardState,
    onSelectAutoLock: (AutoLockTimeout) -> Unit,
    onManualLock: () -> Unit,
    onEmergencyLock: () -> Unit
) {
    val spacing = LocalVaultSpacing.current
    var autoLockMenuExpanded by remember { mutableStateOf(false) }

    val formattedUnlockTime = remember(state.lastUnlockTimestamp) {
        if (state.lastUnlockTimestamp > 0) {
            SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault()).format(Date(state.lastUnlockTimestamp))
        } else {
            "Active session"
        }
    }

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_protection_card")
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            // Auto Lock Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timer Icon",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Auto-Lock Interval",
                            color = VaultColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Locks after app is inactive in background",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Box {
                    Button(
                        onClick = { autoLockMenuExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceElevated),
                        border = BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                        modifier = Modifier.testTag("autolock_dropdown_button")
                    ) {
                        Text(
                            text = state.autoLockTimeout.displayName,
                            color = VaultColors.Platinum,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DropdownMenu(
                        expanded = autoLockMenuExpanded,
                        onDismissRequest = { autoLockMenuExpanded = false },
                        modifier = Modifier.background(VaultColors.SurfaceGraphite)
                    ) {
                        AutoLockTimeout.entries.forEach { timeout ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = timeout.displayName,
                                        color = if (timeout == state.autoLockTimeout) VaultColors.AccentCyan else VaultColors.TextPrimary
                                    )
                                },
                                onClick = {
                                    onSelectAutoLock(timeout)
                                    autoLockMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            // Current Session Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Session",
                        color = VaultColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Unlocked: $formattedUnlockTime",
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onManualLock,
                    border = BorderStroke(1.dp, VaultColors.AccentCyan.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan),
                    modifier = Modifier.testTag("session_lock_button")
                ) {
                    Text("Lock Session", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            // Failed Authentication Defense
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Failed Attempt Defense",
                        color = VaultColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (state.isLockoutActive) {
                            "Cooldown active: ${state.lockoutRemainingSeconds}s remaining"
                        } else {
                            "Recent failed attempts: ${state.failedAttempts} / ${state.maxFailedAttempts}"
                        },
                        color = if (state.isLockoutActive) VaultColors.AccentCrimson else VaultColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (state.isLockoutActive) FontWeight.Bold else FontWeight.Normal
                    )
                }

                SecurityPill(
                    text = if (state.isLockoutActive) "COOLDOWN" else "PROTECTED",
                    dotColor = if (state.isLockoutActive) VaultColors.AccentCrimson else VaultColors.AccentEmerald
                )
            }

            Spacer(modifier = Modifier.height(spacing.s))
            Text(
                text = "Progressive lockout delay enforced after 5 failed attempts (30s, 60s, 120s). PrivateVault never performs automatic destructive data wipes.",
                color = VaultColors.TextTertiary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(spacing.m))

            // Emergency Lock Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(VaultColors.AccentCrimson.copy(alpha = 0.08f))
                    .border(BorderStroke(1.dp, VaultColors.AccentCrimson.copy(alpha = 0.25f)), RoundedCornerShape(8.dp))
                    .padding(spacing.m)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.s)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Emergency Alert",
                                tint = VaultColors.AccentCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Emergency Lockdown",
                                color = VaultColors.AccentCrimson,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onEmergencyLock,
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson),
                            modifier = Modifier.testTag("emergency_lock_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Emergency Lock Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Text(
                        text = "Immediately seals the vault and clears temporary session memory without deleting any files.",
                        color = VaultColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenPrivacySectionCard(
    state: VaultSecurityDashboardState,
    onToggleScreenProtection: (Boolean) -> Unit,
    onToggleRecentApps: (Boolean) -> Unit
) {
    val spacing = LocalVaultSpacing.current

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("screen_privacy_card")
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            // Screenshot Protection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Visibility Shield",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Prevent Screenshots & Capture",
                            color = VaultColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Applies Android FLAG_SECURE window shield",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = state.screenProtectionEnabled,
                    onCheckedChange = onToggleScreenProtection,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VaultColors.AccentCyan,
                        checkedTrackColor = VaultColors.AccentCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = VaultColors.TextSecondary,
                        uncheckedTrackColor = VaultColors.SurfaceElevated
                    ),
                    modifier = Modifier.testTag("toggle_screen_protection_switch")
                )
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            // Recent App Privacy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Recent App Shield",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Hide Content in Recent Apps",
                            color = VaultColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Masks vault preview in app switcher",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = state.recentAppPrivacyEnabled,
                    onCheckedChange = onToggleRecentApps,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VaultColors.AccentCyan,
                        checkedTrackColor = VaultColors.AccentCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = VaultColors.TextSecondary,
                        uncheckedTrackColor = VaultColors.SurfaceElevated
                    ),
                    modifier = Modifier.testTag("toggle_recent_apps_switch")
                )
            }
        }
    }
}

@Composable
private fun DataPrivacySectionCard(
    state: VaultSecurityDashboardState,
    onToggleClipboard: (Boolean) -> Unit,
    onSelectClipboardTimeout: (Int) -> Unit,
    onSelectNotificationPrivacy: (NotificationPrivacyLevel) -> Unit,
    onPurgeTempFiles: () -> Unit
) {
    val spacing = LocalVaultSpacing.current
    var clipTimeoutMenuExpanded by remember { mutableStateOf(false) }
    var notifMenuExpanded by remember { mutableStateOf(false) }

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("data_privacy_card")
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            // Sensitive Clipboard Defense
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Clipboard Icon",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sensitive Clipboard Defense",
                            color = VaultColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tags clipboard sensitive & clears automatically",
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = state.clipboardProtectionEnabled,
                    onCheckedChange = onToggleClipboard,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VaultColors.AccentCyan,
                        checkedTrackColor = VaultColors.AccentCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = VaultColors.TextSecondary,
                        uncheckedTrackColor = VaultColors.SurfaceElevated
                    ),
                    modifier = Modifier.testTag("toggle_clipboard_switch")
                )
            }

            if (state.clipboardProtectionEnabled) {
                Spacer(modifier = Modifier.height(spacing.s))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 46.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-Clear Delay",
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp
                    )

                    Box {
                        Button(
                            onClick = { clipTimeoutMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceElevated),
                            border = BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("clipboard_timeout_button")
                        ) {
                            Text(
                                text = "${state.clipboardTimeoutSeconds} Seconds",
                                color = VaultColors.Platinum,
                                fontSize = 12.sp
                            )
                        }

                        DropdownMenu(
                            expanded = clipTimeoutMenuExpanded,
                            onDismissRequest = { clipTimeoutMenuExpanded = false },
                            modifier = Modifier.background(VaultColors.SurfaceGraphite)
                        ) {
                            listOf(30, 60, 300).forEach { sec ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (sec >= 60) "${sec / 60} Minute(s)" else "$sec Seconds",
                                            color = if (sec == state.clipboardTimeoutSeconds) VaultColors.AccentCyan else VaultColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        onSelectClipboardTimeout(sec)
                                        clipTimeoutMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            // Notification Privacy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VaultColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Notification Icon",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Notification Privacy",
                            color = VaultColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = state.notificationPrivacyLevel.description,
                            color = VaultColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Box {
                    Button(
                        onClick = { notifMenuExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceElevated),
                        border = BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                        modifier = Modifier.testTag("notification_privacy_button")
                    ) {
                        Text(
                            text = state.notificationPrivacyLevel.displayName,
                            color = VaultColors.Platinum,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DropdownMenu(
                        expanded = notifMenuExpanded,
                        onDismissRequest = { notifMenuExpanded = false },
                        modifier = Modifier.background(VaultColors.SurfaceGraphite)
                    ) {
                        NotificationPrivacyLevel.entries.forEach { level ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = level.displayName,
                                        color = if (level == state.notificationPrivacyLevel) VaultColors.AccentCyan else VaultColors.TextPrimary
                                    )
                                },
                                onClick = {
                                    onSelectNotificationPrivacy(level)
                                    notifMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            // Temporary Storage Cleanup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Temporary Working Files",
                        color = VaultColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${state.tempFilesCount} active cached files (${formatFileSize(state.tempFilesSizeBytes)})",
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = onPurgeTempFiles,
                    border = BorderStroke(1.dp, VaultColors.AccentCyan.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan),
                    modifier = Modifier.testTag("purge_temp_files_button")
                ) {
                    Text("Shred Now", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun VaultHealthSectionCard(
    state: VaultSecurityDashboardState,
    isCheckingIntegrity: Boolean,
    isCheckingSecurity: Boolean,
    onRunIntegrityCheck: () -> Unit,
    onRunSecurityCheckup: () -> Unit
) {
    val spacing = LocalVaultSpacing.current

    val lastCheckTime = remember(state.lastIntegrityCheckTimestamp) {
        if (state.lastIntegrityCheckTimestamp > 0) {
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(state.lastIntegrityCheckTimestamp))
        } else {
            "Never"
        }
    }

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_health_card")
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                StatusMiniTile(
                    modifier = Modifier.weight(1f),
                    label = "Encrypted Storage",
                    value = formatFileSize(state.totalEncryptedSizeBytes),
                    color = VaultColors.AccentCyan
                )
                StatusMiniTile(
                    modifier = Modifier.weight(1f),
                    label = "Metadata Records",
                    value = "${state.totalVaultItemsCount} Items",
                    color = VaultColors.AccentEmerald
                )
                StatusMiniTile(
                    modifier = Modifier.weight(1f),
                    label = "Last Integrity Check",
                    value = lastCheckTime,
                    color = VaultColors.Platinum
                )
            }

            state.lastIntegritySummary?.let { summary ->
                Spacer(modifier = Modifier.height(spacing.s))
                Text(
                    text = summary,
                    color = VaultColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(spacing.m))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.s)
            ) {
                Button(
                    onClick = onRunIntegrityCheck,
                    enabled = !isCheckingIntegrity,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.SurfaceElevated),
                    border = BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("run_integrity_check_button")
                ) {
                    if (isCheckingIntegrity) {
                        CircularProgressIndicator(
                            color = VaultColors.AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...", color = VaultColors.TextPrimary, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Integrity",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify Storage", color = VaultColors.TextPrimary, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onRunSecurityCheckup,
                    enabled = !isCheckingSecurity,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, VaultColors.AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("run_security_checkup_button")
                ) {
                    if (isCheckingSecurity) {
                        CircularProgressIndicator(
                            color = VaultColors.AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auditing...", color = VaultColors.AccentCyan, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = "Checkup",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Security Checkup", color = VaultColors.AccentCyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityActivitySectionCard(
    auditLogs: List<SecurityAuditLogEntity>,
    onClearActivity: () -> Unit
) {
    val spacing = LocalVaultSpacing.current

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("security_activity_card")
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Security Events",
                    color = VaultColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (auditLogs.isNotEmpty()) {
                    TextButton(
                        onClick = onClearActivity,
                        modifier = Modifier.testTag("clear_security_activity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear",
                            tint = VaultColors.AccentCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear History", color = VaultColors.AccentCrimson, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.s))

            if (auditLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.m),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recorded security events.",
                        color = VaultColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    auditLogs.take(8).forEach { log ->
                        val dateFormatted = remember(log.timestamp) {
                            SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(VaultColors.SurfaceElevated)
                                .padding(spacing.s),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.s)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (log.isSuccess) VaultColors.AccentEmerald else VaultColors.AccentCrimson)
                                )
                                Column {
                                    Text(
                                        text = log.action,
                                        color = VaultColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    log.details?.let {
                                        Text(
                                            text = it,
                                            color = VaultColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Text(
                                text = dateFormatted,
                                color = VaultColors.TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSecuritySectionCard(state: VaultSecurityDashboardState) {
    val spacing = LocalVaultSpacing.current

    VaultGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_security_card")
    ) {
        Column(modifier = Modifier.padding(spacing.l)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Device",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Host Device Lock Screen",
                            color = VaultColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.isDeviceSecure) "Protected by Android System Lock" else "No device lock configured",
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                SecurityPill(
                    text = if (state.isDeviceSecure) "SECURE" else "UNPROTECTED",
                    dotColor = if (state.isDeviceSecure) VaultColors.AccentEmerald else VaultColors.AccentAmber
                )
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Keystore",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Android KeyStore Enclave",
                            color = VaultColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Hardware-backed AES-256-GCM keys",
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                SecurityPill(text = "ACTIVE", dotColor = VaultColors.AccentEmerald)
            }

            Spacer(modifier = Modifier.height(spacing.m))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(VaultColors.GlassBorderSubtle)
            )
            Spacer(modifier = Modifier.height(spacing.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Offline Fortress",
                        tint = VaultColors.AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Air-Gapped Network Isolation",
                            color = VaultColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "0 internet permissions. Zero cloud transmission.",
                            color = VaultColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                SecurityPill(text = "OFFLINE ONLY", dotColor = VaultColors.AccentCyan)
            }
        }
    }
}

// Dialog Implementations

@Composable
private fun ChangePinDialog(
    currentPinLength: Int,
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, newPin: String, confirmPin: String) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceGraphite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Key, contentDescription = null, tint = VaultColors.AccentCyan)
                Text("Change Master PIN", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Verify your current master PIN and enter a new PIN (4 to 8 digits).",
                    color = VaultColors.TextSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) currentPin = it },
                    label = { Text("Current PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_current_input")
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("New PIN (4-8 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_new_input")
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextPrimary,
                        focusedBorderColor = VaultColors.AccentCyan,
                        unfocusedBorderColor = VaultColors.GlassBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_confirm_input")
                )

                localError?.let { err ->
                    Text(text = err, color = VaultColors.AccentCrimson, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentPin.isEmpty()) {
                        localError = "Please enter current PIN"
                        return@Button
                    }
                    if (newPin.length < 4 || newPin.length > 8) {
                        localError = "New PIN must be between 4 and 8 digits"
                        return@Button
                    }
                    if (newPin != confirmPin) {
                        localError = "New PIN and confirmation do not match"
                        return@Button
                    }
                    localError = null
                    onConfirm(currentPin, newPin, confirmPin)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                modifier = Modifier.testTag("change_pin_submit_button")
            ) {
                Text("Update PIN", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun DisableBiometricConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceGraphite,
        title = {
            Text("Disable Biometric Unlock?", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "Disabling biometrics means you will need to enter your Master PIN every time you access the vault.",
                color = VaultColors.TextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentAmber),
                modifier = Modifier.testTag("confirm_disable_biometric_button")
            ) {
                Text("Disable", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Enabled", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun EmergencyLockConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceGraphite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = VaultColors.AccentCrimson)
                Text("Emergency Lockdown", color = VaultColors.AccentCrimson, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This will immediately:",
                    color = VaultColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("• Seal the authentication session and lock the vault", color = VaultColors.TextSecondary, fontSize = 12.sp)
                Text("• Shred all transient and temporary decrypted files", color = VaultColors.TextSecondary, fontSize = 12.sp)
                Text("• Clear sensitive clipboard contents", color = VaultColors.TextSecondary, fontSize = 12.sp)
                Text("• Return to the Lock Screen", color = VaultColors.TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Note: None of your encrypted vault files will be deleted or altered.",
                    color = VaultColors.AccentEmerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson),
                modifier = Modifier.testTag("confirm_emergency_lock_button")
            ) {
                Text("Lockdown Now", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun ClearActivityConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceGraphite,
        title = {
            Text("Clear Security Activity?", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "This will permanently purge the local security audit history from this device.",
                color = VaultColors.TextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCrimson),
                modifier = Modifier.testTag("confirm_clear_activity_button")
            ) {
                Text("Clear History", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun PurgeTempConfirmDialog(
    tempCount: Int,
    tempSizeBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceGraphite,
        title = {
            Text("Shred Temporary Files?", color = VaultColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "Securely overwrite and purge $tempCount transient cached working files (${formatFileSize(tempSizeBytes)}) immediately.",
                color = VaultColors.TextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan),
                modifier = Modifier.testTag("confirm_purge_temp_button")
            ) {
                Text("Shred Files", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun IntegrityReportDialog(
    report: VaultIntegrityReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceGraphite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (report.isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (report.isHealthy) VaultColors.AccentEmerald else VaultColors.AccentAmber
                )
                Text(
                    text = if (report.isHealthy) "Storage Verified Healthy" else "Integrity Findings",
                    color = VaultColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = report.summaryMessage, color = VaultColors.TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Total Metadata Records: ${report.totalDatabaseRecords}", color = VaultColors.TextPrimary, fontSize = 12.sp)
                Text("• Cryptographically Verified Objects: ${report.verifiedObjectsCount}", color = VaultColors.TextPrimary, fontSize = 12.sp)
                Text("• Missing Physical Objects: ${report.missingObjects.size}", color = if (report.missingObjects.isEmpty()) VaultColors.TextSecondary else VaultColors.AccentCrimson, fontSize = 12.sp)
                Text("• Orphaned Storage Containers: ${report.orphanedStorageFiles.size}", color = if (report.orphanedStorageFiles.isEmpty()) VaultColors.TextSecondary else VaultColors.AccentAmber, fontSize = 12.sp)
                Text("• Active Transient Files: ${report.temporaryFilesCount} (${formatFileSize(report.temporaryFilesSizeBytes)})", color = VaultColors.TextSecondary, fontSize = 12.sp)
                Text("• Encrypted Volume Total: ${formatFileSize(report.totalEncryptedSizeBytes)}", color = VaultColors.TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
            ) {
                Text("Close", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SecurityCheckupDialog(
    report: SecurityCheckupReport,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.SurfaceGraphite,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = if (report.overallStatus == "Recommended") VaultColors.AccentEmerald else VaultColors.AccentAmber
                )
                Column {
                    Text(
                        text = "Security Checkup",
                        color = VaultColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${report.passedCount} of ${report.totalCount} standards met • ${report.overallStatus}",
                        color = if (report.overallStatus == "Recommended") VaultColors.AccentEmerald else VaultColors.AccentAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(report.items) { item ->
                    CheckupItemRow(item = item, onAction = onAction)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VaultColors.AccentCyan)
            ) {
                Text("Done", color = VaultColors.Canvas, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CheckupItemRow(
    item: SecurityCheckupItem,
    onAction: (String) -> Unit
) {
    val borderColor = when (item.severity) {
        SecuritySeverity.PASSED -> VaultColors.AccentEmerald.copy(alpha = 0.3f)
        SecuritySeverity.RECOMMENDED -> VaultColors.AccentCyan.copy(alpha = 0.3f)
        SecuritySeverity.ATTENTION_REQUIRED -> VaultColors.AccentCrimson.copy(alpha = 0.4f)
    }

    val iconColor = when (item.severity) {
        SecuritySeverity.PASSED -> VaultColors.AccentEmerald
        SecuritySeverity.RECOMMENDED -> VaultColors.AccentCyan
        SecuritySeverity.ATTENTION_REQUIRED -> VaultColors.AccentCrimson
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(VaultColors.SurfaceElevated)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when (item.severity) {
                            SecuritySeverity.PASSED -> Icons.Default.CheckCircle
                            SecuritySeverity.RECOMMENDED -> Icons.Default.Info
                            SecuritySeverity.ATTENTION_REQUIRED -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = item.title,
                        color = VaultColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                SecurityPill(
                    text = item.severity.name.replace("_", " "),
                    dotColor = iconColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                color = VaultColors.TextSecondary,
                fontSize = 11.sp
            )

            item.recommendation?.let { rec ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rec,
                    color = VaultColors.Platinum,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            item.actionType?.let { action ->
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onAction(action) },
                    border = BorderStroke(1.dp, VaultColors.AccentCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultColors.AccentCyan),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Configure", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VaultBackupStatusSectionCard(
    latestBackup: com.example.core.database.BackupHistoryEntity?,
    latestVerified: com.example.core.database.BackupHistoryEntity?,
    onOpenBackupCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalVaultSpacing.current
    val verifiedDateStr = if (latestVerified != null) {
        val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        sdf.format(Date(latestVerified.lastVerifiedTimestamp ?: latestVerified.timestamp))
    } else {
        "Never"
    }

    val backupStatus = when {
        latestVerified != null -> "Verified"
        latestBackup != null -> "Attention Required"
        else -> "Not Configured"
    }

    val recoveryConfig = if (latestBackup != null) "Configured (${latestBackup.protectionType})" else "Not Configured"

    VaultGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("security_center_backup_card")
    ) {
        Column(
            modifier = Modifier.padding(spacing.l),
            verticalArrangement = Arrangement.spacedBy(spacing.m)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = if (latestVerified != null) VaultColors.AccentEmerald else VaultColors.AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Backup & Recovery Status",
                        style = LocalVaultTypography.current.title.copy(fontSize = 15.sp),
                        color = VaultColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                SecurityPill(
                    text = backupStatus.uppercase(),
                    dotColor = if (latestVerified != null) VaultColors.AccentEmerald else VaultColors.AccentAmber
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "• Last Verified Backup: $verifiedDateStr",
                    style = LocalVaultTypography.current.bodySmall,
                    color = VaultColors.TextSecondary
                )
                Text(
                    text = "• Backup Status: $backupStatus",
                    style = LocalVaultTypography.current.bodySmall,
                    color = VaultColors.TextSecondary
                )
                Text(
                    text = "• Recovery Configuration: $recoveryConfig",
                    style = LocalVaultTypography.current.bodySmall,
                    color = VaultColors.TextSecondary
                )
            }

            Button(
                onClick = onOpenBackupCenter,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultColors.AccentEmerald
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_center_open_backup_button")
            ) {
                Text(
                    text = "Open Backup & Recovery Center",
                    color = VaultColors.Canvas,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
