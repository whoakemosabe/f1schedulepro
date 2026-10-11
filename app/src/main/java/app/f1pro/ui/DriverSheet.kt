package app.f1pro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.border
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
import app.f1pro.data.DriverCareer
import app.f1pro.data.DriverRound
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
        // Keyed so switching from a team to one of its drivers slides a fresh panel up.
        d != null -> androidx.compose.runtime.key("d-" + d.surname) { SlideSheet(onDismiss = { state.sheetDriver = null }) { DriverPager(state, st, d) } }
        t != null -> androidx.compose.runtime.key("t-" + t.teamId) { SlideSheet(onDismiss = { state.sheetTeam = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { TeamPanel(state, st, t) }
        } }
    }
}

/** Swipe left / right for the next or previous driver in the standings. */
@Composable
private fun DriverPager(state: AppState, st: Standings, first: DriverStanding) {
    val drivers = st.drivers
    val start = drivers.indexOfFirst { it.name == first.name && it.surname == first.surname }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { drivers.size }
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != start) Haptics.segment(view) }
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ swipe for other drivers ›", style = inter(10.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text("${pager.currentPage + 1} / ${drivers.size}", style = orbitron(10.sp, FontWeight.SemiBold, C.TextMuted, 0.08f))
        }
        Spacer(Modifier.height(10.dp))
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DriverPanel(state, st, drivers[page], active = page == pager.settledPage)
            }
        }
    }
}

private fun teammateOf(st: Standings, d: DriverStanding): DriverStanding? =
    st.drivers.filter { it.teamId == d.teamId && !(it.name == d.name && it.surname == d.surname) }.maxByOrNull { it.points }

@Composable
private fun ColumnScope.DriverPanel(state: AppState, st: Standings, d: DriverStanding, active: Boolean) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val color = Color(Teams.color(d.teamId))
    val mate = teammateOf(st, d)
    // The championship rival: the leader, or P2 when this is the leader.
    val rival = if (d.position == 1) st.drivers.getOrNull(1) else st.drivers.firstOrNull()
    LaunchedEffect(d) { state.loadDriverSeason(st.season, d) }
    LaunchedEffect(mate) { mate?.let { state.loadDriverSeason(st.season, it) } }
    LaunchedEffect(rival) { rival?.let { state.loadDriverSeason(st.season, it) } }
    val season = state.driverSeasons[state.driverKey(st.season, d)]
    val mateSeason = mate?.let { state.driverSeasons[state.driverKey(st.season, it)] }
    val rivalSeason = rival?.let { state.driverSeasons[state.driverKey(st.season, it)] }
    val loading = state.driverSeasonLoading[state.driverKey(st.season, d)] == true
    // Career totals take a few requests: only for the driver actually on screen.
    LaunchedEffect(season?.driverId, active) { if (active) season?.driverId?.let { state.loadCareer(it, st.season) } }
    val career = season?.driverId?.let { state.careers[it] }

    PhotoHeader(state, d, color, season?.number)

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

    if (season != null && season.rounds.isNotEmpty()) {
        PanelLabel("SEASON HIGHLIGHTS")
        Highlights(season)
    }

    if (season != null && rivalSeason != null && rival != null && season.rounds.isNotEmpty()) {
        PanelLabel(if (d.position == 1) "LEAD OVER ${rival.surname.uppercase()}" else "GAP TO ${rival.surname.uppercase()}")
        GapChart(DriverStats.gap(season, rivalSeason), color, leader = d.position == 1, rivalName = rival.surname)
    }

    if (mate != null) {
        PanelLabel("VS TEAMMATE")
        HeadToHeadBlock(d, mate, season, mateSeason, color)
        if (season != null && mateSeason != null) DuelStrip(DriverStats.duels(season, mateSeason), color, d.surname, mate.surname)
    }

    if (season != null && season.rounds.isNotEmpty()) {
        PanelLabel("EVERY ROUND")
        RoundList(state, st.season, season, color)
    }

    PanelLabel("CAREER")
    if (career != null) CareerBox(career) else Skeleton(110.dp)

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

/** Team-colour band with the official headshot, the number large behind, name and team. */
@Composable
private fun PhotoHeader(state: AppState, d: DriverStanding, color: Color, number: String?) {
    val url = androidx.compose.runtime.remember(d.surname) { state.headshotUrl(d.surname) }
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        value = url?.let { u ->
            state.image(u)?.let { bytes ->
                try { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } catch (e: Exception) { null }
            }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.Black, 0.15f), lerp(color, Color.Black, 0.6f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    ) {
        (d.number ?: number)?.let {
            Text(
                it,
                style = orbitron(120.sp, FontWeight.Black, Color.White.copy(alpha = 0.16f)),
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-8).dp),
            )
        }
        photo?.let {
            Image(
                it,
                contentDescription = "${d.name} ${d.surname}",
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomEnd,
                modifier = Modifier.align(Alignment.BottomEnd).fillMaxHeight().wrapContentWidth().padding(end = 8.dp),
            )
        }
        // Darken the left so the name stays readable over any team colour.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(d.name, style = inter(14.sp, color = Color.White.copy(alpha = 0.85f)), maxLines = 1)
            Text(d.surname.uppercase(), style = orbitron(22.sp, FontWeight.Bold, Color.White, 0.03f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Flag(Countries.code(d.nationality), 18.dp, 12.dp, 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(d.teamName, style = inter(12.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.9f)), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Highlights(s: DriverSeason) {
    val gained = s.placesGained
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("POLES", s.poles.toString())
            StatTile("FASTEST LAPS", s.fastestLaps.toString())
            StatTile("SPRINT PTS", Fmt.points(s.sprintPoints))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("PTS / RACE", String.format(java.util.Locale.US, "%.1f", s.pointsPerRace))
            StatTile("PLACES GAINED", (if (gained > 0) "+" else "") + gained)
            StatTile("POINTS STREAK", s.pointsStreak.toString())
        }
    }
}

/**
 * Points behind the leader after each round (or ahead of P2, for the leader). The line sits
 * above zero when behind.
 */
@Composable
private fun GapChart(gap: List<Pair<Int, Double>>, color: Color, leader: Boolean, rivalName: String) {
    if (gap.isEmpty()) return
    val now = gap.last().second
    val text = when {
        leader && now <= 0 -> "Leads $rivalName by ${Fmt.points(-now)} points"
        leader -> "$rivalName is ahead by ${Fmt.points(now)} points"
        now > 0 -> "${Fmt.points(now)} points behind $rivalName"
        now < 0 -> "${Fmt.points(-now)} points ahead of $rivalName"
        else -> "Level on points with $rivalName"
    }
    val zero = C.TextMuted
    val grid = C.GlassBorder
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = inter(13.sp, FontWeight.SemiBold, C.Text))
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val pad = 6.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            val vals = gap.map { if (leader) -it.second else it.second }
            val top = maxOf(vals.max(), 0.0)
            val bottom = minOf(vals.min(), 0.0)
            val span = (top - bottom).takeIf { it > 0 } ?: 1.0
            fun x(i: Int) = pad + if (gap.size == 1) w / 2 else i.toFloat() / (gap.size - 1) * w
            fun y(v: Double) = pad + ((top - v) / span).toFloat() * h
            drawLine(grid, Offset(pad, pad), Offset(pad + w, pad), 1.dp.toPx())
            drawLine(grid, Offset(pad, pad + h), Offset(pad + w, pad + h), 1.dp.toPx())
            drawLine(zero.copy(alpha = 0.6f), Offset(pad, y(0.0)), Offset(pad + w, y(0.0)), 1.dp.toPx())
            val line = Path()
            val area = Path()
            vals.forEachIndexed { i, v ->
                if (i == 0) { line.moveTo(x(i), y(v)); area.moveTo(x(i), y(0.0)); area.lineTo(x(i), y(v)) }
                else { line.lineTo(x(i), y(v)); area.lineTo(x(i), y(v)) }
            }
            area.lineTo(x(vals.lastIndex), y(0.0)); area.close()
            drawPath(area, color.copy(alpha = 0.18f))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, 3.5.dp.toPx(), Offset(x(vals.lastIndex), y(vals.last())))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("R${gap.first().first}", style = inter(9.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text(if (leader) "above the line: ahead" else "above the line: behind", style = inter(9.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text("R${gap.last().first}", style = inter(9.sp, color = C.TextMuted))
        }
    }
}

/** One block per round: team colour when this driver finished ahead, grey-ish when the teammate did. */
@Composable
private fun DuelStrip(duels: List<app.f1pro.data.RoundDuel>, color: Color, me: String, mate: String) {
    if (duels.isEmpty()) return
    val other = lerp(color, if (C.dark) Color.White else Color.Black, 0.45f).copy(alpha = 0.55f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (dl in duels) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when (dl.winner) {
                                1 -> color
                                -1 -> other
                                else -> C.SurfaceLight
                            }
                        )
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Text(" $me ahead", style = inter(10.sp, color = C.TextMuted))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(other))
            Text(" $mate ahead", style = inter(10.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text("R${duels.first().round}–R${duels.last().round}", style = inter(10.sp, color = C.TextMuted))
        }
    }
}

/** Flag, race, grid → finish, places gained, points. Tap a round to open that race. */
@Composable
private fun RoundList(state: AppState, year: Int, s: DriverSeason, color: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.SurfaceLight.copy(alpha = 0.5f)),
    ) {
        s.rounds.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
            RoundRow(state, year, r, color)
        }
    }
}

@Composable
private fun RoundRow(state: AppState, year: Int, r: DriverRound, color: Color) {
    val race = androidx.compose.runtime.remember(year, r.round, state.seasons) { state.raceFor(year, r.round) }
    Row(
        Modifier
            .fillMaxWidth()
            .tap(enabled = race != null) { state.openRace(year, r.round) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("R${r.round}", style = orbitron(10.sp, FontWeight.SemiBold, C.TextMuted), modifier = Modifier.width(30.dp))
        if (race != null) {
            Flag(race.circuit.countryCode, 20.dp, 14.dp, 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(r.raceName.replace("Grand Prix", "GP"), style = inter(13.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val grid = when (r.grid) { null -> null; 0 -> "Pit lane"; else -> "P${r.grid}" }
            val line = if (r.finish == null) listOfNotNull(grid?.let { "From $it" }, r.status).joinToString(" · ")
            else listOfNotNull(grid, "P${r.finish}").joinToString(" → ") + if (r.fastestRank == 1) " · Fastest lap" else ""
            Text(line, style = inter(11.sp, color = if (r.finish == null) C.Live else C.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        r.gained?.takeIf { it != 0 }?.let { g ->
            Text(
                (if (g > 0) "+" else "−") + kotlin.math.abs(g),
                style = inter(11.sp, FontWeight.SemiBold, if (g > 0) C.Success else C.Live),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(44.dp)) {
            Text(Fmt.points(r.allPoints), style = orbitron(12.sp, FontWeight.Bold, if (r.allPoints > 0) C.Text else C.TextMuted))
            if (r.sprintPoints > 0) Text("+" + Fmt.points(r.sprintPoints) + " spr", style = inter(9.sp, color = C.TextMuted))
        }
        if (race != null) {
            Spacer(Modifier.width(4.dp))
            Ic(R.drawable.lc_chevron_right, C.TextMuted.copy(alpha = 0.6f), 12.dp)
        }
    }
}

@Composable
private fun CareerBox(c: DriverCareer) {
    val age = c.dateOfBirth?.let { java.time.Period.between(it, java.time.LocalDate.now()).years }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("AGE", age?.toString() ?: "–")
            StatTile("SEASONS", c.seasons.size.toString())
            StatTile("STARTS", c.starts.toString())
            StatTile("TITLES", c.titles.size.toString())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("WINS", c.wins.toString())
            StatTile("PODIUMS", c.podiums.toString())
            StatTile("POLES", c.poles.toString())
        }
        val lines = listOfNotNull(
            c.firstRace?.let { "Debut: $it" },
            c.titles.takeIf { it.isNotEmpty() }?.let { "Champion: " + it.joinToString(", ") },
        )
        if (lines.isNotEmpty()) Text(lines.joinToString(" · "), style = inter(12.sp, color = C.TextSecondary))
        Text("Career figures from Jolpica (the Ergast archive)", style = inter(10.sp, color = C.TextMuted))
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
