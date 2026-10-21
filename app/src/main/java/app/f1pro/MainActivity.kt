package app.f1pro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.graphics.drawable.toDrawable
import app.f1pro.ui.AppState
import app.f1pro.ui.C
import app.f1pro.ui.F1App
import app.f1pro.ui.F1Theme
import app.f1pro.work.Alerts
import app.f1pro.work.Scheduler
import app.f1pro.work.UpdateWatch

class MainActivity : ComponentActivity() {
    private var state: AppState? = null
    private var pendingRace: String? = null
    private var pendingTab: app.f1pro.ui.Tab? = null
    private var pendingNext = false

    companion object {
        /** Launcher shortcuts and the quick settings tile. */
        const val ACTION_NEXT_RACE = "app.f1pro.NEXT_RACE"
        const val ACTION_STANDINGS = "app.f1pro.STANDINGS"
        const val ACTION_NEWS = "app.f1pro.NEWS"

        /** "2026-18" or "2026-test-1", as Race.key makes them. */
        private val RACE_KEY = Regex("""^\d{4}-(test-)?\d{1,2}$""")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // System bar icons for the saved theme from the first frame (they used to start as the
        // dark theme's and flip once the app had loaded, a visible flash in Light).
        barStyle(startsDark())
        Alerts.createChannels(this)
        Scheduler.ensure(this)
        handleIntent(intent)
        setContent {
            // Narrow phones see the same layout scaled down a little rather than wrapping.
            // System text size is followed up to 1.5x (the old cap was 1.15x, which ignored
            // most "large text" settings); beyond that rows would start to clip.
            val base = LocalDensity.current
            val widthDp = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width / base.density
            val scale = (widthDp / 400f).coerceIn(0.88f, 1f)
            val scaled = Density(base.density * scale, base.fontScale.coerceAtMost(1.5f))
            val scope = rememberCoroutineScope()
            val s = remember { AppState(this, scope) }
            state = s
            pendingRace?.let { s.openRaceKey = it; pendingRace = null }
            pendingTab?.let { s.tab = it; pendingTab = null }
            if (pendingNext) { pendingNext = false; s.openNextRace() }
            if (UpdateWatch.openUpdates.value) s.tab = app.f1pro.ui.Tab.Settings
            // Theme: Dark, Light, or follow the phone. Set before anything reads the palette.
            val systemDark = isSystemInDarkTheme()
            val dark = when (s.theme) {
                "light" -> false
                "system" -> systemDark
                else -> true
            }
            C.dark = dark
            LaunchedEffect(dark) {
                barStyle(dark)
                window.setBackgroundDrawable(C.Bg.toArgb().toDrawable())
            }
            // Android 13+ remembers which launch screen to show next time, so it matches the
            // theme setting instead of always being dark.
            LaunchedEffect(s.theme) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    splashScreen.setSplashScreenTheme(
                        when (s.theme) {
                            "light" -> R.style.Theme_F1Pro_Light
                            "system" -> R.style.Theme_F1Pro_System
                            else -> R.style.Theme_F1Pro
                        }
                    )
                }
            }
            CompositionLocalProvider(LocalDensity provides scaled) {
                F1Theme { F1App(s) }
            }
        }
    }

    /** Dark or light, from the saved theme setting, before any screen has loaded. */
    private fun startsDark(): Boolean = when (Prefs(this).theme) {
        "light" -> false
        "system" -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        else -> true
    }

    private fun barStyle(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun openTab(tab: app.f1pro.ui.Tab) {
        val s = state
        if (s != null) s.tab = tab else pendingTab = tab
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(Alerts.EXTRA_OPEN_UPDATES, false)) {
            intent.removeExtra(Alerts.EXTRA_OPEN_UPDATES)
            UpdateWatch.openUpdates.value = true
            state?.tab = app.f1pro.ui.Tab.Settings
        }
        when (intent.action) {
            // The weekend under way, or else the next one (worked out once the calendar is loaded).
            ACTION_NEXT_RACE -> state?.openNextRace() ?: run { pendingNext = true }
            ACTION_STANDINGS -> openTab(app.f1pro.ui.Tab.Standings)
            ACTION_NEWS -> openTab(app.f1pro.ui.Tab.News)
        }
        intent.action = null
        // This screen is open to other apps (it's the launcher entry), so only a race key in
        // the app's own shape is followed; anything else is dropped.
        intent.getStringExtra(Alerts.EXTRA_RACE)?.takeIf { RACE_KEY.matches(it) }.also {
            intent.removeExtra(Alerts.EXTRA_RACE)
        }?.let { key ->
            val s = state
            if (s != null) s.openRaceKey = key else pendingRace = key
        }
    }
}
