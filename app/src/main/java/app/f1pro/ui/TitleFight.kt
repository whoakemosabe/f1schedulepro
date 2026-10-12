package app.f1pro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import app.f1pro.data.DriverStats
import app.f1pro.data.Standings
import app.f1pro.data.Teams
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One line on the title fight chart. */
private class FightLine(
    val code: String,
    val color: Color,
    val dashed: Boolean,
    val points: List<Pair<Int, Double>>,
    val open: () -> Unit,
    /** Why the last point isn't the race results' sum (a penalty), when it isn't. */
    val note: String? = null,
    /** Your driver (or your driver's team): drawn bolder, starred. */
    val fav: Boolean = false,
)

/** The line ends on the standings' total; a difference becomes the line's note. */
private fun snapped(code: String, color: Color, dashed: Boolean, pts: List<Pair<Int, Double>>, official: Double, fav: Boolean = false, open: () -> Unit): FightLine {
    val s = DriverStats.snapTo(pts, official)
    return FightLine(code, color, dashed, s.points, open, DriverStats.adjustmentNote(code, s.adjustment, pts.lastOrNull()?.first), fav)
}

/**
 * The top five's points after every round, drivers or teams, at the top of Standings. Folds
 * away (remembered). Tap the chart to read a round; tap a code to open that driver or team.
 */
@Composable
fun TitleFightCard(state: AppState, st: Standings) {
    val view = androidx.compose.ui.platform.LocalView.current
    var open by remember { mutableStateOf(state.prefs.titleFightOpen) }
    var teams by remember { mutableStateOf(false) }
    // The season data that draws it (one download for every driver; usually saved already).
    LaunchedEffect(st.season) { st.drivers.firstOrNull()?.let { state.loadDriverSeason(st.season, it) } }
    val all = state.seasonAll

    val lines: List<FightLine> = remember(all, st, teams, state.teamColoursVersion, state.favourite) {
        if (all == null) return@remember emptyList()
        if (!teams) {
            val seen = HashSet<String>()
            st.drivers.take(5).mapNotNull { d ->
                val s = DriverStats.idFor(all.entries, d)?.let { all.drivers[it] } ?: return@mapNotNull null
                val color = Color(Teams.color(d.teamId))
                snapped(d.code, color, !seen.add(d.teamId), s.cumulative(), d.points, state.favourite?.matches(d.name, d.surname) == true) { state.sheetDriver = d }
            }
        } else {
            st.teams.take(5).map { t ->
                var pts = DriverStats.teamCumulative(all.drivers.values, t.teamId)
                if (pts.all { it.second == 0.0 }) {
                    // Team ids didn't line up between the feeds: add up its current drivers instead.
                    val mine = st.drivers.filter { it.teamId == t.teamId }
                        .mapNotNull { d -> DriverStats.idFor(all.entries, d)?.let { all.drivers[it] } }
                    val per = sortedMapOf<Int, Double>()
                    for (s in mine) for (r in s.rounds) per[r.round] = (per[r.round] ?: 0.0) + r.allPoints
                    var sum = 0.0
                    pts = per.map { (round, p) -> sum += p; round to sum }
                }
                snapped(Teams.shortName(t.teamName).take(3).uppercase(), Color(Teams.color(t.teamId)), false, pts, t.points, state.favourite?.let { Teams.canonical(it.teamId) } == t.teamId) { state.sheetTeam = t }
            }
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Card)
            .border(1.dp, C.GlassBorder, shape),
    ) {
        // Header: folds the card.
        val turn by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "fold")
        Row(
            Modifier
                .fillMaxWidth()
                .tap {
                    open = !open
                    state.prefs.titleFightOpen = open
                }
                .semantics { contentDescription = if (open) "Title fight chart, tap to fold" else "Title fight chart, tap to open" }
                .heightIn(min = 48.dp)
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_trophy, C.Warning, 14.dp)
            Spacer(Modifier.width(8.dp))
            val off = remember(st.season, state.seasons) { app.f1pro.data.Planner.offSeason(state.seasons, st.season, System.currentTimeMillis()) }
            Text(if (off != null) "FINAL ${st.season} · TOP 5" else "TITLE FIGHT · TOP 5", style = SectionLabel, modifier = Modifier.weight(1f))
            if (!open && lines.isNotEmpty()) {
                // Folded: the leaders' codes still show.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (l in lines.take(3)) CodeChip(l.code, l.color, large = false)
                }
                Spacer(Modifier.width(6.dp))
            }
            Ic(R.drawable.lc_chevron_down, C.TextMuted, 18.dp, Modifier.rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SlidingSegments(listOf("Drivers", "Teams"), if (teams) 1 else 0, Modifier.fillMaxWidth(), height = 34.dp) {
                    Haptics.segment(view)
                    teams = it == 1
                }
                if (lines.isEmpty() || lines.all { it.points.isEmpty() }) {
                    if (all == null) Skeleton(200.dp) else Text("The chart fills in after the first race.", style = inter(12.sp, color = C.TextMuted))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (l in lines) {
                            Box(Modifier.tap(onClick = l.open).semantics { contentDescription = "Open ${l.code}" + if (l.fav) ", your pick" else "" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (l.fav) Text("★", style = inter(12.sp, FontWeight.Bold, C.Warning), modifier = Modifier.padding(end = 2.dp))
                                    CodeChip(l.code, l.color, large = false)
                                }
                            }
                        }
                    }
                    FightChart(lines, key = teams)
                    for (n in lines.mapNotNull { it.note }) Text(n, style = inter(10.sp, color = C.Warning))
                }
            }
        }
    }
}

@Composable
private fun FightChart(lines: List<FightLine>, key: Boolean) {
    val rounds = lines.flatMap { l -> l.points.map { it.first } }.distinct().sorted()
    if (rounds.isEmpty()) return
    val top = lines.maxOf { l -> l.points.maxOfOrNull { it.second } ?: 0.0 }.let { ceil(it / 50.0) * 50.0 }.coerceAtLeast(50.0)
    val grid = C.GlassBorder
    val guide = C.TextMuted
    val ring = C.Card
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    var sel by remember(key, rounds) { mutableStateOf<Int?>(null) }
    val minR = rounds.first()
    val maxR = rounds.last()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(190.dp)) {
            Column(Modifier.fillMaxHeight().width(30.dp), verticalArrangement = Arrangement.SpaceBetween) {
                for (v in listOf(top, top / 2, 0.0)) Text(v.roundToInt().toString(), style = inter(9.sp, color = C.TextMuted))
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = "Points after round $maxR: " + lines.joinToString(", ") { "${it.code} ${Fmt.points(it.points.lastOrNull()?.second ?: 0.0)}" }
                    }
                    .pointerInput(rounds) {
                        detectTapGestures { p ->
                            val pad = 6.dp.toPx()
                            val w = size.width - pad * 2
                            val r = if (maxR == minR) minR else (minR + ((p.x - pad) / w).coerceIn(0f, 1f) * (maxR - minR)).roundToInt()
                            val pick = rounds.minByOrNull { abs(it - r) }
                            sel = if (sel == pick) null else pick
                        }
                    }
            ) {
                val pad = 6.dp.toPx()
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                fun x(round: Int) = pad + if (maxR == minR) w / 2 else (round - minR).toFloat() / (maxR - minR) * w
                fun y(v: Double) = pad + ((top - v) / top).toFloat() * h
                for (v in listOf(top, top / 2, 0.0)) drawLine(grid, Offset(pad, y(v)), Offset(pad + w, y(v)), 1.dp.toPx())
                sel?.let { drawLine(guide.copy(alpha = 0.5f), Offset(x(it), pad), Offset(x(it), pad + h), 1.dp.toPx()) }
                clipRect(right = pad + w * progress.value + 6.dp.toPx()) {
                    for (l in lines) {
                        if (l.points.isEmpty()) continue
                        val path = Path()
                        l.points.forEachIndexed { i, (r, v) -> if (i == 0) path.moveTo(x(r), y(v)) else path.lineTo(x(r), y(v)) }
                        drawPath(
                            path, l.color,
                            style = Stroke(
                                (if (l.fav) 3.6.dp else 2.2.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                                pathEffect = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())) else null,
                            ),
                        )
                        val (lr, lv) = l.points.last()
                        drawCircle(l.color, (if (l.fav) 5f else 3.5f).dp.toPx(), Offset(x(lr), y(lv)))
                    }
                }
                sel?.let { r ->
                    for (l in lines) {
                        val v = l.points.lastOrNull { it.first <= r }?.second ?: continue
                        drawCircle(ring, 5.5.dp.toPx(), Offset(x(r), y(v)))
                        drawCircle(l.color, 4.dp.toPx(), Offset(x(r), y(v)))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            Text("R$minR", style = inter(9.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text("R$maxR", style = inter(9.sp, color = C.TextMuted))
        }
        val pick = sel
        if (pick == null) {
            Text("Tap the chart to compare after any round · tap a code to open it", style = inter(10.sp, color = C.TextMuted))
        } else {
            Text("After R$pick", style = inter(12.sp, FontWeight.SemiBold, C.Text))
            val at = lines.map { l -> l to (l.points.lastOrNull { it.first <= pick }?.second ?: 0.0) }.sortedByDescending { it.second }
            val lead = at.first().second
            for ((l, v) in at) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(l.color))
                    Text("  ${l.code}", style = inter(12.sp, FontWeight.SemiBold, C.Text), modifier = Modifier.width(48.dp))
                    Text(Fmt.points(v) + " pts", style = inter(12.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
                    if (v < lead) Text("−" + Fmt.points(lead - v), style = inter(12.sp, color = C.TextMuted))
                }
            }
        }
    }
}
