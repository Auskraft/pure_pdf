package com.auskraft.purepdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.auskraft.purepdf.data.settings.AccentPreset
import com.materialkolor.rememberDynamicColorScheme

/**
 * Extra colors for the reader's "paper" that aren't part of the M3 ColorScheme.
 * Light: white page, near-black ink. Dark: dark-grey page + light-grey ink (never pure
 * black/white), matching the handoff. Used for page placeholders and the night-mode invert.
 */
data class PaperColors(
    val paper: Color,
    val ink: Color,
    val muted: Color,
)

val LightPaper = PaperColors(
    paper = Color.White,
    ink = Color(0xFF21242A),
    muted = Color(0xFF6A6F78),
)

val DarkPaper = PaperColors(
    paper = Color(0xFF22262E),
    ink = Color(0xFFDCDFE4),
    muted = Color(0xFF9AA0A8),
)

val LocalPaperColors = staticCompositionLocalOf { LightPaper }

/**
 * App theme. The M3 scheme is generated from the accent seed (the Compose equivalent of the
 * prototype's makeScheme()). Dark mode is an explicit user setting, not the system setting.
 */
@Composable
fun PurePdfTheme(
    accent: AccentPreset = AccentPreset.Blue,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = rememberDynamicColorScheme(
        seedColor = Color(accent.seedArgb),
        isDark = darkTheme,
        isAmoled = false,
    )
    val paper = if (darkTheme) DarkPaper else LightPaper

    CompositionLocalProvider(LocalPaperColors provides paper) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PurePdfTypography,
            shapes = PurePdfShapes,
            content = content,
        )
    }
}
