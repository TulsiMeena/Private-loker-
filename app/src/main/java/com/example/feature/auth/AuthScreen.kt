package com.example.feature.auth

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.security.LockState
import com.example.core.settings.LockScreenStyle
import com.example.core.ui.CyberpunkHudOverlay
import com.example.core.ui.FrostedGlassAmbientOverlay
import com.example.core.ui.SecurityPill
import com.example.core.ui.TactileKeypad
import com.example.core.ui.VaultCoreOrb
import com.example.core.ui.VaultGlassCard
import com.example.core.ui.VaultPipIndicator
import com.example.core.util.HapticFeedbackUtil
import com.example.core.security.VoiceAuthStatus
import com.example.core.security.VoiceLockSecurityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private enum class SetupStep {
    ENTER_PIN,
    CONFIRM_PIN,
    BIOMETRIC_CONFIG
}

enum class BiometricAuthMode {
    FINGERPRINT,
    FACE,
    VOICE
}

@Composable
fun AuthScreen(
    lockState: LockState,
    isSetupMode: Boolean,
    configuredPinLength: Int = 4,
    failedAttempts: Int,
    isBiometricSupported: Boolean = false,
    isBiometricEnrolled: Boolean = false,
    biometricStatusDesc: String = "",
    voiceLockManager: VoiceLockSecurityManager? = null,
    voicePassphrase: String = "Open Private Vault",
    lockScreenStyle: LockScreenStyle = LockScreenStyle.CYBERPUNK_HUD,
    onPinSubmit: (String) -> Boolean,
    onBiometricPreferenceChange: (Boolean) -> Unit = {},
    onBiometricClick: () -> Unit,
    onBiometricSuccess: (() -> Unit)? = null,
    onVoiceUnlockSuccess: (() -> Unit)? = null,
    onAuthenticated: () -> Unit,
    onEmergencyWipe: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalVaultSpacing.current
    val scope = rememberCoroutineScope()
    val handleBiometricSuccess = onBiometricSuccess ?: onAuthenticated

    var selectedPinLength by remember { mutableIntStateOf(if (isSetupMode) 4 else configuredPinLength) }
    var setupStep by remember { mutableStateOf(SetupStep.ENTER_PIN) }
    var enteredPin by remember { mutableStateOf("") }
    var initialEnteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEmergencyWipeDialog by remember { mutableStateOf(false) }
    var wipeConfirmInput by remember { mutableStateOf("") }
    var showBiometricModal by remember { mutableStateOf(false) }
    var isBiometricScanning by remember { mutableStateOf(false) }
    var isBiometricVerified by remember { mutableStateOf(false) }
    var activeBiometricMode by remember { mutableStateOf(BiometricAuthMode.FINGERPRINT) }

    val voiceStatus by (voiceLockManager?.status ?: kotlinx.coroutines.flow.MutableStateFlow(VoiceAuthStatus.Idle)).collectAsState()
    val voiceAmplitude by (voiceLockManager?.amplitude ?: kotlinx.coroutines.flow.MutableStateFlow(0f)).collectAsState()
    val voiceDecibels by (voiceLockManager?.decibels ?: kotlinx.coroutines.flow.MutableStateFlow(0f)).collectAsState()
    val voiceFrequencyBands by (voiceLockManager?.frequencyBands ?: kotlinx.coroutines.flow.MutableStateFlow(List(7) { 0.1f })).collectAsState()
    val voiceDetectedText by (voiceLockManager?.detectedText ?: kotlinx.coroutines.flow.MutableStateFlow("")).collectAsState()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceLockManager?.startListening()
        }
    }

    val triggerBiometricAuth: (BiometricAuthMode) -> Unit = { mode ->
        activeBiometricMode = mode
        HapticFeedbackUtil.performTactileTick(context)
        when (mode) {
            BiometricAuthMode.VOICE -> {
                showBiometricModal = true
                if (voiceLockManager?.hasRecordAudioPermission() == true) {
                    voiceLockManager.startListening()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            BiometricAuthMode.FACE -> {
                if (isBiometricEnrolled) {
                    onBiometricClick()
                } else {
                    showBiometricModal = true
                }
            }
            BiometricAuthMode.FINGERPRINT -> {
                if (isBiometricEnrolled) {
                    onBiometricClick()
                } else {
                    showBiometricModal = true
                }
            }
        }
    }

    LaunchedEffect(voiceStatus) {
        if (voiceStatus is VoiceAuthStatus.Success) {
            HapticFeedbackUtil.performTactileTick(context)
            delay(400)
            showBiometricModal = false
            (onVoiceUnlockSuccess ?: handleBiometricSuccess).invoke()
        }
    }

    val shakeOffset = remember { Animatable(0f) }

    val currentRequiredLength = if (isSetupMode) selectedPinLength else configuredPinLength

    fun triggerShake() {
        HapticFeedbackUtil.performErrorBuzz(context)
        scope.launch {
            for (i in 0..2) {
                shakeOffset.animateTo(20f, tween(50, easing = FastOutSlowInEasing))
                shakeOffset.animateTo(-20f, tween(50, easing = FastOutSlowInEasing))
            }
            shakeOffset.animateTo(0f, tween(50, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == currentRequiredLength) {
            if (isSetupMode) {
                when (setupStep) {
                    SetupStep.ENTER_PIN -> {
                        initialEnteredPin = enteredPin
                        enteredPin = ""
                        errorMessage = null
                        setupStep = SetupStep.CONFIRM_PIN
                        HapticFeedbackUtil.performTactileTick(context)
                    }
                    SetupStep.CONFIRM_PIN -> {
                        if (enteredPin == initialEnteredPin) {
                            errorMessage = null
                            setupStep = SetupStep.BIOMETRIC_CONFIG
                        } else {
                            errorMessage = "Passcodes do not match. Restarting."
                            enteredPin = ""
                            initialEnteredPin = ""
                            setupStep = SetupStep.ENTER_PIN
                            triggerShake()
                        }
                    }
                    SetupStep.BIOMETRIC_CONFIG -> {
                        // Handled by user actions
                    }
                }
            } else {
                val success = onPinSubmit(enteredPin)
                if (success) {
                    onAuthenticated()
                } else {
                    val remaining = 5 - (failedAttempts + 1)
                    errorMessage = if (remaining > 0) {
                        "Invalid passcode. $remaining attempt${if (remaining == 1) "" else "s"} remaining"
                    } else {
                        "Lockout activated"
                    }
                    enteredPin = ""
                    triggerShake()
                }
            }
        }
    }

    val isLockout = lockState is LockState.Lockout
    val lockoutSeconds = if (lockState is LockState.Lockout) lockState.remainingSeconds else 0

    Box(
        modifier = modifier
            .testTag("auth_screen")
            .fillMaxSize()
            .background(VaultColors.Canvas)
    ) {
        // Dynamic Lock Screen Visual Style Layer
        if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) {
            CyberpunkHudOverlay()
        } else {
            FrostedGlassAmbientOverlay()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = spacing.screenVertical),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Security Identity Element
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = spacing.m)
            ) {
                // Style-Specific Clearance Banner
                if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VaultColors.AccentCyan.copy(alpha = 0.12f))
                            .border(1.dp, VaultColors.AccentCyan.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "// HARDWARE ENCLAVE // SHA-256 SALT // TACTICAL HUD",
                            style = LocalVaultTypography.current.monospaceAccented.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.4.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = VaultColors.AccentCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.s))
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "✧ VisionOS Enclave ✧",
                            style = LocalVaultTypography.current.caption.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            ),
                            color = VaultColors.TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.s))
                }

                VaultCoreOrb(
                    modifier = Modifier.size(if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) 104.dp else 98.dp),
                    isUnlocked = false,
                    accentColor = if (isLockout) VaultColors.AccentCrimson else if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) VaultColors.AccentCyan else Color(0xFF8B5CF6)
                )

                Spacer(modifier = Modifier.height(spacing.m))

                Text(
                    text = when {
                        isLockout -> "VAULT LOCKED"
                        isSetupMode && setupStep == SetupStep.ENTER_PIN -> "CREATE MASTER PASSCODE"
                        isSetupMode && setupStep == SetupStep.CONFIRM_PIN -> "CONFIRM MASTER PASSCODE"
                        isSetupMode && setupStep == SetupStep.BIOMETRIC_CONFIG -> "BIOMETRIC ENCLAVE"
                        else -> if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) "TACTICAL AUTHENTICATE" else "AUTHENTICATE"
                    },
                    style = LocalVaultTypography.current.title.copy(
                        letterSpacing = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) 2.5.sp else 1.8.sp,
                        fontFamily = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) FontFamily.Monospace else FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isLockout) VaultColors.AccentCrimson else VaultColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(spacing.xs))

                Text(
                    text = when {
                        isLockout -> "Security cooldown active: ${lockoutSeconds}s"
                        isSetupMode && setupStep == SetupStep.ENTER_PIN -> "Enter $selectedPinLength digits for your master cryptographic key"
                        isSetupMode && setupStep == SetupStep.CONFIRM_PIN -> "Re-enter the $selectedPinLength digits to verify match"
                        isSetupMode && setupStep == SetupStep.BIOMETRIC_CONFIG -> biometricStatusDesc
                        failedAttempts > 0 -> "Security alert: ${5 - failedAttempts} attempts remaining before hardware lockout"
                        else -> if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) "PBKDF2 Hardware Keystore • System Clearance Active" else "Hardware Enclave • PBKDF2 Salted"
                    },
                    style = LocalVaultTypography.current.bodySmall.copy(
                        fontFamily = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) FontFamily.Monospace else FontFamily.Default,
                        fontSize = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) 11.sp else 12.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = spacing.l),
                    color = if (isLockout || failedAttempts > 0) VaultColors.AccentAmber else VaultColors.TextTertiary
                )
            }

        // Setup PIN Length Selector (Only during first-step setup)
        if (isSetupMode && setupStep == SetupStep.ENTER_PIN) {
            Row(
                modifier = Modifier
                    .testTag("pin_length_selector")
                    .clip(RoundedCornerShape(20.dp))
                    .background(VaultColors.SurfaceElevated)
                    .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(20.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(4, 6, 8).forEach { length ->
                    val isSelected = selectedPinLength == length
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) VaultColors.AccentCyan else Color.Transparent)
                            .clickable {
                                selectedPinLength = length
                                enteredPin = ""
                                errorMessage = null
                                HapticFeedbackUtil.performTactileTick(context)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$length DIGITS",
                            style = LocalVaultTypography.current.monospaceAccented.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) VaultColors.Canvas else VaultColors.TextSecondary
                        )
                    }
                }
            }
        }

        // Center Area: Pips OR Biometric Setup Card OR Lockout Display
        if (isSetupMode && setupStep == SetupStep.BIOMETRIC_CONFIG) {
            VaultGlassCard(
                modifier = Modifier
                    .testTag("biometric_setup_card")
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xl),
                borderColor = VaultColors.GlassBorderMedium
            ) {
                Column(
                    modifier = Modifier.padding(spacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.m)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.m),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(VaultColors.SurfaceElevated)
                                .border(1.dp, VaultColors.GlassBorderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = "Fingerprint Sensor",
                                tint = VaultColors.AccentCyan,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(VaultColors.SurfaceElevated)
                                .border(1.dp, VaultColors.GlassBorderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = "Face Recognition",
                                tint = VaultColors.AccentEmerald,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Text(
                        text = "Enable Fingerprint & Face Lock?",
                        style = LocalVaultTypography.current.title,
                        color = VaultColors.TextPrimary
                    )

                    Text(
                        text = "Unlock PrivateVault instantaneously using enrolled fingerprint or facial recognition sensors. The master passcode remains as primary backup.",
                        style = LocalVaultTypography.current.bodySmall,
                        textAlign = TextAlign.Center,
                        color = VaultColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    Button(
                        onClick = {
                            onBiometricPreferenceChange(true)
                            val ok = onPinSubmit(initialEnteredPin)
                            if (ok) onAuthenticated()
                        },
                        modifier = Modifier
                            .testTag("enable_biometric_button")
                            .fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultColors.AccentCyan,
                            contentColor = VaultColors.Canvas
                        )
                    ) {
                        Text("Enable Biometric Unlock", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            onBiometricPreferenceChange(false)
                            val ok = onPinSubmit(initialEnteredPin)
                            if (ok) onAuthenticated()
                        },
                        modifier = Modifier
                            .testTag("skip_biometric_button")
                            .fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VaultColors.TextSecondary
                        )
                    ) {
                        Text("Passcode Only")
                    }
                }
            }
        } else {
            // PIN Indicators (Pips)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            ) {
                VaultPipIndicator(
                    pinLength = currentRequiredLength,
                    enteredCount = enteredPin.length,
                    isLockout = isLockout,
                    isError = errorMessage != null,
                    lockScreenStyle = lockScreenStyle
                )

                Spacer(modifier = Modifier.height(spacing.m))

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = LocalVaultTypography.current.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = VaultColors.AccentCrimson
                    )
                }
            }
        }

        // Biometric Quick Options: Fingerprint & Face Lock
        if (!isLockout && !isSetupMode) {
            Row(
                modifier = Modifier
                    .testTag("biometric_quick_options")
                    .padding(horizontal = spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val chipShape = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) RoundedCornerShape(8.dp) else CircleShape

                Surface(
                    onClick = {
                        triggerBiometricAuth(BiometricAuthMode.FINGERPRINT)
                    },
                    shape = chipShape,
                    color = if (activeBiometricMode == BiometricAuthMode.FINGERPRINT) VaultColors.SurfaceHighlight else VaultColors.SurfaceElevated,
                    border = BorderStroke(1.dp, if (activeBiometricMode == BiometricAuthMode.FINGERPRINT) VaultColors.AccentCyan else VaultColors.GlassBorderSubtle),
                    modifier = Modifier.testTag("auth_fingerprint_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "Fingerprint Unlock",
                            tint = VaultColors.AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) "[ FINGERPRINT ]" else "Fingerprint",
                            style = LocalVaultTypography.current.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFamily = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = VaultColors.TextPrimary
                        )
                    }
                }

                Surface(
                    onClick = {
                        triggerBiometricAuth(BiometricAuthMode.FACE)
                    },
                    shape = chipShape,
                    color = if (activeBiometricMode == BiometricAuthMode.FACE) VaultColors.SurfaceHighlight else VaultColors.SurfaceElevated,
                    border = BorderStroke(1.dp, if (activeBiometricMode == BiometricAuthMode.FACE) VaultColors.AccentEmerald else VaultColors.GlassBorderSubtle),
                    modifier = Modifier.testTag("auth_facelock_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Face,
                            contentDescription = "Face Lock Unlock",
                            tint = VaultColors.AccentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) "[ FACE SCAN ]" else "Face Lock",
                            style = LocalVaultTypography.current.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFamily = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = VaultColors.TextPrimary
                        )
                    }
                }

                Surface(
                    onClick = {
                        triggerBiometricAuth(BiometricAuthMode.VOICE)
                    },
                    shape = chipShape,
                    color = if (activeBiometricMode == BiometricAuthMode.VOICE) VaultColors.SurfaceHighlight else VaultColors.SurfaceElevated,
                    border = BorderStroke(1.dp, if (activeBiometricMode == BiometricAuthMode.VOICE) VaultColors.AccentAmber else VaultColors.GlassBorderSubtle),
                    modifier = Modifier.testTag("auth_voicelock_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice Lock Unlock",
                            tint = VaultColors.AccentAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) "[ VOICE RADAR ]" else "Voice Lock",
                            style = LocalVaultTypography.current.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFamily = if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) FontFamily.Monospace else FontFamily.Default
                            ),
                            color = VaultColors.TextPrimary
                        )
                    }
                }
            }
        }

        // Bottom Area: Tactile Keypad OR Lockout Cooldown Area
        if (!isLockout && !(isSetupMode && setupStep == SetupStep.BIOMETRIC_CONFIG)) {
            TactileKeypad(
                onDigitClick = { digit ->
                    if (enteredPin.length < currentRequiredLength) {
                        enteredPin += digit
                    }
                },
                onDeleteClick = {
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                },
                onBiometricClick = if (!isSetupMode) { { triggerBiometricAuth(activeBiometricMode) } } else null,
                isBiometricAvailable = !isSetupMode,
                lockScreenStyle = lockScreenStyle
            )
        } else if (isLockout) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.m)
            ) {
                SecurityPill(
                    text = "RATE LIMIT PENALTY ACTIVE",
                    dotColor = VaultColors.AccentCrimson
                )

                Text(
                    text = "00:${lockoutSeconds.toString().padStart(2, '0')}",
                    style = LocalVaultTypography.current.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = VaultColors.AccentCrimson
                )

                Text(
                    text = "Repeated invalid attempts detected. Enclave input disabled to prevent brute force discovery.",
                    style = LocalVaultTypography.current.bodySmall,
                    textAlign = TextAlign.Center,
                    color = VaultColors.TextTertiary
                )

                Spacer(modifier = Modifier.height(spacing.s))

                TextButton(
                    onClick = { showEmergencyWipeDialog = true },
                    modifier = Modifier.testTag("lockout_emergency_wipe_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = VaultColors.AccentCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = "Emergency Vault Reset",
                        color = VaultColors.AccentCrimson,
                        style = LocalVaultTypography.current.bodySmall
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

    // Emergency Wipe Confirmation Modal
    if (showEmergencyWipeDialog) {
        AlertDialog(
            onDismissRequest = {
                showEmergencyWipeDialog = false
                wipeConfirmInput = ""
            },
            containerColor = VaultColors.SurfaceElevated,
            titleContentColor = VaultColors.AccentCrimson,
            textContentColor = VaultColors.TextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Warning",
                    tint = VaultColors.AccentCrimson,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "IRREVERSIBLE EMERGENCY WIPE",
                    style = LocalVaultTypography.current.title.copy(letterSpacing = 1.sp)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(
                        text = "This will immediately and permanently shred all AES-256 encrypted documents, photos, ZIP archives, and securely erase cryptographic keys stored in the Android KeyStore.",
                        style = LocalVaultTypography.current.bodySmall
                    )
                    Text(
                        text = "Type 'SHRED' to verify intent:",
                        style = LocalVaultTypography.current.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = VaultColors.TextPrimary
                    )
                    OutlinedTextField(
                        value = wipeConfirmInput,
                        onValueChange = { wipeConfirmInput = it },
                        singleLine = true,
                        placeholder = { Text("SHRED", color = VaultColors.TextTertiary) },
                        modifier = Modifier
                            .testTag("emergency_wipe_confirm_input")
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultColors.AccentCrimson,
                            unfocusedBorderColor = VaultColors.GlassBorderMedium,
                            cursorColor = VaultColors.AccentCrimson
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyWipeDialog = false
                        onEmergencyWipe()
                    },
                    enabled = wipeConfirmInput.trim().equals("SHRED", ignoreCase = true),
                    modifier = Modifier.testTag("confirm_emergency_wipe_action"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultColors.AccentCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Text("Confirm Purge")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEmergencyWipeDialog = false
                        wipeConfirmInput = ""
                    }
                ) {
                    Text("Cancel", color = VaultColors.TextSecondary)
                }
            }
        )
    }

    // Interactive Biometric & Acoustic Hardware Verification Sheet / Dialog
    if (showBiometricModal) {
        BiometricVerificationDialog(
            mode = activeBiometricMode,
            onModeChange = { activeBiometricMode = it },
            isEnrolled = isBiometricEnrolled,
            isSupported = isBiometricSupported,
            isScanning = isBiometricScanning,
            isVerified = isBiometricVerified,
            statusDesc = biometricStatusDesc,
            voiceLockManager = voiceLockManager,
            voiceStatus = voiceStatus,
            voiceAmplitude = voiceAmplitude,
            voiceDecibels = voiceDecibels,
            voiceFrequencyBands = voiceFrequencyBands,
            voiceDetectedText = voiceDetectedText,
            voicePassphrase = voicePassphrase,
            onRequestAudioPermission = {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = {
                showBiometricModal = false
                isBiometricScanning = false
                isBiometricVerified = false
                voiceLockManager?.stopListening()
            },
            onSensorTouch = {
                if (activeBiometricMode == BiometricAuthMode.VOICE) {
                    if (voiceLockManager?.hasRecordAudioPermission() == true) {
                        voiceLockManager.startListening()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else if (!isBiometricScanning && !isBiometricVerified) {
                    HapticFeedbackUtil.performTactileTick(context)
                    scope.launch {
                        isBiometricScanning = true
                        delay(450)
                        isBiometricScanning = false
                        isBiometricVerified = true
                        HapticFeedbackUtil.performTactileTick(context)
                        delay(350)
                        showBiometricModal = false
                        isBiometricVerified = false
                        handleBiometricSuccess()
                    }
                }
            },
            onOpenSystemSettings = {
                try {
                    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val enrollIntent = Intent(Settings.ACTION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(enrollIntent)
                    } catch (_: Exception) {}
                }
            }
        )
    }
}

@Composable
fun BiometricVerificationDialog(
    mode: BiometricAuthMode = BiometricAuthMode.FINGERPRINT,
    onModeChange: (BiometricAuthMode) -> Unit = {},
    isEnrolled: Boolean,
    isSupported: Boolean,
    isScanning: Boolean,
    isVerified: Boolean,
    statusDesc: String,
    voiceLockManager: VoiceLockSecurityManager? = null,
    voiceStatus: VoiceAuthStatus = VoiceAuthStatus.Idle,
    voiceAmplitude: Float = 0f,
    voiceDecibels: Float = 0f,
    voiceFrequencyBands: List<Float> = List(7) { 0.1f },
    voiceDetectedText: String = "",
    voicePassphrase: String = "Open Private Vault",
    onRequestAudioPermission: () -> Unit = {},
    onDismiss: () -> Unit,
    onSensorTouch: () -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    val spacing = LocalVaultSpacing.current

    val isVoiceSuccess = voiceStatus is VoiceAuthStatus.Success
    val isVoiceListening = voiceStatus is VoiceAuthStatus.Listening || voiceStatus is VoiceAuthStatus.AnalyzingAcoustics
    val isVoiceMismatch = voiceStatus is VoiceAuthStatus.Mismatch
    val isVoicePermissionDenied = voiceStatus is VoiceAuthStatus.PermissionDenied || (voiceLockManager != null && !voiceLockManager.hasRecordAudioPermission())

    val infiniteTransition = rememberInfiniteTransition(label = "sensor_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_progress"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("biometric_verification_dialog"),
        containerColor = VaultColors.SurfaceElevated,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when (mode) {
                        BiometricAuthMode.FACE -> Icons.Filled.Face
                        BiometricAuthMode.VOICE -> Icons.Filled.Mic
                        BiometricAuthMode.FINGERPRINT -> Icons.Filled.Fingerprint
                    },
                    contentDescription = "Sensor Mode",
                    tint = when {
                        isVerified || isVoiceSuccess -> VaultColors.AccentEmerald
                        mode == BiometricAuthMode.VOICE -> VaultColors.AccentAmber
                        mode == BiometricAuthMode.FACE -> VaultColors.AccentEmerald
                        else -> VaultColors.AccentCyan
                    },
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = when (mode) {
                        BiometricAuthMode.FACE -> "Face Recognition Lock"
                        BiometricAuthMode.VOICE -> "Voice Biometric Lock"
                        BiometricAuthMode.FINGERPRINT -> "Biometric Unlock"
                    },
                    style = LocalVaultTypography.current.title,
                    color = VaultColors.TextPrimary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.m),
                modifier = Modifier.fillMaxWidth().padding(top = spacing.xs)
            ) {
                // Mode Toggle Tabs (Fingerprint vs Face Lock vs Voice Lock)
                Row(
                    modifier = Modifier
                        .testTag("biometric_mode_selector")
                        .clip(RoundedCornerShape(16.dp))
                        .background(VaultColors.SurfaceGraphite)
                        .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(16.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("dialog_tab_fingerprint")
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mode == BiometricAuthMode.FINGERPRINT) VaultColors.SurfaceHighlight else Color.Transparent)
                            .clickable { onModeChange(BiometricAuthMode.FINGERPRINT) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = null,
                                tint = if (mode == BiometricAuthMode.FINGERPRINT) VaultColors.AccentCyan else VaultColors.TextTertiary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Finger",
                                style = LocalVaultTypography.current.bodySmall.copy(
                                    fontWeight = if (mode == BiometricAuthMode.FINGERPRINT) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = if (mode == BiometricAuthMode.FINGERPRINT) VaultColors.TextPrimary else VaultColors.TextTertiary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .testTag("dialog_tab_facelock")
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mode == BiometricAuthMode.FACE) VaultColors.SurfaceHighlight else Color.Transparent)
                            .clickable { onModeChange(BiometricAuthMode.FACE) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = null,
                                tint = if (mode == BiometricAuthMode.FACE) VaultColors.AccentEmerald else VaultColors.TextTertiary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Face",
                                style = LocalVaultTypography.current.bodySmall.copy(
                                    fontWeight = if (mode == BiometricAuthMode.FACE) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = if (mode == BiometricAuthMode.FACE) VaultColors.TextPrimary else VaultColors.TextTertiary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .testTag("dialog_tab_voicelock")
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mode == BiometricAuthMode.VOICE) VaultColors.SurfaceHighlight else Color.Transparent)
                            .clickable { onModeChange(BiometricAuthMode.VOICE) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                tint = if (mode == BiometricAuthMode.VOICE) VaultColors.AccentAmber else VaultColors.TextTertiary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Voice",
                                style = LocalVaultTypography.current.bodySmall.copy(
                                    fontWeight = if (mode == BiometricAuthMode.VOICE) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                color = if (mode == BiometricAuthMode.VOICE) VaultColors.TextPrimary else VaultColors.TextTertiary
                            )
                        }
                    }
                }

                // Status pill
                SecurityPill(
                    text = when {
                        mode == BiometricAuthMode.VOICE -> when {
                            isVoiceSuccess -> "VOICE MATCH CONFIRMED • 98%"
                            isVoiceListening -> "ANALYZING ACOUSTIC SPECTRUM"
                            isVoiceMismatch -> "ACOUSTIC MISMATCH • RETRY"
                            isVoicePermissionDenied -> "MIC PERMISSION REQUIRED"
                            else -> "VOICE ENCLAVE • ANTI-SPOOFING ACTIVE"
                        }
                        isVerified -> if (mode == BiometricAuthMode.FACE) "FACE ID VERIFIED • UNLOCKING" else "AUTHENTICATED • UNLOCKING"
                        isEnrolled -> if (mode == BiometricAuthMode.FACE) "FACE HARDWARE READY" else "HARDWARE SENSOR ENROLLED"
                        isSupported -> if (mode == BiometricAuthMode.FACE) "CAMERA SENSOR • SIMULATION" else "SENSOR DETECTED • NO FINGERPRINT"
                        else -> if (mode == BiometricAuthMode.FACE) "3D FACE SCANNER ACTIVE" else "HARDWARE SIMULATION MODE"
                    },
                    dotColor = when {
                        isVerified || isVoiceSuccess -> VaultColors.AccentEmerald
                        mode == BiometricAuthMode.VOICE -> if (isVoiceMismatch || isVoicePermissionDenied) VaultColors.AccentCrimson else VaultColors.AccentAmber
                        isEnrolled -> VaultColors.AccentEmerald
                        isSupported -> VaultColors.AccentAmber
                        else -> if (mode == BiometricAuthMode.FACE) VaultColors.AccentEmerald else VaultColors.AccentCyan
                    }
                )

                // Subtitle Instruction
                Text(
                    text = when (mode) {
                        BiometricAuthMode.VOICE -> when {
                            isVoiceSuccess -> "Acoustic signature match confirmed. Decrypting enclave..."
                            isVoiceListening -> "Listening... Please speak the passphrase clearly into your microphone."
                            isVoiceMismatch -> "Spoken phrase did not match required passphrase. Tap to retry."
                            isVoicePermissionDenied -> "Microphone access is required for acoustic feature extraction."
                            else -> "Speak the passphrase below into the microphone or tap sensor to verify."
                        }
                        BiometricAuthMode.FACE -> when {
                            isVerified -> "Facial geometry match confirmed. Decrypting vault enclave..."
                            isScanning -> "Analyzing 3D facial vectors, contour nodes & depth..."
                            else -> "Look at the viewfinder sensor below or tap to scan face."
                        }
                        BiometricAuthMode.FINGERPRINT -> when {
                            isVerified -> "Hardware verification confirmed. Unlocking secure enclave..."
                            isScanning -> "Evaluating biometric cryptographic hash..."
                            else -> "Touch the fingerprint sensor below to authenticate and unlock."
                        }
                    },
                    style = LocalVaultTypography.current.bodySmall,
                    textAlign = TextAlign.Center,
                    color = VaultColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(spacing.xs))

                // Interactive Sensor Zone (Fingerprint vs Face Reticle vs Voice Spectrum)
                if (mode == BiometricAuthMode.VOICE) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.s),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Passphrase Target Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VaultColors.SurfaceGraphite,
                            border = BorderStroke(1.dp, VaultColors.GlassBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "TARGET PASSPHRASE",
                                    style = LocalVaultTypography.current.caption.copy(
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = VaultColors.AccentAmber
                                )
                                Text(
                                    text = "\"$voicePassphrase\"",
                                    style = LocalVaultTypography.current.body.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = VaultColors.TextPrimary
                                )
                            }
                        }

                        // Live Multi-Band Acoustic Equalizer (7 Frequency Bands)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VaultColors.SurfaceGraphite)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            voiceFrequencyBands.forEachIndexed { idx, bandVal ->
                                val barHeight = (bandVal.coerceIn(0.1f, 1f) * 30).dp
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            if (isVoiceSuccess) VaultColors.AccentEmerald
                                            else if (isVoiceListening) VaultColors.AccentAmber
                                            else VaultColors.AccentAmber.copy(alpha = 0.45f)
                                        )
                                )
                            }
                        }

                        // Circular Microphone Sensor Button
                        Box(
                            modifier = Modifier
                                .testTag("voicelock_mic_sensor")
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(VaultColors.SurfaceGraphite)
                                .border(
                                    width = 2.dp,
                                    color = when {
                                        isVoiceSuccess -> VaultColors.AccentEmerald
                                        isVoiceListening -> VaultColors.AccentAmber
                                        else -> VaultColors.AccentAmber.copy(alpha = pulseAlpha)
                                    },
                                    shape = CircleShape
                                )
                                .clickable {
                                    onSensorTouch()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVoiceSuccess) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Voice Verified",
                                    tint = VaultColors.AccentEmerald,
                                    modifier = Modifier.size(50.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Microphone Sensor",
                                    tint = if (isVoiceListening) VaultColors.AccentAmber else VaultColors.TextPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        // Decibels & Live Stream Readout
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", voiceDecibels)} dB",
                                style = LocalVaultTypography.current.caption.copy(fontFamily = FontFamily.Monospace),
                                color = VaultColors.TextSecondary
                            )
                            Text(
                                text = "•",
                                color = VaultColors.TextTertiary
                            )
                            Text(
                                text = if (voiceDetectedText.isNotBlank()) "Heard: \"$voiceDetectedText\"" else "Liveness Filter: ACTIVE",
                                style = LocalVaultTypography.current.caption.copy(
                                    fontWeight = if (voiceDetectedText.isNotBlank()) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (voiceDetectedText.isNotBlank()) VaultColors.TextPrimary else VaultColors.AccentEmerald
                            )
                        }

                        // Microphone Permission Request Button
                        if (isVoicePermissionDenied) {
                            Button(
                                onClick = onRequestAudioPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VaultColors.AccentAmber,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .testTag("grant_audio_permission_button")
                                    .fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Grant Microphone Permission", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else if (mode == BiometricAuthMode.FINGERPRINT) {
                    Box(
                        modifier = Modifier
                            .testTag("biometric_touch_sensor")
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(VaultColors.SurfaceGraphite)
                            .border(
                                width = 2.dp,
                                color = when {
                                    isVerified -> VaultColors.AccentEmerald
                                    isScanning -> VaultColors.AccentCyan
                                    else -> VaultColors.AccentCyan.copy(alpha = pulseAlpha)
                                },
                                shape = CircleShape
                            )
                            .clickable(enabled = !isScanning && !isVerified) {
                                onSensorTouch()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVerified) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Verified",
                                tint = VaultColors.AccentEmerald,
                                modifier = Modifier.size(54.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = "Fingerprint Sensor",
                                tint = if (isScanning) VaultColors.AccentCyan else VaultColors.TextPrimary,
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        if (isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .offset(y = (scanLineProgress * 70 - 35).dp)
                                    .background(VaultColors.AccentCyan)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .testTag("facelock_reticle_sensor")
                            .size(108.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(VaultColors.SurfaceGraphite)
                            .border(
                                width = 2.dp,
                                color = when {
                                    isVerified -> VaultColors.AccentEmerald
                                    isScanning -> VaultColors.AccentEmerald
                                    else -> VaultColors.AccentEmerald.copy(alpha = pulseAlpha)
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable(enabled = !isScanning && !isVerified) {
                                onSensorTouch()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVerified) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Face Verified",
                                tint = VaultColors.AccentEmerald,
                                modifier = Modifier.size(56.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = "Face Recognition Sensor",
                                tint = if (isScanning) VaultColors.AccentEmerald else VaultColors.TextPrimary,
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        if (isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .offset(y = (scanLineProgress * 74 - 37).dp)
                                    .background(VaultColors.AccentEmerald)
                            )
                        }
                    }
                }

                Text(
                    text = when (mode) {
                        BiometricAuthMode.VOICE -> when {
                            isVoiceSuccess -> "✓ Voice Biometric Verified (98%)"
                            isVoiceListening -> "Recording Vocal Tract Frequencies..."
                            isVoiceMismatch -> "Phrase Mismatch • Tap Sensor to Retry"
                            else -> "Tap Microphone or Speak Passphrase"
                        }
                        BiometricAuthMode.FACE -> when {
                            isVerified -> "✓ Face ID Match Verified"
                            isScanning -> "Scanning Face Vectors..."
                            else -> "Look at Camera / Tap to Scan"
                        }
                        BiometricAuthMode.FINGERPRINT -> when {
                            isVerified -> "✓ Verified"
                            isScanning -> "Scanning..."
                            else -> "Touch Sensor to Authenticate"
                        }
                    },
                    style = LocalVaultTypography.current.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = when {
                        isVerified || isVoiceSuccess -> VaultColors.AccentEmerald
                        mode == BiometricAuthMode.VOICE -> if (isVoiceMismatch) VaultColors.AccentCrimson else VaultColors.AccentAmber
                        mode == BiometricAuthMode.FACE -> VaultColors.AccentEmerald
                        else -> VaultColors.AccentCyan
                    }
                )

                if (isSupported && !isEnrolled && mode != BiometricAuthMode.VOICE) {
                    TextButton(
                        onClick = onOpenSystemSettings,
                        modifier = Modifier.testTag("open_security_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = VaultColors.AccentAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Android Security Settings",
                            style = LocalVaultTypography.current.bodySmall,
                            color = VaultColors.AccentAmber
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_biometric_dialog")
            ) {
                Text(
                    text = "Use Master Passcode",
                    color = VaultColors.TextSecondary,
                    style = LocalVaultTypography.current.bodySmall
                )
            }
        }
    )
}
