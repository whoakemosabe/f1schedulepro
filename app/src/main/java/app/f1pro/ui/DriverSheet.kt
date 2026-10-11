package app.f1pro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import app.f1pro.data.Countries
import app.f1pro.data.DriverSeason
import app.f1pro.data.DriverStanding
import app.f1pro.data.DriverStats
import app.f1pro.data.Standings
import app.f1pro.data.TeamStanding
import app.f1pro.data.Teams

/*
 * Tapping a driver (or a team) on Standings opens a panel from the bottom instead of the F1
 * website: numbers, the season round by round, and the teammate head-to-head. The website is
 * still one tap away at the bottom.
 */

@Composable
fun StandingsSheets(state: AppState, st: Standings) {
    val d = state.sheetDriver
    val t = state.sheetTeam
    when {
        d != null -> Sheet(onDismiss = { state.sheetDriver = null }) { DriverPanel(state, st, d) }
        t != null -> Sheet(onDismiss = { state.sheetTeam = null }) { TeamPanel(state, st, t) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

private fun teammateOf(st: Standings, d: DriverStanding): DriverStanding? =
    st.drivers.filter { it.teamId == d.teamId && !(it.name == d.name && it.surname == d.surname) }.maxByOrNull { it.points }

@Composable
private fun ColumnScope.DriverPanel(state: AppState, st: Standings, d: DriverStanding) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val color = Color(Teams.color(d.teamId))
    val mate = teammateOf(st, d)
    LaunchedEffect(d) { state.loadDriverSeason(st.season, d) }
    LaunchedEffect(mate) { mate?.let { state.loadDriverSeason(st.season, it) } }
    val season = state.driverSeasons[state.driverKey(st.season, d)]
    val mateSeason = mate?.let { state.driverSeasons[state.driverKey(st.season, it)] }
    val loading = state.driverSeasonLoading[state.driverKey(st.season, d)] == true

    // Who
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 48.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, style = inter(14.sp, color = C.TextSecondary), maxLines = 1)
            Text(d.surname.uppercase(), style = orbitron(20.sp, FontWeight.Bold, C.Text, 0.03f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Flag(Countries.code(d.nationality), 18.dp, 12.dp, 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(d.teamName, style = inter(12.sp, FontWeight.SemiBold, readable(color, C.Surface)), maxLines = 1)
            }
        }
        (d.number ?: season?.number)?.let { Text(it, style = orbitron(34.sp, FontWeight.Black, readable(color, C.Surface))) }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("POSITION", "P${d.position}")
        StatTile("POINTS", Fmt.points(d.points))
        StatTile("WINS", d.wins.toString())
        StatTile("PODIUMS", season?.podiums?.toString() ?: "–")
    }

    PanelLabel("FORM · ${st.season}")
    when {
        season != null && season.rounds.isNotEmpty() -> {
            FormChart(listOf(season to color))
            val bits = listOfNotNull(
                season.bestFinish?.let { "Best P$it" },
                season.avgQuali?.let { "Avg grid P" + String.format(java.util.Locale.US, "%.1f", it) },
                "${season.dnfs} DNF" + if (season.dnfs == 1) "" else "s",
            )
            Text(bits.joinToString(" · "), style = inter(12.sp, color = C.TextSecondary))
        }
        loading -> Skeleton(150.dp)
        else -> Text("No race results for ${d.surname} this season yet.", style = inter(12.sp, color = C.TextMuted))
    }

    if (mate != null) {
        PanelLabel("VS TEAMMATE")
        HeadToHeadBlock(d, mate, season, mateSeason, color)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val isFav = state.favourite?.matches(d.name, d.surname) == true
        Chip(
            if (isFav) "Your driver" else "Make my driver",
            modifier = Modifier.weight(1f),
            icon = R.drawable.lc_star,
            iconColor = C.Highlight,
            selected = isFav,
        ) {
            if (!isFav) {
                Haptics.confirm(view)
                state.chooseFavourite(app.f1pro.data.Favourite(d.name, d.surname, d.teamId, d.teamName))
            }
        }
        Chip("F1 profile", modifier = Modifier.weight(1f), icon = R.drawable.lc_external_link) { openUrl(context, d.url) }
    }
}

@Composable
private fun ColumnScope.TeamPanel(state: AppState, st: Standings, t: TeamStanding) {
    val context = LocalContext.current
    val color = Color(Teams.color(t.teamId))
    val second = lerp(color, if (C.dark) Color.White else Color.Black, 0.45f)
    val drivers = st.drivers.filter { it.teamId == t.teamId }.sortedByDescending { it.points }
    val pair = drivers.take(2)
    LaunchedEffect(t) { pair.forEach { state.loadDriverSeason(st.season, it) } }
    val seasons = pair.map { state.driverSeasons[state.driverKey(st.season, it)] }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 40.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CONSTRUCTOR", style = inter(11.sp, FontWeight.SemiBold, C.TextMuted, 0.08f))
            Text(t.teamName.uppercase(), style = orbitron(18.sp, FontWeight.Bold, readable(color, C.Surface), 0.03f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("POSITION", "P${t.position}")
        StatTile("POINTS", Fmt.points(t.points))
        StatTile("WINS", t.wins.toString())
        StatTile("PODIUMS", if (seasons.isNotEmpty() && seasons.all { it != null }) seasons.sumOf { it!!.podiums }.toString() else "–")
    }

    PanelLabel("DRIVERS")
    pair.forEachIndexed { i, d ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(C.SurfaceLight)
                .tap {
                    state.sheetTeam = null
                    state.sheetDriver = d
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (i == 0) color else second))
            Spacer(Modifier.width(10.dp))
            Text("${d.name} ${d.surname}", style = inter(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            Text("P${d.position} · ${Fmt.points(d.points)} pts", style = inter(12.sp, color = C.TextSecondary))
            Spacer(Modifier.width(6.dp))
            Ic(R.drawable.lc_chevron_right, C.TextMuted, 14.dp)
        }
    }

    val lines = pair.indices.mapNotNull { i -> seasons[i]?.let { it to (if (i == 0) color else second) } }
    if (lines.isNotEmpty() && lines.any { it.first.rounds.isNotEmpty() }) {
        PanelLabel("FORM · ${st.season}")
        FormChart(lines)
    }
    if (pair.size == 2) {
        PanelLabel("HEAD TO HEAD")
        HeadToHeadBlock(pair[0], pair[1], seasons[0], seasons[1], color)
    }

    Teams.url(t.teamId)?.let { url ->
        Chip("Team on F1.com", modifier = Modifier.fillMaxWidth(), icon = R.drawable.lc_external_link) { openUrl(context, url) }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, style = SectionLabel, modifier = Modifier.padding(bottom = 0.dp))
}

@Composable
private fun RowScope.StatTile(label: String, value: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(C.SurfaceLight)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, style = orbitron(16.sp, FontWeight.Bold, C.Text), maxLines = 1)
        Text(label, style = inter(9.sp, FontWeight.SemiBold, C.TextMuted, 0.08f), maxLines = 1)
    }
}

/** Points, races and qualifying, each as a split bar: left is [a], right is [b]. */
@Composable
private fun HeadToHeadBlock(a: DriverStanding, b: DriverStanding, sa: DriverSeason?, sb: DriverSeason?, color: Color) {
    val other = lerp(color, if (C.dark) Color.White else Color.Black, 0.45f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row {
            Text(a.surname, style = inter(13.sp, FontWeight.SemiBold, readable(color, C.Surface)), modifier = Modifier.weight(1f))
            Text(b.surname, style = inter(13.sp, FontWeight.SemiBold, readable(other, C.Surface)))
        }
        SplitBar("Points", a.points, b.points, Fmt.points(a.points), Fmt.points(b.points), color, other)
        if (sa != null && sb != null) {
            val h = DriverStats.headToHead(sa, sb)
            SplitBar("Races ahead", h.raceA.toDouble(), h.raceB.toDouble(), h.raceA.toString(), h.raceB.toString(), color, other)
            SplitBar("Qualifying ahead", h.qualiA.toDouble(), h.qualiB.toDouble(), h.qualiA.toString(), h.qualiB.toString(), color, other)
        }
    }
}

@Composable
private fun SplitBar(label: String, a: Double, b: Double, aText: String, bText: String, ca: Color, cb: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(aText, style = orbitron(13.sp, FontWeight.Bold, C.Text))
            Spacer(Modifier.weight(1f))
            Text(label, style = inter(11.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text(bText, style = orbitron(13.sp, FontWeight.Bold, C.Text))
        }
        val total = a + b
        val f = if (total <= 0) 0.5f else (a / total).toFloat().coerceIn(0.02f, 0.98f)
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            Box(Modifier.weight(f).fillMaxHeight().background(ca))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(1f - f).fillMaxHeight().background(cb))
        }
    }
}

/**
 * Finishing position at each round (P1 at the top). Podiums get a bigger dot, retirements a
 * red cross along the bottom. One line per driver.
 */
@Composable
private fun FormChart(lines: List<Pair<DriverSeason, Color>>) {
    val all = lines.flatMap { it.first.rounds }
    if (all.isEmpty()) return
    val minR = all.minOf { it.round }
    val maxR = all.maxOf { it.round }
    val worst = maxOf(20, all.maxOf { it.finish ?: 0 })
    val grid = C.GlassBorder
    val dnf = C.Live
    val ring = C.Surface
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            Column(Modifier.fillMaxHeight().width(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
                for (l in listOf("P1", "P${(worst + 1) / 2}", "P$worst")) Text(l, style = inter(9.sp, color = C.TextMuted))
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val pad = 6.dp.toPx()
                val w = size.width - pad * 2
                val h = size.height - pad * 2
                fun x(round: Int) = pad + if (maxR == minR) w / 2 else (round - minR).toFloat() / (maxR - minR) * w
                fun y(pos: Int) = pad + (pos - 1).toFloat() / (worst - 1) * h
                for (p in listOf(1, (worst + 1) / 2, worst)) drawLine(grid, Offset(pad, y(p)), Offset(pad + w, y(p)), 1.dp.toPx())
                for ((season, color) in lines) {
                    // Line through finishes; a retirement breaks it.
                    var path = Path()
                    var started = false
                    for (r in season.rounds) {
                        val f = r.finish
                        if (f == null) {
                            if (started) drawPath(path, color.copy(alpha = 0.8f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            path = Path(); started = false
                            continue
                        }
                        if (!started) { path.moveTo(x(r.round), y(f)); started = true } else path.lineTo(x(r.round), y(f))
                    }
                    if (started) drawPath(path, color.copy(alpha = 0.8f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    for (r in season.rounds) {
                        val f = r.finish
                        if (f == null) {
                            val cx = x(r.round); val cy = y(worst); val k = 3.5.dp.toPx()
                            drawLine(dnf, Offset(cx - k, cy - k), Offset(cx + k, cy + k), 2.dp.toPx())
                            drawLine(dnf, Offset(cx - k, cy + k), Offset(cx + k, cy - k), 2.dp.toPx())
                        } else {
                            val big = f <= 3
                            drawCircle(ring, (if (big) 5.5.dp else 3.5.dp).toPx(), Offset(x(r.round), y(f)))
                            drawCircle(color, (if (big) 4.5.dp else 2.5.dp).toPx(), Offset(x(r.round), y(f)))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 28.dp)) {
            Text("R$minR", style = inter(9.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text("R$maxR", style = inter(9.sp, color = C.TextMuted))
        }
    }
}
