package com.orizon.openkiwi.ui.theme

import androidx.compose.ui.graphics.Color

// ── Clean Paper Palette — neutral white with warm undertone, not yellow ──

val PaperCream        = Color(0xFFF9F8F6)   // very light warm gray, NOT yellow
val PaperCard         = Color(0xFFF2F0ED)   // subtle card surface
val PaperCardDark     = Color(0xFFEBE8E4)   // user bubble / pressed
val PaperInk          = Color(0xFF1A1A1A)   // near-black for readability
val PaperInkLight     = Color(0xFF6E6E6E)   // secondary text — neutral gray
val PaperAccent       = Color(0xFF3D7BCC)   // clean blue accent
val PaperAccentMuted  = Color(0xFF6B9FDB)   // lighter blue for dark mode
val PaperBorder       = Color(0xFFE2E0DC)   // subtle border
val PaperBorderLight  = Color(0xFFEBE9E5)   // very subtle border
val PaperRed          = Color(0xFFCF4744)   // error red
val PaperGreen        = Color(0xFF5A9E6F)   // success green
val PaperBlue         = Color(0xFF4A8EC2)   // info / link blue
val PaperPurple       = Color(0xFF7E6BAD)   // tag / badge purple

val PaperDarkBg       = Color(0xFF161616)
val PaperDarkCard     = Color(0xFF222222)
val PaperDarkInk      = Color(0xFFE5E5E5)
val PaperDarkInkLight = Color(0xFF999999)
val PaperDarkBorder   = Color(0xFF333333)

// Legacy aliases
val LuminaBackground = PaperCream
val LuminaAccentGreen = PaperAccent
val LuminaGlassDark = PaperCard
val LuminaGlassLight = PaperCard
val LuminaGlassUser = PaperCardDark
val LuminaGlassBorder = PaperBorder
val LuminaGlassBorderHighlight = PaperBorder

val md_theme_light_tertiary = PaperInkLight
val md_theme_light_onTertiary = Color.White
val md_theme_light_tertiaryContainer = PaperCard
val md_theme_light_onTertiaryContainer = PaperInk
val md_theme_light_error = PaperRed
val md_theme_light_onError = Color.White
val md_theme_light_errorContainer = Color(0xFFFDECEB)

val md_theme_dark_tertiary = PaperDarkInkLight
val md_theme_dark_onTertiary = Color.Black
val md_theme_dark_tertiaryContainer = PaperDarkCard
val md_theme_dark_onTertiaryContainer = PaperDarkInk
val md_theme_dark_error = Color(0xFFE57373)
val md_theme_dark_onError = Color.Black
val md_theme_dark_errorContainer = Color(0xFF401010)
