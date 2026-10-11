package app.f1pro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import java.time.ZoneId

/**
 * The PWA's design tokens, with a light counterpart. [dark] is Compose state, so every screen
 * redraws when the theme changes.
 */
object C {
    var dark by mutableStateOf(true)

    private fun pick(d: Long, l: Long) = Color(if (dark) d else l)

    // Every text colour clears 4.5:1 on cards in both themes (checked with the WCAG formula).
    val Bg get() = pick(0xFF0C0F1A, 0xFFECEFF6)
    val Surface get() = pick(0xFF131830, 0xFFFFFFFF)
    val SurfaceLight get() = pick(0xFF1A2040, 0xFFE6E9F2)
    val SurfaceLighter get() = pick(0xFF222850, 0xFFDDE1EE)
    val Border get() = pick(0xFF252D55, 0xFFD3D8E8)
    val BorderLight get() = pick(0xFF303A6A, 0xFFC0C7DC)

    /** F1 red for fills: pills, tiles, bars, the tab indicator. */
    val Accent get() = Color(0xFFE10600)
    val AccentGlow get() = Color(0xFFFF2D2D)

    /** F1 red when it's text or a thin icon (the fill red is too dark to read on navy). */
    val AccentText get() = pick(0xFFFF4A44, 0xFFC40500)

    /** Something on track right now: brighter than the brand red so it stands apart. */
    val Live get() = pick(0xFFFF453A, 0xFFD70015)

    val Highlight get() = pick(0xFF00D4FF, 0xFF006E8F)
    val Text get() = pick(0xFFF0F2FF, 0xFF0C0F1A)
    val TextSecondary get() = pick(0xFFA0A8C8, 0xFF434B6B)
    val TextMuted get() = pick(0xFF8A93BD, 0xFF5F6788)
    val Success get() = pick(0xFF00E676, 0xFF007638)
    val Warning get() = pick(0xFFFFAB00, 0xFF965800)

    /** "glass-light" flattened onto the background. */
    val Card get() = pick(0xFF0F1325, 0xFFFFFFFF)
    val GlassBorder get() = pick(0x14FFFFFF, 0x170C0F1A)
    val Bar get() = pick(0xF20C0F1C, 0xF5FFFFFF)
}

/** WCAG relative luminance, for picking readable variants of team colours. */
fun luminance(c: Color): Float {
    fun ch(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * ch(c.red) + 0.7152f * ch(c.green) + 0.0722f * ch(c.blue)
}

fun contrast(a: Color, b: Color): Float {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * A team's colour made readable as text on cards: lightened on dark, darkened on light, only
 * as far as needed for 4.5:1, so it still looks like the team. Stripes and bars keep the real one.
 */
fun readable(c: Color, on: Color = C.Card): Color {
    if (contrast(c, on) >= 4.5f) return c
    val toward = if (C.dark) Color.White else Color.Black
    var t = 0.05f
    while (t < 1f) {
        val m = androidx.compose.ui.graphics.lerp(c, toward, t)
        if (contrast(m, on) >= 4.5f) return m
        t += 0.05f
    }
    return toward
}

val Orbitron = FontFamily(
    Font(R.font.orbitron_regular, FontWeight.Normal),
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_semibold, FontWeight.SemiBold),
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_extrabold, FontWeight.ExtraBold),
    Font(R.font.orbitron_black, FontWeight.Black),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Inter text; tracking is in em like Tailwind's tracking-wider (0.05em) / widest (0.1em). */
fun inter(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = C.Text, tracking: Float = 0f) =
    TextStyle(fontFamily = Inter, fontSize = size, fontWeight = weight, color = color, letterSpacing = tracking.em,
        fontFeatureSettings = "tnum")

fun orbitron(size: TextUnit, weight: FontWeight = FontWeight.Bold, color: Color = C.Text, tracking: Float = 0f) =
    TextStyle(fontFamily = Orbitron, fontSize = size, fontWeight = weight, color = color, letterSpacing = tracking.em)

/** Small uppercase section label, like the PWA's "font-display text-xs tracking-widest". */
val SectionLabel: TextStyle get() = orbitron(11.sp, FontWeight.SemiBold, C.TextMuted, 0.1f)

val LocalFmt = staticCompositionLocalOf { Fmt(ZoneId.systemDefault(), false) }

/** Colours for a session name: race red, sprint amber, qualifying cyan, practice soft cyan. */
fun sessionColor(name: String): Color = when {
    name == "Race" -> C.AccentText
    name.contains("Sprint") -> C.Warning
    name == "Qualifying" -> C.Highlight
    else -> C.Highlight.copy(alpha = 0.7f)
}

@Composable
fun F1Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = (if (C.dark) darkColorScheme() else androidx.compose.material3.lightColorScheme()).copy(
            primary = C.Accent,
            onPrimary = Color.White,
            secondary = C.Highlight,
            background = C.Bg,
            surface = C.Surface,
            onSurface = C.Text,
            onBackground = C.Text,
            surfaceVariant = C.SurfaceLight,
            onSurfaceVariant = C.TextSecondary,
            outline = C.Border,
        ),
        content = content,
    )
}
