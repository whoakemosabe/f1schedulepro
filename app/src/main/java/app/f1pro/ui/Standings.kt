package app.f1pro.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import app.f1pro.data.Countries
import app.f1pro.data.DriverStanding
import app.f1pro.data.Teams
import app.f1pro.data.TeamStanding

private fun Modifier.leftEdge(color: Color, width: Float = 4f) = this.drawBehind {
    drawRect(color, size = androidx.compose.ui.geometry.Size(width * density, size.height))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsPage(state: AppState, listState: LazyListState) {
    val context = LocalContext.current
    var view by remember { mutableStateOf(state.prefs.standingsView) }
    LaunchedEffect(Unit) { if (state.standings == null) state.loadStandings() }
    val st = state.standings
    LaunchedEffect(state.sheetDriver, state.sheetTeam) {
        if (state.sheetDriver != null || state.sheetTeam != null) state.dismissStandingsHint()
    }
    val fav = state.favourite
    state.teamColoursVersion // redraw when the official team colours arrive
    val now = rememberNow(60_000)
    val calendarYear = remember(state.seasons, now) { state.seasonFor(now)?.year }
    val hv = androidx.compose.ui.platform.LocalView.current
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.standingsLoading) {
        if (!state.standingsLoading && pulled) {
            if (state.standingsError == null) Haptics.confirm(hv) else Haptics.reject(hv)
            pulled = false
        }
    }

    RefreshBox(
        isRefreshing = state.standingsLoading && st != null,
        onRefresh = {
            Haptics.settle(hv)
            pulled = true
            state.loadStandings()
            state.refreshDriverData()
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(),
            verticalArrangement = Arrangement.spacedBy(PageGap),
        ) {
            if (st != null && calendarYear != null && st.season in 1 until calendarYear) {
                item {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(CardShape)
                            .background(C.Warning.copy(alpha = 0.05f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Ic(R.drawable.lc_info, C.Warning, 18.dp, Modifier.padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${st.season} Season Standings", style = inter(14.sp, FontWeight.SemiBold, C.Warning))
                            Text(
                                "The $calendarYear season hasn't scored points yet. Standings update automatically.",
                                style = inter(12.sp, color = C.TextSecondary),
                            )
                        }
                    }
                }
            }

            val favRow = fav?.let { f -> st?.drivers?.firstOrNull { f.matches(it.name, it.surname) } }
            if (fav != null) {
                item { FavouriteCard(state, fav, favRow, st?.drivers?.firstOrNull(), st?.drivers?.getOrNull(1)) { favRow?.let { Haptics.tap(hv); state.sheetDriver = it } } }
            }

            if (st != null && st.drivers.isNotEmpty()) {
                item(key = "title-fight") { TitleFightCard(state, st) }
            }

            if (state.standingsHint && st != null) {
                item { StandingsHint { state.dismissStandingsHint() } }
            }

            item {
                SlidingSegments(
                    listOf("DRIVERS", "CONSTRUCTORS"),
                    if (view == "drivers") 0 else 1,
                    Modifier.fillMaxWidth(),
                    icons = listOf(R.drawable.lc_trophy, R.drawable.lc_users),
                    height = SectionRowHeight,
                ) {
                    view = if (it == 0) "drivers" else "constructors"
                    state.prefs.standingsView = view
                }
            }

            when {
                st == null && (state.standingsLoading || state.standingsError == null) -> items(10) { Skeleton(56.dp) }
                st == null -> item {
                    Column(
                        Modifier.fillMaxWidth().glassCard().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Ic(R.drawable.lc_circle_alert, C.Live, 28.dp)
                        Text(state.standingsError ?: "Failed to load standings", style = inter(14.sp, color = C.TextSecondary), textAlign = TextAlign.Center)
                        Row(Modifier.tap { state.loadStandings() }, verticalAlignment = Alignment.CenterVertically) {
                            Ic(R.drawable.lc_refresh_cw, C.AccentText, 12.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Retry", style = inter(12.sp, FontWeight.SemiBold, C.AccentText))
                        }
                    }
                }
                view == "drivers" -> {
                    val leader = st.drivers.firstOrNull()?.points ?: 0.0
                    items(st.drivers, key = { "d-" + it.surname + it.position }) { d ->
                        DriverRow(
                            d, leader, fav?.matches(d.name, d.surname) == true,
                            onDriver = { state.sheetDriver = d },
                            onTeam = { Haptics.tap(hv); state.sheetTeam = st.teams.firstOrNull { it.teamId == d.teamId } ?: return@DriverRow },
                        )
                    }
                }
                else -> {
                    val leader = st.teams.firstOrNull()?.points ?: 0.0
                    items(st.teams, key = { "t-" + it.teamId }) { t ->
                        TeamCard(
                            t, st.drivers.filter { it.teamId == t.teamId }, leader, fav,
                            onTeam = { Haptics.tap(hv); state.sheetTeam = t },
                            onDriver = { Haptics.tap(hv); state.sheetDriver = it },
                        )
                    }
                }
            }
            if (st != null) {
                item {
                    Text(
                        "Updated ${Fmt.ago(st.fetchedAt, System.currentTimeMillis())} · pull down to refresh",
                        style = inter(11.sp, color = C.TextMuted),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Size of the code chip: fixed, so every code lines up and the flag below can match it. */
val CodeChipWidth = 54.dp
val CodeChipHeight = 28.dp
private val CodeChipSmallWidth = 42.dp
private val CodeChipSmallHeight = 22.dp

/**
 * The driver's three-letter code ("NOR") on a rounded chip in team colour, black or white
 * text, whichever reads better. A fixed size that fits the widest codes (W, M) in Orbitron.
 */
@Composable
fun CodeChip(code: String, color: Color, large: Boolean = true) {
    val ink = if (contrast(Color.White, color) >= contrast(Color(0xFF0C0F1A), color)) Color.White else Color(0xFF0C0F1A)
    Box(
        Modifier
            .size(if (large) CodeChipWidth else CodeChipSmallWidth, if (large) CodeChipHeight else CodeChipSmallHeight)
            .clip(RoundedCornerShape(if (large) 7.dp else 6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code.take(3),
            style = orbitron(if (large) 12.sp else 10.sp, FontWeight.Black, ink, 0.02f),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One driver on Standings: code chip, big surname, number, a team pill (its own tap: the team's
 * panel) and a chevron. The whole card opens the driver's panel and presses in when touched.
 */
@Composable
private fun DriverRow(d: DriverStanding, leader: Double, isFav: Boolean, onDriver: () -> Unit, onTeam: () -> Unit) {
    val color = Color(Teams.color(d.teamId))
    val behind = if (d.points < leader) leader - d.points else 0.0
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (isFav) Modifier.border(1.dp, color.copy(alpha = 0.6f), CardShape) else Modifier)
            .clip(CardShape)
            .background(
                if (isFav) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0.16f), C.Card))
                else androidx.compose.ui.graphics.SolidColor(C.Card)
            )
            .border(1.dp, C.GlassBorder, CardShape)
            .leftEdge(color)
            .pressable(onDriver)
            .semantics(mergeDescendants = true) {
                contentDescription = "${d.name} ${d.surname}, ${d.teamName}, P${d.position}, ${Fmt.points(d.points)} points. Opens their season."
            }
            .heightIn(min = 76.dp)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(d.position.toString(), style = orbitron(15.sp, FontWeight.Bold, C.TextMuted), modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        // Code chip with the flag the same size, lined up underneath.
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CodeChip(d.code, color)
            Flag(Countries.code(d.nationality), CodeChipWidth, CodeChipHeight, 7.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(d.name, style = inter(12.sp, color = C.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                d.number?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("#$it", style = orbitron(11.sp, FontWeight.Bold, readable(color)))
                }
            }
            Text(d.surname.uppercase(), style = orbitron(17.sp, FontWeight.Bold, C.Text, 0.02f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // The team pill: its own target, opens the team.
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .tap(onClick = onTeam)
                    .semantics { contentDescription = "${d.teamName}. Opens the team." }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(Teams.shortName(d.teamName), style = inter(11.sp, FontWeight.SemiBold, readable(color)), maxLines = 1)
                Text(" ›", style = inter(11.sp, color = C.TextMuted))
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.points(d.points), style = orbitron(16.sp, FontWeight.Bold, C.Text))
                Text(" pts", style = inter(11.sp, color = C.TextMuted))
            }
            Text(if (behind > 0) "−" + Fmt.points(behind) else if (d.position == 1) "Leader" else "", style = inter(11.sp, color = C.TextMuted))
        }
        Ic(R.drawable.lc_chevron_right, C.TextMuted, 18.dp, Modifier.padding(start = 2.dp))
    }
}

/**
 * A team on Standings: the header opens the team, each driver line opens that driver, and a
 * "View team" bar in team colour runs along the bottom.
 */
@Composable
private fun TeamCard(
    t: TeamStanding,
    drivers: List<DriverStanding>,
    leader: Double,
    fav: app.f1pro.data.Favourite?,
    onTeam: () -> Unit,
    onDriver: (DriverStanding) -> Unit,
) {
    val color = Color(Teams.color(t.teamId))
    val behind = if (t.points < leader) leader - t.points else 0.0
    val favTeam = fav != null && fav.teamId == t.teamId
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (favTeam) Modifier.border(1.dp, color.copy(alpha = 0.55f), CardShape) else Modifier)
            .glassCard(fill = if (favTeam) androidx.compose.ui.graphics.lerp(C.Card, color, 0.08f) else C.Card)
            .leftEdge(color)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .tap(onClick = onTeam)
                .semantics(mergeDescendants = true) { contentDescription = "${t.teamName}, P${t.position}, ${Fmt.points(t.points)} points. Opens the team." }
                .heightIn(min = 52.dp)
                .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.position.toString(), style = orbitron(15.sp, FontWeight.Bold, C.TextMuted))
            Spacer(Modifier.width(10.dp))
            Text(t.teamName.uppercase(), style = orbitron(16.sp, FontWeight.Bold, readable(color), 0.03f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.points(t.points), style = orbitron(16.sp, FontWeight.Bold, C.Text))
                    Text(" pts", style = inter(11.sp, color = C.TextMuted))
                }
                if (behind > 0) Text("−" + Fmt.points(behind), style = inter(11.sp, color = C.TextMuted))
            }
        }
        for (d in drivers) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
            Row(
                Modifier
                    .fillMaxWidth()
                    .tap { onDriver(d) }
                    .semantics(mergeDescendants = true) { contentDescription = "${d.name} ${d.surname}, ${Fmt.points(d.points)} points. Opens their season." }
                    .heightIn(min = 52.dp)
                    .padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CodeChip(d.code, color, large = false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = inter(11.sp, color = C.TextSecondary), maxLines = 1)
                    Text(d.surname.uppercase(), style = orbitron(13.sp, FontWeight.Bold, C.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(Fmt.points(d.points), style = inter(13.sp, FontWeight.SemiBold, C.Text))
                Ic(R.drawable.lc_chevron_right, C.TextMuted, 16.dp, Modifier.padding(start = 4.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.12f))
                .tap(onClick = onTeam)
                .heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("View team", style = inter(12.sp, FontWeight.SemiBold, readable(color), 0.04f))
            Ic(R.drawable.lc_chevron_right, readable(color), 14.dp, Modifier.padding(start = 4.dp))
        }
    }
}

/** "Your driver" at the top of Standings: position, points, gap and wins. */
@Composable
private fun FavouriteCard(
    state: AppState,
    fav: app.f1pro.data.Favourite,
    row: DriverStanding?,
    leader: DriverStanding?,
    second: DriverStanding?,
    onOpen: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val color = Color(Teams.color(row?.teamId ?: fav.teamId))
    val shape = CardShape
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .tap(onClick = onOpen)
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.02f))))
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_star, color, 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("YOUR DRIVER", style = inter(11.sp, FontWeight.SemiBold, C.TextMuted, 0.08f))
            Text(fav.name, style = inter(15.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = if (row == null) {
                "Not in this season's standings"
            } else {
                val gap = when {
                    row.position == 1 && second != null -> " · leads by ${Fmt.points(row.points - second.points)}"
                    leader != null && row.position != 1 -> " · ${Fmt.points(leader.points - row.points)} behind"
                    else -> ""
                }
                "${Teams.shortName(row.teamName)} · P${row.position} · ${Fmt.points(row.points)} pts$gap"
            }
            Text(line, style = inter(12.sp, FontWeight.Medium, readable(color)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        // Change opens the driver list right here.
        Box {
            Chip("Change") { picking = true }
            DriverPickerMenu(state, picking) { picking = false }
        }
    }
}

/** Shown once: tells people the rows open panels. Gone after × or the first panel opened. */
@Composable
private fun StandingsHint(onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.Highlight.copy(alpha = 0.08f))
            .border(1.dp, C.Highlight.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.lc_info, C.Highlight, 16.dp)
        Spacer(Modifier.width(10.dp))
        Text("Tap a driver or team for their season", style = inter(12.sp, color = C.TextSecondary), modifier = Modifier.weight(1f))
        Box(
            Modifier.size(48.dp).tap(onClick = onDismiss).semantics { contentDescription = "Dismiss hint" },
            contentAlignment = Alignment.Center,
        ) { Text("×", style = inter(18.sp, color = C.TextMuted)) }
    }
}
