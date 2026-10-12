package com.asides.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// The colours of the app icon and splash screen: deep indigo, with amber for the sound wave.
private val Indigo = Color(0xFF4A3AA8)
private val Amber = Color(0xFFFFB74D)

// Keep these two in step with window_bg in res/values/colors.xml and values-night/colors.xml, so the
// system splash screen is the same colour as the app behind it.
private val BackgroundDark = Color(0xFF0F0D1E)
private val BackgroundLight = Color(0xFFFCF8FF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3BAFF), onPrimary = Color(0xFF241A7A),
    primaryContainer = Indigo, onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFFCAC4EA), onSecondary = Color(0xFF312C58),
    secondaryContainer = Color(0xFF3F3A66), onSecondaryContainer = Color(0xFFE6E0FF),
    tertiary = Amber, onTertiary = Color(0xFF3E2800),
    tertiaryContainer = Color(0xFF5C3C00), onTertiaryContainer = Color(0xFFFFDDB0),
    background = BackgroundDark, onBackground = Color(0xFFE7E3F5),
    surface = BackgroundDark, onSurface = Color(0xFFE7E3F5),
    surfaceVariant = Color(0xFF2A2740), onSurfaceVariant = Color(0xFFC9C4DD),
    outline = Color(0xFF938EA9), outlineVariant = Color(0xFF47435F),
    surfaceContainerLowest = Color(0xFF0A0916), surfaceContainerLow = Color(0xFF151327),
    surfaceContainer = Color(0xFF1A1830), surfaceContainerHigh = Color(0xFF25223B),
    surfaceContainerHighest = Color(0xFF302D47),
)

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF), onPrimaryContainer = Color(0xFF150068),
    secondary = Color(0xFF5D5791), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2DFFF), onSecondaryContainer = Color(0xFF191247),
    // Amber is too pale to read on a light background, so the light scheme uses a deeper shade of it.
    tertiary = Color(0xFF8A5400), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0), onTertiaryContainer = Color(0xFF2B1700),
    background = BackgroundLight, onBackground = Color(0xFF1C1B22),
    surface = BackgroundLight, onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFE5E0F0), onSurfaceVariant = Color(0xFF47455A),
    outline = Color(0xFF777587), outlineVariant = Color(0xFFC8C4DA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F2FF),
    surfaceContainer = Color(0xFFF0ECFA), surfaceContainerHigh = Color(0xFFEAE6F4),
    surfaceContainerHighest = Color(0xFFE4E0EF),
)

/** Follows the system's light or dark setting unless [dark] says otherwise (used by debug builds). */
@Composable
fun AsidesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * The app's name as a wordmark: bold italic serif (an aside is often set in italics), optionally
 * wrapped in amber parentheses like the icon.
 */
@Composable
fun Wordmark(fontSize: TextUnit, parentheses: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.tertiary
    val text = buildAnnotatedString {
        if (parentheses) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Light)) { append("(") }
        append("Asides")
        if (parentheses) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Light)) { append(")") }
    }
    Text(
        text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = (-0.5).sp,
        ),
    )
}
