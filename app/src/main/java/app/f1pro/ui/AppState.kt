package app.f1pro.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.f1pro.Fmt
import app.f1pro.Prefs
import app.f1pro.data.CircuitStats
import app.f1pro.data.HOUR_MS
import app.f1pro.data.MIN_MS
import app.f1pro.data.NewsItem
import app.f1pro.data.Repo
import app.f1pro.data.SeasonCalendar
import app.f1pro.data.Standings
import app.f1pro.widget.Widgets
import app.f1pro.work.Ticker
import app.f1pro.work.UpdateWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val title: String, val icon: Int) {
    Schedule("Schedule", app.f1pro.R.drawable.lc_calendar),
    Standings("Standings", app.f1pro.R.drawable.lc_trophy),
    News("News", app.f1pro.R.drawable.lc_newspaper),
    Settings("Settings", app.f1pro.R.drawable.lc_settings),
}

/** Everything the screens read, loaded from disk first and refreshed from the network. */
class AppState(context: Context, private val scope: CoroutineScope) {
    val context: Context = context.applicationContext
    val prefs = Prefs(this.context)
    private val repo = Repo(this.context)

    var tab by mutableStateOf(Tab.Schedule)

    /** The last crash's details, shown once so they can be copied. */
    var lastCrash by mutableStateOf(app.f1pro.CrashLog.read(this.context))
        private set

    fun dismissCrash() {
        app.f1pro.CrashLog.clear(context)
        lastCrash = null
    }

    var seasons by mutableStateOf(repo.seasons())
        private set
    val circuits: Map<String, CircuitStats> = repo.circuits()

    var fmt by mutableStateOf(Fmt(prefs.zone(), prefs.is24h(this.context)))
        private set

    var standings by mutableStateOf(repo.cachedStandings())
        private set
    var standingsLoading by mutableStateOf(false)
        private set
    var standingsError by mutableStateOf<String?>(null)
        private set

    var news by mutableStateOf(repo.cachedNews().first)
        private set
    var newsAt by mutableLongStateOf(repo.cachedNews().second)
        private set
    var newsLoading by mutableStateOf(false)
        private set
    var newsError by mutableStateOf<String?>(null)
        private set

    /** Bumped when news finishes a manual refresh: positive ok, negative failed. */
    var newsToast by mutableLongStateOf(0L)

    /** A race to open, from a notification tap. */
    var openRaceKey by mutableStateOf<String?>(null)

    // Reminder settings mirrored as state so the Settings screen redraws.
    var alertsOn by mutableStateOf(prefs.alertsOn)
        private set
    var leadMinutes by mutableStateOf(prefs.leadMinutes)
        private set
    var alertWhich by mutableStateOf(prefs.alertWhich)
        private set
    var timezone by mutableStateOf(prefs.timezone)
        private set
    var clock by mutableStateOf(prefs.clock)
        private set
    var theme by mutableStateOf(prefs.theme)
        private set
    var haptics by mutableStateOf(prefs.haptics)
        private set

    init {
        Haptics.enabled = prefs.haptics
        UpdateWatch.load(this.context)
    }

    fun onOpen() {
        fmt = Fmt(prefs.zone(), prefs.is24h(context))
        if (System.currentTimeMillis() - repo.lastCalendarCheck() > 6 * HOUR_MS) refreshCalendar()
        val st = standings
        if (st == null || System.currentTimeMillis() - st.fetchedAt > 30 * MIN_MS) loadStandings()
        scope.launch(Dispatchers.IO) {
            try { Ticker.schedule(context) } catch (e: Exception) { }
            try { Widgets.updateAll(context) } catch (e: Exception) { }
        }
        scope.launch { try { UpdateWatch.check(context, background = false) } catch (e: Exception) { } }
    }

    var calendarLoading by mutableStateOf(false)
        private set

    fun refreshCalendar() {
        if (calendarLoading) return
        calendarLoading = true
        scope.launch {
            val changed = try { repo.refreshCalendar() } catch (e: Exception) { false } finally { calendarLoading = false }
            if (changed) {
                seasons = withContext(Dispatchers.IO) { repo.seasons() }
                withContext(Dispatchers.IO) {
                    try { Ticker.schedule(context) } catch (e: Exception) { }
                    try { Widgets.updateAll(context) } catch (e: Exception) { }
                }
            }
        }
    }

    fun loadStandings() {
        if (standingsLoading) return
        standingsLoading = true
        standingsError = null
        scope.launch {
            try {
                standings = repo.fetchStandings()
            } catch (e: Exception) {
                if (standings == null) standingsError = e.message ?: "Failed to load standings"
            } finally {
                standingsLoading = false
            }
        }
    }

    fun loadNews(manual: Boolean = false) {
        if (newsLoading) return
        newsLoading = true
        newsError = null
        scope.launch {
            var ok = true
            try {
                news = repo.fetchNews()
                newsAt = System.currentTimeMillis()
            } catch (e: Exception) {
                ok = false
                if (news.isEmpty()) newsError = e.message ?: "Failed to load news"
            } finally {
                newsLoading = false
                if (manual) newsToast = if (ok) System.currentTimeMillis() else -System.currentTimeMillis()
            }
        }
    }

    private fun rescheduled() {
        scope.launch(Dispatchers.IO) {
            try { Ticker.schedule(context) } catch (e: Exception) { }
            try { Widgets.updateAll(context) } catch (e: Exception) { }
        }
    }

    fun setAlerts(on: Boolean) {
        prefs.alertsOn = on
        alertsOn = on
        if (on) {
            // Don't fire reminders for sessions already inside the window when switched on.
            val now = System.currentTimeMillis()
            prefs.sentReminders = prefs.sentReminders + app.f1pro.data.Planner.slots(seasons)
                .filter { it.session.start in now..(now + leadMinutes * MIN_MS) }
                .map { it.key }
        }
        rescheduled()
    }

    fun setLead(min: Int) {
        prefs.leadMinutes = min
        leadMinutes = min
        rescheduled()
    }

    fun setWhich(which: String) {
        prefs.alertWhich = which
        alertWhich = which
        rescheduled()
    }

    fun setZone(id: String) {
        prefs.timezone = id
        timezone = id
        fmt = Fmt(prefs.zone(), prefs.is24h(context))
        rescheduled()
    }

    fun chooseClock(c: String) {
        prefs.clock = c
        clock = c
        fmt = Fmt(prefs.zone(), prefs.is24h(context))
        rescheduled()
    }

    fun chooseTheme(t: String) {
        prefs.theme = t
        theme = t
    }

    fun chooseHaptics(on: Boolean) {
        prefs.haptics = on
        haptics = on
        Haptics.enabled = on
    }

    /** Pull-to-refresh on the schedule: calendar, plus an update check like Afli. */
    fun refreshAll() {
        refreshCalendar()
        scope.launch { try { UpdateWatch.check(context, background = false, force = true) } catch (e: Exception) { } }
    }

    fun seasonFor(now: Long): SeasonCalendar? = app.f1pro.data.Planner.currentSeason(seasons, now)

    fun newsSorted(sort: String): List<NewsItem> = when (sort) {
        "oldest" -> news.sortedBy { it.published }
        "source" -> news.sortedWith(compareBy<NewsItem> { it.source }.thenByDescending { it.published })
        else -> news.sortedByDescending { it.published }
    }

    fun standingsOrEmpty(): Standings? = standings
}
