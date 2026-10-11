package app.f1pro.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/*
 * Liquid glass on Kyant's Backdrop 2.x, the same library and recipe as Afli. The page behind is
 * recorded with layerBackdrop and redrawn through a short chain: a touch more colour, a frost,
 * and the library's lens, which bends a rounded edge with depth and colour fringing. The lens
 * and the dissolve need Android 13; on older phones Kyant skips them and the tint is made
 * stronger so text stays readable.
 *
 * Two recordings: [LocalPageBackdrop] is the background plus the scrolling page (for the header
 * and the floating tab bar, which float over content), [LocalBgBackdrop] is the background
 * alone (for controls that sit inside the page and must not refract themselves).
 */

val glassFull: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
val LocalBgBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Space the floating header (top) and tab bar (bottom) take, for pages to pad their content. */
val LocalChrome = staticCompositionLocalOf { androidx.compose.foundation.layout.PaddingValues(0.dp) }

/** Glass rim: a crisp 1 dp edge, light on dark and dark on light. */
val Rim: Color get() = if (C.dark) Color(0x2EFFFFFF) else Color(0x240C0F1A)

/** Fades the frosted copy out over the pane's last stretch, (1 - x)², so there's no edge. */
private const val Dissolve = """
uniform shader content;
uniform float2 offset;
uniform float fadeTop;
uniform float fadeBottom;

half4 main(float2 coord) {
    float y = coord.y + offset.y;
    float x = clamp((y - fadeTop) / max(fadeBottom - fadeTop, 1.0), 0.0, 1.0);
    float a = (1.0 - x) * (1.0 - x);
    return content.eval(coord) * half(a);
}
"""

fun BackdropEffectScope.dissolve(fadeTop: Float, fadeBottom: Float) {
    val pad = padding
    runtimeShaderEffect("F1Dissolve", Dissolve, "content") {
        setFloatUniform("offset", -pad, -pad)
        setFloatUniform("fadeTop", fadeTop)
        setFloatUniform("fadeBottom", fadeBottom)
    }
}

/** Frosted glass for floating cards (the update banner, the crash card). */
fun Modifier.glassFloat(
    backdrop: LayerBackdrop,
    shape: CornerBasedShape = RoundedCornerShape(16.dp),
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        colorControls(saturation = 1.2f)
        blur(12.dp.toPx())
        lens(18.dp.toPx(), 30.dp.toPx(), depthEffect = true, chromaticAberration = true)
    },
    highlight = { Highlight(width = 1.dp, style = HighlightStyle.Default(angle = 45f)) },
    shadow = { Shadow(radius = 20.dp, color = Color.Black.copy(alpha = if (C.dark) 0.3f else 0.12f)) },
    onDrawSurface = {
        val tint = if (C.dark) Color(0xB30F1325) else Color(0xCCFFFFFF)
        drawRect(if (glassFull) tint else tint.copy(alpha = 0.94f))
        drawRect(Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x03FFFFFF))))
    },
)

/** Barely frosted glass for the tab bar, buttons and pills: mostly lens, like iOS 26 controls. */
fun Modifier.glassControl(
    backdrop: LayerBackdrop,
    shape: CornerBasedShape = RoundedCornerShape(50),
    press: () -> Float = { 0f },
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        colorControls(saturation = 1.3f)
        blur(3.dp.toPx())
        val p = press()
        lens((12.dp + 6.dp * p).toPx(), (24.dp + 20.dp * p).toPx(), depthEffect = true, chromaticAberration = true)
    },
    highlight = { Highlight(style = HighlightStyle.Default(angle = 45f)) },
    shadow = { Shadow(radius = 16.dp, color = Color.Black.copy(alpha = if (C.dark) 0.28f else 0.12f)) },
    onDrawSurface = {
        val tint = if (C.dark) Color(0x590C0F1A) else Color(0x80FFFFFF)
        drawRect(if (glassFull) tint else (if (C.dark) Color(0xE00C0F1A) else Color(0xEBFFFFFF)))
    },
)

/**
 * The frosted band behind the status bar and header: no lens, dissolves at the bottom. The pane
 * reaches [margin] past the top and sides and the page is mirrored into that margin, so the blur
 * never pulls in empty space at the edges (Afli's fix for a clear strip along the very top).
 */
@Composable
fun GlassHeader(backdrop: LayerBackdrop, height: Dp, modifier: Modifier = Modifier, fade: Dp = 24.dp, margin: Dp = 40.dp) {
    val density = LocalDensity.current
    val fadePx = with(density) { fade.toPx() }
    val m = with(density) { margin.roundToPx() }
    val screenW = remember { floatArrayOf(0f) }
    Box(modifier.fillMaxWidth().height(height)) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    screenW[0] = constraints.maxWidth.toFloat()
                    val p = measurable.measure(Constraints.fixed(constraints.maxWidth + m * 2, constraints.maxHeight + m))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place(-m, -m) }
                }
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        colorControls(saturation = 1.2f)
                        blur(18.dp.toPx())
                        dissolve(size.height - fadePx, size.height)
                    },
                    onDrawBackdrop = { drawPage ->
                        drawPage()
                        val mf = m.toFloat()
                        val right = mf + screenW[0]
                        withTransform({ scale(-1f, 1f, pivot = Offset(mf, 0f)) }) { drawPage() }
                        withTransform({ scale(-1f, 1f, pivot = Offset(right, 0f)) }) { drawPage() }
                        withTransform({ scale(1f, -1f, pivot = Offset(0f, mf)) }) { drawPage() }
                    },
                    onDrawSurface = {
                        val h = size.height
                        val k = ((h - fadePx) / h).coerceIn(0f, 1f)
                        val tint = when {
                            C.dark && glassFull -> Color(0x660C0F1A)
                            C.dark -> Color(0xE00C0F1A)
                            glassFull -> Color(0x8CECEFF6)
                            else -> Color(0xEBECEFF6)
                        }
                        drawRect(Brush.verticalGradient(0f to tint, k to tint.copy(alpha = tint.alpha * 0.6f), 1f to Color.Transparent))
                    },
                ),
        )
    }
}

/**
 * What the glass refracts: the page colour with two soft glows, F1 red up top and timing-screen
 * cyan low down, plus faint speed lines. Static, so it costs nothing while idle.
 */
@Composable
fun F1Background(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(C.Bg)
        val w = size.width
        val h = size.height
        val strength = if (C.dark) 1f else 0.55f
        drawCircle(
            Brush.radialGradient(
                listOf(C.Accent.copy(alpha = 0.24f * strength), Color.Transparent),
                center = Offset(w * 0.1f, h * 0.06f),
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = Offset(w * 0.1f, h * 0.06f),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF00D4FF).copy(alpha = 0.14f * strength), Color.Transparent),
                center = Offset(w * 0.95f, h * 0.78f),
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = Offset(w * 0.95f, h * 0.78f),
        )
        // Speed lines: long faint diagonals.
        val line = (if (C.dark) Color.White else Color(0xFF0C0F1A)).copy(alpha = 0.025f)
        var x = -h
        while (x < w) {
            drawLine(line, Offset(x, h), Offset(x + h * 0.55f, 0f), strokeWidth = 1.5f)
            x += 56f * density
        }
    }
}

/** A small glass button over the page background, with a flat fallback when there's no backdrop. */
@Composable
fun Modifier.glassButton(shape: CornerBasedShape = RoundedCornerShape(50)): Modifier {
    val bg = LocalBgBackdrop.current
    val base = if (bg != null) this.glassControl(bg, shape) else this.clip(shape).background(C.SurfaceLight)
    return base.clip(shape).border(1.dp, Rim, shape)
}
