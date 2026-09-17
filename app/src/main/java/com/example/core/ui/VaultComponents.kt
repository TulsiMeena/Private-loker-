package com.example.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val rotationInner by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = if (isActiveAnimating) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (isActiveAnimating) 1.04f else 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
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
            val baseRadius = size.minDimension / 2f * 0.85f

            // 1. Soft radial ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedColor.copy(alpha = 0.12f * pulseScale),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.3f
                ),
                radius = baseRadius * 1.3f,
                center = center
            )

            // 2. Outer thin hairline ring
            drawCircle(
                color = VaultColors.GlassBorderMedium,
                radius = baseRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // 3. Segmented outer arc indicating active security
            val arcAngle = 75f
            drawArc(
                color = animatedColor.copy(alpha = 0.8f),
                startAngle = rotationOuter,
                sweepAngle = arcAngle,
                useCenter = false,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2f, baseRadius * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = animatedColor.copy(alpha = 0.4f),
                startAngle = rotationOuter + 180f,
                sweepAngle = arcAngle / 2,
                useCenter = false,
                topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2f, baseRadius * 2f),
                style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Middle concentric precision ring
            val midRadius = baseRadius * 0.72f
            drawCircle(
                color = VaultColors.SurfaceHighlight.copy(alpha = 0.6f),
                radius = midRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Middle arc opposite rotation
            drawArc(
                color = animatedColor.copy(alpha = 0.6f),
                startAngle = rotationInner,
                sweepAngle = 45f,
                useCenter = false,
                topLeft = Offset(center.x - midRadius, center.y - midRadius),
                size = androidx.compose.ui.geometry.Size(midRadius * 2f, midRadius * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 5. Inner Core Node
            val innerRadius = baseRadius * 0.42f * pulseScale
            drawCircle(
                color = VaultColors.SurfaceElevated,
                radius = innerRadius,
                center = center
            )
            drawCircle(
                color = animatedColor.copy(alpha = 0.7f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = 1.5f.dp.toPx())
            )
        }

        // Center Icon inside orb
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
@Composable
fun TactileKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onBiometricClick: (() -> Unit)?,
    isBiometricAvailable: Boolean = true,
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
                                onClick = {
                                    HapticFeedbackUtil.performTactileTick(context)
                                    onDeleteClick()
                                }
                            )
                        }
                        else -> {
                            KeypadDigitButton(
                                digit = key,
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
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .testTag("keypad_digit_$digit")
            .clip(CircleShape)
            .background(VaultColors.SurfaceElevated.copy(alpha = 0.7f))
            .border(BorderStroke(1.dp, VaultColors.GlassBorderSubtle), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            style = LocalVaultTypography.current.keypadDigit,
            color = VaultColors.TextPrimary
        )
    }
}

@Composable
private fun KeypadActionButton(
    icon: ImageVector,
    contentDesc: String,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .testTag(tag)
            .clip(CircleShape)
            .background(VaultColors.SurfaceGraphite)
            .border(BorderStroke(1.dp, VaultColors.GlassBorderSubtle), CircleShape)
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

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(pipColor)
                    .border(1.5.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isFilled && !isLockout && !isError) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
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
