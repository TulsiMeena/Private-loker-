package com.example.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    DARK("Dark"),
    SYSTEM("System Default"),
    LIGHT("Light")
}

enum class VaultAccentColor(val title: String, val hexCode: Long) {
    CYAN("Cyan (Default)", 0xFF06B6D4),
    EMERALD("Emerald", 0xFF10B981),
    AMBER("Amber", 0xFFF59E0B),
    SAPPHIRE("Sapphire", 0xFF3B82F6),
    AMETHYST("Amethyst", 0xFF8B5CF6),
    CRIMSON("Crimson", 0xFFEF4444)
}

enum class LockScreenStyle(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    CYBERPUNK_HUD(
        title = "Cyberpunk HUD Radar",
        subtitle = "Arc reactor rings, holographic scanlines, radar laser & tactical keypad",
        badge = "SCI-FI HUD"
    ),
    FROSTED_GLASS(
        title = "Frosted Glassmorphism",
        subtitle = "VisionOS translucent glass cards, fluid droplets, specular depth & ambient glow",
        badge = "LUXURY GLASS"
    )
}

enum class GlassIntensity(val title: String, val alphaFactor: Float) {
    OFF("Off (Solid)", 0.0f),
    LOW("Subtle", 0.35f),
    MEDIUM("Medium (Default)", 0.65f),
    HIGH("Vibrant", 0.90f)
}

enum class AnimationIntensity(val title: String, val durationMultiplier: Float) {
    OFF("Disabled", 0.0f),
    FAST("Fast (0.5x)", 0.5f),
    NORMAL("Normal (1.0x)", 1.0f)
}

enum class LineEndingPreference(val title: String, val sequence: String) {
    LF("Unix (LF - \\n)", "\n"),
    CRLF("Windows (CRLF - \\r\\n)", "\r\n")
}

enum class EncodingPreference(val title: String, val charsetName: String) {
    UTF_8("UTF-8 (Standard)", "UTF-8"),
    ASCII("US-ASCII", "US-ASCII"),
    ISO_8859_1("ISO-8859-1 (Latin-1)", "ISO-8859-1")
}

enum class EditorThemePreference(val title: String) {
    MONOKAI("Monokai Dark"),
    SOLARIZED_DARK("Solarized Dark"),
    GITHUB_DARK("GitHub Dark"),
    HIGH_CONTRAST("High Contrast")
}

enum class ThumbnailQualityPreference(val title: String, val maxDimensionPx: Int) {
    LOW("Low (Fast)", 160),
    MEDIUM("Medium (Balanced)", 320),
    HIGH("High (Sharp)", 640)
}

/**
 * Centralized Settings Management for PrivateVault.
 *
 * Persists user customization and interface preferences safely.
 * IMPORTANT: Sensitive data (PINs, encryption keys, recovery phrases) MUST NEVER
 * be stored here. This manager only stores harmless display, editor, media,
 * and accessibility preferences.
 */
class AppSettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "private_vault_app_settings"

        // Appearance
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_ACCENT_COLOR = "pref_accent_color"
        private const val KEY_LOCK_SCREEN_STYLE = "pref_lock_screen_style"
        private const val KEY_GLASS_INTENSITY = "pref_glass_intensity"
        private const val KEY_ANIMATION_INTENSITY = "pref_animation_intensity"
        private const val KEY_REDUCED_MOTION = "pref_reduced_motion"

        // Editor
        private const val KEY_EDITOR_FONT_SIZE = "pref_editor_font_size"
        private const val KEY_EDITOR_WORD_WRAP = "pref_editor_word_wrap"
        private const val KEY_EDITOR_LINE_NUMBERS = "pref_editor_line_numbers"
        private const val KEY_EDITOR_SYNTAX_HIGHLIGHTING = "pref_editor_syntax_highlighting"
        private const val KEY_EDITOR_TAB_SIZE = "pref_editor_tab_size"
        private const val KEY_EDITOR_USE_SPACES = "pref_editor_use_spaces"
        private const val KEY_EDITOR_AUTO_INDENT = "pref_editor_auto_indent"
        private const val KEY_EDITOR_LINE_ENDING = "pref_editor_line_ending"
        private const val KEY_EDITOR_ENCODING = "pref_editor_encoding"
        private const val KEY_EDITOR_THEME = "pref_editor_theme"

        // Media
        private const val KEY_MEDIA_PLAYBACK_SPEED = "pref_media_playback_speed"
        private const val KEY_MEDIA_AUTO_PLAY = "pref_media_auto_play"
        private const val KEY_MEDIA_FULLSCREEN = "pref_media_fullscreen"
        private const val KEY_MEDIA_REMEMBER_POSITION = "pref_media_remember_position"
        private const val KEY_MEDIA_THUMBNAIL_QUALITY = "pref_media_thumbnail_quality"
        private const val KEY_MEDIA_CACHE_LIMIT_MB = "pref_media_cache_limit_mb"

        // Accessibility & Feedback
        private const val KEY_HAPTIC_FEEDBACK = "pref_haptic_feedback"
        private const val KEY_HIGH_CONTRAST = "pref_high_contrast"
        private const val KEY_LARGE_TOUCH_TARGETS = "pref_large_touch_targets"

        @Volatile
        private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Appearance StateFlows
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(loadAccentColor())
    val accentColor: StateFlow<VaultAccentColor> = _accentColor.asStateFlow()

    private val _lockScreenStyle = MutableStateFlow(loadLockScreenStyle())
    val lockScreenStyle: StateFlow<LockScreenStyle> = _lockScreenStyle.asStateFlow()

    private val _glassIntensity = MutableStateFlow(loadGlassIntensity())
    val glassIntensity: StateFlow<GlassIntensity> = _glassIntensity.asStateFlow()

    private val _animationIntensity = MutableStateFlow(loadAnimationIntensity())
    val animationIntensity: StateFlow<AnimationIntensity> = _animationIntensity.asStateFlow()

    private val _reducedMotion = MutableStateFlow(prefs.getBoolean(KEY_REDUCED_MOTION, false))
    val reducedMotion: StateFlow<Boolean> = _reducedMotion.asStateFlow()

    // Editor StateFlows
    private val _editorFontSize = MutableStateFlow(prefs.getInt(KEY_EDITOR_FONT_SIZE, 14))
    val editorFontSize: StateFlow<Int> = _editorFontSize.asStateFlow()

    private val _editorWordWrap = MutableStateFlow(prefs.getBoolean(KEY_EDITOR_WORD_WRAP, false))
    val editorWordWrap: StateFlow<Boolean> = _editorWordWrap.asStateFlow()

    private val _editorLineNumbers = MutableStateFlow(prefs.getBoolean(KEY_EDITOR_LINE_NUMBERS, true))
    val editorLineNumbers: StateFlow<Boolean> = _editorLineNumbers.asStateFlow()

    private val _editorSyntaxHighlighting = MutableStateFlow(prefs.getBoolean(KEY_EDITOR_SYNTAX_HIGHLIGHTING, true))
    val editorSyntaxHighlighting: StateFlow<Boolean> = _editorSyntaxHighlighting.asStateFlow()

    private val _editorTabSize = MutableStateFlow(prefs.getInt(KEY_EDITOR_TAB_SIZE, 4))
    val editorTabSize: StateFlow<Int> = _editorTabSize.asStateFlow()

    private val _editorUseSpaces = MutableStateFlow(prefs.getBoolean(KEY_EDITOR_USE_SPACES, true))
    val editorUseSpaces: StateFlow<Boolean> = _editorUseSpaces.asStateFlow()

    private val _editorAutoIndent = MutableStateFlow(prefs.getBoolean(KEY_EDITOR_AUTO_INDENT, true))
    val editorAutoIndent: StateFlow<Boolean> = _editorAutoIndent.asStateFlow()

    private val _editorLineEnding = MutableStateFlow(loadLineEnding())
    val editorLineEnding: StateFlow<LineEndingPreference> = _editorLineEnding.asStateFlow()

    private val _editorEncoding = MutableStateFlow(loadEncoding())
    val editorEncoding: StateFlow<EncodingPreference> = _editorEncoding.asStateFlow()

    private val _editorTheme = MutableStateFlow(loadEditorTheme())
    val editorTheme: StateFlow<EditorThemePreference> = _editorTheme.asStateFlow()

    // Media StateFlows
    private val _mediaPlaybackSpeed = MutableStateFlow(prefs.getFloat(KEY_MEDIA_PLAYBACK_SPEED, 1.0f))
    val mediaPlaybackSpeed: StateFlow<Float> = _mediaPlaybackSpeed.asStateFlow()

    private val _mediaAutoPlay = MutableStateFlow(prefs.getBoolean(KEY_MEDIA_AUTO_PLAY, false))
    val mediaAutoPlay: StateFlow<Boolean> = _mediaAutoPlay.asStateFlow()

    private val _mediaFullscreen = MutableStateFlow(prefs.getBoolean(KEY_MEDIA_FULLSCREEN, false))
    val mediaFullscreen: StateFlow<Boolean> = _mediaFullscreen.asStateFlow()

    private val _mediaRememberPosition = MutableStateFlow(prefs.getBoolean(KEY_MEDIA_REMEMBER_POSITION, true))
    val mediaRememberPosition: StateFlow<Boolean> = _mediaRememberPosition.asStateFlow()

    private val _mediaThumbnailQuality = MutableStateFlow(loadThumbnailQuality())
    val mediaThumbnailQuality: StateFlow<ThumbnailQualityPreference> = _mediaThumbnailQuality.asStateFlow()

    private val _mediaCacheLimitMb = MutableStateFlow(prefs.getInt(KEY_MEDIA_CACHE_LIMIT_MB, 100))
    val mediaCacheLimitMb: StateFlow<Int> = _mediaCacheLimitMb.asStateFlow()

    // Accessibility & Feedback
    private val _hapticFeedbackEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _highContrastEnabled = MutableStateFlow(prefs.getBoolean(KEY_HIGH_CONTRAST, false))
    val highContrastEnabled: StateFlow<Boolean> = _highContrastEnabled.asStateFlow()

    private val _largeTouchTargets = MutableStateFlow(prefs.getBoolean(KEY_LARGE_TOUCH_TARGETS, true))
    val largeTouchTargets: StateFlow<Boolean> = _largeTouchTargets.asStateFlow()

    // Loaders
    private fun loadThemeMode(): AppThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name)
        return try { AppThemeMode.valueOf(raw ?: AppThemeMode.DARK.name) } catch (_: Exception) { AppThemeMode.DARK }
    }

    private fun loadAccentColor(): VaultAccentColor {
        val raw = prefs.getString(KEY_ACCENT_COLOR, VaultAccentColor.CYAN.name)
        return try { VaultAccentColor.valueOf(raw ?: VaultAccentColor.CYAN.name) } catch (_: Exception) { VaultAccentColor.CYAN }
    }

    private fun loadLockScreenStyle(): LockScreenStyle {
        val raw = prefs.getString(KEY_LOCK_SCREEN_STYLE, LockScreenStyle.CYBERPUNK_HUD.name)
        return try { LockScreenStyle.valueOf(raw ?: LockScreenStyle.CYBERPUNK_HUD.name) } catch (_: Exception) { LockScreenStyle.CYBERPUNK_HUD }
    }

    private fun loadGlassIntensity(): GlassIntensity {
        val raw = prefs.getString(KEY_GLASS_INTENSITY, GlassIntensity.MEDIUM.name)
        return try { GlassIntensity.valueOf(raw ?: GlassIntensity.MEDIUM.name) } catch (_: Exception) { GlassIntensity.MEDIUM }
    }

    private fun loadAnimationIntensity(): AnimationIntensity {
        val raw = prefs.getString(KEY_ANIMATION_INTENSITY, AnimationIntensity.NORMAL.name)
        return try { AnimationIntensity.valueOf(raw ?: AnimationIntensity.NORMAL.name) } catch (_: Exception) { AnimationIntensity.NORMAL }
    }

    private fun loadLineEnding(): LineEndingPreference {
        val raw = prefs.getString(KEY_EDITOR_LINE_ENDING, LineEndingPreference.LF.name)
        return try { LineEndingPreference.valueOf(raw ?: LineEndingPreference.LF.name) } catch (_: Exception) { LineEndingPreference.LF }
    }

    private fun loadEncoding(): EncodingPreference {
        val raw = prefs.getString(KEY_EDITOR_ENCODING, EncodingPreference.UTF_8.name)
        return try { EncodingPreference.valueOf(raw ?: EncodingPreference.UTF_8.name) } catch (_: Exception) { EncodingPreference.UTF_8 }
    }

    private fun loadEditorTheme(): EditorThemePreference {
        val raw = prefs.getString(KEY_EDITOR_THEME, EditorThemePreference.MONOKAI.name)
        return try { EditorThemePreference.valueOf(raw ?: EditorThemePreference.MONOKAI.name) } catch (_: Exception) { EditorThemePreference.MONOKAI }
    }

    private fun loadThumbnailQuality(): ThumbnailQualityPreference {
        val raw = prefs.getString(KEY_MEDIA_THUMBNAIL_QUALITY, ThumbnailQualityPreference.MEDIUM.name)
        return try { ThumbnailQualityPreference.valueOf(raw ?: ThumbnailQualityPreference.MEDIUM.name) } catch (_: Exception) { ThumbnailQualityPreference.MEDIUM }
    }

    // Setters - Appearance
    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccentColor(accent: VaultAccentColor) {
        prefs.edit().putString(KEY_ACCENT_COLOR, accent.name).apply()
        _accentColor.value = accent
    }

    fun setLockScreenStyle(style: LockScreenStyle) {
        prefs.edit().putString(KEY_LOCK_SCREEN_STYLE, style.name).apply()
        _lockScreenStyle.value = style
    }

    fun setGlassIntensity(intensity: GlassIntensity) {
        prefs.edit().putString(KEY_GLASS_INTENSITY, intensity.name).apply()
        _glassIntensity.value = intensity
    }

    fun setAnimationIntensity(intensity: AnimationIntensity) {
        prefs.edit().putString(KEY_ANIMATION_INTENSITY, intensity.name).apply()
        _animationIntensity.value = intensity
    }

    fun setReducedMotion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCED_MOTION, enabled).apply()
        _reducedMotion.value = enabled
        if (enabled) {
            setAnimationIntensity(AnimationIntensity.OFF)
        } else if (_animationIntensity.value == AnimationIntensity.OFF) {
            setAnimationIntensity(AnimationIntensity.NORMAL)
        }
    }

    // Setters - Editor
    fun setEditorFontSize(sizeSp: Int) {
        val clamped = sizeSp.coerceIn(10, 26)
        prefs.edit().putInt(KEY_EDITOR_FONT_SIZE, clamped).apply()
        _editorFontSize.value = clamped
    }

    fun setEditorWordWrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_WORD_WRAP, enabled).apply()
        _editorWordWrap.value = enabled
    }

    fun setEditorLineNumbers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_LINE_NUMBERS, enabled).apply()
        _editorLineNumbers.value = enabled
    }

    fun setEditorSyntaxHighlighting(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_SYNTAX_HIGHLIGHTING, enabled).apply()
        _editorSyntaxHighlighting.value = enabled
    }

    fun setEditorTabSize(size: Int) {
        val safe = if (size in listOf(2, 4, 8)) size else 4
        prefs.edit().putInt(KEY_EDITOR_TAB_SIZE, safe).apply()
        _editorTabSize.value = safe
    }

    fun setEditorUseSpaces(useSpaces: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_USE_SPACES, useSpaces).apply()
        _editorUseSpaces.value = useSpaces
    }

    fun setEditorAutoIndent(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_AUTO_INDENT, enabled).apply()
        _editorAutoIndent.value = enabled
    }

    fun setEditorLineEnding(ending: LineEndingPreference) {
        prefs.edit().putString(KEY_EDITOR_LINE_ENDING, ending.name).apply()
        _editorLineEnding.value = ending
    }

    fun setEditorEncoding(encoding: EncodingPreference) {
        prefs.edit().putString(KEY_EDITOR_ENCODING, encoding.name).apply()
        _editorEncoding.value = encoding
    }

    fun setEditorTheme(theme: EditorThemePreference) {
        prefs.edit().putString(KEY_EDITOR_THEME, theme.name).apply()
        _editorTheme.value = theme
    }

    // Setters - Media
    fun setMediaPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_MEDIA_PLAYBACK_SPEED, speed).apply()
        _mediaPlaybackSpeed.value = speed
    }

    fun setMediaAutoPlay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEDIA_AUTO_PLAY, enabled).apply()
        _mediaAutoPlay.value = enabled
    }

    fun setMediaFullscreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEDIA_FULLSCREEN, enabled).apply()
        _mediaFullscreen.value = enabled
    }

    fun setMediaRememberPosition(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEDIA_REMEMBER_POSITION, enabled).apply()
        _mediaRememberPosition.value = enabled
    }

    fun setMediaThumbnailQuality(quality: ThumbnailQualityPreference) {
        prefs.edit().putString(KEY_MEDIA_THUMBNAIL_QUALITY, quality.name).apply()
        _mediaThumbnailQuality.value = quality
    }

    fun setMediaCacheLimitMb(limitMb: Int) {
        prefs.edit().putInt(KEY_MEDIA_CACHE_LIMIT_MB, limitMb).apply()
        _mediaCacheLimitMb.value = limitMb
    }

    // Setters - Accessibility
    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _hapticFeedbackEnabled.value = enabled
    }

    fun setHighContrastEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _highContrastEnabled.value = enabled
    }

    fun setLargeTouchTargets(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LARGE_TOUCH_TARGETS, enabled).apply()
        _largeTouchTargets.value = enabled
    }

    /**
     * Resets ONLY user-interface and application preferences to their pristine defaults.
     *
     * GUARANTEE:
     * - Vault files are untouched.
     * - Trash items are untouched.
     * - Master keys, salt, and PIN hashes are NEVER touched or removed.
     * - Security policies remain enforced.
     */
    fun resetAppPreferences() {
        prefs.edit().clear().apply()

        _themeMode.value = AppThemeMode.DARK
        _accentColor.value = VaultAccentColor.CYAN
        _glassIntensity.value = GlassIntensity.MEDIUM
        _animationIntensity.value = AnimationIntensity.NORMAL
        _reducedMotion.value = false

        _editorFontSize.value = 14
        _editorWordWrap.value = false
        _editorLineNumbers.value = true
        _editorSyntaxHighlighting.value = true
        _editorTabSize.value = 4
        _editorUseSpaces.value = true
        _editorAutoIndent.value = true
        _editorLineEnding.value = LineEndingPreference.LF
        _editorEncoding.value = EncodingPreference.UTF_8
        _editorTheme.value = EditorThemePreference.MONOKAI

        _mediaPlaybackSpeed.value = 1.0f
        _mediaAutoPlay.value = false
        _mediaFullscreen.value = false
        _mediaRememberPosition.value = true
        _mediaThumbnailQuality.value = ThumbnailQualityPreference.MEDIUM
        _mediaCacheLimitMb.value = 100

        _hapticFeedbackEnabled.value = true
        _highContrastEnabled.value = false
        _largeTouchTargets.value = true
    }
}
