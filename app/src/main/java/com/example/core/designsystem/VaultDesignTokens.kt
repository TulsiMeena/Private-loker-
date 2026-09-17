package com.example.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ultra-premium design tokens for PrivateVault.
 * Hardware-grade security aesthetic: deep graphite, obsidian, titanium,
 * restrained emerald/cyan status lights, hairline borders, and tactile micro-interactions.
 */
object VaultColors {
    // Canvas & Surface Hierarchy
    val Canvas = Color(0xFF07090C)
    val SurfaceGraphite = Color(0xFF0E1116)
    val SurfaceDark = Color(0xFF0E1116)
    val SurfaceElevated = Color(0xFF141820)
    val SurfaceOverlay = Color(0xFF1B202B)
    val SurfaceHighlight = Color(0xFF242A38)

    // Glass & Hairline Borders
    val GlassBorderSubtle = Color(0x18FFFFFF)
    val GlassBorderMedium = Color(0x28FFFFFF)
    val GlassBorderFocus = Color(0x4038BDF8)
    val GlassBackground = Color(0x990E1116)
    val GlassBackgroundLight = Color(0x551E2433)

    // Core Security & Accent Tones
    val AccentCyan = Color(0xFF38BDF8)
    val AccentEmerald = Color(0xFF10B981)
    val AccentSuccess = Color(0xFF10B981)
    val AccentAmber = Color(0xFFF59E0B)
    val AccentCrimson = Color(0xFFF43F5E)
    val AccentRed = Color(0xFFF43F5E)
    val Titanium = Color(0xFF94A3B8)
    val Platinum = Color(0xFFE2E8F0)

    // Typography Tones
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)
    val TextDisabled = Color(0xFF475569)
    val TextInverse = Color(0xFF07090C)
}

@Immutable
data class VaultSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    val screenHorizontal: Dp = 20.dp,
    val screenVertical: Dp = 24.dp
)

@Immutable
data class VaultCornerRadius(
    val sharp: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 14.dp,
    val large: Dp = 20.dp,
    val pill: Dp = 999.dp
)

@Immutable
data class VaultElevation(
    val flat: Dp = 0.dp,
    val low: Dp = 2.dp,
    val medium: Dp = 6.dp,
    val high: Dp = 12.dp
)

@Immutable
data class VaultIconSizes(
    val tiny: Dp = 14.dp,
    val small: Dp = 18.dp,
    val medium: Dp = 24.dp,
    val large: Dp = 32.dp,
    val hero: Dp = 56.dp
)

@Immutable
data class VaultTouchTargets(
    val minimum: Dp = 48.dp,
    val keypadButton: Dp = 72.dp
)

object VaultMotion {
    const val DurationFastMs = 180
    const val DurationNormalMs = 320
    const val DurationSlowMs = 500
    const val DurationCoreActivationMs = 850

    val EasingStandard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EasingDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val EasingAccelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    val TactileSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

@Immutable
data class VaultTypography(
    val displayLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = VaultColors.TextPrimary
    ),
    val displayMedium: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
        color = VaultColors.TextPrimary
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        color = VaultColors.TextPrimary
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
        color = VaultColors.TextPrimary
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        color = VaultColors.TextSecondary
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = VaultColors.TextTertiary
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        color = VaultColors.TextTertiary
    ),
    val monospaceAccented: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
        color = VaultColors.AccentCyan
    ),
    val keypadDigit: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        color = VaultColors.TextPrimary
    )
)

val LocalVaultSpacing = staticCompositionLocalOf { VaultSpacing() }
val LocalVaultCornerRadius = staticCompositionLocalOf { VaultCornerRadius() }
val LocalVaultElevation = staticCompositionLocalOf { VaultElevation() }
val LocalVaultIconSizes = staticCompositionLocalOf { VaultIconSizes() }
val LocalVaultTouchTargets = staticCompositionLocalOf { VaultTouchTargets() }
val LocalVaultTypography = staticCompositionLocalOf { VaultTypography() }
