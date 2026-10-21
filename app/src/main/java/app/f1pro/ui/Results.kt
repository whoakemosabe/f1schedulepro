package app.f1pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.R
import app.f1pro.data.Favourite
import app.f1pro.data.PodiumEntry
import app.f1pro.data.QualiResult
import app.f1pro.data.Race
import app.f1pro.data.RaceResult
import app.f1pro.data.Teams

private fun team(id: String) = Color(Teams.color(id))

/** "1 VER · 2 ANT · 3 HAM" under a finished race's card. */
@Composable
fun PodiumLine(entries: List<PodiumEntry>) {
    Row(
        Modifier.padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (e in entries) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(width = 3.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).background(team(e.constructorId)))
                Text("${e.position}", style = orbitron(10.sp, FontWeight.Bold, C.TextMuted))
                Text(e.code, style = inter(11.sp, FontWeight.SemiBold, if (e.position == 1) C.Text else C.TextSecondary, 0.04f))
            }
        }
    }
}

/** The top three on steps, P2 · P1 · P3, coloured by team. */
@Composable
fun PodiumBlock(race: List<RaceResult>, fav: Favourite?) {
    val byPos = race.filter { (it.position ?: 0) in 1..3 }.associateBy { it.position ?: 0 }
    if (byPos.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        for ((pos, h) in listOf(2 to 66, 1 to 92, 3 to 52)) {
            val r = byPos[pos]
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r != null) {
                    val isFav = fav?.matches(r.givenName, r.familyName) == true
                    Text(
                        r.familyName + if (isFav) " ★" else "",
                        style = inter(if (pos == 1) 14.sp else 13.sp, if (pos == 1) FontWeight.Bold else FontWeight.SemiBold, C.Text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(r.gap, style = inter(11.sp, color = C.TextMuted), maxLines = 1)
                }
                val c = r?.let { team(it.constructorId) } ?: C.BorderLight
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(h.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.06f))))
                        .background(Brush.verticalGradient(listOf(c, Color.Transparent), endY = 8f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("$pos", style = orbitron(if (pos == 1) 26.sp else 21.sp, FontWeight.Black, C.Text))
                    if (r?.fastestRank == 1) {
                        Text("FASTEST LAP", style = inter(10.sp, FontWeight.Bold, Purple, 0.08f))
                    }
                }
            }
        }
    }
}

/** Fastest-lap purple, like the timing screens. */
val Purple get() = if (C.dark) Color(0xFFC9A6FF) else Color(0xFF7B3FD1)

/** Race, Sprint and Qualifying results for a weekend, in one card with tabs. */
@Composable
fun ResultsCard(state: AppState, race: Race, now: Long) {
    LaunchedEffect(race.key) { state.loadResults(race) }
    val res = state.results[race.key]
    val loading = state.resultsLoading[race.key] == true
    val fav = state.favourite

    fun ended(name: String) = race.sessions.any { it.name == name && !it.tbc && it.end < now }
    val tabs = buildList {
        if (ended("Race") || res?.race?.isNotEmpty() == true) add("Race")
        if (race.isSprint && (ended("Sprint") || res?.sprint?.isNotEmpty() == true)) add("Sprint")
        if (ended("Qualifying") || res?.quali?.isNotEmpty() == true) add("Qualifying")
    }
    if (tabs.isEmpty()) return
    var picked by remember(race.key) { mutableStateOf(tabs.first()) }
    val tab = if (picked in tabs) picked else tabs.first()

    Column(Modifier.fillMaxWidth().glassCard(C.Accent.copy(alpha = 0.5f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.lc_trophy, C.TextMuted, 12.dp)
            Spacer(Modifier.width(8.dp))
            Text("RESULTS", style = SectionLabel)
            Spacer(Modifier.weight(1f))
            if (loading) Text("Updating…", style = inter(11.sp, color = C.TextMuted))
        }
        if (tabs.size > 1) {
            SlidingSegments(
                tabs.map { it.uppercase() },
                tabs.indexOf(tab),
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                height = 38.dp,
            ) { picked = tabs[it] }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))

        val raceRows = when (tab) {
            "Race" -> res?.race.orEmpty()
            "Sprint" -> res?.sprint.orEmpty()
            else -> emptyList()
        }
        val qualiRows = if (tab == "Qualifying") res?.quali.orEmpty() else emptyList()
        when {
            raceRows.isEmpty() && qualiRows.isEmpty() && (loading || res == null && state.resultsFailed[race.key] != true) ->
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { repeat(5) { Skeleton(36.dp) } }
            raceRows.isEmpty() && qualiRows.isEmpty() ->
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (state.resultsFailed[race.key] == true) "Couldn't load results." else "Results aren't out yet.",
                        style = inter(13.sp, FontWeight.Medium, C.TextSecondary),
                    )
                    Text(
                        "They usually appear an hour or two after the session.",
                        style = inter(12.sp, color = C.TextMuted),
                        textAlign = TextAlign.Center,
                    )
                    Chip("Try again", icon = R.drawable.lc_refresh_cw, iconColor = C.AccentText) { state.loadResults(race) }
                }
            tab == "Qualifying" -> {
                QualiHeader()
                val gaps = remember(qualiRows) { app.f1pro.data.QualiGaps.gaps(qualiRows) }
                qualiRows.forEachIndexed { i, q ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
                    QualiRow(q, fav?.matches(q.givenName, q.familyName) == true, gaps.getOrNull(i))
                }
            }
            else -> {
                RaceHeader()
                // The weekend's biggest climber from the grid (3+ places) gets a badge.
                val mover = remember(raceRows) { raceRows.filter { (it.gained ?: 0) >= 3 }.maxByOrNull { it.gained ?: 0 } }
                raceRows.forEachIndexed { i, r ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.GlassBorder))
                    RaceRow(r, fav?.matches(r.givenName, r.familyName) == true, mover = r === mover)
                }
            }
        }
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = inter(10.sp, FontWeight.Bold, C.TextMuted, 0.1f), modifier = modifier, textAlign = align)
}

@Composable
private fun RaceHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderText("POS", Modifier.width(30.dp), TextAlign.Center)
        Spacer(Modifier.width(13.dp))
        HeaderText("DRIVER", Modifier.weight(1f))
        HeaderText("GAP", Modifier.width(78.dp), TextAlign.End)
        HeaderText("PTS", Modifier.width(34.dp), TextAlign.End)
    }
}

@Composable
private fun QualiHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderText("POS", Modifier.width(30.dp), TextAlign.Center)
        Spacer(Modifier.width(13.dp))
        HeaderText("DRIVER", Modifier.weight(1f))
        HeaderText("BEST · GAP", Modifier.width(110.dp), TextAlign.End)
    }
}

private fun rowModifier(isFav: Boolean, color: Color): Modifier =
    Modifier
        .fillMaxWidth()
        .background(if (isFav) color.copy(alpha = 0.12f) else Color.Transparent)
        .padding(horizontal = 14.dp, vertical = 8.dp)

@Composable
private fun RaceRow(r: RaceResult, isFav: Boolean, mover: Boolean = false) {
    val color = team(r.constructorId)
    Row(rowModifier(isFav, color), verticalAlignment = Alignment.CenterVertically) {
        val label = when (r.positionText) {
            "R" -> "DNF"
            "D", "E" -> "DSQ"
            "W" -> "DNS"
            "N" -> "NC"
            "F" -> "DNQ"
            else -> "${r.position ?: "–"}"
        }
        Text(
            label,
            style = orbitron(if (label.length > 2) 9.sp else 13.sp, FontWeight.Bold, if (r.out) C.TextMuted else C.TextSecondary),
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(width = 3.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.name + if (isFav) " ★" else "",
                    style = inter(14.sp, if (isFav) FontWeight.Bold else FontWeight.SemiBold, if (r.out) C.TextMuted else C.Text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (r.fastestRank == 1) {
                    Spacer(Modifier.width(6.dp))
                    Badge(R.drawable.lc_timer, "FL", Purple, "Fastest lap" + (r.fastestLap?.let { ", $it" } ?: ""))
                }
                if (mover) {
                    Spacer(Modifier.width(6.dp))
                    Badge(R.drawable.lc_zap, "+${r.gained}", C.Success, "Biggest mover, up ${r.gained} places")
                }
            }
            val g = r.gained
            val (sub, subColor) = when {
                r.grid == 0 -> "${Teams.shortName(r.constructorName)} · from pit lane" to C.TextSecondary
                g == null -> Teams.shortName(r.constructorName) to C.TextSecondary
                g > 0 -> "${Teams.shortName(r.constructorName)} · up $g" to C.Success
                g < 0 -> "${Teams.shortName(r.constructorName)} · down ${-g}" to C.TextMuted
                else -> "${Teams.shortName(r.constructorName)} · from P${r.grid}" to C.TextSecondary
            }
            Text(sub, style = inter(11.sp, color = subColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            r.gap,
            style = inter(12.sp, color = if (r.out) C.Live.copy(alpha = 0.8f) else C.TextSecondary),
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (r.points > 0) app.f1pro.Fmt.points(r.points) else "",
            style = orbitron(12.sp, FontWeight.Bold, C.Text),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun QualiRow(q: QualiResult, isFav: Boolean, gap: Long?) {
    val color = team(q.constructorId)
    Row(rowModifier(isFav, color), verticalAlignment = Alignment.CenterVertically) {
        Text("${q.position ?: "–"}", style = orbitron(13.sp, FontWeight.Bold, C.TextSecondary), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(width = 3.dp, height = 28.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                q.name + if (isFav) " ★" else "",
                style = inter(14.sp, if (isFav) FontWeight.Bold else FontWeight.SemiBold, C.Text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Teams.shortName(q.constructorName), style = inter(11.sp, color = C.TextSecondary), maxLines = 1)
        }
        Column(Modifier.width(110.dp), horizontalAlignment = Alignment.End) {
            Text(q.best ?: "No time", style = inter(12.sp, FontWeight.Medium, if (q.best == null) C.TextMuted else C.Text))
            if (q.part.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // The gap to the fastest in the same part: Q3 to pole, Q2 to the best Q2…
                    val pole = gap == 0L && q.part == "Q3"
                    val gapText = when {
                        gap == null -> null
                        pole -> "POLE"
                        gap == 0L -> "fastest"
                        else -> app.f1pro.data.QualiGaps.format(gap)
                    }
                    gapText?.let {
                        Text(
                            it,
                            style = if (pole) inter(10.sp, FontWeight.Bold, C.Highlight, 0.06f)
                            else inter(11.sp, FontWeight.Medium, C.TextSecondary).copy(fontFeatureSettings = "tnum"),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        q.part,
                        style = inter(10.sp, FontWeight.Bold, if (q.part == "Q3") C.Highlight else C.TextMuted, 0.06f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, (if (q.part == "Q3") C.Highlight else C.TextMuted).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp),
                    )
                }
            }
        }
    }
}

/** A small coloured tag on a result row (fastest lap, biggest mover). */
@Composable
private fun Badge(@androidx.annotation.DrawableRes icon: Int, text: String, color: Color, description: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .semantics { contentDescription = description }
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(icon, color, 9.dp)
        Spacer(Modifier.width(3.dp))
        Text(text, style = inter(10.sp, FontWeight.Bold, color, 0.05f), maxLines = 1)
    }
}
