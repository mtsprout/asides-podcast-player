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
private val BrandIndigo = Color(0xFF4A3AA8)
private val Amber = Color(0xFFFFB74D)

// Keep these two in step with window_bg in res/values/colors.xml and values-night/colors.xml, so the
// system splash screen is the same colour as the app behind it.
private val BackgroundDark = Color(0xFF0F0D1E)
private val BackgroundLight = Color(0xFFFCF8FF)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC3BAFF), onPrimary = Color(0xFF241A7A),
    primaryContainer = BrandIndigo, onPrimaryContainer = Color(0xFFE6E0FF),
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
    primary = BrandIndigo, onPrimary = Color.White,
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

/**
 * A scheme built from two hues: [hue] tints the surfaces and the main colour, [accentHue] is the accent
 * the amber plays in the default theme. [tint] scales how colourful the surfaces are (1 = fully).
 */
private fun generatedScheme(dark: Boolean, hue: Float, accentHue: Float, tint: Float): ColorScheme {
    fun c(h: Float, s: Float, l: Float) = Color.hsl(h, (s * tint).coerceIn(0f, 1f), l)
    // The accent keeps its own full strength, whatever the tint.
    fun a(s: Float, l: Float) = Color.hsl(accentHue, s, l)
    return if (dark) darkColorScheme(
        primary = c(hue, .95f, .84f), onPrimary = c(hue, .6f, .22f),
        primaryContainer = c(hue, .5f, .42f), onPrimaryContainer = c(hue, 1f, .93f),
        secondary = c(hue, .4f, .84f), onSecondary = c(hue, .3f, .24f),
        secondaryContainer = c(hue, .25f, .33f), onSecondaryContainer = c(hue, .8f, .93f),
        tertiary = a(.95f, .65f), onTertiary = a(1f, .12f),
        tertiaryContainer = a(1f, .18f), onTertiaryContainer = a(1f, .85f),
        background = c(hue, .4f, .09f), onBackground = c(hue, .4f, .93f),
        surface = c(hue, .4f, .09f), onSurface = c(hue, .4f, .93f),
        surfaceVariant = c(hue, .25f, .20f), onSurfaceVariant = c(hue, .2f, .82f),
        outline = c(hue, .12f, .62f), outlineVariant = c(hue, .15f, .31f),
        surfaceContainerLowest = c(hue, .4f, .06f), surfaceContainerLow = c(hue, .4f, .11f),
        surfaceContainer = c(hue, .4f, .13f), surfaceContainerHigh = c(hue, .3f, .17f),
        surfaceContainerHighest = c(hue, .3f, .21f),
    ) else lightColorScheme(
        primary = c(hue, .5f, .43f), onPrimary = Color.White,
        primaryContainer = c(hue, 1f, .93f), onPrimaryContainer = c(hue, 1f, .20f),
        secondary = c(hue, .25f, .45f), onSecondary = Color.White,
        secondaryContainer = c(hue, .8f, .92f), onSecondaryContainer = c(hue, .6f, .17f),
        tertiary = a(1f, .27f), onTertiary = Color.White,
        tertiaryContainer = a(1f, .85f), onTertiaryContainer = a(1f, .09f),
        background = c(hue, 1f, .985f), onBackground = c(hue, .1f, .12f),
        surface = c(hue, 1f, .985f), onSurface = c(hue, .1f, .12f),
        surfaceVariant = c(hue, .3f, .92f), onSurfaceVariant = c(hue, .12f, .31f),
        outline = c(hue, .08f, .50f), outlineVariant = c(hue, .2f, .82f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = c(hue, 1f, .97f),
        surfaceContainer = c(hue, .6f, .95f), surfaceContainerHigh = c(hue, .4f, .93f),
        surfaceContainerHighest = c(hue, .3f, .91f),
    )
}

/**
 * The colour themes to choose from. Each has a light and a dark scheme; which one shows follows the
 * system setting. [Indigo] is the app's own look and matches the icon and splash screen.
 */
enum class ColorTheme(val key: String, val label: String, val swatch: Color, private val build: (Boolean) -> ColorScheme) {
    Indigo("indigo", "Indigo", BrandIndigo, { dark -> if (dark) DarkColors else LightColors }),
    Forest("forest", "Forest", Color.hsl(145f, .5f, .35f), { generatedScheme(it, 145f, 35f, 1f) }),
    Ocean("ocean", "Ocean", Color.hsl(205f, .6f, .42f), { generatedScheme(it, 205f, 15f, 1f) }),
    Rose("rose", "Rose", Color.hsl(335f, .5f, .48f), { generatedScheme(it, 335f, 45f, 1f) }),
    Ember("ember", "Ember", Color.hsl(18f, .7f, .48f), { generatedScheme(it, 18f, 175f, 1f) }),
    Graphite("graphite", "Graphite", Color.hsl(220f, .08f, .42f), { generatedScheme(it, 220f, 40f, .25f) });

    fun scheme(dark: Boolean): ColorScheme = build(dark)

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Indigo
    }
}

/**
 * Follows the system's light or dark setting unless [dark] says otherwise (used by debug builds), in
 * the colour theme chosen in Settings.
 */
@Composable
fun AsidesTheme(dark: Boolean = isSystemInDarkTheme(), theme: ColorTheme = AppSettings.colorTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = theme.scheme(dark), content = content)
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
