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

    /** A race page is open on Schedule: its own swipe goes race to race, so tabs don't swipe. */
    var raceOpen by mutableStateOf(false)

    /** An extra bar under the header, inside its frost (the race page's back / arrows bar). */
    var headerBar by mutableStateOf<(@androidx.compose.runtime.Composable () -> Unit)?>(null)

    // Reminder settings mirrored as state so the Settings screen redraws.
    var alertsOn by mutableStateOf(prefs.alertsOn)
        private set
    var leadMinutes by mutableStateOf(prefs.leadMinutes)
    var countdownOn by mutableStateOf(prefs.countdownOn)
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

    // ── Favourite driver ──

    var favourite by mutableStateOf(prefs.favourite)
        private set
    var resultAlerts by mutableStateOf(prefs.resultAlerts)
        private set
    var fahrenheit by mutableStateOf(prefs.fahrenheit)
        private set

    fun chooseFavourite(f: app.f1pro.data.Favourite?) {
        prefs.favourite = f
        favourite = f
        scope.launch(Dispatchers.IO) {
            try { Widgets.updateAll(context) } catch (e: Exception) { }
            try { app.f1pro.work.ResultsWatch.schedule(context, seasons) } catch (e: Exception) { }
        }
    }

    fun chooseResultAlerts(on: Boolean) {
        prefs.resultAlerts = on
        resultAlerts = on
        if (on) scope.launch(Dispatchers.IO) { try { app.f1pro.work.ResultsWatch.schedule(context, seasons) } catch (e: Exception) { } }
    }

    fun chooseFahrenheit(on: Boolean) {
        prefs.fahrenheit = on
        fahrenheit = on
    }

    /** "27°" in the chosen unit. */
    fun temp(c: Double): String = if (fahrenheit) "${Math.round(c * 9 / 5 + 32)}°" else "${Math.round(c)}°"

    // ── Podiums, results, weather ──

    /** season → round → podium, for the race cards. */
    val podiums = androidx.compose.runtime.mutableStateMapOf<Int, Map<Int, List<app.f1pro.data.PodiumEntry>>>()
    private val podiumsAt = HashMap<Int, Long>()

    fun loadPodiums(season: Int, force: Boolean = false) {
        if (season !in podiums) {
            val (cached, at) = repo.cachedPodiums(season)
            if (cached.isNotEmpty()) podiums[season] = cached
            podiumsAt[season] = at
        }
        if (!force && System.currentTimeMillis() - (podiumsAt[season] ?: 0L) < 30 * MIN_MS) return
        podiumsAt[season] = System.currentTimeMillis()
        scope.launch {
            try { podiums[season] = repo.fetchPodiums(season) } catch (e: Exception) { }
        }
    }

    /** race.key → results. */
    val results = androidx.compose.runtime.mutableStateMapOf<String, app.f1pro.data.WeekendResults>()
    val resultsLoading = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    val resultsFailed = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun loadResults(race: app.f1pro.data.Race) {
        val key = race.key
        if (key !in results) repo.cachedResults(race.season, race.round)?.let { results[key] = it }
        val have = results[key]
        val now = System.currentTimeMillis()
        // Refetched at most every 10 minutes while results may still be landing or amended;
        // once a weekend is three days old and its race is classified, the cache is final.
        val settled = have != null && have.race.isNotEmpty() && now - race.endsAt > 3 * 24 * HOUR_MS
        val fresh = have != null && (settled || now - have.fetchedAt < 10 * MIN_MS)
        if (fresh || resultsLoading[key] == true) return
        resultsLoading[key] = true
        resultsFailed.remove(key)
        scope.launch {
            try {
                results[key] = repo.fetchResults(race)
            } catch (e: Exception) {
                resultsFailed[key] = true
            } finally {
                resultsLoading[key] = false
            }
        }
    }

    /** circuitId → forecast. */
    val weather = androidx.compose.runtime.mutableStateMapOf<String, app.f1pro.data.Forecast>()
    private val weatherLoading = HashSet<String>()

    fun loadWeather(race: app.f1pro.data.Race) {
        val id = race.circuit.id
        val now = System.currentTimeMillis()
        // Open-Meteo forecasts 16 days ahead; nothing to show before that or after the weekend.
        if (race.first - now > 15 * 24 * HOUR_MS || now > race.endsAt) return
        if (id !in weather) repo.cachedWeather(id)?.let { weather[id] = it }
        val have = weather[id]
        if (have != null && now - have.fetchedAt < 3 * HOUR_MS) return
        if (!weatherLoading.add(id)) return
        val (lat, lon) = repo.coords(race.circuit) ?: run { weatherLoading.remove(id); return }
        scope.launch {
            try { weather[id] = repo.fetchWeather(id, lat, lon) } catch (e: Exception) { } finally { weatherLoading.remove(id) }
        }
    }

    /** Bumped when official team colours arrive, so coloured stripes redraw. */
    var teamColoursVersion by mutableLongStateOf(0L)
        private set

    // ── Driver / team panel (Standings) ──

    /** The driver or team whose panel is open. */
    var sheetDriver by mutableStateOf<app.f1pro.data.DriverStanding?>(null)
    var sheetTeam by mutableStateOf<app.f1pro.data.TeamStanding?>(null)
    val driverSeasons = androidx.compose.runtime.mutableStateMapOf<String, app.f1pro.data.DriverSeason>()
    val driverSeasonLoading = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun driverKey(season: Int, d: app.f1pro.data.DriverStanding) =
        "$season-" + app.f1pro.data.Names.norm(d.surname) + "-" + app.f1pro.data.Names.norm(d.name).take(6)

    val careers = androidx.compose.runtime.mutableStateMapOf<String, app.f1pro.data.DriverCareer>()
    private val careerLoading = HashSet<String>()

    fun loadCareer(driverId: String, season: Int) {
        if (careers.containsKey(driverId) || !careerLoading.add(driverId)) return
        scope.launch {
            try { repo.driverCareer(driverId, season)?.let { careers[driverId] = it } } catch (e: Exception) { }
            finally { careerLoading.remove(driverId) }
        }
    }

    fun headshotUrl(family: String): String? = repo.headshotUrl(family)
    suspend fun image(url: String): ByteArray? = repo.image(url)

    /** The calendar's race for a season and round (for flags and opening it). */
    fun raceFor(season: Int, round: Int): app.f1pro.data.Race? =
        seasons.firstOrNull { it.year == season }?.races?.firstOrNull { it.round == round && !it.isTesting }

    /** From a driver panel: close it and open that round's race page. */
    fun openRace(season: Int, round: Int) {
        val key = raceFor(season, round)?.key ?: return
        sheetDriver = null
        sheetTeam = null
        tab = Tab.Schedule
        openRaceKey = key
    }

    fun loadDriverSeason(season: Int, d: app.f1pro.data.DriverStanding) {
        val k = driverKey(season, d)
        if (driverSeasonLoading[k] == true || driverSeasons.containsKey(k)) return
        driverSeasonLoading[k] = true
        scope.launch {
            try {
                repo.driverSeason(season, d.name, d.surname)?.let { driverSeasons[k] = it }
            } catch (e: Exception) {
            } finally {
                driverSeasonLoading[k] = false
            }
        }
    }

    init {
        repo.loadTeamColours()
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
        seasonFor(System.currentTimeMillis())?.let { loadPodiums(it.year) }
        scope.launch {
            val before = app.f1pro.data.Teams.live
            repo.refreshTeamColours()
            if (app.f1pro.data.Teams.live != before) {
                teamColoursVersion = System.currentTimeMillis()
                withContext(Dispatchers.IO) { try { Widgets.updateAll(context) } catch (e: Exception) { } }
            }
        }
        scope.launch(Dispatchers.IO) { try { app.f1pro.work.ResultsWatch.schedule(context, seasons) } catch (e: Exception) { } }
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
                scope.launch(Dispatchers.IO) { try { Widgets.updateAll(context) } catch (e: Exception) { } }
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

    fun setCountdown(on: Boolean) {
        prefs.countdownOn = on
        countdownOn = on
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
        seasonFor(System.currentTimeMillis())?.let { loadPodiums(it.year, force = true) }
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
