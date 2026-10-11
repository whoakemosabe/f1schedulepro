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
import app.f1pro.data.GridEntry
import app.f1pro.data.GridKind
import app.f1pro.data.StartingGrid
import app.f1pro.data.Race
import app.f1pro.data.Teams

/**
 * The starting grid, laid out like the real thing: two columns, the right one a slot behind.
 * Before the race it's qualifying's order (provisional: penalties can still move people);
 * once the race results are out it's the grid the race actually started from. Sprint weekends
 * get a Sprint tab. Folds away (remembered). Tap a car for that driver's panel.
 */
@Composable
fun GridCard(state: AppState, race: Race, grids: List<StartingGrid>) {
    if (grids.isEmpty()) return
    var open by remember { mutableStateOf(state.prefs.gridOpen) }
    var pick by remember(grids.size) { mutableStateOf(0) }
    val grid = grids[pick.coerceIn(0, grids.size - 1)]
    val fav = state.favourite
    val order = grid.cars
    val view = androidx.compose.ui.platform.LocalView.current
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
                    CodeChip(q.code, Color(Teams.color(q.constructorId)), large = false)
                    Spacer(Modifier.width(4.dp))
                }
            } else {
                Text(
                    when (grid.kind) { GridKind.Official -> "Official grid"; GridKind.Provisional -> "Provisional"; GridKind.Sprint -> "Sprint grid" },
                    style = inter(11.sp, color = if (grid.kind == GridKind.Provisional) C.Warning else C.TextMuted),
                )
                Spacer(Modifier.width(6.dp))
            }
            Ic(R.drawable.lc_chevron_down, C.TextMuted, 18.dp, Modifier.rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (grids.size > 1) {
                    SlidingSegments(
                        grids.map { if (it.kind == GridKind.Sprint) "Sprint" else "Grand Prix" },
                        pick, Modifier.fillMaxWidth(), height = 34.dp,
                    ) { Haptics.segment(view); pick = it }
                }
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
                Text(
                    when (grid.kind) {
                        GridKind.Provisional -> "Provisional, from qualifying: penalties can still change it"
                        GridKind.Official -> "As the race started, penalties included · PL pit lane"
                        GridKind.Sprint -> "As the sprint started · PL pit lane"
                    } + " · ★ your driver",
                    style = inter(10.sp, color = C.TextMuted),
                )
            }
        }
    }
}

@Composable
private fun GridSlot(state: AppState, q: GridEntry, isFav: Boolean) {
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
            .semantics { contentDescription = "${if (q.slot == "PL") "Pit lane" else q.slot}, ${q.givenName} ${q.familyName}, ${q.constructorName}" }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(q.slot, style = orbitron(11.sp, FontWeight.Bold, C.TextMuted), modifier = Modifier.width(26.dp))
        CodeChip(q.code, color, large = false)
        Spacer(Modifier.width(6.dp))
        Text(
            (if (isFav) "★ " else "") + Teams.shortName(q.constructorName),
            style = inter(11.sp, color = C.TextSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
