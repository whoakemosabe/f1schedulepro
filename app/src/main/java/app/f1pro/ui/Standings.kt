package app.f1pro.ui

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
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                item { FavouriteCard(fav, favRow, st?.drivers?.firstOrNull(), st?.drivers?.getOrNull(1)) { state.tab = Tab.Settings } }
            }

            item {
                SlidingSegments(
                    listOf("DRIVERS", "CONSTRUCTORS"),
                    if (view == "drivers") 0 else 1,
                    Modifier.fillMaxWidth(),
                    icons = listOf(R.drawable.lc_trophy, R.drawable.lc_users),
                    height = 44.dp,
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
                        DriverRow(d, leader, fav?.matches(d.name, d.surname) == true) { openUrl(context, it) }
                    }
                }
                else -> {
                    val leader = st.teams.firstOrNull()?.points ?: 0.0
                    items(st.teams, key = { "t-" + it.teamId }) { t ->
                        TeamCard(t, st.drivers.filter { it.teamId == t.teamId }, leader, fav) { openUrl(context, it) }
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

@Composable
private fun DriverRow(d: DriverStanding, leader: Double, isFav: Boolean, open: (String) -> Unit) {
    val color = Color(Teams.color(d.teamId))
    val behind = if (d.points < leader) leader - d.points else 0.0
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (isFav) Modifier.border(1.dp, color.copy(alpha = 0.55f), CardShape) else Modifier)
            .glassCard(fill = if (isFav) androidx.compose.ui.graphics.lerp(C.Card, color, 0.1f) else C.Card)
            .leftEdge(color)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(d.position.toString(), style = orbitron(15.sp, FontWeight.Bold, C.TextMuted), modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(8.dp))
        Flag(Countries.code(d.nationality), 24.dp, 16.dp, 2.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.tap { open(d.url) }, verticalAlignment = Alignment.CenterVertically) {
                Text(d.name, style = inter(14.sp, FontWeight.SemiBold), maxLines = 1)
                Text(" " + d.surname, style = inter(14.sp, color = C.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Ic(R.drawable.lc_external_link, C.TextMuted, 10.dp)
            }
            Row(Modifier.tap { Teams.url(d.teamId)?.let(open) }, verticalAlignment = Alignment.CenterVertically) {
                Text(d.teamName.uppercase(), style = inter(11.sp, FontWeight.Medium, readable(color), 0.05f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Ic(R.drawable.lc_external_link, C.TextMuted, 8.dp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.points(d.points), style = orbitron(14.sp, FontWeight.Bold, C.Text))
                Text(" pts", style = inter(11.sp, color = C.TextMuted))
            }
            if (behind > 0) Text("-" + Fmt.points(behind), style = inter(11.sp, color = C.TextMuted))
        }
    }
}

@Composable
private fun TeamCard(t: TeamStanding, drivers: List<DriverStanding>, leader: Double, fav: app.f1pro.data.Favourite?, open: (String) -> Unit) {
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
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.position.toString(), style = orbitron(15.sp, FontWeight.Bold, C.TextMuted))
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).tap { Teams.url(t.teamId)?.let(open) }, verticalAlignment = Alignment.CenterVertically) {
                Text(t.teamName.uppercase(), style = inter(14.sp, FontWeight.Bold, readable(color), 0.03f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Ic(R.drawable.lc_external_link, C.TextMuted, 10.dp)
            }
            Text(Fmt.points(t.points), style = orbitron(14.sp, FontWeight.Bold, C.Text))
            Text(" pts", style = inter(11.sp, color = C.TextMuted))
            if (behind > 0) Text("  -" + Fmt.points(behind), style = inter(11.sp, color = C.TextMuted))
        }
        for (d in drivers) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
            Row(
                Modifier.fillMaxWidth().tap { open(d.url) }.padding(start = 16.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${d.name} ${d.surname}", style = inter(13.sp, color = C.TextSecondary))
                Spacer(Modifier.width(4.dp))
                Ic(R.drawable.lc_external_link, C.TextMuted, 9.dp)
                Spacer(Modifier.weight(1f))
                Text(Fmt.points(d.points), style = inter(13.sp, FontWeight.SemiBold, C.Text))
            }
        }
    }
}

/** "Your driver" at the top of Standings: position, points, gap and wins. */
@Composable
private fun FavouriteCard(
    fav: app.f1pro.data.Favourite,
    row: DriverStanding?,
    leader: DriverStanding?,
    second: DriverStanding?,
    onChange: () -> Unit,
) {
    val color = Color(Teams.color(row?.teamId ?: fav.teamId))
    val shape = CardShape
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
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
        Chip("Change", onClick = onChange)
    }
}
