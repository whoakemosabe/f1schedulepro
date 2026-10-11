package app.f1pro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

/** The PWA's design tokens. */
object C {
    val Bg = Color(0xFF0C0F1A)
    val Surface = Color(0xFF131830)
    val SurfaceLight = Color(0xFF1A2040)
    val SurfaceLighter = Color(0xFF222850)
    val Border = Color(0xFF252D55)
    val BorderLight = Color(0xFF303A6A)
    val Accent = Color(0xFFE10600)
    val AccentGlow = Color(0xFFFF2D2D)
    val Highlight = Color(0xFF00D4FF)
    val Text = Color(0xFFF0F2FF)
    val TextSecondary = Color(0xFFA0A8C8)
    val TextMuted = Color(0xFF6670A0)
    val Success = Color(0xFF00E676)
    val Warning = Color(0xFFFFAB00)
    val Live = Color(0xFFE10600)

    /** "glass-light" flattened onto the background. */
    val Card = Color(0xFF0F1325)
    val GlassBorder = Color(0x14FFFFFF)
    val Bar = Color(0xF20C0F1C)
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
val SectionLabel = orbitron(11.sp, FontWeight.SemiBold, C.TextMuted, 0.1f)

val LocalFmt = staticCompositionLocalOf { Fmt(ZoneId.systemDefault(), false) }

/** Colours for a session name: race red, sprint amber, qualifying cyan, practice soft cyan. */
fun sessionColor(name: String): Color = when {
    name == "Race" -> C.Live
    name.contains("Sprint") -> C.Warning
    name == "Qualifying" -> C.Highlight
    else -> C.Highlight.copy(alpha = 0.7f)
}

@Composable
fun F1Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
