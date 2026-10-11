package app.f1pro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.f1pro.Fmt
import app.f1pro.R
import app.f1pro.data.MIN_MS
import kotlinx.coroutines.delay

private data class QuickLink(val label: String, val url: String, val icon: Int, val color: Color)

private val QUICK_LINKS get() = listOf(
    QuickLink("F1 TV", "https://f1tv.formula1.com", R.drawable.lc_tv, C.Highlight),
    QuickLink("YouTube", "https://www.youtube.com/F1", R.drawable.lc_circle_play, C.Live),
    QuickLink("Motorsport", "https://www.motorsport.com/f1/", R.drawable.lc_newspaper, C.Accent),
    QuickLink("Reddit", "https://www.reddit.com/r/formula1/", R.drawable.lc_message_circle, C.Warning),
    QuickLink("Calendar", "https://f1calendar.com", R.drawable.lc_calendar, C.Success),
    QuickLink("FIA Rules", "https://www.fia.com/regulation/category/110", R.drawable.lc_bookmark, C.TextMuted),
)

private val SOURCE_COLORS get() = mapOf(
    "RACER" to C.Highlight.copy(alpha = 0.6f),
    "Autosport" to C.Warning.copy(alpha = 0.6f),
    "Motorsport" to C.Success.copy(alpha = 0.6f),
    "The Race" to C.Accent.copy(alpha = 0.6f),
)

private val SORT_LABELS = mapOf("newest" to "Newest", "oldest" to "Oldest", "source" to "Source")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewsPage(state: AppState, listState: LazyListState) {
    val context = LocalContext.current
    var sort by remember { mutableStateOf(state.prefs.newsSort) }
    var sortOpen by remember { mutableStateOf(false) }
    val now = rememberNow(60_000)
    val view = androidx.compose.ui.platform.LocalView.current

    LaunchedEffect(Unit) {
        if (state.news.isEmpty() || System.currentTimeMillis() - state.newsAt > 5 * MIN_MS) state.loadNews()
    }

    // "News updated" / "Refresh failed" toast after a manual refresh.
    var toast by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.newsToast) {
        if (state.newsToast == 0L) return@LaunchedEffect
        toast = state.newsToast > 0
        if (state.newsToast > 0) Haptics.confirm(view) else Haptics.reject(view)
        delay(2000)
        toast = null
    }

    val items = remember(state.news, sort) { state.newsSorted(sort) }
    val refreshing = state.newsLoading && state.news.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                Haptics.settle(view)
                state.loadNews(manual = true)
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (l in QUICK_LINKS) {
                            Chip(l.label, icon = l.icon, iconColor = l.color) { openUrl(context, l.url) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("LATEST NEWS", style = SectionLabel)
                        if (items.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text("${items.size} articles", style = inter(10.sp, color = C.TextMuted.copy(alpha = 0.6f)))
                        }
                        Spacer(Modifier.weight(1f))
                        Box {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(C.SurfaceLight)
                                    .border(1.dp, C.GlassBorder, RoundedCornerShape(8.dp))
                                    .tap { sortOpen = true }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Ic(R.drawable.lc_arrow_up_down, C.TextMuted, 11.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(SORT_LABELS[sort] ?: "Newest", style = inter(11.sp, FontWeight.SemiBold, C.TextSecondary))
                            }
                            DropdownMenu(
                                expanded = sortOpen,
                                onDismissRequest = { sortOpen = false },
                                containerColor = C.Surface,
                            ) {
                                for ((k, label) in SORT_LABELS) {
                                    DropdownMenuItem(
                                        text = { Text(label, style = inter(12.sp, FontWeight.Medium, if (k == sort) C.Accent else C.Text)) },
                                        onClick = {
                                            if (k != sort) Haptics.segment(view)
                                            sort = k
                                            state.prefs.newsSort = k
                                            sortOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        val spin = if (state.newsLoading) {
                            val t = rememberInfiniteTransition(label = "spin")
                            t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "r").value
                        } else 0f
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.newsLoading) C.Accent.copy(alpha = 0.1f) else Color.Transparent)
                                .tap(enabled = !state.newsLoading) { state.loadNews(manual = true) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Ic(R.drawable.lc_refresh_cw, if (state.newsLoading) C.Accent else C.TextMuted, 14.dp, Modifier.rotate(spin))
                        }
                    }
                }

                if (state.news.isEmpty() && state.newsLoading) {
                    items(6) { Skeleton(96.dp) }
                } else if (state.news.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().glassCard().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Ic(R.drawable.lc_circle_alert, C.Live, 28.dp)
                            Text(state.newsError ?: "Failed to load news", style = inter(14.sp, color = C.TextSecondary), textAlign = TextAlign.Center)
                            Row(Modifier.tap { state.loadNews(manual = true) }, verticalAlignment = Alignment.CenterVertically) {
                                Ic(R.drawable.lc_refresh_cw, C.Accent, 12.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Retry", style = inter(12.sp, FontWeight.SemiBold, C.Accent))
                            }
                        }
                    }
                } else {
                    itemsIndexed(items, key = { _, n -> n.source + n.link }) { i, n ->
                        val featured = i == 0 && sort == "newest"
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (refreshing) 0.4f else 1f)
                                .glassCard(edge = if (featured) C.Accent else C.Highlight.copy(alpha = 0.3f), fill = if (featured) androidx.compose.ui.graphics.lerp(C.Card, C.Accent, 0.05f) else C.Card)
                                .pressable { openUrl(context, n.link) }
                                .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (featured) Text("LATEST", style = inter(10.sp, FontWeight.Bold, C.Accent, 0.1f))
                            Text(
                                n.title,
                                style = inter(if (featured) 15.sp else 14.sp, FontWeight.SemiBold, C.Text).copy(lineHeight = if (featured) 20.sp else 19.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ic(R.drawable.lc_clock, C.TextMuted, 11.dp)
                                Spacer(Modifier.width(5.dp))
                                Text(Fmt.ago(n.published, now), style = inter(11.sp, color = C.TextMuted))
                                Text("  ·  ", style = inter(11.sp, color = C.BorderLight))
                                Text(n.source.uppercase(), style = inter(10.sp, FontWeight.SemiBold, SOURCE_COLORS[n.source] ?: C.TextMuted, 0.05f))
                            }
                            if (n.excerpt.isNotBlank()) {
                                Text(
                                    n.excerpt.take(140),
                                    style = inter(13.sp, color = C.TextSecondary).copy(lineHeight = 19.sp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Read more", style = inter(11.sp, FontWeight.Medium, C.Highlight, 0.03f))
                                Spacer(Modifier.width(4.dp))
                                Ic(R.drawable.lc_external_link, C.Highlight, 11.dp)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        ) {
            val ok = toast != false
            val color = if (ok) C.Success else C.Live
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.Surface.copy(alpha = 0.95f))
                    .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(if (ok) R.drawable.lc_check else R.drawable.lc_circle_alert, color, 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (ok) "News updated" else "Refresh failed", style = inter(12.sp, FontWeight.Bold, color, 0.03f))
            }
        }
    }
}
