package app.f1pro.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import app.f1pro.data.CircuitStats
import app.f1pro.data.Planner
import app.f1pro.data.Race
import app.f1pro.data.RaceStatus
import kotlinx.coroutines.launch

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun SchedulePage(state: AppState, listState: LazyListState) {
    val now = rememberNow(30_000)
    // Normally the current season; another one while a race from it is open (see scheduleYear).
    val season = remember(state.seasons, now, state.scheduleYear) {
        state.scheduleYear?.let { y -> state.seasons.firstOrNull { it.year == y } } ?: state.seasonFor(now)
    }
    val races = season?.races.orEmpty()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    // A tapped reminder opens its race.
    LaunchedEffect(state.openRaceKey, races) {
        val key = state.openRaceKey ?: return@LaunchedEffect
        val i = races.indexOfFirst { it.key == key }
        if (i >= 0) selected = i
        state.openRaceKey = null
    }

    val statuses = remember(races, now) { races.map { it.status(now) } }
    val highlighted = remember(races, statuses) {
        statuses.indexOfFirst { it == RaceStatus.ACTIVE }.takeIf { it >= 0 }
            ?: statuses.indexOfFirst { it == RaceStatus.UPCOMING }.takeIf { it >= 0 }
            ?: races.lastIndex
    }
    val globalNext = remember(state.seasons, now) { Planner.next(state.seasons, now) }
    LaunchedEffect(season?.year) { season?.year?.let { state.loadPodiums(it) } }
    state.teamColoursVersion // redraw podium stripes when the official team colours arrive

    val sel = selected
    val open = sel != null && sel in races.indices
    androidx.compose.runtime.SideEffect { state.raceOpen = open }
    // Back from a race of another season: Schedule returns to the current one.
    var wasOpen by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (wasOpen && !open) state.scheduleYear = null
        wasOpen = open
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.raceOpen = false } }

    // The race the page is showing (it changes as you swipe between races): back shrinks into
    // that race's card, so the list is scrolled to it first if it's off screen.
    var shownPage by rememberSaveable { mutableStateOf(sel ?: 0) }
    fun prepareReturn() {
        val key = races.getOrNull(shownPage)?.key ?: return
        if (listState.layoutInfo.visibleItemsInfo.none { it.key == key }) {
            listState.requestScrollToItem(maxOf(0, shownPage))
        }
    }

    // List ⇄ race page as one seekable transition, so the back gesture can scrub it.
    val transitionState = remember { SeekableTransitionState(if (open) sel else null) }
    LaunchedEffect(sel, open) {
        val target = if (open) sel else null
        if (transitionState.targetState != target || transitionState.currentState != target) transitionState.animateTo(target)
    }
    // Android 14+: the page shrinks toward its card as the back swipe moves, and springs back
    // open if the swipe is let go early. Older phones get the same animation on back.
    PredictiveBackHandler(enabled = open && state.tab == Tab.Schedule) { progress ->
        prepareReturn()
        try {
            progress.collect { e -> transitionState.seekTo(e.progress, targetState = null) }
            selected = null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            transitionState.animateTo(transitionState.currentState)
        }
    }
    val transition = rememberTransition(transitionState, label = "race")

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            transition.AnimatedContent(
                transitionSpec = { fadeIn(tween(320, delayMillis = 60)) togetherWith fadeOut(tween(220)) },
                contentKey = { it != null },
            ) { target ->
                CompositionLocalProvider(LocalAnimScope provides this) {
                    if (target != null && target in races.indices) {
                        RaceDetailPager(
                            state, races, target, globalNext?.session?.start,
                            onPage = { shownPage = it },
                            onBack = {
                                prepareReturn()
                                selected = null
                            },
                        )
                    } else {
                        ScheduleList(state, races, statuses, highlighted, listState, now) { i ->
                            shownPage = i
                            selected = i
                        }
                    }
                }
            }
        }
    }
}

/** The season's races as cards. Tapping one opens its race page. */
@Composable
private fun ScheduleList(
    state: AppState,
    races: List<Race>,
    statuses: List<RaceStatus>,
    highlighted: Int,
    listState: LazyListState,
    now: Long,
    onOpen: (Int) -> Unit,
) {
    // Open on the current/next race instead of the top, once.
    var centered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(highlighted, races.size) {
        if (!centered && highlighted >= 0) {
            listState.scrollToItem(maxOf(0, highlighted - 1))
            centered = true
        }
    }

    if (races.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(pagePadding(24.dp, 24.dp, 24.dp)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No calendar yet", style = inter(15.sp, FontWeight.SemiBold))
            Text("Connect to the internet to load the season.", style = inter(13.sp, color = C.TextSecondary))
        }
        return
    }

    val view = androidx.compose.ui.platform.LocalView.current
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.calendarLoading) {
        if (!state.calendarLoading && pulled) {
            Haptics.confirm(view)
            pulled = false
        }
    }
    RefreshBox(
        isRefreshing = state.calendarLoading && pulled,
        onRefresh = {
            Haptics.settle(view)
            pulled = true
            state.refreshAll()
        },
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(PageGap),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().height(SectionRowHeight).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Legend(C.Success, "DONE")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveDot(8.dp); Text("LIVE", style = inter(11.sp, FontWeight.Medium, C.TextMuted, 0.05f))
                }
                Legend(C.Highlight, "NEXT")
                Spacer(Modifier.weight(1f))
                val count = races.count { !it.isTesting }
                Text("$count races", style = inter(11.sp, FontWeight.Medium, C.TextMuted, 0.05f))
            }
        }
        itemsIndexed(races, key = { _, r -> r.key }) { i, race ->
            val status = statuses[i]
            RaceCard(
                race = race,
                status = status,
                stats = state.circuits[race.circuit.id],
                podium = state.podiums[race.season]?.get(race.round),
                now = now,
                isNext = i == highlighted && status == RaceStatus.UPCOMING,
                onClick = { onOpen(i) },
            )
        }
    }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(color, 8.dp)
        Text(label, style = inter(11.sp, FontWeight.Medium, C.TextMuted, 0.05f))
    }
}

private fun typeShort(type: String) = when (type) {
    "permanent" -> "Perm"
    "street" -> "Street"
    "hybrid" -> "Hybrid"
    else -> ""
}

@Composable
private fun RaceCard(
    race: Race,
    status: RaceStatus,
    stats: CircuitStats?,
    podium: List<app.f1pro.data.PodiumEntry>?,
    now: Long,
    isNext: Boolean,
    onClick: () -> Unit,
) {
    val fmt = LocalFmt.current
    val edge = when {
        isNext -> C.Highlight
        status == RaceStatus.ACTIVE -> C.Live
        status == RaceStatus.COMPLETED -> C.Success.copy(alpha = 0.3f)
        else -> C.BorderLight
    }
    val glow = if (status == RaceStatus.ACTIVE) {
        val t = rememberInfiniteTransition(label = "glow")
        t.animateFloat(0.15f, 0.3f, infiniteRepeatable(tween(1250, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g").value
    } else 0f
    val ring = when {
        isNext -> C.Highlight.copy(alpha = 0.25f)
        status == RaceStatus.ACTIVE -> C.Live.copy(alpha = glow)
        else -> null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .sharedRaceBounds(race.key)
            .alpha(if (status == RaceStatus.COMPLETED) (if (podium.isNullOrEmpty()) 0.45f else 0.6f) else 1f)
            .then(if (ring != null) Modifier.border(1.dp, ring, CardShape) else Modifier)
            .glassCard(edge)
            .pressable(onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.sharedRaceElement(race.key, "flag")) { Flag(race.circuit.countryCode, 36.dp, 24.dp, 4.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(race.label, style = orbitron(11.sp, FontWeight.SemiBold, C.TextMuted, 0.1f))
                if (stats?.night == true) Badge("NGT", C.Highlight.copy(alpha = 0.85f), icon = R.drawable.lc_moon, bgAlpha = 0.08f)
                if (race.isSprint) Badge("SPR", C.Warning, icon = R.drawable.lc_zap)
            }
            Text(
                race.name,
                style = inter(15.sp, FontWeight.SemiBold, if (status == RaceStatus.COMPLETED) C.TextMuted else C.Text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Ic(R.drawable.lc_map_pin, C.TextMuted, 11.dp)
                Spacer(Modifier.width(5.dp))
                Text(race.circuit.locality, style = inter(12.sp, color = C.TextSecondary))
                val t = stats?.type?.let { typeShort(it) }.orEmpty()
                if (t.isNotEmpty()) Text(" · $t", style = inter(12.sp, color = C.TextMuted))
            }
            if (status == RaceStatus.COMPLETED && !podium.isNullOrEmpty()) PodiumLine(podium)
            if (status == RaceStatus.ACTIVE) {
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (s in race.sessions) Dot(if (s.start < now) C.Success else C.BorderLight, 6.dp)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                isNext -> Badge("NEXT", C.Highlight, bgAlpha = 0.15f, borderAlpha = 0.25f)
                status == RaceStatus.ACTIVE -> Badge("LIVE", C.Live, leading = { LiveDot(6.dp) })
                status == RaceStatus.COMPLETED -> Badge("COMPLETED", C.Success.copy(alpha = 0.8f), icon = R.drawable.lc_check)
                else -> Badge("UPCOMING", C.TextMuted, bgAlpha = 0.25f, borderAlpha = 0.3f)
            }
            val raceSession = race.raceSession ?: race.sessions.last()
            if (status == RaceStatus.UPCOMING) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.lc_clock, C.TextMuted, 12.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(fmt.date(raceSession.start), style = inter(11.sp, color = C.TextMuted))
                    }
                    Row {
                        Text(if (raceSession.tbc) "TBC" else fmt.time(raceSession.start), style = inter(11.sp, color = C.TextSecondary))
                        Text(" " + fmt.abbr(raceSession.start), style = inter(11.sp, color = C.TextMuted))
                    }
                }
            }
            if (status == RaceStatus.ACTIVE) {
                val ns = race.nextSession(now)
                if (ns != null) {
                    val tick = rememberNow(1000)
                    val cd = Fmt.countdown(ns.start, tick)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(ns.name, style = inter(11.sp, color = C.TextMuted, tracking = 0.03f))
                        val st = orbitron(14.sp, FontWeight.Bold, C.Live)
                        val text = buildString {
                            if (cd.days > 0) append("${cd.days}d ")
                            if (cd.hours > 0) append("${cd.hours}h ")
                            append(cd.minutes.toString().padStart(2, '0'))
                            append(":")
                            append(cd.seconds.toString().padStart(2, '0'))
                        }
                        FlipText(text, st)
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Ic(R.drawable.lc_chevron_right, C.TextMuted.copy(alpha = 0.3f), 14.dp)
    }
}

@Composable
private fun RaceDetailPager(
    state: AppState,
    races: List<Race>,
    start: Int,
    globalNextStart: Long?,
    onPage: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = start) { races.size }
    LaunchedEffect(pager.currentPage) { onPage(pager.currentPage) }
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    // A tick each time a swipe lands on another race.
    var lastPage by remember { mutableStateOf(start) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != lastPage) Haptics.segment(view)
        lastPage = pager.settledPage
    }
    // The back / arrows bar lives in the frosted header (App draws it there), so the race
    // page scrolls under it like it does under the header.
    val back by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(pager) {
        val bar: @Composable () -> Unit = {
            RaceBar(
                label = races.getOrNull(pager.currentPage)?.label.orEmpty(),
                canPrev = pager.currentPage > 0,
                canNext = pager.currentPage < races.lastIndex,
                onBack = { back() },
                onPrev = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            )
        }
        state.headerBar = bar
        onDispose { if (state.headerBar === bar) state.headerBar = null }
    }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            RaceDetail(state, races[page], globalNextStart, shared = page == pager.currentPage)
        }
    }
}

/** Back to Schedule, then the previous / next race arrows around the round number. */
@Composable
fun RaceBar(label: String, canPrev: Boolean, canNext: Boolean, onBack: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .height(SectionRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(SectionRowHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(C.SurfaceLight.copy(alpha = 0.5f))
                .border(1.dp, C.GlassBorder, RoundedCornerShape(12.dp))
                .tap(onClick = onBack)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ic(R.drawable.lc_chevron_left, C.TextMuted, 18.dp)
            Spacer(Modifier.width(4.dp))
            Text("Schedule", style = inter(13.sp, FontWeight.Medium, C.TextMuted))
        }
        Spacer(Modifier.weight(1f))
        NavSquare(R.drawable.lc_chevron_left, "Previous race", enabled = canPrev, onClick = onPrev)
        Text(
            label,
            style = orbitron(11.sp, FontWeight.SemiBold, C.TextMuted, 0.1f),
            modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        NavSquare(R.drawable.lc_chevron_right, "Next race", enabled = canNext, onClick = onNext)
    }
}

@Composable
private fun NavSquare(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(SectionRowHeight)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(RoundedCornerShape(12.dp))
            .background(C.SurfaceLight.copy(alpha = 0.5f))
            .border(1.dp, C.GlassBorder, RoundedCornerShape(12.dp))
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ic(icon, C.TextMuted, 18.dp, contentDescription = label) }
}

private fun trackType(type: String) = when (type) {
    "permanent" -> "Permanent"
    "street" -> "Street Circuit"
    "hybrid" -> "Hybrid"
    else -> ""
}

@Composable
private fun RaceDetail(state: AppState, race: Race, globalNextStart: Long?, shared: Boolean = false) {
    val context = LocalContext.current
    val fmt = LocalFmt.current
    // Statuses only change at session starts and ends: every 15 s is plenty. The countdown
    // below ticks every second on its own, so the rest of the page doesn't redraw with it.
    val now = rememberNow(15_000)
    val status = race.status(now)
    val stats = state.circuits[race.circuit.id]
    val live = race.liveSession(now)
    val next = race.nextSession(now)
    val heroEdge = when (status) {
        RaceStatus.COMPLETED -> C.Success.copy(alpha = 0.5f)
        RaceStatus.ACTIVE -> C.Live
        RaceStatus.UPCOMING -> C.Highlight
    }
    LaunchedEffect(race.key) {
        state.loadWeather(race)
        if (!race.isTesting && status != RaceStatus.UPCOMING) state.loadResults(race)
    }
    val forecast = state.weather[race.circuit.id]?.takeIf { status != RaceStatus.COMPLETED }
    var bellNote by remember { mutableStateOf<BellNote?>(null) }
    val raceResults = state.results[race.key]?.race.orEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 12.dp, end = 12.dp,
                top = 12.dp + LocalChrome.current.calculateTopPadding(),
                bottom = 12.dp + LocalChrome.current.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(PageGap),
    ) {
        // Hero
        Column(
            Modifier.fillMaxWidth().sharedRaceBounds(race.key, shared).glassCard(heroEdge).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.sharedRaceElement(race.key, "flag", shared)) { Flag(race.circuit.countryCode, 48.dp, 32.dp, 8.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (race.isTesting) "PRE-SEASON" else "ROUND ${race.round}",
                            style = orbitron(11.sp, FontWeight.SemiBold, C.TextMuted, 0.1f),
                        )
                        if (stats?.night == true) Badge("NIGHT", C.Highlight.copy(alpha = 0.85f), icon = R.drawable.lc_moon, bgAlpha = 0.08f)
                        if (race.isSprint) Badge("SPRINT", C.Warning, icon = R.drawable.lc_zap)
                    }
                    Text(race.name, style = orbitron(18.sp, FontWeight.Bold, C.Text, 0.02f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            FadeLine()
            race.officialName?.let {
                Text(it, style = inter(11.sp, color = C.TextMuted, tracking = 0.15f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.lc_map_pin, C.Highlight, 13.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${race.circuit.name} — ${race.circuit.locality}, ${race.circuit.country}",
                    style = inter(13.sp, color = C.TextSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                status == RaceStatus.COMPLETED && raceResults.isNotEmpty() -> PodiumBlock(raceResults, state.favourite)
                status == RaceStatus.COMPLETED -> StatusBox(C.Success.copy(alpha = 0.2f), null) {
                    Ic(R.drawable.lc_circle_check, C.Success, 32.dp)
                    Text(if (race.isTesting) "TESTING COMPLETE" else "RACE COMPLETE", style = orbitron(13.sp, FontWeight.Bold, C.Success, 0.1f))
                }
                live != null -> StatusBox(C.Live.copy(alpha = 0.25f), C.Live.copy(alpha = 0.08f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveDot(10.dp)
                        Text("LIVE", style = inter(11.sp, FontWeight.Bold, C.Live, 0.1f))
                    }
                    Text(live.name, style = orbitron(14.sp, FontWeight.Bold, sessionColor(live.name), 0.03f))
                }
                next != null && !next.tbc -> {
                    val isGlobalNext = globalNextStart == next.start
                    val color = if (isGlobalNext) sessionColor(next.name) else C.TextMuted
                    val border = if (isGlobalNext) color.copy(alpha = 0.25f) else C.BorderLight
                    val bg = if (isGlobalNext) color.copy(alpha = 0.06f) else null
                    StatusBox(border, bg) {
                        Text("${next.name.uppercase()} STARTS IN", style = inter(11.sp, FontWeight.Medium, color, 0.2f))
                        CountdownDigits(next.start, color)
                    }
                }
            }

            if (stats != null) {
                FadeLine()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stat(R.drawable.lc_ruler, C.Highlight, stats.lengthKm.toString(), "km")
                    Sep()
                    Stat(R.drawable.lc_corner_down_right, C.Highlight, stats.turns.toString(), "turns")
                    Sep()
                    Stat(R.drawable.lc_zap, C.Warning, stats.drsZones.toString(), "DRS")
                    Spacer(Modifier.weight(1f))
                    Text(trackType(stats.type), style = inter(11.sp, FontWeight.Medium, C.TextMuted))
                }
                stats.lapRecord?.let { lr ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.lc_trophy, C.Warning, 11.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Lap record", style = inter(11.sp, color = C.TextSecondary))
                        Spacer(Modifier.weight(1f))
                        Text(lr.time, style = orbitron(11.sp, FontWeight.Bold, C.AccentText))
                        Text("  |  ", style = inter(11.sp, color = C.TextMuted.copy(alpha = 0.4f)))
                        Row(
                            Modifier.tap { openUrl(context, lr.driverUrl) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(lr.driver, style = inter(11.sp, color = C.TextSecondary))
                            Text(" '" + lr.year.toString().takeLast(2), style = inter(11.sp, color = C.TextMuted))
                            Spacer(Modifier.width(4.dp))
                            Ic(R.drawable.lc_external_link, C.TextMuted.copy(alpha = 0.4f), 9.dp)
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(C.SurfaceLight)
                        .border(1.dp, C.GlassBorder, RoundedCornerShape(6.dp))
                        .tap { openUrl(context, stats.wiki) }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("View on Wikipedia", style = inter(11.sp, FontWeight.SemiBold, C.Highlight, 0.03f))
                    Spacer(Modifier.width(6.dp))
                    Ic(R.drawable.lc_external_link, C.Highlight, 11.dp)
                }
            }
        }

        // Below the hero, the cards arrive one by one as the race card opens into the page.
        var order = 1
        // After qualifying, until the race is run: the grid it set.
        val quali = state.results[race.key]?.quali.orEmpty()
        if (!race.isTesting && quali.isNotEmpty() && raceResults.isEmpty()) Box(Modifier.enterStagger(order++)) { GridCard(state, race, quali) }
        if (!race.isTesting && status != RaceStatus.UPCOMING) Box(Modifier.enterStagger(order++)) { ResultsCard(state, race, now) }
        if (forecast != null) Box(Modifier.enterStagger(order++)) { WeatherCard(state, race, forecast) }
        if (!race.isTesting) Box(Modifier.enterStagger(order++)) { TrackCard(race.circuit.id, race.circuit.name) }

        // Sessions
        Column(Modifier.fillMaxWidth().enterStagger(order).glassCard(C.Highlight.copy(alpha = 0.4f))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.lc_clock, C.TextMuted, 12.dp)
                Spacer(Modifier.width(8.dp))
                Text("SESSIONS", style = SectionLabel)
                Spacer(Modifier.weight(1f))
                Text(fmt.abbr(now), style = inter(11.sp, FontWeight.Medium, C.Highlight, 0.05f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
            race.sessions.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
                val isLive = live?.name == s.name
                val isPast = !isLive && s.start < now && !s.tbc
                val isGlobalNext = globalNextStart != null && s.start == globalNextStart
                val results = if (isPast) race.resultsUrl(s) else null
                val rowBg = when {
                    isLive -> C.Live.copy(alpha = 0.08f)
                    isGlobalNext -> C.Highlight.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
                val rowEdge = when {
                    isLive -> C.Live
                    isGlobalNext -> C.Highlight
                    else -> Color.Transparent
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .drawBehind { drawRect(rowEdge, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                        .then(if (results != null) Modifier.tap { openUrl(context, results) } else Modifier)
                        .alpha(if (isPast) 0.4f else 1f)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        isLive -> LiveDot(10.dp)
                        isPast -> Ic(R.drawable.lc_check, C.Success, 14.dp)
                        s.name == "Race" -> Ic(R.drawable.lc_flag, C.AccentText, 14.dp)
                        s.name.contains("Sprint") -> Ic(R.drawable.lc_zap, C.Warning, 14.dp)
                        s.name == "Qualifying" -> Ic(R.drawable.lc_crosshair, C.Highlight, 14.dp)
                        else -> Ic(R.drawable.lc_circle_dot, C.Highlight.copy(alpha = 0.5f), 14.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    val nameColor = when {
                        isLive -> C.Live
                        isPast -> C.TextMuted
                        s.name == "Race" -> C.Text
                        s.name.contains("Sprint") -> C.Warning
                        s.name == "Qualifying" -> C.Highlight
                        else -> C.TextSecondary
                    }
                    Text(
                        s.name,
                        style = inter(14.sp, if (isLive || s.name == "Race") FontWeight.SemiBold else FontWeight.Medium, nameColor)
                            .copy(textDecoration = if (isPast) TextDecoration.LineThrough else null),
                    )
                    if (isGlobalNext && !isLive) {
                        Spacer(Modifier.width(8.dp))
                        Badge("NEXT", C.Highlight, bgAlpha = 0.12f)
                    }
                    if (results != null) {
                        Spacer(Modifier.width(6.dp))
                        Ic(R.drawable.lc_external_link, C.TextMuted.copy(alpha = 0.5f), 10.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (s.tbc) "TBC" else fmt.time(s.start),
                            style = inter(13.sp, color = if (isLive) C.Live else if (isPast) C.TextMuted else C.TextSecondary)
                                .copy(textDecoration = if (isPast) TextDecoration.LineThrough else null),
                        )
                        Text(fmt.date(s.start), style = inter(11.sp, color = C.TextMuted))
                    }
                    val wh = if (!isPast && !s.tbc) forecast?.at(s.start) else null
                    if (wh != null) {
                        Spacer(Modifier.width(10.dp))
                        WeatherChip(state, wh)
                    }
                    // A bell for this one session (future sessions with a time).
                    if (!isPast && !isLive && !s.tbc) {
                        Spacer(Modifier.width(4.dp))
                        SessionBell(state, race, s.name) { msg -> bellNote = msg }
                    }
                }
            }
            // "Qualifying reminder set" with Undo, for a few seconds after a bell is tapped.
            androidx.compose.animation.AnimatedVisibility(bellNote != null) {
                val note = bellNote
                Row(
                    Modifier.fillMaxWidth().background(C.SurfaceLight).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(note?.text.orEmpty(), style = inter(12.sp, color = C.Text), modifier = Modifier.weight(1f))
                    if (note?.undo != null) {
                        Box(
                            Modifier.heightIn(min = 44.dp).tap { note.undo.invoke(); bellNote = null }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Undo", style = inter(13.sp, FontWeight.Bold, C.Highlight)) }
                    } else if (note?.settings == true) {
                        Box(
                            Modifier.heightIn(min = 44.dp).tap { bellNote = null; state.tab = Tab.Settings }.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Settings", style = inter(13.sp, FontWeight.Bold, C.Highlight)) }
                    } else {
                        Spacer(Modifier.height(44.dp))
                    }
                }
            }
            LaunchedEffect(bellNote) { if (bellNote != null) { kotlinx.coroutines.delay(4000); bellNote = null } }
            Text(
                if (state.alertsOn) "Bells change single sessions; Settings › Notifications sets the rest."
                else "Reminders are off in Settings, so the bells are paused.",
                style = inter(11.sp, color = C.TextMuted),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The confirmation under the session list after a bell tap. */
private class BellNote(val text: String, val undo: (() -> Unit)?, val settings: Boolean = false)

/**
 * The bell on one session row: this session's reminder, on or off. Settings decides the
 * default for every session; a bell overrides just this one, either way. Red: on (filled when
 * changed here, outline when it's the usual setting). Slashed: off. With reminders off in
 * Settings every bell is greyed out and a tap says so.
 */
@Composable
private fun SessionBell(state: AppState, race: Race, session: String, note: (BellNote) -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    val slot = app.f1pro.data.Slot(race, race.sessions.first { it.name == session })
    val master = state.alertsOn
    val on = state.bellOn(slot)
    val changed = state.bellOverridden(slot)
    val lead = state.leadMinutes
    fun flip() {
        val now = state.toggleBell(slot)
        if (now) Haptics.confirm(view) else Haptics.tap(view)
        note(
            BellNote(
                if (now) "$session reminder on, $lead min before" else "$session reminder off",
                undo = { state.toggleBell(slot) },
            )
        )
    }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) flip() else note(BellNote("Allow notifications for F1 Pro to get reminders", null)) }
    val color = when {
        !master -> C.TextMuted.copy(alpha = 0.5f)
        on -> C.AccentText
        else -> C.TextMuted
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (master && on && changed) C.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (master && on) C.AccentText.copy(alpha = if (changed) 0.6f else 0.3f) else C.GlassBorder, RoundedCornerShape(10.dp))
            .tap {
                when {
                    !master -> note(BellNote("Reminders are off in Settings", undo = null, settings = true))
                    !on && !app.f1pro.work.Alerts.canNotify(context) && android.os.Build.VERSION.SDK_INT >= 33 ->
                        ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else -> flip()
                }
            }
            .semantics {
                contentDescription = when {
                    !master -> "$session: reminders are off in Settings"
                    on -> "$session reminder on. Tap to turn off"
                    else -> "$session reminder off. Tap to turn on"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Ic(if (master && on) R.drawable.lc_bell else R.drawable.lc_bell_off, color, 18.dp)
    }
}

@Composable
private fun StatusBox(border: Color, bg: Color?, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg ?: C.Card)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun Stat(icon: Int, iconColor: Color, value: String, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, iconColor, 11.dp)
        Spacer(Modifier.width(4.dp))
        Text(value, style = inter(11.sp, FontWeight.SemiBold, C.Text))
        Text(" $unit", style = inter(11.sp, color = C.TextSecondary))
    }
}

@Composable
private fun Sep() {
    Text("  |  ", style = inter(11.sp, color = C.BorderLight))
}

/** D H M S to [start], ticking every second (only this redraws). */
@Composable
private fun CountdownDigits(start: Long, color: Color) {
    val now = rememberNow(1000)
    val cd = Fmt.countdown(start, now)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for ((v, l) in listOf(cd.days to "D", cd.hours to "H", cd.minutes to "M", cd.seconds to "S")) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlipText(v.toString().padStart(2, '0'), orbitron(24.sp, FontWeight.Bold, color))
                Text(l, style = inter(11.sp, FontWeight.Medium, C.TextMuted, 0.1f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
