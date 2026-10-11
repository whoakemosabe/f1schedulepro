package app.f1pro.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.data.Planner
import kotlinx.coroutines.launch

@Composable
fun Header(state: AppState) {
    val now = rememberNow(1000)
    val fmt = LocalFmt.current
    val season = remember(state.seasons, now / 60_000) { state.seasonFor(now) }
    // Which session is next almost never changes; look it up every 10 seconds.
    val slowNow = now / 10_000
    val live = remember(state.seasons, slowNow) { Planner.live(state.seasons, now) }
    val next = remember(state.seasons, slowNow) { Planner.next(state.seasons, now) }

    // A buzz when a session goes live while you're looking.
    val view = androidx.compose.ui.platform.LocalView.current
    var lastLive by remember { androidx.compose.runtime.mutableStateOf(live?.key) }
    androidx.compose.runtime.LaunchedEffect(live?.key) {
        if (live != null && live.key != lastLive) Haptics.confirm(view)
        lastLive = live?.key
    }

    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .shadow(12.dp, RoundedCornerShape(8.dp), ambientColor = C.Accent, spotColor = C.Accent)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("F1", style = orbitron(11.sp, FontWeight.Black, Color.White))
            }
            Spacer(Modifier.width(12.dp))
            Text(state.tab.title.uppercase(), style = orbitron(15.sp, FontWeight.Bold, C.Text, 0.1f))
            Spacer(Modifier.width(10.dp))
            Text((season?.year ?: "").toString(), style = inter(11.sp, FontWeight.Medium, C.TextMuted, 0.2f))
            Spacer(Modifier.weight(1f))
            val (clock, ampm) = fmt.clock(now)
            Text(clock, style = inter(13.sp, FontWeight.Bold, C.Text))
            if (ampm.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
                Text(ampm, style = inter(10.sp, FontWeight.Medium, C.TextMuted))
            }
            Spacer(Modifier.width(8.dp))
            Badge(fmt.abbr(now), C.Highlight, bgAlpha = 0.1f, borderAlpha = 0.2f)
        }

        if (live != null) {
            FadeLine(C.Live.copy(alpha = 0.4f))
            Row(
                Modifier.fillMaxWidth().background(C.Live.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveDot(8.dp)
                Text("LIVE", style = inter(11.sp, FontWeight.Bold, C.Live, 0.1f))
                Text("|", style = inter(11.sp, color = C.BorderLight))
                Flag(live.race.circuit.countryCode, 20.dp, 14.dp, 2.dp)
                Text(
                    live.race.name,
                    style = inter(12.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Badge(live.session.short, sessionColor(live.session.name), bgAlpha = 0.12f, borderAlpha = 0.2f)
            }
        } else if (next != null) {
            val cd = Fmt.countdown(next.session.start, now)
            if (cd.total > 0) {
                FadeLine(C.Highlight.copy(alpha = 0.3f))
                Row(
                    Modifier.fillMaxWidth().background(C.Highlight.copy(alpha = 0.05f)).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                ) {
                    Flag(next.race.circuit.countryCode, 20.dp, 14.dp, 2.dp)
                    Text(
                        next.race.name,
                        style = inter(12.sp, FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("·", style = inter(12.sp, color = C.TextMuted.copy(alpha = 0.4f)))
                    Badge(next.session.short, sessionColor(next.session.name), bgAlpha = 0.12f, borderAlpha = 0.2f)
                    Text("·", style = inter(12.sp, color = C.TextMuted.copy(alpha = 0.4f)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val digit = orbitron(12.sp, FontWeight.Bold, C.Highlight)
                        val unit = inter(9.sp, FontWeight.SemiBold, C.TextMuted)
                        if (cd.days > 0) {
                            FlipText(cd.days.toString(), digit); Text("D", style = unit, modifier = Modifier.padding(start = 1.dp, end = 3.dp))
                        }
                        FlipText(cd.hours.toString().padStart(2, '0'), digit); Text("H", style = unit, modifier = Modifier.padding(start = 1.dp, end = 3.dp))
                        FlipText(cd.minutes.toString().padStart(2, '0'), digit); Text("M", style = unit, modifier = Modifier.padding(start = 1.dp, end = 3.dp))
                        FlipText(cd.seconds.toString().padStart(2, '0'), digit); Text("S", style = unit, modifier = Modifier.padding(start = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNav(state: AppState, modifier: Modifier = Modifier) {
    val tabs = Tab.entries
    val view = androidx.compose.ui.platform.LocalView.current
    val backdrop = LocalPageBackdrop.current
    val shape = RoundedCornerShape(50)
    // Pressing a tab squishes the capsule a little and deepens its lens, like Afli's bar.
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = 1f - 0.02f * press
                scaleY = 1f - 0.03f * press
            }
            .then(if (backdrop != null) Modifier.glassControl(backdrop, shape, press = { press }) else Modifier.background(C.Bar, shape))
            .border(1.dp, Rim, shape),
    ) {
        val tabW = maxWidth / tabs.size
        val idx = tabs.indexOf(state.tab)
        // The PWA's bouncy indicator, now a red glass pill that springs to the chosen tab.
        val x by animateDpAsState(
            tabW * idx,
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
            label = "indicator",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(tabW)
                .fillMaxHeight()
                .padding(5.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(C.Accent.copy(alpha = 0.32f), C.Accent.copy(alpha = 0.14f))))
                .border(1.dp, Brush.verticalGradient(listOf(C.AccentGlow.copy(alpha = 0.85f), C.Accent.copy(alpha = 0.3f))), shape)
        )
        Row(Modifier.fillMaxSize()) {
            for (t in tabs) {
                val active = t == state.tab
                val s by animateFloatAsState(if (active) 1.08f else 1f, tween(200), label = "s")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(source, indication = null) {
                            if (t != state.tab) Haptics.segment(view) else Haptics.tap(view)
                            state.tab = t
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Ic(t.icon, if (active) C.AccentText else C.TextSecondary, 22.dp, Modifier.scale(s))
                    Text(
                        t.title.uppercase(),
                        style = inter(10.sp, FontWeight.SemiBold, if (active) C.AccentText else C.TextSecondary, 0.05f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The update banner across the top of every tab when a new version is waiting, like Afli's.
 * One tap downloads it (progress fills the bar), the next installs it; × hides it until the
 * next version.
 */
@Composable
fun UpdateBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val v = app.f1pro.work.UpdateWatch.waitingVersion
    val ui = app.f1pro.work.UpdateWatch.ui
    val readyVersion = (ui as? app.f1pro.work.UpdateUi.Ready)?.version
    val shownVersion = v ?: readyVersion
    androidx.compose.animation.AnimatedVisibility(
        visible = shownVersion != null && app.f1pro.work.UpdateWatch.bannerHidden != shownVersion,
        enter = androidx.compose.animation.expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) +
            androidx.compose.animation.fadeIn(tween(220)),
        exit = androidx.compose.animation.shrinkVertically(tween(220)) + androidx.compose.animation.fadeOut(tween(180)),
    ) {
        androidx.compose.runtime.LaunchedEffect(Unit) { Haptics.settle(view) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                .then(
                    LocalPageBackdrop.current?.let { Modifier.glassFloat(it, RoundedCornerShape(16.dp)) }
                        ?: Modifier.clip(RoundedCornerShape(16.dp)).background(C.Card)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(C.Accent.copy(alpha = 0.14f), C.Highlight.copy(alpha = 0.08f))))
                .border(1.dp, C.Accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .tap {
                    scope.launch {
                        val ok = app.f1pro.work.UpdateWatch.tap(context)
                        if (ok) Haptics.confirm(view)
                        else if (app.f1pro.work.UpdateWatch.ui is app.f1pro.work.UpdateUi.Error) Haptics.reject(view)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(C.Accent),
                    contentAlignment = Alignment.Center,
                ) { Ic(app.f1pro.R.drawable.lc_download, Color.White, 16.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("F1 Pro $shownVersion is ready", style = orbitron(13.sp, FontWeight.Bold, C.Text, 0.02f))
                    Text(
                        when (ui) {
                            app.f1pro.work.UpdateUi.Idle, app.f1pro.work.UpdateUi.UpToDate -> "Tap to download"
                            app.f1pro.work.UpdateUi.Checking -> "Checking…"
                            is app.f1pro.work.UpdateUi.Downloading -> "Downloading… ${(app.f1pro.work.UpdateWatch.progress * 100).toInt()}%"
                            is app.f1pro.work.UpdateUi.Ready -> ui.note ?: "Downloaded. Tap to install"
                            is app.f1pro.work.UpdateUi.Error -> ui.message
                        },
                        style = inter(12.sp, color = C.TextSecondary),
                    )
                }
                Text(
                    "×",
                    style = inter(22.sp, FontWeight.Light, C.TextMuted),
                    modifier = Modifier
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            Haptics.tap(view)
                            app.f1pro.work.UpdateWatch.bannerHidden = shownVersion
                        }
                        .padding(start = 12.dp, end = 2.dp),
                )
            }
            if (ui is app.f1pro.work.UpdateUi.Downloading) {
                Spacer(Modifier.height(10.dp))
                ProgressLine(app.f1pro.work.UpdateWatch.progress)
            }
        }
    }
}

/** After a crash: what went wrong, with a Copy button so it can be pasted to Claude. */
@Composable
fun CrashCard(state: AppState) {
    val text = state.lastCrash ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var copied by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp)
            .then(
                LocalPageBackdrop.current?.let { Modifier.glassFloat(it, RoundedCornerShape(16.dp)) }
                    ?: Modifier.clip(RoundedCornerShape(16.dp)).background(C.Card)
            )
            .border(1.dp, C.Live.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("F1 Pro crashed last time", style = orbitron(13.sp, FontWeight.Bold, C.Live, 0.02f))
        Text(
            text.lines().filter { it.isNotBlank() }.take(8).joinToString("\n"),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 10.sp,
                color = C.TextSecondary,
                lineHeight = 13.sp,
            ),
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (copied) "Copied" else "Copy details", icon = app.f1pro.R.drawable.lc_bookmark, iconColor = C.Highlight) {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("F1 Pro crash", text))
                copied = true
                Haptics.confirm(view)
            }
            Chip("Dismiss") { state.dismissCrash() }
        }
    }
}
