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
    fun get(url: String, accept: String = "*/*"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("User-Agent", "F1SchedulePro/2.0 (Android)")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
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
