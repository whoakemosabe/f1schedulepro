package app.f1pro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
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
import coil3.request.crossfade
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Tapping a driver (or a team) on Standings opens a panel from the bottom: a header that stays
 * put (photo, numbers, tabs) over Season / Rounds / Career, a Compare panel for any two
 * drivers, and the F1 website one tap away at the bottom.
 */

@Composable
fun StandingsSheets(state: AppState, st: Standings) {
    val d = state.sheetDriver
    val t = state.sheetTeam
    when {
        // Keyed so switching from a team to one of its drivers slides a fresh panel up.
        d != null -> key("d-" + d.surname) { SlideSheet(onDismiss = { state.sheetDriver = null }) { DriverPager(state, st, d) } }
        t != null -> key("t-" + t.teamId) {
            SlideSheet(onDismiss = { state.sheetTeam = null }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { TeamPanel(state, st, t) }
            }
        }
    }
    // Compare slides up over the driver panel; closing it goes back to that driver.
    state.compare?.let { (a, _) ->
        key("c-" + a.surname) { SlideSheet(onDismiss = { state.compare = null }) { ComparePanel(state, st, a) } }
    }
}

/** Swipe left / right for the next or previous driver; the chosen tab stays as you swipe. */
@Composable
private fun DriverPager(state: AppState, st: Standings, first: DriverStanding) {
    val drivers = st.drivers
    val start = drivers.indexOfFirst { it.name == first.name && it.surname == first.surname }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { drivers.size }
    val view = androidx.compose.ui.platform.LocalView.current
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != start) Haptics.segment(view) }
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹ swipe for other drivers ›", style = inter(10.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text("${pager.currentPage + 1} / ${drivers.size}", style = orbitron(10.sp, FontWeight.SemiBold, C.TextMuted, 0.08f))
        }
        Spacer(Modifier.height(10.dp))
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { page ->
            androidx.compose.runtime.CompositionLocalProvider(LocalIntro provides (page == start)) {
                DriverPanel(state, st, drivers[page], active = page == pager.settledPage, tab = tab, onTab = { tab = it })
            }
        }
    }
}

private fun teammateOf(st: Standings, d: DriverStanding): DriverStanding? =
    st.drivers.filter { it.teamId == d.teamId && !(it.name == d.name && it.surname == d.surname) }.maxByOrNull { it.points }

/** A different shade of [c] for a second line or bar (two drivers of one team). */
private fun shade(c: Color): Color = lerp(c, if (C.dark) Color.White else Color.Black, 0.45f)

@Composable
private fun DriverPanel(state: AppState, st: Standings, d: DriverStanding, active: Boolean, tab: Int, onTab: (Int) -> Unit) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val color = Color(Teams.color(d.teamId))
    val mate = teammateOf(st, d)
    // The championship rival: the leader, or P2 when this is the leader.
    val rival = if (d.position == 1) st.drivers.getOrNull(1) else st.drivers.firstOrNull()
    LaunchedEffect(d) { state.loadDriverSeason(st.season, d) }
    val season = state.driverSeasons[state.driverKey(st.season, d)]
    val mateSeason = mate?.let { state.driverSeasons[state.driverKey(st.season, it)] }
    val rivalSeason = rival?.let { state.driverSeasons[state.driverKey(st.season, it)] }
    val loading = state.driverSeasonLoading[state.driverKey(st.season, d)] == true
    // Career totals and the track record take requests: only for the driver on screen.
    LaunchedEffect(season?.driverId, active, tab) { if (active && tab == 2) season?.driverId?.let { state.loadCareer(it, st.season) } }

    Column(Modifier.fillMaxSize()) {
        // Stays put: photo, the four numbers, the tabs.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoHeader(state, d, color, season?.number)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("POSITION", "P" + counted(d.position.toDouble(), from = 22.0) { it.roundToInt().toString() })
                StatTile("POINTS", counted(d.points) { Fmt.points(it) })
                StatTile("WINS", counted(d.wins.toDouble()) { it.roundToInt().toString() })
                StatTile("PODIUMS", season?.let { s -> counted(s.podiums.toDouble()) { it.roundToInt().toString() } } ?: "–")
            }
            SlidingSegments(listOf("SEASON", "ROUNDS", "CAREER"), tab, Modifier.fillMaxWidth()) { onTab(it) }
        }
        Spacer(Modifier.height(12.dp))
        // Scrolls: the chosen tab.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                0 -> {
                    ThisWeekend(state, st, d, season, active, color)
                    PanelLabel("FORM · ${st.season}")
                    when {
                        season != null && season.rounds.isNotEmpty() -> {
                            FormChart(listOf(season to color), listOf(d.surname))
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
                        PanelLabel(if (d.position == 1) "LEAD OVER ${rival.code}" else "GAP TO ${rival.code}")
                        val rc = Color(Teams.color(rival.teamId)).let { if (rival.teamId == d.teamId) shade(it) else it }
                        GapCard(state, st, d, rival, season, rivalSeason, color, rc)
                    }
                    if (mate != null) {
                        PanelLabel("VS TEAMMATE")
                        HeadToHeadBlock(d, mate, season, mateSeason, color)
                        if (season != null && mateSeason != null) DuelStrip(DriverStats.duels(season, mateSeason), color, shade(color), d.surname, mate.surname)
                    }
                    DataAge(state.seasonDataAt[st.season])
                }
                1 -> when {
                    season != null && season.rounds.isNotEmpty() -> RoundList(state, st.season, season, color)
                    loading -> Skeleton(300.dp)
                    else -> Text("No rounds for ${d.surname} this season yet.", style = inter(12.sp, color = C.TextMuted))
                }
                else -> {
                    val careerId = season?.driverId
                    val career = careerId?.let { state.careers[it] }
                    when {
                        career != null -> CareerBox(career)
                        careerId != null && state.careerFailed[careerId] == true -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.careerError[careerId] ?: "Couldn't load career stats.", style = inter(12.sp, color = C.TextMuted), modifier = Modifier.weight(1f))
                            Chip("Retry", icon = R.drawable.lc_refresh_cw, iconColor = C.AccentText) { state.loadCareer(careerId, st.season, retry = true) }
                        }
                        careerId == null && !loading -> Text("Career stats show once they've raced this season.", style = inter(12.sp, color = C.TextMuted))
                        else -> Skeleton(180.dp)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isFav = state.favourite?.matches(d.name, d.surname) == true
                Chip(
                    if (isFav) "Your driver" else "My driver",
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
                Chip("Compare", modifier = Modifier.weight(1f), icon = R.drawable.lc_users, iconColor = C.Highlight) {
                    Haptics.tap(view)
                    state.compare = d to null
                }
                Chip("F1 profile", modifier = Modifier.weight(1f), icon = R.drawable.lc_external_link) { openUrl(context, d.url) }
            }
        }
    }
}

/**
 * Whether this page's numbers and charts animate in: only on the driver the panel opened on.
 * Swiping on to other drivers shows them settled, so fast swiping doesn't replay everything.
 */
private val LocalIntro = androidx.compose.runtime.staticCompositionLocalOf { true }

/** "Updated 2 h ago · pull down on Standings to refresh": how fresh the saved season data is. */
@Composable
private fun DataAge(at: Long?) {
    if (at == null) return
    val now = rememberNow(60_000)
    val old = now - at > 24 * 60 * 60_000L
    Text(
        (if (old) "Saved data from " + Fmt.ago(at, now) else "Updated " + Fmt.ago(at, now)) + " · pull down on Standings to refresh",
        style = inter(10.sp, color = if (old) C.Warning else C.TextMuted),
    )
}

/** A number that counts up to [target] the first time it shows (and moves to new values). */
@Composable
private fun counted(target: Double, from: Double = 0.0, format: (Double) -> String): String {
    val intro = LocalIntro.current
    val a = remember { Animatable(if (intro) from.toFloat() else target.toFloat()) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(800, easing = FastOutSlowInEasing)) }
    val v = a.value.toDouble()
    return if (abs(v - target) < 0.01) format(target) else format(kotlin.math.round(v))
}

/**
 * The header: team livery in diagonal stripes, the number large and slanted behind, the
 * official headshot cut out on the right, the name bottom left. Always on dark, so the white
 * type reads in both themes.
 */
@Composable
private fun PhotoHeader(state: AppState, d: DriverStanding, color: Color, number: String?) {
    val url = remember(d.surname, d.shortCode) { state.headshotUrl(d) }
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    val deep = lerp(color, Color.Black, 0.72f)
    val light = lerp(color, Color.White, 0.25f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.Black, 0.45f), deep)))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
    ) {
        // Livery: three slanted bands across the right half, then a soft glow behind the photo.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val slant = h * 0.55f
            fun band(x: Float, width: Float, c: Color) {
                drawPath(Path().apply {
                    moveTo(x + slant, 0f); lineTo(x + slant + width, 0f); lineTo(x + width, h); lineTo(x, h); close()
                }, c)
            }
            band(w * 0.44f, w * 0.20f, color.copy(alpha = 0.9f))
            band(w * 0.66f, w * 0.06f, light.copy(alpha = 0.85f))
            band(w * 0.75f, w * 0.012f, Color.White.copy(alpha = 0.55f))
            band(w * 0.79f, w * 0.30f, color.copy(alpha = 0.55f))
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), center = Offset(w * 0.78f, h * 0.55f), radius = h * 0.8f),
                radius = h * 0.8f, center = Offset(w * 0.78f, h * 0.55f),
            )
        }
        (d.number ?: number)?.let {
            Text(
                it,
                style = orbitron(118.sp, FontWeight.Black, Color.White.copy(alpha = 0.2f)).copy(fontStyle = FontStyle.Italic),
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 120.dp, y = (-6).dp),
            )
        }
        // Coil: downloads and decodes off the main thread, keeps memory and disk caches,
        // and fades the photo in.
        url?.let { original ->
            // The bigger rendition first (sharper when shown this large); the original if not.
            var src by remember(original) { mutableStateOf(DriverStats.sharperHeadshot(original)) }
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(context).data(src).crossfade(true).build(),
                onError = { if (src != original) src = original },
                contentDescription = "Photo of ${d.name} ${d.surname}",
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomEnd,
                modifier = Modifier.align(Alignment.BottomEnd).fillMaxHeight().fillMaxWidth(0.5f).padding(top = 8.dp, end = 4.dp),
            )
        }
        // Darken the left so the name stays readable over any livery.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.55f to Color.Transparent)))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(d.name, style = inter(13.sp, color = Color.White.copy(alpha = 0.85f)), maxLines = 1)
            Text(d.surname.uppercase(), style = orbitron(24.sp, FontWeight.Black, Color.White, 0.03f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Flag(Countries.code(d.nationality), 18.dp, 12.dp, 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(d.teamName, style = inter(12.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.92f)), maxLines = 1)
            }
        }
    }
}

/**
 * Next up: this driver's record at the coming track, last year's result there, and the
 * championship maths with what's left to score.
 */
@Composable
private fun ThisWeekend(state: AppState, st: Standings, d: DriverStanding, season: DriverSeason?, active: Boolean, color: Color) {
    val ahead = remember(st.season, state.seasons) { state.ahead(st.season) }
    val race = ahead.race
    val driverId = season?.driverId
    LaunchedEffect(race?.circuit?.id, driverId, active) {
        if (active && race != null && driverId != null) state.loadTrackRecord(race.circuit.id, driverId)
    }
    val record = if (race != null && driverId != null) state.trackRecords["${race.circuit.id}-$driverId"] else null
    val leader = st.drivers.firstOrNull()
    val second = st.drivers.getOrNull(1)
    val left = DriverStats.pointsLeft(ahead.racesLeft, ahead.sprintsLeft)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.SurfaceLight)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val now = System.currentTimeMillis()
        val off = remember(st.season, state.seasons) { app.f1pro.data.Planner.offSeason(state.seasons, st.season, now) }
        if (off != null) {
            // Between seasons: how this one ended, and when the next starts.
            Text("FINAL ${off.finished}", style = inter(10.sp, FontWeight.Bold, readable(color, C.SurfaceLight), 0.12f))
            Text(
                if (d.position == 1) "Champion with ${Fmt.points(d.points)} points." else "Finished P${d.position} with ${Fmt.points(d.points)} points.",
                style = inter(13.sp, FontWeight.SemiBold, C.Text),
            )
            Text(seasonStartsLine(off, now), style = inter(12.sp, color = C.TextSecondary))
            return@Column
        }
        if (race != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT UP", style = inter(10.sp, FontWeight.Bold, readable(color, C.SurfaceLight), 0.12f))
                Spacer(Modifier.width(8.dp))
                Flag(race.circuit.countryCode, 18.dp, 12.dp, 2.dp)
                Spacer(Modifier.width(6.dp))
                Text(race.name.replace("Grand Prix", "GP"), style = inter(13.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val trackLine = when {
                record == null -> "Loading their record at ${race.circuit.name}…"
                record.starts == 0 -> "First race at ${race.circuit.name}."
                else -> listOfNotNull(
                    "${record.starts} start" + if (record.starts == 1) "" else "s",
                    record.wins.takeIf { it > 0 }?.let { "$it win" + if (it == 1) "" else "s" },
                    record.podiums.takeIf { it > 0 }?.let { "$it podium" + if (it == 1) "" else "s" },
                    record.best?.let { "best P$it" },
                ).joinToString(" · ") + " here"
            }
            Text(trackLine, style = inter(12.sp, color = C.TextSecondary))
            record?.lastBefore(st.season + 1)?.takeIf { it.season < st.season || ahead.racesLeft > 0 }?.let { last ->
                val res = last.finish?.let { "P$it" } ?: "DNF"
                val grid = last.grid?.takeIf { it > 0 }?.let { " from P$it" } ?: ""
                Text("Last time (${last.season}): $res$grid", style = inter(12.sp, color = C.TextSecondary))
            }
        }
        // Championship maths.
        if (leader != null) {
            val gap = leader.points - d.points
            val text = when {
                ahead.racesLeft == 0 && d.position == 1 -> "Champion."
                ahead.racesLeft == 0 -> "Season over: P${d.position}."
                d.position == 1 && second != null && d.points - second.points > left ->
                    "Champion already: ${Fmt.points(d.points - second.points)} ahead with $left left."
                d.position == 1 && second != null -> "Leads by ${Fmt.points(d.points - second.points)} with $left points left."
                gap > left -> "Out of the title fight: ${Fmt.points(gap)} behind, $left points left."
                else -> "Still in it: ${Fmt.points(gap)} behind ${leader.surname}, $left points left."
            }
            Text(text, style = inter(12.sp, FontWeight.SemiBold, C.Text))
        }
    }
}

/** "2027 season starts in 142 days" (or tomorrow, or once the calendar is out). */
fun seasonStartsLine(off: app.f1pro.data.Planner.OffSeason, now: Long): String {
    val year = off.nextYear ?: (off.finished + 1)
    return when (val days = off.days(now)) {
        null -> if (off.nextYear == null) "$year calendar not out yet." else "$year season under way."
        0 -> "$year season starts today."
        1 -> "$year season starts tomorrow."
        else -> "$year season starts in $days days."
    }
}

@Composable
private fun Highlights(s: DriverSeason) {
    val gained = s.placesGained
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("POLES", counted(s.poles.toDouble()) { it.roundToInt().toString() })
            StatTile("FASTEST LAPS", counted(s.fastestLaps.toDouble()) { it.roundToInt().toString() })
            StatTile("SPRINT PTS", counted(s.sprintPoints) { Fmt.points(it) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("PTS / RACE", String.format(java.util.Locale.US, "%.1f", s.pointsPerRace))
            StatTile("PLACES GAINED", (if (gained > 0) "+" else "") + counted(gained.toDouble()) { it.roundToInt().toString() })
            StatTile("POINTS STREAK", counted(s.pointsStreak.toDouble()) { it.roundToInt().toString() })
        }
    }
}

/**
 * The championship gap, made readable: a sentence ("34 behind ANT, 12 closer than 3 races
 * ago"), both drivers' points as two lines with the gap shaded between them and labelled at
 * the end, and what it takes from here. Tap the chart to read any round.
 */
@Composable
private fun GapCard(
    state: AppState,
    st: Standings,
    me: DriverStanding,
    rival: DriverStanding,
    mine: DriverSeason,
    theirs: DriverSeason,
    color: Color,
    rivalColor: Color,
) {
    // Both lines end on the standings' totals (penalties the race results don't show).
    val snapA = DriverStats.snapTo(mine.cumulative(), me.points)
    val snapB = DriverStats.snapTo(theirs.cumulative(), rival.points)
    val a = snapA.points.toMap()
    val b = snapB.points.toMap()
    val rounds = (a.keys + b.keys).distinct().sorted()
    if (rounds.isEmpty()) return
    val notes = listOfNotNull(
        DriverStats.adjustmentNote(me.code, snapA.adjustment, snapA.points.lastOrNull()?.first),
        DriverStats.adjustmentNote(rival.code, snapB.adjustment, snapB.points.lastOrNull()?.first),
    )
    // Totals after each round (a round someone missed keeps their last total).
    var la = 0.0; var lb = 0.0
    val rows = rounds.map { r -> a[r]?.let { la = it }; b[r]?.let { lb = it }; Triple(r, la, lb) }
    val leader = me.position == 1
    // Use the standings for "now" (they include any penalties the race results don't).
    val gapNow = rival.points - me.points
    val back = rows.getOrNull(rows.size - 4)
    val gapThen = back?.let { it.third - it.second }
    val headline = when {
        gapNow > 0 -> "${Fmt.points(gapNow)} behind ${rival.code}"
        gapNow < 0 -> "Leads ${rival.code} by ${Fmt.points(-gapNow)}"
        else -> "Level with ${rival.code}"
    }
    val modelGap = rows.last().third - rows.last().second
    val trend = gapThen?.let { then ->
        val moved = then - modelGap // positive: closed in (or lead grew)
        when {
            abs(moved) < 0.5 -> "same as 3 races ago"
            gapNow > 0 && moved > 0 -> "${Fmt.points(moved)} closer than 3 races ago"
            gapNow > 0 -> "${Fmt.points(-moved)} further back than 3 races ago"
            moved > 0 -> "lead up ${Fmt.points(moved)} in 3 races"
            else -> "lead down ${Fmt.points(-moved)} in 3 races"
        }
    }
    val ahead = remember(st.season, state.seasons) { state.ahead(st.season) }
    val left = DriverStats.pointsLeft(ahead.racesLeft, ahead.sprintsLeft)
    val needs = when {
        ahead.racesLeft == 0 -> null
        gapNow > 0 && gapNow > left -> "Can't catch ${rival.code} now: only $left points left."
        gapNow > 0 -> "Needs to outscore ${rival.code} by " + String.format(java.util.Locale.US, "%.1f", gapNow / ahead.racesLeft) +
            " points a race over the ${ahead.racesLeft} left."
        gapNow < 0 -> "Can lose " + String.format(java.util.Locale.US, "%.1f", -gapNow / ahead.racesLeft) +
            " points a race to ${rival.code} and still lead."
        else -> null
    }

    val grid = C.GlassBorder
    val guide = C.TextMuted
    val ring = C.Surface
    val intro = LocalIntro.current
    val progress = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    var sel by remember(rows.size) { mutableStateOf<Int?>(null) }
    val top = rows.maxOf { maxOf(it.second, it.third) }.let { kotlin.math.ceil(it / 50.0) * 50.0 }.coerceAtLeast(50.0)
    val minR = rounds.first(); val maxR = rounds.last()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(headline, style = orbitron(16.sp, FontWeight.Bold, if (gapNow > 0) C.Text else readable(color)))
        trend?.let { Text(it.replaceFirstChar { c -> c.uppercase() }, style = inter(12.sp, color = C.TextSecondary)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineSwatch(color, dashed = false)
            Text(" ${me.code}    ", style = inter(11.sp, FontWeight.Medium, C.TextSecondary))
            LineSwatch(rivalColor, dashed = true)
            Text(" ${rival.code}", style = inter(11.sp, FontWeight.Medium, C.TextSecondary))
            Spacer(Modifier.weight(1f))
            Text("points", style = inter(10.sp, color = C.TextMuted))
        }
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            Column(Modifier.fillMaxHeight().width(30.dp), verticalArrangement = Arrangement.SpaceBetween) {
                for (v in listOf(top, top / 2, 0.0)) Text(v.roundToInt().toString(), style = inter(9.sp, color = C.TextMuted))
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = "$headline. ${me.code} and ${rival.code} points after each round." }
                    .pointerInput(rounds) {
                        detectTapGestures { p ->
                            val pad = 6.dp.toPx()
                            val w = size.width - pad * 2 - 34.dp.toPx()
                            val i = if (rows.size == 1) 0 else (((p.x - pad) / w) * (rows.size - 1)).roundToInt().coerceIn(0, rows.lastIndex)
                            sel = if (sel == i) null else i
                        }
                    }
            ) {
                val pad = 6.dp.toPx()
                val labelRoom = 34.dp.toPx()
                val w = size.width - pad * 2 - labelRoom
                val h = size.height - pad * 2
                fun x(i: Int) = pad + if (rows.size == 1) w / 2 else i.toFloat() / (rows.size - 1) * w
                fun y(v: Double) = pad + ((top - v) / top).toFloat() * h
                for (v in listOf(top, top / 2, 0.0)) drawLine(grid, Offset(pad, y(v)), Offset(pad + w, y(v)), 1.dp.toPx())
                sel?.let { drawLine(guide.copy(alpha = 0.5f), Offset(x(it), pad), Offset(x(it), pad + h), 1.dp.toPx()) }
                clipRect(right = pad + w * progress.value + 6.dp.toPx()) {
                    // The gap: shaded between the two lines.
                    val band = Path().apply {
                        rows.forEachIndexed { i, r -> if (i == 0) moveTo(x(i), y(r.second)) else lineTo(x(i), y(r.second)) }
                        for (i in rows.indices.reversed()) lineTo(x(i), y(rows[i].third))
                        close()
                    }
                    drawPath(band, (if (gapNow > 0) rivalColor else color).copy(alpha = 0.16f))
                    val pa = Path(); val pb = Path()
                    rows.forEachIndexed { i, r ->
                        if (i == 0) { pa.moveTo(x(i), y(r.second)); pb.moveTo(x(i), y(r.third)) } else { pa.lineTo(x(i), y(r.second)); pb.lineTo(x(i), y(r.third)) }
                    }
                    drawPath(pb, rivalColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
                    drawPath(pa, color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val last = rows.lastIndex
                    drawCircle(rivalColor, 3.5.dp.toPx(), Offset(x(last), y(rows[last].third)))
                    drawCircle(color, 4.dp.toPx(), Offset(x(last), y(rows[last].second)))
                    // The gap bracket at the right end.
                    val x2 = x(last) + 8.dp.toPx()
                    drawLine(guide, Offset(x2, y(rows[last].second)), Offset(x2, y(rows[last].third)), 1.5.dp.toPx())
                }
                sel?.let { i ->
                    drawCircle(ring, 6.dp.toPx(), Offset(x(i), y(rows[i].second))); drawCircle(color, 4.5.dp.toPx(), Offset(x(i), y(rows[i].second)))
                    drawCircle(ring, 5.dp.toPx(), Offset(x(i), y(rows[i].third))); drawCircle(rivalColor, 3.5.dp.toPx(), Offset(x(i), y(rows[i].third)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            Text("R$minR", style = inter(9.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text(
                (if (gapNow > 0) "−" else if (gapNow < 0) "+" else "") + Fmt.points(abs(gapNow)),
                style = orbitron(11.sp, FontWeight.Bold, if (gapNow > 0) C.TextSecondary else readable(color)),
            )
            Spacer(Modifier.width(4.dp))
            Text("R$maxR", style = inter(9.sp, color = C.TextMuted))
        }
        val pick = sel
        if (pick == null) {
            Text("Tap the chart for any round", style = inter(10.sp, color = C.TextMuted))
        } else {
            val (r, pa, pb) = rows[pick]
            val g = pb - pa
            Text(
                "After R$r: ${me.code} ${Fmt.points(pa)} · ${rival.code} ${Fmt.points(pb)} · " +
                    (if (g > 0) "${Fmt.points(g)} behind" else if (g < 0) "${Fmt.points(-g)} ahead" else "level"),
                style = inter(12.sp, FontWeight.SemiBold, C.Text),
            )
        }
        needs?.let { Text(it, style = inter(12.sp, FontWeight.Medium, if (gapNow > left) C.Warning else C.TextSecondary)) }
        if (needs == null && ahead.racesLeft == 0) Text("Final ${st.season} standings", style = inter(12.sp, FontWeight.Medium, C.TextSecondary))
        for (n in notes) Text(n, style = inter(10.sp, color = C.Warning))
    }
}

/** One block per round: [color] when the first driver finished ahead, [other] when the second did. */
@Composable
private fun DuelStrip(duels: List<app.f1pro.data.RoundDuel>, color: Color, otherColor: Color, me: String, mate: String) {
    if (duels.isEmpty()) return
    val other = otherColor.copy(alpha = 0.75f)
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
    // The race results don't know this team by the standings' id: say so rather than guess.
    val all = state.seasonAll
    if (all != null && all.season == st.season && t.points > 0 &&
        DriverStats.teamCumulative(all.drivers.values, t.teamId).all { it.second == 0.0 }
    ) {
        Text(
            "Race results list this team under a different name, so its charts use its current drivers' results. Totals are the official standings.",
            style = inter(11.sp, color = C.Warning),
        )
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

    val shown = pair.indices.filter { seasons[it] != null }
    val lines = shown.map { i -> seasons[i]!! to (if (i == 0) color else second) }
    if (lines.isNotEmpty() && lines.any { it.first.rounds.isNotEmpty() }) {
        PanelLabel("FORM · ${st.season}")
        FormChart(lines, shown.map { pair[it].surname })
        val missing = pair.indices.filter { seasons[it] == null }.map { pair[it].surname }
        if (missing.isNotEmpty() && pair.none { state.driverSeasonLoading[state.driverKey(st.season, it)] == true }) {
            Text("No race results for ${missing.joinToString(" or ")} this season.", style = inter(11.sp, color = C.TextMuted))
        }
    } else if (pair.any { state.driverSeasonLoading[state.driverKey(st.season, it)] == true }) {
        PanelLabel("FORM · ${st.season}")
        Skeleton(150.dp)
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
private fun CareerBox(c: DriverCareer) {
    val age = c.dateOfBirth?.let { java.time.Period.between(it, java.time.LocalDate.now()).years }
    fun n(v: Int?) = v?.toString() ?: "–"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("AGE", n(age))
            StatTile("SEASONS", if (c.seasons.isEmpty()) "–" else c.seasons.size.toString())
            StatTile("STARTS", c.starts?.let { v -> counted(v.toDouble()) { it.roundToInt().toString() } } ?: "–")
            StatTile("TITLES", c.titles?.size?.toString() ?: "–")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("WINS", c.wins?.let { v -> counted(v.toDouble()) { it.roundToInt().toString() } } ?: "–")
            StatTile("PODIUMS", c.podiums?.let { v -> counted(v.toDouble()) { it.roundToInt().toString() } } ?: "–")
            StatTile("POLES", c.poles?.let { v -> counted(v.toDouble()) { it.roundToInt().toString() } } ?: "–")
        }
        val starts = c.starts
        val wins = c.wins
        val lines = listOfNotNull(
            c.firstRace?.let { "Debut: $it" },
            c.seasons.takeIf { it.isNotEmpty() }?.let { "Seasons: ${it.first()}–${it.last()}" },
            c.titles?.takeIf { it.isNotEmpty() }?.let { "Champion: " + it.joinToString(", ") },
            if (starts != null && wins != null && starts > 0) "Win rate: " + String.format(java.util.Locale.US, "%.0f%%", wins * 100.0 / starts) else null,
        )
        for (l in lines) Text(l, style = inter(12.sp, color = C.TextSecondary))
        Text("Career figures from Jolpica (the Ergast archive)", style = inter(10.sp, color = C.TextMuted))
    }
}

/**
 * Any two drivers side by side: pick the second from the list, then both form lines on one
 * chart, the numbers row by row (the better one highlighted), the head-to-head, and who
 * finished ahead round by round.
 */
@Composable
private fun ComparePanel(state: AppState, st: Standings, a: DriverStanding) {
    val view = androidx.compose.ui.platform.LocalView.current
    val b = state.compare?.second
    var picking by remember { mutableStateOf(b == null) }
    val ca = Color(Teams.color(a.teamId))
    val cbBase = b?.let { Color(Teams.color(it.teamId)) } ?: C.TextMuted
    val cb = if (b != null && b.teamId == a.teamId) shade(cbBase) else cbBase
    LaunchedEffect(a) { state.loadDriverSeason(st.season, a) }
    val sa = state.driverSeasons[state.driverKey(st.season, a)]
    val sb = b?.let { state.driverSeasons[state.driverKey(st.season, it)] }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("COMPARE", style = SectionLabel)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NameBlock(a, ca, Modifier.weight(1f), Alignment.Start)
            Text("VS", style = orbitron(14.sp, FontWeight.Black, C.TextMuted), modifier = Modifier.padding(horizontal = 10.dp))
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.SurfaceLight)
                        .tap { picking = true }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (b != null) {
                        Text(b.name, style = inter(12.sp, color = C.TextSecondary), maxLines = 1)
                        Text(b.surname.uppercase(), style = orbitron(15.sp, FontWeight.Bold, readable(cb, C.SurfaceLight)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Change ▾", style = inter(10.sp, color = C.TextMuted))
                    } else {
                        Text("Pick a driver ▾", style = inter(14.sp, FontWeight.SemiBold, C.AccentText))
                    }
                }
                DropdownMenu(
                    expanded = picking,
                    onDismissRequest = { picking = false },
                    containerColor = C.Surface,
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    for (x in st.drivers) {
                        if (x.name == a.name && x.surname == a.surname) continue
                        DropdownMenuItem(
                            leadingIcon = { Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(Color(Teams.color(x.teamId)))) },
                            text = {
                                Column {
                                    Text("${x.name} ${x.surname}", style = inter(14.sp, FontWeight.Medium, C.Text))
                                    Text(Teams.shortName(x.teamName) + " · P${x.position}", style = inter(11.sp, color = C.TextMuted))
                                }
                            },
                            onClick = {
                                Haptics.segment(view)
                                state.compare = a to x
                                state.loadDriverSeason(st.season, x)
                                picking = false
                            },
                        )
                    }
                }
            }
        }

        if (b != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CompareRow("Position", "P${a.position}", "P${b.position}", a.position < b.position, b.position < a.position, ca, cb)
                CompareRow("Points", Fmt.points(a.points), Fmt.points(b.points), a.points > b.points, b.points > a.points, ca, cb)
                CompareRow("Wins", a.wins.toString(), b.wins.toString(), a.wins > b.wins, b.wins > a.wins, ca, cb)
                if (sa != null && sb != null) {
                    CompareRow("Podiums", sa.podiums.toString(), sb.podiums.toString(), sa.podiums > sb.podiums, sb.podiums > sa.podiums, ca, cb)
                    CompareRow("Poles", sa.poles.toString(), sb.poles.toString(), sa.poles > sb.poles, sb.poles > sa.poles, ca, cb)
                    val ba = sa.bestFinish; val bb = sb.bestFinish
                    CompareRow("Best finish", ba?.let { "P$it" } ?: "–", bb?.let { "P$it" } ?: "–", (ba ?: 99) < (bb ?: 99), (bb ?: 99) < (ba ?: 99), ca, cb)
                    val qa = sa.avgQuali; val qb = sb.avgQuali
                    CompareRow(
                        "Avg grid", qa?.let { String.format(java.util.Locale.US, "P%.1f", it) } ?: "–", qb?.let { String.format(java.util.Locale.US, "P%.1f", it) } ?: "–",
                        qa != null && qb != null && qa < qb, qa != null && qb != null && qb < qa, ca, cb,
                    )
                    CompareRow("DNFs", sa.dnfs.toString(), sb.dnfs.toString(), sa.dnfs < sb.dnfs, sb.dnfs < sa.dnfs, ca, cb)
                }
            }
            if (sa != null && sb != null) {
                PanelLabel("FORM · ${st.season}")
                FormChart(listOf(sa to ca, sb to cb), listOf(a.surname, b.surname))
                PanelLabel("HEAD TO HEAD")
                HeadToHeadBlock(a, b, sa, sb, ca, cb)
                DuelStrip(DriverStats.duels(sa, sb), ca, cb, a.surname, b.surname)
            } else {
                Skeleton(150.dp)
            }
        } else {
            Text("Pick someone to compare ${a.surname} with.", style = inter(13.sp, color = C.TextMuted))
        }
    }
}

@Composable
private fun NameBlock(d: DriverStanding, color: Color, modifier: Modifier, align: Alignment.Horizontal) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(C.SurfaceLight).padding(10.dp),
        horizontalAlignment = align,
    ) {
        Text(d.name, style = inter(12.sp, color = C.TextSecondary), maxLines = 1)
        Text(d.surname.uppercase(), style = orbitron(15.sp, FontWeight.Bold, readable(color, C.SurfaceLight)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Teams.shortName(d.teamName), style = inter(10.sp, color = C.TextMuted), maxLines = 1)
    }
}

/** "Points   212  |  187": the better value in its driver's colour. */
@Composable
private fun CompareRow(label: String, a: String, b: String, aBetter: Boolean, bBetter: Boolean, ca: Color, cb: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.SurfaceLight.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(a, style = orbitron(13.sp, FontWeight.Bold, if (aBetter) readable(ca, C.SurfaceLight) else C.Text), modifier = Modifier.weight(1f))
        Text(label, style = inter(11.sp, color = C.TextMuted))
        Text(b, style = orbitron(13.sp, FontWeight.Bold, if (bBetter) readable(cb, C.SurfaceLight) else C.Text), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, style = SectionLabel)
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
private fun HeadToHeadBlock(a: DriverStanding, b: DriverStanding, sa: DriverSeason?, sb: DriverSeason?, color: Color, otherColor: Color? = null) {
    val other = otherColor ?: shade(color)
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

/** A split bar that grows out from the middle when it first shows. */
@Composable
private fun SplitBar(label: String, a: Double, b: Double, aText: String, bText: String, ca: Color, cb: Color) {
    val total = a + b
    val target = if (total <= 0) 0.5f else (a / total).toFloat().coerceIn(0.02f, 0.98f)
    val intro = LocalIntro.current
    val f = remember { Animatable(if (intro) 0.5f else target) }
    LaunchedEffect(target) { f.animateTo(target, tween(750, easing = FastOutSlowInEasing)) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(aText, style = orbitron(13.sp, FontWeight.Bold, C.Text))
            Spacer(Modifier.weight(1f))
            Text(label, style = inter(11.sp, color = C.TextMuted))
            Spacer(Modifier.weight(1f))
            Text(bText, style = orbitron(13.sp, FontWeight.Bold, C.Text))
        }
        val v = f.value.coerceIn(0.02f, 0.98f)
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            Box(Modifier.weight(v).fillMaxHeight().background(ca))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.weight(1f - v).fillMaxHeight().background(cb))
        }
    }
}

/**
 * Finishing position at each round (P1 at the top), one line per driver, drawn in from the
 * left when it first shows. Podiums get a bigger dot, retirements a red cross along the
 * bottom. Tap the chart to read a round.
 */
@Composable
private fun FormChart(lines: List<Pair<DriverSeason, Color>>, names: List<String> = emptyList()) {
    val all = lines.flatMap { it.first.rounds }
    if (all.isEmpty()) return
    val minR = all.minOf { it.round }
    val maxR = all.maxOf { it.round }
    val rounds = all.map { it.round }.distinct().sorted()
    val worst = maxOf(20, all.maxOf { it.finish ?: 0 })
    val grid = C.GlassBorder
    val dnf = C.Live
    val ring = C.Surface
    val guide = C.TextMuted
    val intro = LocalIntro.current
    val progress = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    var sel by remember(lines.map { it.first.driverId }) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (names.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                lines.forEachIndexed { i, (_, c) ->
                    LineSwatch(c, dashed = i > 0)
                    Text(" " + names.getOrElse(i) { "" } + "    ", style = inter(11.sp, FontWeight.Medium, C.TextSecondary))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            Column(Modifier.fillMaxHeight().width(28.dp), verticalArrangement = Arrangement.SpaceBetween) {
                for (l in listOf("P1", "P${(worst + 1) / 2}", "P$worst")) Text(l, style = inter(9.sp, color = C.TextMuted))
            }
            // What a screen reader says for the chart: each driver's finish, round by round.
            val spoken = lines.mapIndexed { i, (season, _) ->
                (names.getOrNull(i)?.let { "$it: " } ?: "") + season.rounds.joinToString(", ") { r ->
                    "round ${r.round} " + (r.finish?.let { "P$it" } ?: "did not finish")
                }
            }.joinToString(". ")
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = "Finishing positions. $spoken" }
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
                fun y(pos: Int) = pad + (pos - 1).toFloat() / (worst - 1) * h
                for (p in listOf(1, (worst + 1) / 2, worst)) drawLine(grid, Offset(pad, y(p)), Offset(pad + w, y(p)), 1.dp.toPx())
                sel?.let { drawLine(guide.copy(alpha = 0.5f), Offset(x(it), pad), Offset(x(it), pad + h), 1.dp.toPx()) }
                clipRect(right = pad + w * progress.value + 8.dp.toPx()) {
                    lines.forEachIndexed { index, (season, color) ->
                        // The second driver's line is dashed with hollow dots, so two drivers in
                        // the same team colour stay apart even where their lines cross.
                        val dashed = index > 0
                        val stroke = Stroke(
                            2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())) else null,
                        )
                        // Line through finishes; a retirement breaks it.
                        var path = Path()
                        var started = false
                        for (r in season.rounds) {
                            val f = r.finish
                            if (f == null) {
                                if (started) drawPath(path, color.copy(alpha = 0.85f), style = stroke)
                                path = Path(); started = false
                                continue
                            }
                            if (!started) { path.moveTo(x(r.round), y(f)); started = true } else path.lineTo(x(r.round), y(f))
                        }
                        if (started) drawPath(path, color.copy(alpha = 0.85f), style = stroke)
                        for (r in season.rounds) {
                            val f = r.finish
                            val picked = r.round == sel
                            if (f == null) {
                                val cx = x(r.round); val cy = y(worst); val k = (if (picked) 5.dp else 3.5.dp).toPx()
                                drawLine(dnf, Offset(cx - k, cy - k), Offset(cx + k, cy + k), 2.dp.toPx())
                                drawLine(dnf, Offset(cx - k, cy + k), Offset(cx + k, cy - k), 2.dp.toPx())
                            } else {
                                val big = f <= 3 || picked
                                val at = Offset(x(r.round), y(f))
                                drawCircle(ring, (if (picked) 7.dp else if (big) 5.5.dp else 3.5.dp).toPx(), at)
                                drawCircle(color, (if (picked) 5.5.dp else if (big) 4.5.dp else 2.5.dp).toPx(), at)
                                // Hollow for the second driver.
                                if (dashed) drawCircle(ring, (if (picked) 3.dp else if (big) 2.5.dp else 1.2.dp).toPx(), at)
                            }
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
        // What the tapped round was, per driver.
        val pick = sel
        if (pick == null) {
            Text("Tap the chart for any round", style = inter(10.sp, color = C.TextMuted))
        } else {
            val raceName = lines.firstNotNullOfOrNull { (s, _) -> s.rounds.firstOrNull { it.round == pick }?.raceName }?.replace("Grand Prix", "GP")
            Text("R$pick" + (raceName?.let { " · $it" } ?: ""), style = inter(12.sp, FontWeight.SemiBold, C.Text))
            lines.forEachIndexed { i, (s, c) ->
                val r = s.rounds.firstOrNull { it.round == pick }
                val who = names.getOrNull(i)?.takeIf { lines.size > 1 }?.let { "$it: " } ?: ""
                val text = when {
                    r == null -> "${who}didn't race"
                    r.finish == null -> who + (r.grid?.takeIf { it > 0 }?.let { "from P$it · " } ?: "") + r.status
                    else -> who + "P${r.finish}" + (r.grid?.takeIf { it > 0 }?.let { " from P$it" } ?: "") +
                        (r.gained?.takeIf { it != 0 }?.let { g -> if (g > 0) " (+$g)" else " (−${-g})" } ?: "") +
                        " · ${Fmt.points(r.allPoints)} pts" + (if (r.fastestRank == 1) " · fastest lap" else "")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineSwatch(c, dashed = i > 0)
                    Text("  $text", style = inter(12.sp, color = if (r?.finish == null) C.Live else C.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A short sample of a chart line (solid, or dashed for the second driver) for keys. */
@Composable
private fun LineSwatch(color: Color, dashed: Boolean) {
    val ring = C.Surface
    Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
        val y = size.height / 2
        drawLine(
            color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
        )
        drawCircle(color, 3.dp.toPx(), Offset(size.width / 2, y))
        if (dashed) drawCircle(ring, 1.5.dp.toPx(), Offset(size.width / 2, y))
    }
}
