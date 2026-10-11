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
    var lastCrash by mutableStateOf<String?>(null)
        private set

    /**
     * False until the saved calendar, standings and news have been read from storage, which
     * happens off the main thread so the first frame isn't held up. The app shows its plain
     * background until then (a few tens of milliseconds).
     */
    var ready by mutableStateOf(false)
        private set
    private var openWhenReady = false

    fun dismissCrash() {
        app.f1pro.CrashLog.clear(context)
        lastCrash = null
    }

    var seasons by mutableStateOf<List<SeasonCalendar>>(emptyList())
        private set
    var circuits by mutableStateOf<Map<String, CircuitStats>>(emptyMap())
        private set

    var fmt by mutableStateOf(Fmt(prefs.zone(), prefs.is24h(this.context)))
        private set

    var standings by mutableStateOf<Standings?>(null)
        private set
    var standingsLoading by mutableStateOf(false)
        private set
    var standingsError by mutableStateOf<String?>(null)
        private set

    var news by mutableStateOf<List<NewsItem>>(emptyList())
        private set
    var newsAt by mutableLongStateOf(0L)
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

    /** Career totals that couldn't be loaded (shown with a Retry instead of a skeleton). */
    val careerFailed = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    /** Why a driver's career couldn't load (shown with Retry). */
    val careerError = androidx.compose.runtime.mutableStateMapOf<String, String>()

    fun loadCareer(driverId: String, season: Int, retry: Boolean = false) {
        if (careers.containsKey(driverId) || !careerLoading.add(driverId)) return
        if (!retry && careerFailed[driverId] == true) { careerLoading.remove(driverId); return }
        careerFailed.remove(driverId)
        careerError.remove(driverId)
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val races = seasons.firstOrNull { it.year == season }?.races.orEmpty()
                val over = races.isNotEmpty() && races.all { (it.raceSession?.end ?: 0L) < now }
                val current = driverSeasons.values.firstOrNull { it.driverId == driverId && it.season == season }
                // Never a spinner forever: give up after 40 s and offer Retry.
                val c = kotlinx.coroutines.withTimeout(40_000) { repo.driverCareer(driverId, season, over, current) }
                if (c != null) careers[driverId] = c else careerFailed[driverId] = true
            } catch (e: app.f1pro.data.Net.TooManyRequests) {
                val mins = ((app.f1pro.data.Net.jolpicaBlockedUntil - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
                careerError[driverId] = "The results service has had too many requests. Try again in about $mins min."
                careerFailed[driverId] = true
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                careerError[driverId] = "The results service is slow right now."
                careerFailed[driverId] = true
            } catch (e: Exception) {
                careerError[driverId] = "Couldn't reach the results service."
                careerFailed[driverId] = true
            } finally {
                careerLoading.remove(driverId)
            }
        }
    }

    /** For the This weekend card: the season's next (or current) race and what's left to score. */
    data class Ahead(val race: app.f1pro.data.Race?, val racesLeft: Int, val sprintsLeft: Int)

    fun ahead(season: Int, now: Long = System.currentTimeMillis()): Ahead {
        val races = seasons.firstOrNull { it.year == season }?.races.orEmpty().filter { !it.isTesting }
        val next = races.firstOrNull { (it.raceSession?.end ?: 0L) > now }
        val left = races.filter { (it.raceSession?.start ?: 0L) > now }
        val sprintsLeft = races.count { r -> r.sessions.any { it.name == "Sprint" && it.start > now } }
        return Ahead(next, left.size, sprintsLeft)
    }

    /** Opens the compare panel for [a]; [b] is picked there. */
    var compare by mutableStateOf<Pair<app.f1pro.data.DriverStanding, app.f1pro.data.DriverStanding?>?>(null)

    /** Family name (normalised) → headshot URL, read with the rest of the saved data. */
    private var headshots: Map<String, String> = emptyMap()

    fun headshotUrl(family: String): String? = headshots[app.f1pro.data.Names.norm(family)]

    /** The calendar's race for a season and round (for flags and opening it). */
    fun raceFor(season: Int, round: Int): app.f1pro.data.Race? =
        seasons.firstOrNull { it.year == season }?.races?.firstOrNull { it.round == round && !it.isTesting }

    /**
     * The season Schedule shows instead of the current one, while a race from another season
     * (opened from a driver panel between seasons) is open. Cleared when that race closes.
     */
    var scheduleYear by mutableStateOf<Int?>(null)

    /** From a driver panel: close it and open that round's race page. */
    fun openRace(season: Int, round: Int) {
        val key = raceFor(season, round)?.key ?: return
        sheetDriver = null
        sheetTeam = null
        compare = null
        if (app.f1pro.data.Planner.currentSeason(seasons, System.currentTimeMillis())?.year != season) scheduleYear = season
        tab = Tab.Schedule
        openRaceKey = key
    }

    /** Season → every driver's numbers (one download per season, see Repo.seasonStats). */
    private val seasonStats = HashMap<Int, app.f1pro.data.DriverStats.SeasonStats>()
    private val seasonStatsLoading = HashSet<Int>()

    /** The season's finished races' end time, newest: the season data is refreshed after it. */
    private fun lastRaceEnd(season: Int, now: Long = System.currentTimeMillis()): Long? =
        seasons.firstOrNull { it.year == season }?.races
            ?.mapNotNull { r -> r.raceSession?.end?.takeIf { it < now } }?.maxOrNull()

    /** When each season's driver data was downloaded (for "Updated 2 h ago" in the panels). */
    val seasonDataAt = androidx.compose.runtime.mutableStateMapOf<Int, Long>()

    /** Pull to refresh: download the season's driver data again now, whatever its age. */
    fun refreshDriverData() {
        val season = standings?.season ?: return
        if (!seasonStatsLoading.add(season)) return
        scope.launch {
            try { repo.seasonStats(season, lastRaceEnd(season), force = true)?.let { publish(season, it) } } catch (e: Exception) { }
            finally { seasonStatsLoading.remove(season) }
        }
    }

    /** Fills [driverSeasons] for every driver in the standings from one season download. */
    private fun publish(season: Int, stats: app.f1pro.data.DriverStats.SeasonStats) {
        seasonStats[season] = stats
        if (stats.fetchedAt > 0) seasonDataAt[season] = stats.fetchedAt
        val st = standings ?: return
        if (st.season != season) return
        for (d in st.drivers) {
            val id = app.f1pro.data.DriverStats.findId(stats.entries, d.name, d.surname) ?: continue
            stats.drivers[id]?.let { driverSeasons[driverKey(season, d)] = it }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun loadDriverSeason(season: Int, d: app.f1pro.data.DriverStanding) {
        if (seasonStats.containsKey(season) || !seasonStatsLoading.add(season)) return
        for (x in standings?.drivers.orEmpty()) driverSeasonLoading[driverKey(season, x)] = !driverSeasons.containsKey(driverKey(season, x))
        scope.launch {
            try {
                // What's saved first (instant), then a fresh copy if a race has finished since.
                withContext(Dispatchers.IO) { repo.cachedSeasonStats(season) }?.let { publish(season, it) }
                repo.seasonStats(season, lastRaceEnd(season))?.let { publish(season, it) }
            } catch (e: Exception) {
            } finally {
                seasonStatsLoading.remove(season)
                for (x in standings?.drivers.orEmpty()) driverSeasonLoading[driverKey(season, x)] = false
            }
        }
    }

    init {
        Haptics.enabled = prefs.haptics
        scope.launch {
            // Everything saved on the phone, read in one go off the main thread.
            val boot = withContext(Dispatchers.IO) {
                repo.loadTeamColours()
                UpdateWatch.load(context)
                val n = repo.cachedNews()
                Boot(repo.seasons(), repo.circuits(), repo.cachedStandings(), n.first, n.second, app.f1pro.CrashLog.read(context), repo.headshots())
            }
            seasons = boot.seasons
            circuits = boot.circuits
            standings = boot.standings
            news = boot.news
            newsAt = boot.newsAt
            lastCrash = boot.crash
            headshots = boot.headshots
            ready = true
            if (openWhenReady) { openWhenReady = false; onOpen() }
            pendingAction?.let { pendingAction = null; it() }
        }
    }

    private class Boot(
        val seasons: List<SeasonCalendar>,
        val circuits: Map<String, CircuitStats>,
        val standings: Standings?,
        val news: List<NewsItem>,
        val newsAt: Long,
        val crash: String?,
        val headshots: Map<String, String>,
    )

    /** Something asked for before [ready] (a shortcut, the tile): run once data is loaded. */
    private var pendingAction: (() -> Unit)? = null

    fun whenReady(action: () -> Unit) {
        if (ready) action() else pendingAction = action
    }

    /** The "Next race" shortcut / tile: the weekend under way, or else the next one. */
    fun openNextRace() = whenReady {
        val race = app.f1pro.data.Planner.weekend(seasons, System.currentTimeMillis())
        tab = Tab.Schedule
        if (race != null) openRaceKey = race.key
    }

    fun onOpen() {
        if (!ready) { openWhenReady = true; return }
        fmt = Fmt(prefs.zone(), prefs.is24h(context))
        // Panels re-check their season data next time (cheap: the saved copy is used unless
        // a race has finished since).
        seasonStats.clear()
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
            headshots = withContext(Dispatchers.IO) { repo.headshots() }
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
        refreshDriverData()
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
