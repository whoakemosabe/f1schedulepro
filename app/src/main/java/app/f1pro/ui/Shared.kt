package app.f1pro.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/*
 * The race card ↔ race page morph. Schedule wraps the list and the race page in one
 * SharedTransitionLayout; pieces tagged with the same key (the card and the page's hero, the
 * flag on each) move and resize into each other instead of the screen just switching.
 */

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** The card and the hero: the box grows/shrinks between them, contents cross-fade. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRaceBounds(raceKey: String, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val s = LocalSharedScope.current ?: return this
    val a = LocalAnimScope.current ?: return this
    return with(s) { this@sharedRaceBounds.sharedBounds(rememberSharedContentState("race-card-$raceKey"), a) }
}

/** The same thing (the flag) in both places: it glides and resizes. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedRaceElement(raceKey: String, part: String, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val s = LocalSharedScope.current ?: return this
    val a = LocalAnimScope.current ?: return this
    return with(s) { this@sharedRaceElement.sharedElement(rememberSharedContentState("race-$part-$raceKey"), a) }
}

/**
 * The race page's other cards, as the card opens into the page: each slides up a little and
 * fades in, one after another, instead of all appearing at once. [index] sets the order.
 */
@Composable
fun Modifier.enterStagger(index: Int): Modifier {
    val a = LocalAnimScope.current ?: return this
    val delay = 150 + index * 70
    return with(a) {
        this@enterStagger.animateEnterExit(
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320, delayMillis = delay)) +
                androidx.compose.animation.slideInVertically(
                    androidx.compose.animation.core.tween(420, delayMillis = delay, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                ) { it / 4 },
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)),
        )
    }
}
