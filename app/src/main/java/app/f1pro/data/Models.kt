package app.f1pro.data

const val MIN_MS = 60_000L
const val HOUR_MS = 60 * MIN_MS
const val DAY_MS = 24 * HOUR_MS

data class Circuit(
    val id: String,
    val name: String,
    val locality: String,
    val country: String,
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
) {
    val url: String get() = Teams.driverUrl(name, surname)
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
