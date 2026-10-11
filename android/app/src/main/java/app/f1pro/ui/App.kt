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
import androidx.compose.ui.Modifier
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

    CompositionLocalProvider(LocalFmt provides state.fmt) {
        Column(Modifier.fillMaxSize().background(C.Bg)) {
            Header(state)
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
            BottomNav(state)
        }
    }
}
