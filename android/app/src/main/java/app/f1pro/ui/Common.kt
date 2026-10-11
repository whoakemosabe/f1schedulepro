package app.f1pro.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** A Lucide icon from res/drawable, tinted. */
@Composable
fun Ic(@DrawableRes id: Int, color: Color, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = null, tint = color, modifier = modifier.size(size))
}

/** Wall-clock time that updates every [periodMs], aligned to the period. */
@Composable
fun rememberNow(periodMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            val t = System.currentTimeMillis()
            now = t
            delay(periodMs - (t % periodMs))
        }
    }
    return now
}

val CardShape = RoundedCornerShape(12.dp)

/** The PWA's "glass-light rounded-xl" card, with an optional coloured left edge. */
fun Modifier.glassCard(edge: Color? = null, shape: Shape = CardShape, fill: Color = C.Card): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, C.GlassBorder, shape)
    .then(
        if (edge != null) Modifier.drawBehind {
            drawRect(edge, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
        } else Modifier
    )

/** Card press feedback: a tiny scale-down while pressed. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.985f else 1f, tween(80), label = "press")
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Small pill badge: uppercase, tinted background and border. */
@Composable
fun Badge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    bgAlpha: Float = 0.1f,
    borderAlpha: Float = 0.15f,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = bgAlpha))
            .border(1.dp, color.copy(alpha = borderAlpha), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        leading?.invoke()
        if (icon != null) Ic(icon, color, 11.dp)
        Text(text, style = inter(11.sp, androidx.compose.ui.text.font.FontWeight.Bold, color, 0.05f))
    }
}

/** The pulsing red dot. */
@Composable
fun LiveDot(dot: Dp = 8.dp, color: Color = C.Live) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(1f, 0.6f, infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "r")
    Box(
        Modifier
            .size(dot)
            .drawBehind {
                val r = dot.toPx() / 2
                drawCircle(color.copy(alpha = 0.4f * (1 - ring)), radius = r + ring * 5.dp.toPx(), center = Offset(r, r))
                drawCircle(color.copy(alpha = a), radius = r, center = Offset(r, r))
            }
    )
}

/** Digits that flip (slide + fade) when they change, each in a fixed-width slot. */
@Composable
fun FlipText(text: String, style: TextStyle, slot: Float = 0.84f) {
    val width = with(LocalDensity.current) { (style.fontSize * slot).toDp() }
    Row {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (slideInVertically(tween(300)) { -it / 2 } + fadeIn(tween(300))) togetherWith
                        (slideOutVertically(tween(300)) { it / 2 } + fadeOut(tween(300))) using SizeTransform(clip = true)
                },
                label = "digit$i",
            ) { c ->
                val w = if (c.isDigit()) width else Dp.Unspecified
                Text(
                    c.toString(),
                    style = style,
                    textAlign = TextAlign.Center,
                    modifier = if (w != Dp.Unspecified) Modifier.width(w) else Modifier,
                )
            }
        }
    }
}

/** A hairline that fades out at both ends. */
@Composable
fun FadeLine(color: Color = C.BorderLight, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent)))
    )
}

/** Shimmering placeholder block. */
@Composable
fun Skeleton(height: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CardShape)
            .drawBehind {
                val w = size.width
                drawRect(
                    Brush.linearGradient(
                        listOf(C.Surface, C.SurfaceLight, C.Surface),
                        start = Offset(w * (x - 0.5f), 0f),
                        end = Offset(w * (x + 0.5f), 0f),
                    )
                )
            }
    )
}

/** A small rounded chip-style button. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconColor: Color = C.TextMuted,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) C.Accent else C.SurfaceLight)
            .border(1.dp, if (selected) C.Accent else C.GlassBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Ic(icon, if (selected) Color.White else iconColor, 12.dp)
        Text(text, style = inter(12.sp, androidx.compose.ui.text.font.FontWeight.SemiBold, if (selected) Color.White else C.TextSecondary))
    }
}

@Composable
fun RowScope.Dot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Opens a link in a Chrome custom tab styled like the app, or any browser. */
fun openUrl(context: Context, url: String) {
    try {
        val dark = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(C.Bg.toArgb())
            .setNavigationBarColor(C.Bg.toArgb())
            .build()
        CustomTabsIntent.Builder()
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setDefaultColorSchemeParams(dark)
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) { }
    } catch (e: Exception) { }
}
