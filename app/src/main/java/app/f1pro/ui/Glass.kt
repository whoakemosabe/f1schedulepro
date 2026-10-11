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
import androidx.compose.ui.input.pointer.pointerInput
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
 * Frosted glass on Kyant's Backdrop 2.x, the same library as Afli. The page is recorded with
 * layerBackdrop ([LocalPageBackdrop]) and redrawn blurred behind the header and the tab bar, so
 * content scrolls underneath both. The dissolve needs Android 13; on older phones Kyant skips
 * it and the tint is made stronger so text stays readable.
 */

val glassFull: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Space the floating header (top) and tab bar (bottom) take, for pages to pad their content. */
val LocalChrome = staticCompositionLocalOf { androidx.compose.foundation.layout.PaddingValues(0.dp) }

/**
 * Makes a floating bar solid to touch: taps and drags on it stop here instead of reaching the
 * page underneath. Buttons inside it still work, since they sit inside this node.
 */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { if (!it.isConsumed) it.consume() }
    }
}

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

/**
 * The frosted band behind the status bar and header: no lens, dissolves at the bottom. The pane
 * reaches [margin] past the top and sides and the page is mirrored into that margin, so the blur
 * never pulls in empty space at the edges (Afli's fix for a clear strip along the very top).
 */
@Composable
fun GlassHeader(backdrop: LayerBackdrop, height: Dp, modifier: Modifier = Modifier, fade: Dp = 24.dp, margin: Dp = 40.dp) {
    // Before Android 13 the blur can't fade out and costs more per frame: a plain tinted band
    // instead (same look at rest, no per-frame redraw of the page behind).
    if (!glassFull) {
        val tint = if (C.dark) Color(0xF00C0F1A) else Color(0xF5ECEFF6)
        Box(
            modifier.fillMaxWidth().height(height).background(
                Brush.verticalGradient(0f to tint, 0.75f to tint, 1f to Color.Transparent)
            )
        )
        return
    }
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

/** The page colour behind everything (what the header and tab bar blur). */
@Composable
fun F1Background(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawRect(C.Bg) }
}

/**
 * The frosted band behind the tab bar: the full width of the screen, blurred page underneath.
 * Like [GlassHeader] it reaches [margin] past the sides and bottom with the page mirrored into
 * that margin, so the blur doesn't pull in empty space along the edges.
 */
@Composable
fun GlassBar(backdrop: LayerBackdrop, modifier: Modifier = Modifier, margin: Dp = 40.dp) {
    if (!glassFull) {
        // Older phones: solid bar, no live blur (see GlassHeader).
        Box(modifier.background(if (C.dark) Color(0xF50C0F1A) else Color(0xF7F4F6FB)))
        return
    }
    val density = LocalDensity.current
    val m = with(density) { margin.roundToPx() }
    val box = remember { floatArrayOf(0f, 0f) }
    Box(
        modifier
            .layout { measurable, constraints ->
                box[0] = constraints.maxWidth.toFloat()
                box[1] = constraints.maxHeight.toFloat()
                val p = measurable.measure(Constraints.fixed(constraints.maxWidth + m * 2, constraints.maxHeight + m))
                layout(constraints.maxWidth, constraints.maxHeight) { p.place(-m, 0) }
            }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(0.dp) },
                effects = {
                    colorControls(saturation = 1.2f)
                    blur(18.dp.toPx())
                },
                onDrawBackdrop = { drawPage ->
                    drawPage()
                    val mf = m.toFloat()
                    val right = mf + box[0]
                    withTransform({ scale(-1f, 1f, pivot = Offset(mf, 0f)) }) { drawPage() }
                    withTransform({ scale(-1f, 1f, pivot = Offset(right, 0f)) }) { drawPage() }
                    withTransform({ scale(1f, -1f, pivot = Offset(0f, box[1])) }) { drawPage() }
                },
                onDrawSurface = {
                    drawRect(
                        when {
                            C.dark && glassFull -> Color(0x8C0C0F1A)
                            C.dark -> Color(0xEB0C0F1A)
                            glassFull -> Color(0xA6F4F6FB)
                            else -> Color(0xF0F4F6FB)
                        }
                    )
                },
            ),
    )
}
