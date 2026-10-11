package app.f1pro.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset

object Net {
    /**
     * Jolpica allows about 4 requests a second (and 500 an hour). Every request to it waits
     * its turn here, at most one per [JOLPICA_GAP_MS], app-wide; "too many requests" answers
     * are retried after the wait the server asks for.
     */
    private const val JOLPICA_GAP_MS = 300L
    private val jolpicaLock = Object()
    private var jolpicaLast = 0L

    private fun paceJolpica() {
        synchronized(jolpicaLock) {
            val wait = jolpicaLast + JOLPICA_GAP_MS - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            jolpicaLast = System.currentTimeMillis()
        }
    }

    class TooManyRequests(val retryAfterMs: Long) : IOException("HTTP 429")

    /**
     * GET as text. Follows redirects itself (up to 5), including 307/308 and http↔https hops,
     * which HttpURLConnection won't always do: feeds like RACER's answer with a 308 move.
     */
    /** Until when Jolpica has asked us to stop (its hourly limit): requests fail at once until then. */
    @Volatile var jolpicaBlockedUntil = 0L
        private set

    fun get(url: String, accept: String = "*/*"): String {
        val jolpica = url.contains("jolpi.ca")
        var attempt = 0
        while (true) {
            if (jolpica) {
                val wait = jolpicaBlockedUntil - System.currentTimeMillis()
                if (wait > 0) throw TooManyRequests(wait)
                paceJolpica()
            }
            try {
                return getOnce(url, accept)
            } catch (e: TooManyRequests) {
                if (!jolpica) throw e
                // A short wait (the per-second limit): wait and retry once or twice. A long one
                // (the hourly limit): stop asking until then and fail straight away.
                if (e.retryAfterMs > 10_000L || attempt >= 2) {
                    jolpicaBlockedUntil = System.currentTimeMillis() + e.retryAfterMs.coerceAtLeast(10_000L)
                    throw e
                }
                attempt++
                Thread.sleep(e.retryAfterMs.coerceIn(1000L, 5_000L))
            }
        }
    }

    private fun getOnce(url: String, accept: String): String {
        var target = URL(url)
        repeat(6) {
            val c = target.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 20_000
                c.setRequestProperty("Accept", accept)
                c.setRequestProperty("User-Agent", "F1SchedulePro/2.0 (Android)")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IOException("HTTP $code without a Location")
                    target = URL(target, loc)
                    return@repeat
                }
                if (code == 429) {
                    val secs = c.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: 2L
                    throw TooManyRequests(secs * 1000)
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    /** GET as bytes (images), up to [max] bytes. */
    fun bytes(url: String, max: Int = 2_000_000): ByteArray {
        var target = URL(url)
        repeat(6) {
            val c = target.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 20_000
                c.setRequestProperty("User-Agent", "F1SchedulePro/2.0 (Android)")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IOException("HTTP $code without a Location")
                    target = URL(target, loc)
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                val data = c.inputStream.use { it.readBytes() }
                if (data.size > max) throw IOException("Too big")
                return data
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }
}

data class NewsSource(val name: String, val url: String)

/**
 * Calendar, standings and news. Everything is cached on disk so the app, widgets and
 * reminders work offline; the bundled calendar is the last resort.
 */
class Repo(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "cache").apply { mkdirs() }

    companion object {
        const val JOLPICA = "https://api.jolpi.ca/ergast/f1"
        const val F1API = "https://f1api.dev/api/current"
        val NEWS = listOf(
            NewsSource("RACER", "https://racer.com/f1/feed/"),
            NewsSource("Autosport", "https://www.autosport.com/rss/feed/f1"),
            NewsSource("Motorsport", "https://www.motorsport.com/rss/f1/news/"),
            NewsSource("The Race", "https://the-race.com/feed/"),
        )

        @Volatile private var memo: Pair<String, List<SeasonCalendar>>? = null
        @Volatile private var circuitsMemo: Map<String, CircuitStats>? = null
    }

    // ── Calendar ──

    private fun liveFile(year: Int) = File(dir, "calendar-$year.json")

    /** Every season we know: live copies win over the bundled ones. */
    fun seasons(): List<SeasonCalendar> {
        val bundledNames = try {
            app.assets.list("")?.filter { it.matches(Regex("calendar-\\d{4}\\.json")) }.orEmpty()
        } catch (e: Exception) { emptyList() }
        val liveFiles = dir.listFiles { f -> f.name.matches(Regex("calendar-\\d{4}\\.json")) }.orEmpty().toList()
        val stamp = bundledNames.sorted().joinToString() + "|" + liveFiles.sortedBy { it.name }.joinToString { "${it.name}:${it.lastModified()}" }
        memo?.let { if (it.first == stamp) return it.second }

        val extras = bundledExtras()
        val byYear = LinkedHashMap<Int, SeasonCalendar>()
        for (name in bundledNames) {
            val text = try { app.assets.open(name).bufferedReader().use { it.readText() } } catch (e: Exception) { continue }
            val year = name.filter { it.isDigit() }.toInt()
            parse(text, extras[year], live = false)?.let { byYear[it.year] = it }
        }
        for (f in liveFiles) {
            val year = f.name.filter { it.isDigit() }.toInt()
            val s = parse(f.readText(), extras[year], live = true) ?: continue
            byYear[s.year] = s
        }
        val list = byYear.values.sortedBy { it.year }
        memo = stamp to list
        return list
    }

    private fun parse(text: String, extras: SeasonExtras?, live: Boolean): SeasonCalendar? =
        try { Parsers.calendar(text, extras, live) } catch (e: Exception) { null }

    private fun bundledExtras(): Map<Int, SeasonExtras> {
        val out = HashMap<Int, SeasonExtras>()
        val names = try { app.assets.list("")?.filter { it.matches(Regex("extras-\\d{4}\\.json")) }.orEmpty() } catch (e: Exception) { emptyList() }
        for (n in names) {
            try {
                val e = Parsers.extras(app.assets.open(n).bufferedReader().use { it.readText() })
                out[e.season] = e
            } catch (e: Exception) { }
        }
        return out
    }

    fun circuits(): Map<String, CircuitStats> {
        circuitsMemo?.let { return it }
        val m = try { Parsers.circuits(app.assets.open("circuits.json").bufferedReader().use { it.readText() }) } catch (e: Exception) { emptyMap() }
        circuitsMemo = m
        return m
    }

    /** Pulls this year's and next year's calendars. True if anything new was stored. */
    suspend fun refreshCalendar(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val year = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).year
        var changed = false
        for (y in listOf(year, year + 1)) {
            try {
                val text = Net.get("$JOLPICA/$y.json?limit=100", "application/json")
                val parsed = Parsers.calendar(text, null, live = true) ?: continue
                if (parsed.year != y) continue
                val f = liveFile(y)
                if (!f.exists() || f.readText() != text) {
                    f.writeText(text)
                    changed = true
                }
            } catch (e: Exception) {
                // offline or the feed is down: keep what we have
            }
        }
        val prefsFile = File(dir, "calendar-checked")
        prefsFile.writeText(now.toString())
        changed
    }

    fun lastCalendarCheck(): Long = try { File(dir, "calendar-checked").readText().toLong() } catch (e: Exception) { 0L }

    // ── Standings ──

    fun cachedStandings(): Standings? = try {
        val o = JSONObject(File(dir, "standings.json").readText())
        val (season, drivers) = Parsers.drivers(o.getString("drivers"))
        val teams = o.optString("teams").takeIf { it.isNotBlank() }?.let { Parsers.teams(it) }.orEmpty()
        Standings(season, drivers, teams.ifEmpty { Parsers.teamsFromDrivers(drivers) }, o.optLong("at"))
    } catch (e: Exception) { null }

    suspend fun fetchStandings(): Standings = withContext(Dispatchers.IO) {
        coroutineScope {
            val d = async { Net.get("$F1API/drivers-championship", "application/json") }
            val t = async { try { Net.get("$F1API/constructors-championship", "application/json") } catch (e: Exception) { "" } }
            val driversJson = d.await()
            val teamsJson = t.await()
            val (season, drivers) = Parsers.drivers(driversJson)
            if (drivers.isEmpty()) throw IOException("No standings yet")
            val teams = (if (teamsJson.isNotBlank()) try { Parsers.teams(teamsJson) } catch (e: Exception) { emptyList() } else emptyList())
                .ifEmpty { Parsers.teamsFromDrivers(drivers) }
            val now = System.currentTimeMillis()
            File(dir, "standings.json").writeText(
                JSONObject().put("drivers", driversJson).put("teams", teamsJson).put("at", now).toString()
            )
            Standings(season, drivers, teams, now)
        }
    }

    // ── Results ──

    private fun resultsFile(season: Int, round: Int) = File(dir, "results-$season-$round.json")

    fun cachedResults(season: Int, round: Int): WeekendResults? = try {
        val o = JSONObject(resultsFile(season, round).readText())
        WeekendResults(
            season, round,
            race = o.optString("race").takeIf { it.isNotBlank() }?.let { ResultParsers.race(it) }.orEmpty(),
            sprint = o.optString("sprint").takeIf { it.isNotBlank() }?.let { ResultParsers.race(it, "SprintResults") }.orEmpty(),
            quali = o.optString("quali").takeIf { it.isNotBlank() }?.let { ResultParsers.qualifying(it) }.orEmpty(),
            fetchedAt = o.optLong("at"),
        )
    } catch (e: Exception) { null }

    /** Race, sprint (on sprint weekends) and qualifying classifications for one round. */
    suspend fun fetchResults(race: Race): WeekendResults = withContext(Dispatchers.IO) {
        val base = "$JOLPICA/${race.season}/${race.round}"
        coroutineScope {
            val r = async { try { Net.get("$base/results.json?limit=40", "application/json") } catch (e: Exception) { null } }
            val q = async { try { Net.get("$base/qualifying.json?limit=40", "application/json") } catch (e: Exception) { null } }
            val sp = async {
                if (race.isSprint) try { Net.get("$base/sprint.json?limit=40", "application/json") } catch (e: Exception) { null } else ""
            }
            val rj = r.await()
            val qj = q.await()
            val sj = sp.await()
            if (rj == null && qj == null && sj.isNullOrEmpty()) throw IOException("Couldn't reach the results feed")
            val prev = cachedResults(race.season, race.round)
            val res = WeekendResults(
                race.season, race.round,
                race = rj?.let { ResultParsers.race(it) } ?: prev?.race.orEmpty(),
                sprint = sj?.takeIf { it.isNotBlank() }?.let { ResultParsers.race(it, "SprintResults") } ?: prev?.sprint.orEmpty(),
                quali = qj?.let { ResultParsers.qualifying(it) } ?: prev?.quali.orEmpty(),
                fetchedAt = System.currentTimeMillis(),
            )
            // Keep the raw feeds that worked; fall back to what was cached for the ones that didn't.
            val old = try { JSONObject(resultsFile(race.season, race.round).readText()) } catch (e: Exception) { JSONObject() }
            resultsFile(race.season, race.round).writeText(
                JSONObject()
                    .put("race", rj ?: old.optString("race"))
                    .put("sprint", sj ?: old.optString("sprint"))
                    .put("quali", qj ?: old.optString("quali"))
                    .put("at", res.fetchedAt)
                    .toString()
            )
            res
        }
    }

    /** Official standings after [round] of [season], if Jolpica has them yet. */
    suspend fun standingsAfter(season: Int, round: Int): List<StandingAfter>? = withContext(Dispatchers.IO) {
        try {
            val (r, rows) = Report.standingsAfter(Net.get("$JOLPICA/$season/$round/driverStandings.json", "application/json")) ?: return@withContext null
            rows.takeIf { r == round && it.isNotEmpty() }
        } catch (e: Exception) { null }
    }

    // ── Whole season, every driver (for the driver and team panels) ──

    private fun seasonStatsFile(season: Int) = File(dir, "season-stats-$season.json")

    /**
     * Saved before the latest race's results were out (results take about two hours to be
     * published), and they're out now: fetch again.
     */
    private fun stale(savedAt: Long, lastRaceEnd: Long?): Boolean {
        if (lastRaceEnd == null) return false
        val published = lastRaceEnd + 2 * HOUR_MS
        return savedAt < published && System.currentTimeMillis() >= published
    }

    /** Every row of a Jolpica query, page by page (100 a page, paced by [Net]). */
    private fun allPages(url: String): List<String> {
        val first = Net.get("$url?limit=100", "application/json")
        val total = DriverStats.total(first)
        return listOf(first) + DriverStats.pageOffsets(total).map { Net.get("$url?limit=100&offset=$it", "application/json") }
    }

    fun cachedSeasonStats(season: Int): DriverStats.SeasonStats? = try {
        val f = seasonStatsFile(season)
        val o = JSONObject(f.readText())
        fun pages(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        DriverStats.seasonAll(season, pages("results"), pages("quali"), pages("sprint"), o.optLong("at"))
    } catch (e: Exception) { null }

    /**
     * The whole season's races, qualifying and sprints in a dozen or so requests, saved on the
     * phone; every driver's numbers are worked out from it. Downloaded again only when a race
     * has finished since (plus two hours for the results to be published), or after a day.
     * If the download fails, the saved copy is used.
     */
    suspend fun seasonStats(season: Int, lastRaceEnd: Long?, force: Boolean = false): DriverStats.SeasonStats? = withContext(Dispatchers.IO) {
        val cached = cachedSeasonStats(season)
        val now = System.currentTimeMillis()
        if (cached != null && !force) {
            val age = now - cached.fetchedAt
            val afterLastRace = lastRaceEnd == null || cached.fetchedAt > lastRaceEnd + 2 * HOUR_MS || now < lastRaceEnd + 2 * HOUR_MS
            if (age < 30 * MIN_MS || (afterLastRace && age < 24 * HOUR_MS)) return@withContext cached
        }
        try {
            val results = allPages("$JOLPICA/$season/results.json")
            val quali = try { allPages("$JOLPICA/$season/qualifying.json") } catch (e: Exception) { emptyList() }
            val sprint = try { allPages("$JOLPICA/$season/sprint.json") } catch (e: Exception) { emptyList() }
            val stats = DriverStats.seasonAll(season, results, quali, sprint, now)
            if (stats.drivers.isEmpty()) return@withContext cached ?: stats
            fun arr(l: List<String>) = org.json.JSONArray().apply { l.forEach { put(it) } }
            seasonStatsFile(season).writeText(
                JSONObject().put("at", now).put("results", arr(results)).put("quali", arr(quali)).put("sprint", arr(sprint)).toString()
            )
            stats
        } catch (e: Exception) {
            cached
        }
    }

    /**
     * Career totals: the driver's whole race history and front-row list, fetched once per
     * season and kept (past seasons never change), plus this season from [current]. A few
     * requests the first time a driver is opened in a season, none after that.
     */
    suspend fun driverCareer(id: String, currentSeason: Int, seasonOver: Boolean, current: DriverSeason?): DriverCareer? = withContext(Dispatchers.IO) {
        val f = File(dir, "career3-$id-$currentSeason.json")
        fun pages(o: JSONObject, k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val saved = try { JSONObject(f.readText()) } catch (e: Exception) { null }
        val o = saved ?: run {
            val results = allPages("$JOLPICA/drivers/$id/results.json")
            val poles = try { allPages("$JOLPICA/drivers/$id/qualifying/1.json") } catch (e: Net.TooManyRequests) { throw e } catch (e: Exception) { emptyList() }
            fun arr(l: List<String>) = org.json.JSONArray().apply { l.forEach { put(it) } }
            JSONObject().put("results", arr(results)).put("poles", arr(poles)).also {
                try { f.writeText(it.toString()) } catch (e: Exception) { }
            }
        }
        val partial = DriverStats.career(pages(o, "results"), pages(o, "poles"), currentSeason, current, null)
        val champs = champions(partial.seasons.filter { it < currentSeason || (it == currentSeason && seasonOver) })
        partial.copy(titles = champs?.let { m -> m.filterValues { it == id }.keys.sorted() })
    }

    /**
     * Season → champion's driver id, for finished seasons. Kept on the phone for good (a past
     * champion never changes), so each season is asked about once, whoever's panel asks.
     * Null if any season couldn't be looked up.
     */
    private fun champions(seasons: List<Int>): Map<Int, String>? {
        val f = File(dir, "champions.json")
        val known = try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() }
        var ok = true
        var changed = false
        for (y in seasons) {
            if (known.has(y.toString())) continue
            try {
                DriverStats.champion(Net.get("$JOLPICA/$y/driverStandings/1.json", "application/json"))?.let {
                    known.put(y.toString(), it); changed = true
                } ?: run { ok = false }
            } catch (e: Exception) { ok = false }
        }
        if (changed) try { f.writeText(known.toString()) } catch (e: Exception) { }
        if (!ok) return null
        return seasons.associateWith { known.optString(it.toString()) }
    }

    /** This driver's record at a circuit, cached for a day. */
    suspend fun trackRecord(circuitId: String, driverId: String, lastRaceEnd: Long? = null): TrackRecord? = withContext(Dispatchers.IO) {
        val f = File(dir, "track-$circuitId-$driverId.json")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * HOUR_MS && !stale(f.lastModified(), lastRaceEnd)) {
            try { return@withContext DriverStats.trackRecord(f.readText()) } catch (e: Exception) { }
        }
        try {
            val raw = Net.get("$JOLPICA/circuits/$circuitId/drivers/$driverId/results.json?limit=100", "application/json")
            f.writeText(raw)
            DriverStats.trackRecord(raw)
        } catch (e: Exception) {
            try { DriverStats.trackRecord(f.readText()) } catch (e2: Exception) { null }
        }
    }

    /** Every driver's official headshot URL (OpenF1, saved with the team colours), by family name. */
    fun headshots(): Map<String, String> = try {
        DriverStats.headshots(JSONObject(teamColourFile.readText()).getString("raw"))
    } catch (e: Exception) { emptyMap() }

    /** An image, cached on disk for a week. */
    suspend fun image(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = File(dir, "img-" + Integer.toHexString(url.hashCode()) + ".bin")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 24 * HOUR_MS) {
            return@withContext try { f.readBytes() } catch (e: Exception) { null }
        }
        try {
            Net.bytes(url).also { f.writeBytes(it) }
        } catch (e: Exception) {
            if (f.exists()) try { f.readBytes() } catch (e2: Exception) { null } else null
        }
    }

    // ── Podiums (for the race cards) ──

    private fun podiumFile(season: Int) = File(dir, "podiums-$season.json")

    fun cachedPodiums(season: Int): Pair<Map<Int, List<PodiumEntry>>, Long> = try {
        val o = JSONObject(podiumFile(season).readText())
        ResultParsers.podiums(listOf(o.optString("p1"), o.optString("p2"), o.optString("p3"))) to o.optLong("at")
    } catch (e: Exception) { emptyMap<Int, List<PodiumEntry>>() to 0L }

    suspend fun fetchPodiums(season: Int): Map<Int, List<PodiumEntry>> = withContext(Dispatchers.IO) {
        val raw = coroutineScope {
            (1..3).map { p -> async { Net.get("$JOLPICA/$season/results/$p.json?limit=40", "application/json") } }.map { it.await() }
        }
        podiumFile(season).writeText(
            JSONObject().put("p1", raw[0]).put("p2", raw[1]).put("p3", raw[2]).put("at", System.currentTimeMillis()).toString()
        )
        ResultParsers.podiums(raw)
    }

    // ── Weather ──

    private fun weatherFile(circuitId: String) = File(dir, "weather-$circuitId.json")

    fun cachedWeather(circuitId: String): Forecast? = try {
        val o = JSONObject(weatherFile(circuitId).readText())
        ResultParsers.forecast(o.getString("raw"), o.optLong("at"))
    } catch (e: Exception) { null }

    /** Open-Meteo hourly + daily forecast at the track, in the track's own time zone. */
    suspend fun fetchWeather(circuitId: String, lat: Double, lon: Double): Forecast = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=16"
        val raw = Net.get(url, "application/json")
        val now = System.currentTimeMillis()
        val f = ResultParsers.forecast(raw, now)
        if (f.hours.isEmpty()) throw IOException("No forecast")
        weatherFile(circuitId).writeText(JSONObject().put("raw", raw).put("at", now).toString())
        f
    }

    /** A circuit's position: from the live calendar, else the bundled circuit list. */
    fun coords(circuit: Circuit): Pair<Double, Double>? {
        if (circuit.lat != null && circuit.lon != null) return circuit.lat to circuit.lon
        val s = circuits()[circuit.id] ?: return null
        val lat = s.lat ?: return null
        val lon = s.lon ?: return null
        return lat to lon
    }

    // ── Team colours (OpenF1, free once a session is over) ──

    private val teamColourFile get() = File(dir, "team-colours.json")

    /** Loads cached official colours into [Teams.live]. Cheap; call at startup and in widgets. */
    fun loadTeamColours() {
        if (Teams.live.isNotEmpty()) return
        try {
            val o = JSONObject(teamColourFile.readText())
            Teams.live = ResultParsers.teamColours(o.getString("raw"))
        } catch (e: Exception) { }
    }

    /** Refreshes the official colours at most daily. During a live session OpenF1 needs a login; that just fails quietly. */
    suspend fun refreshTeamColours(force: Boolean = false) = withContext(Dispatchers.IO) {
        val at = try { JSONObject(teamColourFile.readText()).optLong("at") } catch (e: Exception) { 0L }
        if (!force && System.currentTimeMillis() - at < 24 * HOUR_MS) return@withContext
        try {
            val raw = Net.get("https://api.openf1.org/v1/drivers?session_key=latest", "application/json")
            val map = ResultParsers.teamColours(raw)
            if (map.size >= 8) {
                teamColourFile.writeText(JSONObject().put("raw", raw).put("at", System.currentTimeMillis()).toString())
                Teams.live = map
            }
        } catch (e: Exception) { }
    }

    // ── News ──

    fun cachedNews(): Pair<List<NewsItem>, Long> = try {
        val o = JSONObject(File(dir, "news.json").readText())
        val arr = o.getJSONArray("items")
        val items = (0 until arr.length()).map {
            val n = arr.getJSONObject(it)
            NewsItem(n.getString("title"), n.getString("link"), n.optLong("published"), n.optString("excerpt"), n.optString("source"))
        }
        items to o.optLong("at")
    } catch (e: Exception) { emptyList<NewsItem>() to 0L }

    suspend fun fetchNews(): List<NewsItem> = withContext(Dispatchers.IO) {
        val lists = coroutineScope {
            NEWS.map { src ->
                async {
                    try { Parsers.rss(Net.get(src.url, "application/rss+xml, application/xml, text/xml"), src.name) } catch (e: Exception) { emptyList() }
                }
            }.map { it.await() }
        }
        val merged = Parsers.mergeNews(lists)
        if (merged.isEmpty()) throw IOException("No articles loaded")
        val arr = org.json.JSONArray()
        merged.take(120).forEach {
            arr.put(JSONObject().put("title", it.title).put("link", it.link).put("published", it.published).put("excerpt", it.excerpt).put("source", it.source))
        }
        File(dir, "news.json").writeText(JSONObject().put("items", arr).put("at", System.currentTimeMillis()).toString())
        merged
    }
}
