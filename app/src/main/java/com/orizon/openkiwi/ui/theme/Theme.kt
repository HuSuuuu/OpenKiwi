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

val LocalAccentColor = compositionLocalOf { PaperAccent }

object AccentColors {
    val green = Color(0xFF4CAF7D)
    val blue = Color(0xFF4A8EC2)
    val purple = Color(0xFF7E6BAD)
    val orange = PaperAccent
    val pink = Color(0xFFD4697A)
    val cyan = Color(0xFF3BA5A8)
    val red = Color(0xFFCF4744)
    val indigo = Color(0xFF5C6BC0)

    val all = mapOf(
        "green" to green, "blue" to blue, "purple" to purple,
        "orange" to orange, "pink" to pink, "cyan" to cyan,
        "red" to red, "indigo" to indigo
    )
    val labels = mapOf(
        "green" to "\u7EFF", "blue" to "\u84DD", "purple" to "\u7D2B",
        "orange" to "\u6A59", "pink" to "\u7C89", "cyan" to "\u9752",
        "red" to "\u7EA2", "indigo" to "\u9756\u84DD"
    )

    fun fromKey(key: String): Color = all[key] ?: PaperAccent
}

object AppFonts {
    val all = mapOf(
        "default" to FontFamily.Serif,
        "serif" to FontFamily.Serif,
        "monospace" to FontFamily.Monospace,
        "sans" to FontFamily.SansSerif,
        "cursive" to FontFamily.Cursive
    )
    val labels = mapOf(
        "default" to "\u5B8B\u4F53", "serif" to "\u5B8B\u4F53/\u886C\u7EBF",
        "monospace" to "\u7B49\u5BBD", "sans" to "\u65E0\u886C\u7EBF",
        "cursive" to "\u624B\u5199\u4F53"
    )

    fun fromKey(key: String): FontFamily = all[key] ?: FontFamily.Serif
}

private fun buildLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDF3FA),
    onPrimaryContainer = Color(0xFF1A3A5C),
    secondary = Color(0xFF5A6B7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF1F4),
    onSecondaryContainer = Color(0xFF1A2530),
    tertiary = Color(0xFF6B5E7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0ECF4),
    onTertiaryContainer = Color(0xFF251E30),
    background = PaperCream,
    onBackground = PaperInk,
    surface = PaperCream,
    onSurface = PaperInk,
    surfaceVariant = PaperCard,
    onSurfaceVariant = PaperInkLight,
    outline = PaperBorder,
    outlineVariant = PaperBorderLight,
    error = PaperRed,
    onError = Color.White,
    errorContainer = Color(0xFFFCEDEC)
)

private fun buildDarkScheme(accent: Color) = darkColorScheme(
    primary = PaperAccentMuted,
    onPrimary = Color.Black,
    primaryContainer = PaperDarkCard,
    onPrimaryContainer = PaperDarkInk,
    secondary = PaperDarkInkLight,
    onSecondary = Color.Black,
    secondaryContainer = PaperDarkCard,
    onSecondaryContainer = PaperDarkInk,
    tertiary = Color(0xFFB0A0C8),
    onTertiary = Color.Black,
    tertiaryContainer = PaperDarkCard,
    onTertiaryContainer = PaperDarkInk,
    background = PaperDarkBg,
    onBackground = PaperDarkInk,
    surface = PaperDarkBg,
    onSurface = PaperDarkInk,
    surfaceVariant = PaperDarkCard,
    onSurfaceVariant = PaperDarkInkLight,
    outline = PaperDarkBorder,
    outlineVariant = PaperDarkBorder,
    error = Color(0xFFE57373),
    onError = Color.Black,
    errorContainer = Color(0xFF401010)
)

@Composable
fun OpenKiwiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColorKey: String = "orange",
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
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
