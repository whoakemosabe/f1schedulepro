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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Alerts.createChannels(this)
        Scheduler.ensure(this)
        handleIntent(intent)
        setContent {
            // Narrow phones see the same layout scaled down a little rather than wrapping;
            // very large system text is capped so rows stay on one line.
            val base = LocalDensity.current
            val widthDp = LocalConfiguration.current.screenWidthDp
            val scale = (widthDp / 400f).coerceIn(0.88f, 1f)
            val scaled = Density(base.density * scale, base.fontScale.coerceAtMost(1.15f))
            val scope = rememberCoroutineScope()
            val s = remember { AppState(this, scope) }
            state = s
            pendingRace?.let { s.openRaceKey = it; pendingRace = null }
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
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(C.Bg.toArgb()))
            }
            CompositionLocalProvider(LocalDensity provides scaled) {
                F1Theme { F1App(s) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(Alerts.EXTRA_OPEN_UPDATES, false)) {
            intent.removeExtra(Alerts.EXTRA_OPEN_UPDATES)
            UpdateWatch.openUpdates.value = true
            state?.tab = app.f1pro.ui.Tab.Settings
        }
        intent.getStringExtra(Alerts.EXTRA_RACE)?.let { key ->
            intent.removeExtra(Alerts.EXTRA_RACE)
            val s = state
            if (s != null) s.openRaceKey = key else pendingRace = key
        }
    }
}
