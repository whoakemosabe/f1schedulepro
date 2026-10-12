package app.f1pro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.f1pro.R

/**
 * "What's new in v2.0.68": opens once when a new version is found, with its highlights, so
 * the update is something to look forward to rather than a chore. Update now starts the
 * download (the banner shows progress and installs); Later leaves the banner waiting.
 */
@Composable
fun WhatsNewDialog(version: String, notes: List<String>, behind: Int, onUpdate: () -> Unit, onLater: () -> Unit) {
    val view = LocalView.current
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(20.dp)
        // The card rises in, then the lines arrive one after another.
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 24.dp.toPx()
                    scaleX = 0.96f + 0.04f * enter.value
                    scaleY = scaleX
                }
                .clip(shape)
                .background(C.Card)
                .border(1.dp, C.Accent.copy(alpha = 0.45f), shape),
        ) {
            // Header: a red racing stripe fading out, the version in big type.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(C.Accent.copy(alpha = 0.28f), Color.Transparent)))
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(C.Accent),
                        contentAlignment = Alignment.Center,
                    ) { Ic(R.drawable.lc_zap, Color.White, 16.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text("UPDATE READY", style = inter(11.sp, FontWeight.Bold, C.AccentText, 0.14f), modifier = Modifier.weight(1f))
                    if (behind > 1) {
                        // Skipped a few: say so, and the list below covers all of them.
                        Text(
                            (if (behind >= 30) "30+" else "$behind") + " updates behind",
                            style = inter(11.sp, FontWeight.SemiBold, C.Warning),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(C.Warning.copy(alpha = 0.12f))
                                .border(1.dp, C.Warning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(if (behind > 1) "What's new since your version" else "What's new in v$version", style = orbitron(20.sp, FontWeight.Bold, C.Text))
                if (behind > 1) Text("Installing v$version brings you fully up to date.", style = inter(12.sp, color = C.TextSecondary))
            }

            Column(
                Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                notes.forEachIndexed { i, n -> Highlight(n, i) }
            }

            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.Accent)
                        .tap { Haptics.confirm(view); onUpdate() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.lc_download, Color.White, 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Update now", style = inter(15.sp, FontWeight.SemiBold, Color.White))
                    }
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).tap { onLater() },
                    contentAlignment = Alignment.Center,
                ) { Text("Later", style = inter(14.sp, FontWeight.Medium, C.TextSecondary)) }
            }
        }
    }
}

@Composable
private fun Highlight(text: String, index: Int) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(280, delayMillis = 180 + index * 70, easing = FastOutSlowInEasing)) }
    Row(
        Modifier.graphicsLayer {
            alpha = a.value
            translationX = (1f - a.value) * 16.dp.toPx()
        },
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 6.dp).size(width = 10.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(C.Accent))
        Spacer(Modifier.width(12.dp))
        Text(text, style = inter(14.sp, color = C.Text).copy(lineHeight = 20.sp))
    }
}
