package app.f1pro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun F1App(state: AppState) {
    val scheduleList = rememberLazyListState()
    val standingsList = rememberLazyListState()
    val newsList = rememberLazyListState()

    // Coming back to the app: re-read settings that may have changed, refresh stale data.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.onOpen() }

    // A reminder tap lands on the Schedule tab with that race open.
    LaunchedEffect(state.openRaceKey) {
        if (state.openRaceKey != null) state.tab = Tab.Schedule
    }

    // The page (background + content) is recorded so the header and tab bar can blur it.
    val pageBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    val chrome = with(density) { PaddingValues(top = topPx.toDp(), bottom = bottomPx.toDp()) }

    CompositionLocalProvider(LocalFmt provides state.fmt) {
        Box(Modifier.fillMaxSize()) {
            // The page scrolls under the frosted header and tab bar.
            Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
                F1Background(Modifier.fillMaxSize())
                CompositionLocalProvider(LocalChrome provides chrome) {
                    AnimatedContent(
                        targetState = state.tab,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 60 }) togetherWith fadeOut(tween(90))
                        },
                        label = "tab",
                    ) { tab ->
                        when (tab) {
                            Tab.Schedule -> SchedulePage(state, scheduleList)
                            Tab.Standings -> StandingsPage(state, standingsList)
                            Tab.News -> NewsPage(state, newsList)
                            Tab.Settings -> SettingsPage(state)
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalPageBackdrop provides pageBackdrop) {
                // The frost reaches 20 dp past the header to fade out; it is drawn on its own so
                // that fade isn't counted as header height (pages would start 20 dp too low).
                var headerPx by remember { mutableIntStateOf(0) }
                GlassHeader(pageBackdrop, with(density) { headerPx.toDp() } + 20.dp, Modifier.align(Alignment.TopCenter))
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { topPx = it.height }
                ) {
                    Box(Modifier.fillMaxWidth().onSizeChanged { headerPx = it.height }) { Header(state) }
                    UpdateBanner()
                    CrashCard(state)
                }
                BottomNav(
                    state,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { bottomPx = it.height },
                )
            }
        }
    }
}
