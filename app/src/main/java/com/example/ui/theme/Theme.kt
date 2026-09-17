package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.core.designsystem.LocalVaultCornerRadius
import com.example.core.designsystem.LocalVaultElevation
import com.example.core.designsystem.LocalVaultIconSizes
import com.example.core.designsystem.LocalVaultSpacing
import com.example.core.designsystem.LocalVaultTouchTargets
import com.example.core.designsystem.LocalVaultTypography
import com.example.core.designsystem.VaultColors
import com.example.core.designsystem.VaultCornerRadius
import com.example.core.designsystem.VaultElevation
import com.example.core.designsystem.VaultIconSizes
import com.example.core.designsystem.VaultSpacing
import com.example.core.designsystem.VaultTouchTargets
import com.example.core.designsystem.VaultTypography
import com.example.core.settings.AppThemeMode
import com.example.core.settings.GlassIntensity
import com.example.core.settings.VaultAccentColor

val LocalVaultAccent = compositionLocalOf { VaultAccentColor.CYAN }
val LocalGlassIntensity = compositionLocalOf { GlassIntensity.MEDIUM }
val LocalReducedMotion = compositionLocalOf { false }

private val VaultDarkColorScheme = darkColorScheme(
    primary = VaultColors.AccentCyan,
    onPrimary = Color(0xFF041E28),
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = VaultColors.AccentCyan,
    secondary = VaultColors.AccentEmerald,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = VaultColors.AccentEmerald,
    tertiary = VaultColors.Titanium,
    onTertiary = Color(0xFF0F172A),
    background = VaultColors.Canvas,
    onBackground = VaultColors.TextPrimary,
    surface = VaultColors.SurfaceGraphite,
    onSurface = VaultColors.TextPrimary,
    surfaceVariant = VaultColors.SurfaceElevated,
    onSurfaceVariant = VaultColors.TextSecondary,
    surfaceTint = VaultColors.AccentCyan,
    outline = VaultColors.GlassBorderSubtle,
    outlineVariant = VaultColors.GlassBorderMedium,
    error = VaultColors.AccentCrimson,
    onError = Color.White
)

private val VaultLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = VaultColors.AccentEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = VaultColors.Titanium,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color(0xFF0284C7),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1),
    error = VaultColors.AccentCrimson,
    onError = Color.White
)

@Composable
fun PrivateVaultTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    accentColor: VaultAccentColor = VaultAccentColor.CYAN,
    glassIntensity: GlassIntensity = GlassIntensity.MEDIUM,
    reducedMotion: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemInDark
    }

    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) VaultDarkColorScheme else VaultLightColorScheme
    }

    // Apply custom accent color
    val accentRgb = Color(accentColor.hexCode)
    val colorScheme = baseScheme.copy(
        primary = accentRgb,
        primaryContainer = if (isDark) accentRgb.copy(alpha = 0.25f) else accentRgb.copy(alpha = 0.15f),
        onPrimaryContainer = if (isDark) accentRgb else Color(0xFF0F172A),
        surfaceTint = accentRgb
    )

    CompositionLocalProvider(
        LocalVaultSpacing provides VaultSpacing(),
        LocalVaultCornerRadius provides VaultCornerRadius(),
        LocalVaultElevation provides VaultElevation(),
        LocalVaultIconSizes provides VaultIconSizes(),
        LocalVaultTouchTargets provides VaultTouchTargets(),
        LocalVaultTypography provides VaultTypography(),
        LocalVaultAccent provides accentColor,
        LocalGlassIntensity provides glassIntensity,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Keep backward compatibility for existing templates/tests
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PrivateVaultTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        dynamicColor = dynamicColor,
        content = content
    )
}
