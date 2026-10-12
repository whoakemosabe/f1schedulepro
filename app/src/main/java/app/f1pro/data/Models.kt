package app.f1pro.data

const val MIN_MS = 60_000L
const val HOUR_MS = 60 * MIN_MS
const val DAY_MS = 24 * HOUR_MS

data class Circuit(
    val id: String,
    val name: String,
    val locality: String,
    val country: String,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val countryCode: String get() = Countries.code(country)
    val flag: String get() = Countries.flag(countryCode)
}

/** One session. [tbc] means the feed has a date but no start time yet. */
data class Session(val name: String, val start: Long, val tbc: Boolean = false) {
    val end: Long get() = start + Sessions.durationMin(name) * MIN_MS
    fun isLive(now: Long): Boolean = !tbc && now in start..end
    val short: String get() = Sessions.short(name)
}

enum class RaceType { CONVENTIONAL, SPRINT, TESTING }

enum class RaceStatus { COMPLETED, ACTIVE, UPCOMING }

data class Race(
    val season: Int,
    val round: Int,
    val name: String,
    val officialName: String?,
    val circuit: Circuit,
    val sessions: List<Session>,
    val type: RaceType,
    val f1ResultsId: Int? = null,
    val f1ResultsSlug: String? = null,
) {
    val isTesting: Boolean get() = type == RaceType.TESTING
    val isSprint: Boolean get() = type == RaceType.SPRINT

    /** Stable id across refreshes, used for notifications and deep links. */
    val key: String get() = if (isTesting) "$season-test-$round" else "$season-$round"

    /** "R07", or "TEST" for pre-season testing. */
    val label: String get() = if (isTesting) "TEST" else "R" + round.toString().padStart(2, '0')

    val first: Long get() = sessions.first().start
    val raceSession: Session? get() = sessions.firstOrNull { it.name == "Race" }

    /** Like the PWA: a weekend is over three hours after its last session starts (or when it ends). */
    val endsAt: Long get() = sessions.last().let { maxOf(it.start + 3 * HOUR_MS, it.end) }

    fun status(now: Long): RaceStatus = when {
        now > endsAt -> RaceStatus.COMPLETED
        now >= first -> RaceStatus.ACTIVE
        else -> RaceStatus.UPCOMING
    }

    fun nextSession(now: Long): Session? = sessions.firstOrNull { it.start > now }
    fun liveSession(now: Long): Session? = sessions.firstOrNull { it.isLive(now) }

    /** formula1.com results page for a finished session, when we know the race's id. */
    fun resultsUrl(session: Session): String? {
        val id = f1ResultsId ?: return null
        val slug = f1ResultsSlug ?: return null
        val suffix = Sessions.resultsSuffix(session.name) ?: return null
        return "https://www.formula1.com/en/results/$season/races/$id/$slug/$suffix"
    }
}

data class SeasonCalendar(val year: Int, val races: List<Race>, val live: Boolean)

object Sessions {
    private val DURATION = mapOf(
        "Practice 1" to 60, "Practice 2" to 60, "Practice 3" to 60,
        "Sprint Qualifying" to 60, "Sprint" to 60, "Qualifying" to 60, "Race" to 150,
    )
    private val SHORT = mapOf(
        "Practice 1" to "FP1", "Practice 2" to "FP2", "Practice 3" to "FP3",
        "Sprint Qualifying" to "SQ", "Qualifying" to "QUALI", "Sprint" to "SPRINT", "Race" to "RACE",
    )
    private val RESULTS = mapOf(
        "Practice 1" to "practice/1", "Practice 2" to "practice/2", "Practice 3" to "practice/3",
        "Sprint Qualifying" to "sprint-qualifying", "Sprint" to "sprint-results",
        "Qualifying" to "qualifying", "Race" to "race-result",
    )

    fun durationMin(name: String): Long = when {
        name.startsWith("Day") -> 540L // testing days run all day
        else -> (DURATION[name] ?: 90).toLong()
    }

    fun short(name: String): String = SHORT[name] ?: name.uppercase()
    fun resultsSuffix(name: String): String? = RESULTS[name]

    /** Which sessions a reminder setting covers: "all", "quali" (qualifying + races) or "race". */
    fun wanted(name: String, which: String): Boolean = when (which) {
        "race" -> name == "Race" || name == "Sprint"
        "quali" -> name == "Race" || name == "Sprint" || name == "Qualifying" || name == "Sprint Qualifying"
        else -> true
    }
}

data class DriverStanding(
    val position: Int,
    val points: Double,
    val wins: Int,
    val name: String,
    val surname: String,
    val nationality: String,
    val number: String?,
    val teamId: String,
    val teamName: String,
    /** The three-letter code ("NOR"), from the feed when it has one. */
    val shortCode: String? = null,
    /** The feed's driver id, the same one Jolpica uses ("antonelli", "max_verstappen"). */
    val driverId: String? = null,
) {
    val url: String get() = Teams.driverUrl(name, surname)

    /** "NOR": the feed's code, else the first three letters of the surname (accents dropped). */
    val code: String get() = shortCode?.takeIf { it.length == 3 }?.uppercase()
        ?: Names.norm(surname).take(3).uppercase().ifBlank { "—" }
}

data class TeamStanding(
    val position: Int,
    val points: Double,
    val wins: Int,
    val teamId: String,
    val teamName: String,
)

data class Standings(
    val season: Int,
    val drivers: List<DriverStanding>,
    val teams: List<TeamStanding>,
    val fetchedAt: Long,
)

data class NewsItem(
    val title: String,
    val link: String,
    val published: Long,
    val excerpt: String,
    val source: String,
)

// ── Results (Jolpica) ──

data class RaceResult(
    val position: Int?,
    val positionText: String,
    val number: String,
    val code: String,
    val givenName: String,
    val familyName: String,
    val nationality: String,
    val constructorId: String,
    val constructorName: String,
    val grid: Int?,
    val laps: Int,
    val status: String,
    val time: String?,
    val points: Double,
    val fastestRank: Int?,
    val fastestLap: String?,
) {
    val name: String get() = "$givenName $familyName"

    /** "+2.307", the winner's race time, "+1 Lap", or why they stopped ("Retired"). */
    val gap: String get() = time ?: status

    /** Places gained (+) or lost (−) from the grid; null for a pit-lane start or no grid. */
    val gained: Int? get() = if (grid != null && grid > 0 && position != null) grid - position else null

    /** Didn't see the flag: retired, disqualified, didn't start. */
    val out: Boolean get() = time == null && !status.startsWith("+") && status != "Finished" && !status.contains("Lap")
}

data class QualiResult(
    val position: Int?,
    val number: String,
    val code: String,
    val givenName: String,
    val familyName: String,
    val constructorId: String,
    val constructorName: String,
    val q1: String?,
    val q2: String?,
    val q3: String?,
) {
    val name: String get() = "$givenName $familyName"
    val best: String? get() = q3 ?: q2 ?: q1
    val part: String get() = when { q3 != null -> "Q3"; q2 != null -> "Q2"; q1 != null -> "Q1"; else -> "" }
}

/** Everything published for one weekend. Empty lists mean "not out yet". */
data class WeekendResults(
    val season: Int,
    val round: Int,
    val race: List<RaceResult>,
    val sprint: List<RaceResult>,
    val quali: List<QualiResult>,
    val fetchedAt: Long,
) {
    val isEmpty: Boolean get() = race.isEmpty() && sprint.isEmpty() && quali.isEmpty()
}

data class PodiumEntry(val position: Int, val code: String, val familyName: String, val constructorId: String)

// ── Weather (Open-Meteo) ──

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class WeatherHour(val time: Long, val tempC: Double, val rainChance: Int?, val code: Int, val windKmh: Double?) {
    val sky: Sky get() = Wmo.sky(code)
}

data class WeatherDay(val date: java.time.LocalDate, val maxC: Double, val minC: Double, val rainChance: Int?, val code: Int) {
    val sky: Sky get() = Wmo.sky(code)
}

data class Forecast(
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
    val fetchedAt: Long,
    /** The track's offset from UTC, for turning session times into its local dates. */
    val offsetMs: Long = 0,
) {
    fun localDate(ms: Long): java.time.LocalDate =
        java.time.Instant.ofEpochMilli(ms + offsetMs).atZone(java.time.ZoneOffset.UTC).toLocalDate()

    /** The forecast hour nearest to [ms], if the forecast reaches that far. */
    fun at(ms: Long): WeatherHour? =
        hours.minByOrNull { kotlin.math.abs(it.time - ms) }?.takeIf { kotlin.math.abs(it.time - ms) <= 90 * MIN_MS }

    fun day(date: java.time.LocalDate): WeatherDay? = days.firstOrNull { it.date == date }
}

/** WMO weather codes, as Open-Meteo reports them. */
object Wmo {
    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun label(sky: Sky): String = when (sky) {
        Sky.CLEAR -> "Clear"
        Sky.PARTLY -> "Partly cloudy"
        Sky.CLOUDY -> "Cloudy"
        Sky.FOG -> "Fog"
        Sky.DRIZZLE -> "Drizzle"
        Sky.RAIN -> "Rain"
        Sky.SNOW -> "Snow"
        Sky.STORM -> "Thunderstorms"
    }

    fun wet(sky: Sky): Boolean = sky == Sky.DRIZZLE || sky == Sky.RAIN || sky == Sky.STORM || sky == Sky.SNOW
}

// ── Favourite driver ──

/** The driver picked in Settings, matched across feeds by name (ids differ between sources). */
data class Favourite(val givenName: String, val familyName: String, val teamId: String, val teamName: String) {
    val name: String get() = "$givenName $familyName"

    /**
     * Same driver? Feeds split names differently ("Andrea" + "Kimi Antonelli" in one,
     * "Andrea Kimi" + "Antonelli" in another), so the whole name counts, and family names
     * match on their last word too.
     */
    fun matches(given: String, family: String): Boolean {
        if (Names.norm(given + family) == Names.norm(givenName + familyName)) return true
        val sameFamily = Names.norm(family) == Names.norm(familyName) ||
            Names.norm(family.trim().substringAfterLast(' ')) == Names.norm(familyName.trim().substringAfterLast(' '))
        if (!sameFamily) return false
        val a = Names.norm(given)
        val b = Names.norm(givenName)
        if (a == b || a.startsWith(b) || b.startsWith(a) || a.isEmpty() || b.isEmpty()) return true
        // "Kimi Antonelli" vs "Andrea Kimi Antonelli": every word of one name is in the other.
        fun words(x: String) = x.split(' ', '-').map { Names.norm(it) }.filter { it.isNotEmpty() }.toSet()
        val w1 = words("$given $family")
        val w2 = words("$givenName $familyName")
        return w1.containsAll(w2) || w2.containsAll(w1)
    }

    /** Does [text] (a headline) name this driver? Their family name as a whole word, accents ignored. */
    fun mentionedIn(text: String): Boolean {
        val last = Names.norm(familyName.trim().substringAfterLast(' '))
        if (last.length < 3) return false
        val words = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase()
            .split(Regex("[^\\p{L}]+"))
        return words.any { it == last || it == last + "s" }
    }
}

object Names {
    /** "Hülkenberg" → "hulkenberg", "Andrea Kimi" → "andreakimi". */
    fun norm(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .filter { it.isLetter() }
}
