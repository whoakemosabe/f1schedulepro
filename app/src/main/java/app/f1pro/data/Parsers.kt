package app.f1pro.data

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Things the live calendar doesn't carry: official names, formula1.com result ids, testing. */
data class SeasonExtras(
    val season: Int,
    val races: Map<String, RaceExtra>,
    val testing: List<Race>,
)

data class RaceExtra(val officialName: String?, val f1ResultsId: Int?, val f1ResultsSlug: String?)

data class CircuitStats(
    val lengthKm: Double,
    val turns: Int,
    val drsZones: Int,
    val firstGP: Int,
    val lapRecord: LapRecord?,
    val type: String,
    val night: Boolean,
    val wiki: String,
    val lat: Double? = null,
    val lon: Double? = null,
)

data class LapRecord(val time: String, val driver: String, val year: Int, val driverUrl: String)

/** Pure parsers, kept free of Android classes so they run in plain JVM unit tests. */
object Parsers {

    // ── Calendar (Jolpica / Ergast shape) ──

    private val SESSION_KEYS = listOf(
        "FirstPractice" to "Practice 1",
        "SecondPractice" to "Practice 2",
        "ThirdPractice" to "Practice 3",
        "SprintQualifying" to "Sprint Qualifying",
        "SprintShootout" to "Sprint Qualifying",
        "Sprint" to "Sprint",
        "Qualifying" to "Qualifying",
    )

    /** Reads a Jolpica season. Returns null if it isn't one, or has no races yet. */
    fun calendar(json: String, extras: SeasonExtras?, live: Boolean): SeasonCalendar? {
        val table = JSONObject(json).optJSONObject("MRData")?.optJSONObject("RaceTable") ?: return null
        val year = table.optString("season").toIntOrNull() ?: return null
        val list = table.optJSONArray("Races") ?: return null
        val races = ArrayList<Race>()
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i)
            val c = r.getJSONObject("Circuit")
            val loc = c.optJSONObject("Location")
            val circuit = Circuit(
                id = c.getString("circuitId"),
                name = c.optString("circuitName"),
                locality = loc?.optString("locality").orEmpty(),
                country = loc?.optString("country").orEmpty(),
                lat = loc?.optString("lat")?.toDoubleOrNull(),
                lon = loc?.optString("long")?.toDoubleOrNull(),
            )
            val sessions = ArrayList<Session>()
            for ((key, name) in SESSION_KEYS) {
                val o = r.optJSONObject(key) ?: continue
                if (sessions.any { it.name == name }) continue
                sessionOf(name, o.optString("date"), o.optString("time"))?.let { sessions += it }
            }
            sessionOf("Race", r.optString("date"), r.optString("time"))?.let { sessions += it }
            if (sessions.isEmpty()) continue
            sessions.sortBy { it.start }
            val extra = extras?.takeIf { it.season == year }?.races?.get(circuit.id)
            races += Race(
                season = year,
                round = r.optString("round").toIntOrNull() ?: (i + 1),
                name = r.optString("raceName"),
                officialName = extra?.officialName,
                circuit = circuit,
                sessions = sessions,
                type = if (sessions.any { it.name == "Sprint" }) RaceType.SPRINT else RaceType.CONVENTIONAL,
                f1ResultsId = extra?.f1ResultsId,
                f1ResultsSlug = extra?.f1ResultsSlug,
            )
        }
        if (races.isEmpty()) return null
        extras?.takeIf { it.season == year }?.testing?.let { races += it }
        races.sortBy { it.first }
        return SeasonCalendar(year, races, live)
    }

    private fun sessionOf(name: String, date: String, time: String): Session? {
        if (date.isBlank()) return null
        return try {
            if (time.isBlank()) {
                // Date known, start time not announced yet: keep it for the list, never alert on it.
                val ms = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
                Session(name, ms, tbc = true)
            } else {
                val t = if (time.endsWith("Z") || time.contains('+')) time else time + "Z"
                Session(name, ZonedDateTime.parse("${date}T$t").toInstant().toEpochMilli())
            }
        } catch (e: Exception) {
            null
        }
    }

    fun extras(json: String): SeasonExtras {
        val o = JSONObject(json)
        val season = o.getInt("season")
        val races = HashMap<String, RaceExtra>()
        o.optJSONObject("races")?.let { rs ->
            for (id in rs.keys()) {
                val e = rs.getJSONObject(id)
                races[id] = RaceExtra(
                    officialName = e.optString("officialName").takeIf { it.isNotBlank() && it != "null" },
                    f1ResultsId = if (e.isNull("f1ResultsId")) null else e.optInt("f1ResultsId"),
                    f1ResultsSlug = if (e.isNull("f1ResultsSlug")) null else e.optString("f1ResultsSlug"),
                )
            }
        }
        val testing = ArrayList<Race>()
        o.optJSONArray("testing")?.let { ts ->
            for (i in 0 until ts.length()) {
                val t = ts.getJSONObject(i)
                val sess = t.getJSONArray("sessions")
                val sessions = (0 until sess.length()).map {
                    val s = sess.getJSONObject(it)
                    Session(s.getString("name"), Instant.parse(s.getString("dateUtc")).toEpochMilli())
                }.sortedBy { it.start }
                if (sessions.isEmpty()) continue
                testing += Race(
                    season = season,
                    round = i,
                    name = t.getString("name"),
                    officialName = t.optString("officialName").takeIf { it.isNotBlank() },
                    circuit = Circuit(t.getString("circuitId"), t.optString("circuitName"), t.optString("locality"), t.optString("country")),
                    sessions = sessions,
                    type = RaceType.TESTING,
                )
            }
        }
        return SeasonExtras(season, races, testing)
    }

    fun circuits(json: String): Map<String, CircuitStats> {
        val o = JSONObject(json)
        val out = HashMap<String, CircuitStats>()
        for (id in o.keys()) {
            val c = o.optJSONObject(id) ?: continue
            val lr = c.optJSONObject("lapRecord")
            out[id] = CircuitStats(
                lengthKm = c.optDouble("lengthKm"),
                turns = c.optInt("turns"),
                drsZones = c.optInt("drsZones"),
                firstGP = c.optInt("firstGP"),
                lapRecord = lr?.let { LapRecord(it.optString("time"), it.optString("driver"), it.optInt("year"), it.optString("driverUrl")) },
                type = c.optString("type"),
                night = c.optBoolean("night"),
                wiki = c.optString("wiki"),
                lat = if (c.has("lat")) c.optDouble("lat") else null,
                lon = if (c.has("lon")) c.optDouble("lon") else null,
            )
        }
        return out
    }

    // ── Standings (f1api.dev) ──

    fun drivers(json: String): Pair<Int, List<DriverStanding>> {
        val o = JSONObject(json)
        val arr = o.optJSONArray("drivers_championship") ?: JSONArray()
        val list = (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val drv = d.optJSONObject("driver") ?: JSONObject()
            val team = d.optJSONObject("team") ?: JSONObject()
            DriverStanding(
                position = d.optInt("position", i + 1),
                points = d.optDouble("points", 0.0).let { if (it.isNaN()) 0.0 else it },
                wins = d.optInt("wins"),
                name = drv.optString("name"),
                surname = drv.optString("surname"),
                nationality = drv.optString("nationality"),
                number = drv.optString("number").takeIf { it.isNotBlank() && it != "null" },
                teamId = d.optString("teamId").ifBlank { team.optString("teamId") },
                teamName = team.optString("teamName"),
            )
        }.sortedBy { it.position }
        return o.optInt("season") to list
    }

    fun teams(json: String): List<TeamStanding> {
        val o = JSONObject(json)
        val arr = o.optJSONArray("constructors_championship") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            val team = t.optJSONObject("team") ?: JSONObject()
            TeamStanding(
                position = t.optInt("position", i + 1),
                points = t.optDouble("points", 0.0).let { if (it.isNaN()) 0.0 else it },
                wins = t.optInt("wins"),
                teamId = t.optString("teamId"),
                teamName = team.optString("teamName"),
            )
        }.sortedBy { it.position }
    }

    /** Constructors from the driver table, for when the constructors feed is down. */
    fun teamsFromDrivers(drivers: List<DriverStanding>): List<TeamStanding> =
        drivers.groupBy { it.teamId }
            .map { (id, ds) -> TeamStanding(0, ds.sumOf { it.points }, ds.sumOf { it.wins }, id, ds.first().teamName) }
            .sortedByDescending { it.points }
            .mapIndexed { i, t -> t.copy(position = i + 1) }

    // ── News (RSS 2.0 or Atom) ──

    fun rss(xml: String, source: String): List<NewsItem> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (e: Exception) { }
        try { factory.isExpandEntityReferences = false } catch (e: Exception) { }
        val doc = factory.newDocumentBuilder().parse(xml.trim().byteInputStream(Charsets.UTF_8))
        val out = ArrayList<NewsItem>()
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val e = items.item(i) as? Element ?: continue
            val title = text(e, "title")
            val link = text(e, "link").ifBlank { text(e, "guid") }
            if (title.isBlank() || !link.startsWith("http")) continue
            out += NewsItem(
                title = stripHtml(title),
                link = link.trim(),
                published = date(text(e, "pubDate").ifBlank { text(e, "dc:date") }),
                excerpt = stripHtml(text(e, "description")).take(200),
                source = source,
            )
        }
        if (out.isEmpty()) {
            val entries = doc.getElementsByTagName("entry")
            for (i in 0 until entries.length) {
                val e = entries.item(i) as? Element ?: continue
                val links = e.getElementsByTagName("link")
                var href = ""
                for (j in 0 until links.length) {
                    val l = links.item(j) as Element
                    if (l.getAttribute("rel").let { it.isBlank() || it == "alternate" }) { href = l.getAttribute("href"); break }
                }
                val title = text(e, "title")
                if (title.isBlank() || !href.startsWith("http")) continue
                out += NewsItem(
                    title = stripHtml(title),
                    link = href,
                    published = date(text(e, "published").ifBlank { text(e, "updated") }),
                    excerpt = stripHtml(text(e, "summary").ifBlank { text(e, "content") }).take(200),
                    source = source,
                )
            }
        }
        return out
    }

    /** All feeds together: newest first, one copy of each headline. */
    fun mergeNews(lists: List<List<NewsItem>>): List<NewsItem> {
        val seen = HashSet<String>()
        return lists.flatten()
            .sortedByDescending { it.published }
            .filter { seen.add(it.title.lowercase().trim()) }
    }

    private fun text(parent: Element, tag: String): String {
        val nodes = parent.getElementsByTagName(tag)
        if (nodes.length == 0) return ""
        return nodes.item(0).textContent?.trim().orEmpty()
    }

    private val DATE_PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss Z",
    )

    /** RSS and Atom dates to epoch ms; 0 when unreadable. */
    fun date(s: String): Long {
        val v = s.trim()
        if (v.isEmpty()) return 0
        try { return ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() } catch (e: Exception) { }
        try { return ZonedDateTime.parse(v).toInstant().toEpochMilli() } catch (e: Exception) { }
        try { return Instant.parse(v).toEpochMilli() } catch (e: Exception) { }
        for (p in DATE_PATTERNS) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.isLenient = true
                return f.parse(v)?.time ?: continue
            } catch (e: Exception) { }
        }
        return 0
    }

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "ndash" to "–", "mdash" to "—", "hellip" to "…",
    )

    fun stripHtml(html: String): String {
        val noTags = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?s)<[^>]*>"), " ")
        val decoded = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(noTags) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> ENTITIES[v.lowercase()] ?: m.value
            }
        }
        return decoded.replace(Regex("\\s+"), " ").trim()
    }
}

/** Results, podiums and weather: also pure, also JVM-testable. */
object ResultParsers {
    private fun races(json: String): JSONArray? =
        JSONObject(json).optJSONObject("MRData")?.optJSONObject("RaceTable")?.optJSONArray("Races")

    private fun int(o: JSONObject, key: String): Int? = o.optString(key).trim().toIntOrNull()

    private fun str(o: JSONObject?, key: String): String? =
        o?.optString(key)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    /** Race or sprint classification ([key] "Results" or "SprintResults"). */
    fun race(json: String, key: String = "Results"): List<RaceResult> {
        val r = races(json)?.optJSONObject(0)?.optJSONArray(key) ?: return emptyList()
        return (0 until r.length()).map { i ->
            val o = r.getJSONObject(i)
            val d = o.optJSONObject("Driver") ?: JSONObject()
            val c = o.optJSONObject("Constructor") ?: JSONObject()
            val fl = o.optJSONObject("FastestLap")
            RaceResult(
                position = int(o, "position"),
                positionText = o.optString("positionText"),
                number = o.optString("number"),
                code = d.optString("code").ifBlank { d.optString("familyName").take(3).uppercase() },
                givenName = d.optString("givenName"),
                familyName = d.optString("familyName"),
                nationality = d.optString("nationality"),
                constructorId = c.optString("constructorId"),
                constructorName = c.optString("name"),
                grid = int(o, "grid"),
                laps = int(o, "laps") ?: 0,
                status = o.optString("status"),
                time = str(o.optJSONObject("Time"), "time"),
                points = o.optString("points").toDoubleOrNull() ?: 0.0,
                fastestRank = fl?.let { int(it, "rank") },
                fastestLap = str(fl?.optJSONObject("Time"), "time"),
            )
        }.sortedBy { it.position ?: Int.MAX_VALUE }
    }

    fun qualifying(json: String): List<QualiResult> {
        val r = races(json)?.optJSONObject(0)?.optJSONArray("QualifyingResults") ?: return emptyList()
        return (0 until r.length()).map { i ->
            val o = r.getJSONObject(i)
            val d = o.optJSONObject("Driver") ?: JSONObject()
            val c = o.optJSONObject("Constructor") ?: JSONObject()
            QualiResult(
                position = int(o, "position"),
                number = o.optString("number"),
                code = d.optString("code").ifBlank { d.optString("familyName").take(3).uppercase() },
                givenName = d.optString("givenName"),
                familyName = d.optString("familyName"),
                constructorId = c.optString("constructorId"),
                constructorName = c.optString("name"),
                q1 = str(o, "Q1"),
                q2 = str(o, "Q2"),
                q3 = str(o, "Q3"),
            )
        }.sortedBy { it.position ?: Int.MAX_VALUE }
    }

    /**
     * One season's podiums from the three position-filtered feeds (/results/1, /2, /3):
     * round → [P1, P2, P3] (fewer while a race is still being classified).
     */
    fun podiums(jsons: List<String>): Map<Int, List<PodiumEntry>> {
        val out = HashMap<Int, MutableList<PodiumEntry>>()
        for (json in jsons) {
            val rs = try { races(json) } catch (e: Exception) { null } ?: continue
            for (i in 0 until rs.length()) {
                val race = rs.getJSONObject(i)
                val round = int(race, "round") ?: continue
                val res = race.optJSONArray("Results") ?: continue
                for (j in 0 until res.length()) {
                    val o = res.getJSONObject(j)
                    val pos = int(o, "position") ?: continue
                    if (pos !in 1..3) continue
                    val d = o.optJSONObject("Driver") ?: JSONObject()
                    out.getOrPut(round) { ArrayList() } += PodiumEntry(
                        pos,
                        d.optString("code").ifBlank { d.optString("familyName").take(3).uppercase() },
                        d.optString("familyName"),
                        o.optJSONObject("Constructor")?.optString("constructorId").orEmpty(),
                    )
                }
            }
        }
        return out.mapValues { (_, v) -> v.distinctBy { it.position }.sortedBy { it.position } }
    }

    /** Open-Meteo forecast with timezone=auto: times are the track's local time plus an offset. */
    fun forecast(json: String, now: Long): Forecast {
        val o = JSONObject(json)
        val offset = o.optInt("utc_offset_seconds", 0).toLong() * 1000
        val hours = ArrayList<WeatherHour>()
        o.optJSONObject("hourly")?.let { h ->
            val t = h.optJSONArray("time") ?: JSONArray()
            val temp = h.optJSONArray("temperature_2m")
            val rain = h.optJSONArray("precipitation_probability")
            val code = h.optJSONArray("weather_code")
            val wind = h.optJSONArray("wind_speed_10m")
            for (i in 0 until t.length()) {
                val local = try { java.time.LocalDateTime.parse(t.getString(i)) } catch (e: Exception) { continue }
                val ms = local.toInstant(ZoneOffset.UTC).toEpochMilli() - offset
                val tc = temp?.optDouble(i)?.takeIf { !it.isNaN() } ?: continue
                hours += WeatherHour(
                    time = ms,
                    tempC = tc,
                    rainChance = rain?.let { if (it.isNull(i)) null else it.optInt(i) },
                    code = code?.optInt(i) ?: 3,
                    windKmh = wind?.optDouble(i)?.takeIf { !it.isNaN() },
                )
            }
        }
        val days = ArrayList<WeatherDay>()
        o.optJSONObject("daily")?.let { d ->
            val t = d.optJSONArray("time") ?: JSONArray()
            val max = d.optJSONArray("temperature_2m_max")
            val min = d.optJSONArray("temperature_2m_min")
            val rain = d.optJSONArray("precipitation_probability_max")
            val code = d.optJSONArray("weather_code")
            for (i in 0 until t.length()) {
                val date = try { LocalDate.parse(t.getString(i)) } catch (e: Exception) { continue }
                val hi = max?.optDouble(i)?.takeIf { !it.isNaN() } ?: continue
                val lo = min?.optDouble(i)?.takeIf { !it.isNaN() } ?: continue
                days += WeatherDay(date, hi, lo, rain?.let { if (it.isNull(i)) null else it.optInt(i) }, code?.optInt(i) ?: 3)
            }
        }
        return Forecast(hours, days, now, offset)
    }
}
