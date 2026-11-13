package com.paperstar.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours from the web prototype, with extras Material doesn't have a slot for. */
@Immutable
data class PaperColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val highlight: Color,
    val danger: Color,
    val glass: Color,
    val glassLine: Color,
    val glassShine: Color,
    val lids: List<Color>,
)

private val Light = PaperColors(
    bg = Color(0xFFF2F1F8), surface = Color.White, ink = Color(0xFF1D1A2C), muted = Color(0xFF6C6882),
    line = Color(0xFFE1DFEC), accent = Color(0xFF4F44D6), accentInk = Color.White, accentSoft = Color(0xFFE7E4FF),
    highlight = Color(0xFFFFE45C), danger = Color(0xFFC8344B),
    glass = Color(0xB8FFFFFF), glassLine = Color(0xFFC6C0DA), glassShine = Color(0xF2FFFFFF),
    lids = listOf(Color(0xFFFFC7D6), Color(0xFFBFE3FF), Color(0xFFCFF2C9), Color(0xFFFFE3A8), Color(0xFFDCCBFF)),
)

private val Dark = PaperColors(
    bg = Color(0xFF14121C), surface = Color(0xFF1F1C2B), ink = Color(0xFFECEAF6), muted = Color(0xFFA29DB9),
    line = Color(0xFF2F2B40), accent = Color(0xFF9D94FF), accentInk = Color(0xFF14121C), accentSoft = Color(0xFF2B2650),
    highlight = Color(0xFFE5CB33), danger = Color(0xFFFF7C8F),
    glass = Color(0x0FFFFFFF), glassLine = Color(0xFF4C4762), glassShine = Color(0x38FFFFFF),
    lids = listOf(Color(0xFF7A3A50), Color(0xFF2C5576), Color(0xFF35603A), Color(0xFF76592A), Color(0xFF53408A)),
)

/** Paper stars are paper: the same pastel colours in light and dark mode. */
val StarColors = listOf(
    Color(0xFFFFC7D6), Color(0xFFFFE3A8), Color(0xFFBFE3FF), Color(0xFFCFF2C9),
    Color(0xFFDCCBFF), Color(0xFFFFD1B3), Color(0xFFFFF3A8),
)

val LocalPaper = staticCompositionLocalOf { Light }

object Paper {
    val colors: PaperColors @Composable get() = LocalPaper.current
}

@Composable
fun PaperStarTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.accentInk, primaryContainer = c.accentSoft,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.accentSoft, onSurfaceVariant = c.muted, outline = c.line, error = c.danger,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.accentInk, primaryContainer = c.accentSoft,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.accentSoft, onSurfaceVariant = c.muted, outline = c.line, error = c.danger,
    )
    val type = Typography(
        headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp),
    )
    CompositionLocalProvider(LocalPaper provides c) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
