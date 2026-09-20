package com.example.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.example.core.settings.LockScreenStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import com.example.core.designsystem.LocalVaultCornerRadius
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.util.HapticFeedbackUtil

/**
 * Premium translucent graphite card with hairline borders and subtle depth.
 */
@Composable
fun VaultGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = VaultColors.GlassBorderSubtle,
    backgroundColor: Color = VaultColors.SurfaceGraphite.copy(alpha = 0.85f),
    cornerRadius: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier.clip(shape)
    }

    Surface(
        modifier = cardModifier
            .border(BorderStroke(1.dp, borderColor), shape),
        color = backgroundColor,
        shape = shape,
        tonalElevation = 2.dp
    ) {
        content()
    }
}

/**
 * Vault Core visual identity element:
 * Minimalist geometric security layers with concentric rings, radial status indicator,
 * precision tick marks and breathing energy glow.
 */
@Composable
fun VaultCoreOrb(
    modifier: Modifier = Modifier,
    isUnlocked: Boolean = false,
    isActiveAnimating: Boolean = true,
    accentColor: Color = if (isUnlocked) VaultColors.AccentEmerald else VaultColors.AccentCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VaultCoreTransition")

    val rotationOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isActiveAnimating) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val rotationInner by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = if (isActiveAnimating) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isActiveAnimating) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (isActiveAnimating) 1.06f else 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val rippleWave by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleWave"
    )

    val animatedColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(600),
        label = "accentColorAnim"
    )

    Box(
        modifier = modifier
            .testTag("vault_core_orb")
            .size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * 0.82f

            // 1. Expanding ultrasonic ripple wave
            val waveAlpha = ((1.35f - rippleWave) / 0.85f).coerceIn(0f, 0.45f)
            drawCircle(
                color = animatedColor.copy(alpha = waveAlpha),
                radius = baseRadius * rippleWave,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 2. Multi-layer ambient energy core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedColor.copy(alpha = 0.28f * pulseScale),
                        animatedColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.35f
                ),
                radius = baseRadius * 1.35f,
                center = center
            )

            // 3. Outer precision notched boundary
            drawCircle(
                color = VaultColors.GlassBorderMedium,
                radius = baseRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 4. Circular holographic radar scanner sweep
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        animatedColor.copy(alpha = 0.02f),
                        animatedColor.copy(alpha = 0.35f)
                    ),
                    center = center
                ),
                startAngle = radarAngle - 60f,
                sweepAngle = 60f,
                useCenter = true,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2f, baseRadius * 2f)
            )

            // 5. Dual orbiting segmented data shields
            val arcAngle = 70f
            drawArc(
                color = animatedColor.copy(alpha = 0.85f),
                startAngle = rotationOuter,
                sweepAngle = arcAngle,
                useCenter = false,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2f, baseRadius * 2f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = animatedColor.copy(alpha = 0.5f),
                startAngle = rotationOuter + 180f,
                sweepAngle = arcAngle * 0.7f,
                useCenter = false,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2f, baseRadius * 2f),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // 6. Cardinal reticle tick marks (12 ticks like an atomic compass)
            val tickRadius = baseRadius * 0.90f
            for (i in 0 until 12) {
                val angleDeg = (i * 30f) + (rotationOuter * 0.2f)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 3 == 0
                val tickLen = if (isMajor) 7.dp.toPx() else 3.5.dp.toPx()
                val startX = (center.x + cos(angleRad) * (tickRadius - tickLen)).toFloat()
                val startY = (center.y + sin(angleRad) * (tickRadius - tickLen)).toFloat()
                val endX = (center.x + cos(angleRad) * tickRadius).toFloat()
                val endY = (center.y + sin(angleRad) * tickRadius).toFloat()

                drawLine(
                    color = if (isMajor) animatedColor.copy(alpha = 0.9f) else VaultColors.TextTertiary.copy(alpha = 0.4f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
                )
            }

            // 7. Middle counter-rotating sub-enclave ring
            val midRadius = baseRadius * 0.68f
            drawCircle(
                color = VaultColors.SurfaceHighlight.copy(alpha = 0.5f),
                radius = midRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            drawArc(
                color = animatedColor.copy(alpha = 0.75f),
                startAngle = rotationInner,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(center.x - midRadius, center.y - midRadius),
                size = androidx.compose.ui.geometry.Size(midRadius * 2f, midRadius * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = animatedColor.copy(alpha = 0.4f),
                startAngle = rotationInner + 180f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(center.x - midRadius, center.y - midRadius),
                size = androidx.compose.ui.geometry.Size(midRadius * 2f, midRadius * 2f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 8. Quantum satellite orbit nodes
            val nodeRadius = midRadius * 0.88f
            for (nodeIdx in 0 until 3) {
                val nodeAngle = Math.toRadians((rotationOuter * 1.5f + nodeIdx * 120.0))
                val nodeX = (center.x + cos(nodeAngle) * nodeRadius).toFloat()
                val nodeY = (center.y + sin(nodeAngle) * nodeRadius).toFloat()
                drawCircle(
                    color = animatedColor.copy(alpha = 0.85f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
            }

            // 9. Inner Central Core Node
            val innerRadius = baseRadius * 0.44f * pulseScale
            drawCircle(
                color = VaultColors.SurfaceElevated,
                radius = innerRadius,
                center = center
            )
            drawCircle(
                color = animatedColor.copy(alpha = 0.85f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center Icon inside orb with subtle breathing scale
        Icon(
            imageVector = if (isUnlocked) Icons.Filled.LockOpen else Icons.Filled.Shield,
            contentDescription = if (isUnlocked) "Vault Unlocked" else "Vault Secured",
            tint = animatedColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Security metadata pill indicator (e.g. "AES-256-GCM", "GUARD ACTIVE").
 */
@Composable
fun SecurityPill(
    text: String,
    modifier: Modifier = Modifier,
    dotColor: Color = VaultColors.AccentEmerald
) {
    val cornerRadius = LocalVaultCornerRadius.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.pill))
            .background(VaultColors.SurfaceElevated)
            .border(1.dp, VaultColors.GlassBorderSubtle, RoundedCornerShape(cornerRadius.pill))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium
            ),
            color = VaultColors.TextSecondary
        )
    }
}

/**
 * Precision tactile keypad for master PIN unlock.
 */
/**
 * Precision tactile keypad for master PIN unlock.
 * Dynamically styled based on the active LockScreenStyle.
 */
@Composable
fun TactileKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: (() -> Unit)?,
    isBiometricAvailable: Boolean = true,
    lockScreenStyle: LockScreenStyle = LockScreenStyle.CYBERPUNK_HUD,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalVaultSpacing.current

    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("BIO", "0", "DEL")
    )

    Column(
        modifier = modifier
            .testTag("tactile_keypad")
            .fillMaxWidth()
            .padding(horizontal = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    when (key) {
                        "BIO" -> {
                            if (isBiometricAvailable && onBiometricClick != null) {
                                KeypadActionButton(
                                    icon = Icons.Filled.Fingerprint,
                                    contentDesc = "Unlock with Biometrics",
                                    tag = "keypad_biometric_button",
                                    lockScreenStyle = lockScreenStyle,
                                    onClick = {
                                        HapticFeedbackUtil.performTactileTick(context)
                                        onBiometricClick()
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
                        }
                        "DEL" -> {
                            KeypadActionButton(
                                icon = Icons.Filled.Backspace,
                                contentDesc = "Delete digit",
                                tag = "keypad_delete_button",
                                lockScreenStyle = lockScreenStyle,
                                onClick = {
                                    HapticFeedbackUtil.performTactileTick(context)
                                    onDeleteClick()
                                }
                            )
                        }
                        else -> {
                            KeypadDigitButton(
                                digit = key,
                                lockScreenStyle = lockScreenStyle,
                                onClick = {
                                    HapticFeedbackUtil.performTactileTick(context)
                                    onDigitClick(key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: String,
    lockScreenStyle: LockScreenStyle,
    onClick: () -> Unit
) {
    val subText = when (digit) {
        "2" -> "ABC"
        "3" -> "DEF"
        "4" -> "GHI"
        "5" -> "JKL"
        "6" -> "MNO"
        "7" -> "PQRS"
        "8" -> "TUV"
        "9" -> "WXYZ"
        "0" -> "+"
        else -> ""
    }

    if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) {
        // High-Tech Cyberpunk Tactical Button
        val cornerShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .testTag("keypad_digit_$digit")
                .clip(cornerShape)
                .background(VaultColors.SurfaceElevated.copy(alpha = 0.85f))
                .border(BorderStroke(1.2.dp, VaultColors.GlassBorderMedium), cornerShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit,
                    style = LocalVaultTypography.current.keypadDigit.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = VaultColors.TextPrimary
                )
                if (subText.isNotEmpty()) {
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = VaultColors.AccentCyan.copy(alpha = 0.75f)
                    )
                }
            }
        }
    } else {
        // Frosted Glassmorphism Apple-Vision Luxury Button
        val circle = CircleShape
        Box(
            modifier = Modifier
                .size(72.dp)
                .testTag("keypad_digit_$digit")
                .clip(circle)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.38f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    ),
                    circle
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit,
                    style = LocalVaultTypography.current.keypadDigit,
                    color = VaultColors.TextPrimary
                )
                if (subText.isNotEmpty()) {
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = VaultColors.TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadActionButton(
    icon: ImageVector,
    contentDesc: String,
    tag: String,
    lockScreenStyle: LockScreenStyle,
    onClick: () -> Unit
) {
    if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) {
        val cornerShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .testTag(tag)
                .clip(cornerShape)
                .background(VaultColors.SurfaceGraphite)
                .border(BorderStroke(1.2.dp, VaultColors.GlassBorderMedium), cornerShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = VaultColors.AccentCyan,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        val circle = CircleShape
        Box(
            modifier = Modifier
                .size(72.dp)
                .testTag(tag)
                .clip(circle)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.03f)
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    ),
                    circle
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = VaultColors.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Visual feedback for master PIN entry:
 * Tactile geometric pips that animate smoothly as digits are entered,
 * providing clear entry feedback without leaking digits to observers.
 */
@Composable
fun VaultPipIndicator(
    pinLength: Int,
    enteredCount: Int,
    isLockout: Boolean = false,
    isError: Boolean = false,
    lockScreenStyle: LockScreenStyle = LockScreenStyle.CYBERPUNK_HUD,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.testTag("vault_pip_indicator"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until pinLength) {
            val isFilled = i < enteredCount
            val pipColor = when {
                isLockout || isError -> VaultColors.AccentCrimson
                isFilled -> VaultColors.AccentCyan
                else -> Color.Transparent
            }
            val borderColor = when {
                isLockout || isError -> VaultColors.AccentCrimson
                isFilled -> VaultColors.AccentCyan
                else -> VaultColors.GlassBorderMedium
            }

            if (lockScreenStyle == LockScreenStyle.CYBERPUNK_HUD) {
                // Cyberpunk Angular Data Blocks
                val shape = RoundedCornerShape(4.dp)
                Box(
                    modifier = Modifier
                        .size(16.dp, 16.dp)
                        .clip(shape)
                        .background(pipColor)
                        .border(1.5.dp, borderColor, shape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFilled && !isLockout && !isError) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White)
                        )
                    }
                }
            } else {
                // Frosted Glass Liquid Droplet
                val dropletModifier = if (isFilled && !isLockout && !isError) {
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color.White,
                                    VaultColors.AccentCyan
                                )
                            )
                        )
                        .border(1.5.dp, borderColor, CircleShape)
                } else {
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(pipColor)
                        .border(1.5.dp, borderColor, CircleShape)
                }
                Box(
                    modifier = dropletModifier,
                    contentAlignment = Alignment.Center
                ) {
                    if (isFilled && !isLockout && !isError) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated Cyberpunk HUD background overlay with fine grid lines, laser scanning radar bar,
 * tactical corner targeting reticles, and matrix data stream markers.
 */
@Composable
fun CyberpunkHudOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_scan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_laser"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hud_pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val gridSpacing = 44.dp.toPx()

        // Subtle sci-fi grid
        var x = 0f
        while (x <= width) {
            drawLine(
                color = VaultColors.AccentCyan.copy(alpha = 0.035f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += gridSpacing
        }

        var y = 0f
        while (y <= height) {
            drawLine(
                color = VaultColors.AccentCyan.copy(alpha = 0.035f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += gridSpacing
        }

        // Corner targeting tactical brackets
        val bracketSize = 24.dp.toPx()
        val bracketMargin = 16.dp.toPx()
        val bracketColor = VaultColors.AccentCyan.copy(alpha = 0.4f * pulseGlow)
        val bracketStroke = 1.5.dp.toPx()

        // Top-left bracket
        drawLine(bracketColor, Offset(bracketMargin, bracketMargin), Offset(bracketMargin + bracketSize, bracketMargin), bracketStroke)
        drawLine(bracketColor, Offset(bracketMargin, bracketMargin), Offset(bracketMargin, bracketMargin + bracketSize), bracketStroke)

        // Top-right bracket
        drawLine(bracketColor, Offset(width - bracketMargin - bracketSize, bracketMargin), Offset(width - bracketMargin, bracketMargin), bracketStroke)
        drawLine(bracketColor, Offset(width - bracketMargin, bracketMargin), Offset(width - bracketMargin, bracketMargin + bracketSize), bracketStroke)

        // Bottom-left bracket
        drawLine(bracketColor, Offset(bracketMargin, height - bracketMargin), Offset(bracketMargin + bracketSize, height - bracketMargin), bracketStroke)
        drawLine(bracketColor, Offset(bracketMargin, height - bracketMargin - bracketSize), Offset(bracketMargin, height - bracketMargin), bracketStroke)

        // Bottom-right bracket
        drawLine(bracketColor, Offset(width - bracketMargin - bracketSize, height - bracketMargin), Offset(width - bracketMargin, height - bracketMargin), bracketStroke)
        drawLine(bracketColor, Offset(width - bracketMargin, height - bracketMargin - bracketSize), Offset(width - bracketMargin, height - bracketMargin), bracketStroke)

        // Animated laser sweep bar with neon blur gradient
        val scanY = height * scanProgress
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    VaultColors.AccentCyan.copy(alpha = 0.15f),
                    VaultColors.AccentCyan.copy(alpha = 0.65f),
                    VaultColors.AccentCyan.copy(alpha = 0.15f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, scanY),
            end = Offset(width, scanY),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

/**
 * Ambient Frosted Glass background overlay with subtle floating ethereal glow orbs and dynamic bokeh.
 */
@Composable
fun FrostedGlassAmbientOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "glass_ambient")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_pulse"
    )

    val driftY by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_drift"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center1 = Offset(size.width * 0.5f, (size.height * 0.28f) + driftY)
        val radius1 = size.minDimension * 0.55f * pulse

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VaultColors.AccentCyan.copy(alpha = 0.14f),
                    Color(0xFF8B5CF6).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center1,
                radius = radius1
            ),
            center = center1,
            radius = radius1
        )

        val center2 = Offset(size.width * 0.8f, size.height * 0.65f)
        val radius2 = size.minDimension * 0.40f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF3B82F6).copy(alpha = 0.09f),
                    Color(0xFF10B981).copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = center2,
                radius = radius2
            ),
            center = center2,
            radius = radius2
        )
    }
}

/**
 * Top app bar with security lock status badge, Panic Lock action, and settings.
 */
@Composable
fun VaultHeader(
    title: String = "PrivateVault",
    isLocked: Boolean = false,
    onLockClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = LocalVaultSpacing.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isLocked) VaultColors.AccentCrimson else VaultColors.AccentEmerald)
            )
            Text(
                text = title,
                style = LocalVaultTypography.current.headline.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = VaultColors.TextPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            if (onLockClick != null) {
                Row(
                    modifier = Modifier
                        .testTag("header_lock_action")
                        .clip(RoundedCornerShape(8.dp))
                        .background(VaultColors.AccentCrimson.copy(alpha = 0.14f))
                        .border(1.dp, VaultColors.AccentCrimson.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onLockClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Panic Lock",
                        tint = VaultColors.AccentCrimson,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "LOCK",
                        style = LocalVaultTypography.current.monospaceAccented.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = VaultColors.AccentCrimson
                    )
                }
            }

            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("header_settings_action")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Security Settings",
                        tint = VaultColors.TextSecondary
                    )
                }
            }
        }
    }
}
