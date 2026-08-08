package com.wakaroute.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The palette.
 *
 * Deliberately **not** dynamic colour. The map uses colour to carry a reading —
 * 学習中 against 手前でつまずき — and while §8 requires a word or a symbol beside
 * it either way, a palette pulled from the student's wallpaper can put those two
 * meanings at nearly the same hue. Fixed colours keep the contrast we tested.
 */
private val Blue = Color(0xFF1B5E9C)
private val BlueLight = Color(0xFF9FCAF3)
private val Amber = Color(0xFF9A5B00)
private val AmberLight = Color(0xFFFFB868)

internal val LightScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF535F70),
    // Set explicitly because it is what the navigation bar tints its selected
    // pill with. Left at the Material default it comes out lavender, which
    // reads as a different app's accent sitting under ours.
    secondaryContainer = Color(0xFFD3E4FF),
    onSecondaryContainer = Color(0xFF001C38),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB6),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    error = Color(0xFFA4162A),
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410006),
)

internal val DarkScheme = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color(0xFF00325B),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF004880),
    onSecondaryContainer = Color(0xFFD3E4FF),
    tertiary = AmberLight,
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDDB6),
    background = Color(0xFF121316),
    surface = Color(0xFF121316),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    error = Color(0xFFFFB4AA),
    errorContainer = Color(0xFF8C1D2A),
    onErrorContainer = Color(0xFFFFDAD8),
)

@Composable
fun WakaRouteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme

    // The system bars are left to `enableEdgeToEdge()` in MainActivity. Setting
    // `window.statusBarColor` here as well is both deprecated and a second
    // source of truth for the same pixels.

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WakaRouteTypography,
        content = content,
    )
}

/** True when the student has turned text size up far enough to change layouts. */
@Composable
fun isLargeFontScale(): Boolean = LocalContext.current.resources.configuration.fontScale >= 1.3f
