package com.orizon.openkiwi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

val LocalAccentColor = compositionLocalOf { LuminaAccentGreen }

object AccentColors {
    val green = Color(0xFF1DB954)
    val blue = Color(0xFF007AFF)
    val purple = Color(0xFF8B5CF6)
    val orange = Color(0xFFFF9500)
    val pink = Color(0xFFFF2D55)
    val cyan = Color(0xFF00C7BE)
    val red = Color(0xFFFF3B30)
    val indigo = Color(0xFF5856D6)

    val all = mapOf(
        "green" to green, "blue" to blue, "purple" to purple,
        "orange" to orange, "pink" to pink, "cyan" to cyan,
        "red" to red, "indigo" to indigo
    )
    val labels = mapOf(
        "green" to "绿", "blue" to "蓝", "purple" to "紫",
        "orange" to "橙", "pink" to "粉", "cyan" to "青",
        "red" to "红", "indigo" to "靛蓝"
    )

    fun fromKey(key: String): Color = all[key] ?: green
}

object AppFonts {
    val all = mapOf(
        "default" to FontFamily.Default,
        "serif" to FontFamily.Serif,
        "monospace" to FontFamily.Monospace,
        "sans" to FontFamily.SansSerif,
        "cursive" to FontFamily.Cursive
    )
    val labels = mapOf(
        "default" to "默认", "serif" to "宋体/衬线",
        "monospace" to "等宽", "sans" to "无衬线",
        "cursive" to "手写体"
    )

    fun fromKey(key: String): FontFamily = all[key] ?: FontFamily.Default
}

private fun buildLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = LuminaGlassUser,
    onPrimaryContainer = Color.Black,
    secondary = accent,
    onSecondary = Color.White,
    secondaryContainer = LuminaGlassDark,
    onSecondaryContainer = Color.Black,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    background = LuminaBackground,
    onBackground = Color.Black,
    surface = LuminaBackground,
    onSurface = Color.Black,
    surfaceVariant = LuminaGlassDark,
    onSurfaceVariant = Color.Black,
    outline = LuminaGlassBorder,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer
)

private fun buildDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = LuminaGlassUser,
    onPrimaryContainer = Color.Black,
    secondary = accent,
    onSecondary = Color.White,
    secondaryContainer = LuminaGlassDark,
    onSecondaryContainer = Color.Black,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    background = LuminaBackground,
    onBackground = Color.Black,
    surface = LuminaBackground,
    onSurface = Color.Black,
    surfaceVariant = LuminaGlassDark,
    onSurfaceVariant = Color.Black,
    outline = LuminaGlassBorder,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer
)

@Composable
fun OpenKiwiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColorKey: String = "green",
    fontFamilyKey: String = "default",
    content: @Composable () -> Unit
) {
    val accent = AccentColors.fromKey(accentColorKey)
    val fontFamily = AppFonts.fromKey(fontFamilyKey)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> buildDarkScheme(accent)
        else -> buildLightScheme(accent)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    CompositionLocalProvider(
        LocalAccentColor provides accent
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildTypography(fontFamily),
            content = content
        )
    }
}
