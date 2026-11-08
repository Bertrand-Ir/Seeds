package app.seeds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.seeds.R
import app.seeds.data.prefs.Settings

/*
 * SEEDS — brand system
 *
 * Soil   #0E1210  the dark ground everything grows from (dark background, icon)
 * Paper  #F5F3EC  warm unbleached paper (light background)
 * Sprout #B6F36A  the brand: fresh, alive, hopeful (accent in dark, fills in both)
 * Forest #1E7A4A  sprout's grown-up self (accent on light backgrounds)
 * Ember  #FF6B57  drift & overspending — honest, never alarming red
 *
 * Type: Plus Jakarta Sans — geometric, warm, very legible numerals (tabular for timers).
 * Shape: soft 24dp cards, full pills for actions, circles for seeds.
 */

@Immutable
data class SeedsColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val sprout: Color,
    val onSprout: Color,
    val moneyIn: Color,
    val moneyOut: Color,
    val drift: Color,
    val warn: Color,
)

val DarkSeeds = SeedsColors(
    isDark = true,
    bg = Color(0xFF0E1210), surface = Color(0xFF161B18), surfaceHi = Color(0xFF1F2621), line = Color(0xFF2A322D),
    ink = Color(0xFFEEF2EC), inkMuted = Color(0xFF97A39B), inkFaint = Color(0xFF5C6760),
    accent = Color(0xFFB6F36A), onAccent = Color(0xFF0E1210), accentSoft = Color(0x24B6F36A),
    sprout = Color(0xFFB6F36A), onSprout = Color(0xFF0E1210),
    moneyIn = Color(0xFFB6F36A), moneyOut = Color(0xFFFF9F7A), drift = Color(0xFFFF6B57), warn = Color(0xFFFFD166),
)

val LightSeeds = SeedsColors(
    isDark = false,
    bg = Color(0xFFF5F3EC), surface = Color(0xFFFFFFFF), surfaceHi = Color(0xFFEDEAE1), line = Color(0xFFE2DED3),
    ink = Color(0xFF111613), inkMuted = Color(0xFF5F6A63), inkFaint = Color(0xFFA3ABA5),
    accent = Color(0xFF1E7A4A), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFDFF3D0),
    sprout = Color(0xFFB6F36A), onSprout = Color(0xFF0E1210),
    moneyIn = Color(0xFF1E7A4A), moneyOut = Color(0xFFC2471F), drift = Color(0xFFD93F2E), warn = Color(0xFFA87300),
)

/** Eight seed colors. `fill` reads with dark ink on top in both themes; `deep` is for strokes/text on light. */
object SeedPalette {
    private val fills = longArrayOf(0xFFB6F36A, 0xFF8CCBFF, 0xFFFFD166, 0xFFFF9F7A, 0xFFC3B1FF, 0xFF6FE3C6, 0xFFFF9EC0, 0xFFE9D8A6)
    private val deeps = longArrayOf(0xFF3E7A12, 0xFF1F6FB2, 0xFFA87300, 0xFFB8461F, 0xFF5B45C2, 0xFF12806A, 0xFFB8336A, 0xFF8A7340)
    val names = listOf("Sprout", "Sky", "Sun", "Clay", "Lilac", "Mint", "Rose", "Sand")
    val size get() = fills.size

    fun fill(i: Int): Int = fills[Math.floorMod(i, fills.size)].toInt()
    fun deep(i: Int): Int = deeps[Math.floorMod(i, deeps.size)].toInt()
    fun fillColor(i: Int) = Color(fill(i))
    fun deepColor(i: Int) = Color(deep(i))
}

/** The color a seed uses for rings and strokes in the current theme. */
@Composable
fun seedTone(i: Int): Color = if (LocalSeedsColors.current.isDark) SeedPalette.fillColor(i) else SeedPalette.deepColor(i)

val Ink = Color(0xFF0E1210)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

object SeedsType {
    val display = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum")
    val timer = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum")
    val headline = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp)
    val title = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp)
    val subtitle = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
    val label = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum")
    val caption = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum")
    val overline = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp)
    val number = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum")
}

object Dimens {
    val gutter = 20.dp
    val card = 24.dp
    val gap = 12.dp
}

val CardShape = RoundedCornerShape(Dimens.card)
val InnerShape = RoundedCornerShape(16.dp)
val Pill = RoundedCornerShape(percent = 50)

val LocalSeedsColors = staticCompositionLocalOf { DarkSeeds }

object SeedsTheme {
    val colors: SeedsColors @Composable get() = LocalSeedsColors.current
}

@Composable
fun SeedsTheme(themeMode: Int = Settings.THEME_SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        Settings.THEME_LIGHT -> false
        Settings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkSeeds else LightSeeds
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.ink,
        secondary = c.accent, background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surfaceHi, onSurfaceVariant = c.inkMuted, surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHi,
        surfaceContainerHighest = c.surfaceHi, surfaceContainerLow = c.surface, outline = c.line, outlineVariant = c.line,
        error = c.drift, tertiary = c.warn,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.ink,
        secondary = c.accent, background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surfaceHi, onSurfaceVariant = c.inkMuted, surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceHi,
        surfaceContainerHighest = c.surfaceHi, surfaceContainerLow = c.surface, outline = c.line, outlineVariant = c.line,
        error = c.drift, tertiary = c.warn,
    )
    val typography = Typography(
        displayLarge = SeedsType.display, headlineMedium = SeedsType.headline, headlineSmall = SeedsType.title,
        titleLarge = SeedsType.title, titleMedium = SeedsType.subtitle, titleSmall = SeedsType.label,
        bodyLarge = SeedsType.body, bodyMedium = SeedsType.body, bodySmall = SeedsType.caption,
        labelLarge = SeedsType.label, labelMedium = SeedsType.caption, labelSmall = SeedsType.overline,
    )
    CompositionLocalProvider(LocalSeedsColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(small = InnerShape, medium = InnerShape, large = CardShape, extraLarge = RoundedCornerShape(32.dp)),
            content = content,
        )
    }
}

fun Color.argb(): Int = toArgb()
