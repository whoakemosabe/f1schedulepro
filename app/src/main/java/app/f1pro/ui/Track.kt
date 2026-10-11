package app.f1pro.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.R
import app.f1pro.data.TrackShape
import app.f1pro.data.Tracks

/** Loads the bundled outlines once. */
object TrackStore {
    @Volatile private var all: Map<String, TrackShape>? = null

    fun get(context: Context, circuitId: String): TrackShape? {
        val m = all ?: try {
            Tracks.parse(context.assets.open("tracks.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            emptyMap()
        }.also { all = it }
        return m[circuitId]
    }
}

/** The circuit drawn as a line, on the race page. Nothing is shown for a track we don't have. */
@Composable
fun TrackCard(circuitId: String, circuitName: String) {
    val context = LocalContext.current
    val track = remember(circuitId) { TrackStore.get(context, circuitId) } ?: return
    Column(
        Modifier.fillMaxWidth().glassCard().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.lc_map_pin, C.Highlight, 14.dp)
            Spacer(Modifier.width(8.dp))
            Text("THE TRACK", style = SectionLabel)
            Spacer(Modifier.weight(1f))
            Text(circuitName, style = inter(10.sp, color = C.TextMuted), maxLines = 1)
        }
        TrackDrawing(track, Modifier.fillMaxWidth().aspectRatio((track.w / track.h).coerceIn(1.15f, 2f)).padding(vertical = 4.dp))
        Text("Outline is approximate · bacinger/f1-circuits", style = inter(10.sp, color = C.TextMuted))
    }
}

@Composable
fun TrackDrawing(track: TrackShape, modifier: Modifier = Modifier) {
    val glow = C.Accent.copy(alpha = if (C.dark) 0.28f else 0.18f)
    val line = C.Text
    Canvas(modifier) {
        val pad = 10.dp.toPx()
        val scale = minOf((size.width - pad * 2) / track.w, (size.height - pad * 2) / track.h)
        val ox = (size.width - track.w * scale) / 2f
        val oy = (size.height - track.h * scale) / 2f
        fun at(i: Int) = Offset(ox + track.points[i * 2] * scale, oy + track.points[i * 2 + 1] * scale)
        val path = Path().apply {
            val first = at(0)
            moveTo(first.x, first.y)
            for (i in 1 until track.size) at(i).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, glow, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
