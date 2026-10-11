package app.f1pro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.R
import app.f1pro.data.QualiResult
import app.f1pro.data.Race
import app.f1pro.data.Teams

/**
 * The grid as qualifying set it, laid out like the real thing: two columns, the right one a
 * slot behind. Folds away (remembered). Penalties aren't known here, so it says "from
 * qualifying". Tap a car for that driver's panel.
 */
@Composable
fun GridCard(state: AppState, race: Race, quali: List<QualiResult>) {
    if (quali.isEmpty()) return
    var open by remember { mutableStateOf(state.prefs.gridOpen) }
    val fav = state.favourite
    val order = quali.filter { it.position != null }.sortedBy { it.position }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(C.Card).border(1.dp, C.GlassBorder, shape)) {
        val turn by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "fold")
        Row(
            Modifier
                .fillMaxWidth()
                .tap {
                    open = !open
                    state.prefs.gridOpen = open
                }
                .semantics { contentDescription = if (open) "Starting grid, tap to fold" else "Starting grid, tap to open" }
                .heightIn(min = 48.dp)
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_flag, C.Highlight, 14.dp)
            Spacer(Modifier.width(8.dp))
            Text("STARTING GRID", style = SectionLabel, modifier = Modifier.weight(1f))
            if (!open) {
                order.take(2).forEach { q ->
                    CodeChip(q.code.ifBlank { q.familyName.take(3).uppercase() }, Color(Teams.color(q.constructorId)), large = false)
                    Spacer(Modifier.width(4.dp))
                }
            } else {
                Text("from qualifying", style = inter(11.sp, color = C.TextMuted))
                Spacer(Modifier.width(6.dp))
            }
            Ic(R.drawable.lc_chevron_down, C.TextMuted, 18.dp, Modifier.rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Pairs of slots: P1 left, P2 right and a little further back, and so on.
                for (pair in order.chunked(2)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEachIndexed { i, q ->
                            Box(Modifier.weight(1f).padding(top = if (i == 1) 20.dp else 0.dp, bottom = if (i == 0) 20.dp else 0.dp)) {
                                GridSlot(state, q, fav?.matches(q.givenName, q.familyName) == true)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text("Penalties can change the final grid · ★ your driver", style = inter(10.sp, color = C.TextMuted))
            }
        }
    }
}

@Composable
private fun GridSlot(state: AppState, q: QualiResult, isFav: Boolean) {
    val color = Color(Teams.color(q.constructorId))
    val line = C.TextSecondary
    val driver = state.standings?.drivers?.firstOrNull { it.code == q.code || app.f1pro.data.Favourite(it.name, it.surname, "", "").matches(q.givenName, q.familyName) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(bottomEnd = 8.dp))
            .background(if (isFav) color.copy(alpha = 0.16f) else C.SurfaceLight.copy(alpha = 0.6f))
            // The painted starting box: a line across the front and down the left side.
            .drawBehind {
                drawLine(line.copy(alpha = 0.7f), Offset(0f, 1f), Offset(size.width, 1f), 2.dp.toPx())
                drawLine(line.copy(alpha = 0.35f), Offset(1f, 0f), Offset(1f, size.height), 2.dp.toPx())
            }
            .then(if (isFav) Modifier.border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(bottomEnd = 8.dp)) else Modifier)
            .tap(enabled = driver != null) { driver?.let { state.sheetDriver = it } }
            .semantics { contentDescription = "P${q.position}, ${q.givenName} ${q.familyName}, ${q.constructorName}" }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("P${q.position}", style = orbitron(11.sp, FontWeight.Bold, C.TextMuted), modifier = Modifier.width(26.dp))
        CodeChip(q.code.ifBlank { q.familyName.take(3).uppercase() }, color, large = false)
        Spacer(Modifier.width(6.dp))
        Text(
            (if (isFav) "★ " else "") + Teams.shortName(q.constructorName),
            style = inter(11.sp, color = C.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
