package app.f1pro.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.f1pro.Fmt
import app.f1pro.MainActivity
import app.f1pro.Prefs
import app.f1pro.R
import app.f1pro.data.DAY_MS
import app.f1pro.data.Planner
import app.f1pro.data.Race
import app.f1pro.data.Repo
import app.f1pro.data.Slot
import app.f1pro.work.Scheduler
import app.f1pro.work.Ticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Widgets {
    suspend fun updateAll(context: Context) {
        NextWidget().updateAll(context)
        WeekendWidget().updateAll(context)
    }
}

private val Bg = Color(0xF20C0F1A)
private val Text1 = Color(0xFFF0F2FF)
private val Text2 = Color(0xFFA0A8C8)
private val Muted = Color(0xFF6670A0)
private val Red = Color(0xFFE10600)
private val Cyan = Color(0xFF00D4FF)
private val Amber = Color(0xFFFFAB00)

private fun sessionColor(name: String): Color = when {
    name == "Race" -> Red
    name.contains("Sprint") -> Amber
    name == "Qualifying" -> Cyan
    else -> Text2
}

/** Everything the widgets show, computed from the cached calendar. */
class WidgetData(
    val now: Long,
    val fmt: Fmt,
    val live: Slot?,
    val next: Slot?,
    val weekend: Race?,
) {
    companion object {
        fun load(context: Context): WidgetData {
            val now = System.currentTimeMillis()
            val prefs = Prefs(context)
            val seasons = Repo(context).seasons()
            return WidgetData(
                now = now,
                fmt = Fmt(prefs.zone(), prefs.is24h(context)),
                live = Planner.live(seasons, now),
                next = Planner.next(seasons, now),
                weekend = Planner.weekend(seasons, now),
            )
        }
    }
}

class NextWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { WidgetData.load(context) }
        provideContent { NextContent(context, data) }
    }
}

class WeekendWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { WidgetData.load(context) }
        provideContent { WeekendContent(context, data) }
    }
}

class NextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Scheduler.ensure(context)
        Ticker.schedule(context)
    }
}

class WeekendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekendWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Scheduler.ensure(context)
        Ticker.schedule(context)
    }
}

@Suppress("UNUSED_PARAMETER")
private fun style(
    color: Color,
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    condensed: Boolean = false,
    strike: Boolean = false,
) = TextStyle(
    color = ColorProvider(color),
    fontSize = size.sp,
    fontWeight = weight,
    textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
)

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(Bg)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Box(modifier = GlanceModifier.width(4.dp).fillMaxHeight().background(Red)) {}
            Box(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
        }
    }
}

/** A countdown that ticks by itself in the launcher (under a day), or a plain "in 3d 4h". */
@Composable
private fun Countdown(context: Context, target: Long, now: Long, small: Boolean) {
    val rem = target - now
    if (rem in 1 until DAY_MS) {
        val rv = RemoteViews(context.packageName, if (small) R.layout.widget_chrono_small else R.layout.widget_chrono)
        rv.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + rem, null, true)
        rv.setChronometerCountDown(R.id.chrono, true)
        AndroidRemoteViews(rv)
    } else {
        Text(Fmt.until(target, now), style = style(Cyan, if (small) 15 else 20, FontWeight.Bold, condensed = true), maxLines = 1)
    }
}

@Composable
private fun NextContent(context: Context, d: WidgetData) {
    Card {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            val live = d.live
            val next = d.next
            if (live != null) {
                Text("● LIVE", style = style(Red, 11, FontWeight.Bold))
                Spacer(GlanceModifier.height(4.dp))
                Text("${live.race.circuit.flag} ${live.race.name}", style = style(Text1, 13, FontWeight.Medium), maxLines = 2)
                Spacer(GlanceModifier.defaultWeight())
                Text(live.session.name.uppercase(), style = style(sessionColor(live.session.name), 18, FontWeight.Bold, condensed = true), maxLines = 1)
                if (next != null) {
                    Text("Next: ${next.session.short} ${d.fmt.day(next.session.start)} ${d.fmt.shortTime(next.session.start)}", style = style(Muted, 11), maxLines = 1)
                }
            } else if (next != null) {
                Text("NEXT SESSION", style = style(Muted, 10, FontWeight.Bold))
                Spacer(GlanceModifier.height(4.dp))
                Text("${next.race.circuit.flag} ${next.race.name}", style = style(Text1, 13, FontWeight.Medium), maxLines = 2)
                Spacer(GlanceModifier.defaultWeight())
                Text(next.session.name.uppercase(), style = style(sessionColor(next.session.name), 11, FontWeight.Bold), maxLines = 1)
                Countdown(context, next.session.start, d.now, small = LocalSize.current.width.value < 150)
                Text(
                    "${d.fmt.day(next.session.start)} ${d.fmt.shortTime(next.session.start)} ${d.fmt.abbr(next.session.start)}",
                    style = style(Text2, 11),
                    maxLines = 1,
                )
            } else {
                Text("F1 PRO", style = style(Red, 12, FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text("No sessions scheduled", style = style(Text1, 13, FontWeight.Medium))
                Text("Open the app to refresh", style = style(Muted, 11))
            }
        }
    }
}

@Composable
private fun WeekendContent(context: Context, d: WidgetData) {
    Card {
        val race = d.weekend
        if (race == null) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text("F1 PRO", style = style(Red, 12, FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text("No race weekend scheduled", style = style(Text1, 13, FontWeight.Medium))
            }
            return@Card
        }
        val nextInRace = race.nextSession(d.now)
        val liveInRace = race.liveSession(d.now)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${race.circuit.flag} ", style = style(Text1, 14))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(race.name, style = style(Text1, 13, FontWeight.Bold), maxLines = 1)
                    Text("${race.label} · ${race.circuit.locality}", style = style(Muted, 10), maxLines = 1)
                }
                if (liveInRace != null) {
                    Text("● LIVE", style = style(Red, 11, FontWeight.Bold))
                } else if (nextInRace != null && !nextInRace.tbc) {
                    Countdown(context, nextInRace.start, d.now, small = true)
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            for (s in race.sessions.take(5)) {
                val past = !s.isLive(d.now) && s.start < d.now
                val isLive = s.isLive(d.now)
                val isNext = !isLive && nextInRace?.name == s.name
                val color = when {
                    isLive -> Red
                    past -> Muted
                    isNext -> Cyan
                    else -> Text2
                }
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (past) "✓ " else if (isLive) "● " else if (isNext) "▸ " else "  ") + s.name,
                        style = style(color, 12, if (isNext || isLive) FontWeight.Bold else FontWeight.Normal, strike = past),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1,
                    )
                    Text(
                        if (s.tbc) "${d.fmt.day(s.start)} TBC" else "${d.fmt.day(s.start)} ${d.fmt.shortTime(s.start)}",
                        style = style(if (past) Muted else Text2, 12, FontWeight.Medium, condensed = true),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
