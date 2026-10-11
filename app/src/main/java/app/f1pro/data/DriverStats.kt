package app.f1pro.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One round for one driver. [finish] is null for a retirement or disqualification ([status]
 * says why); [order] is the classified order, always set. [grid] 0 means a pit-lane start.
 */
data class DriverRound(
    val round: Int,
    val raceName: String,
    val finish: Int?,
    val order: Int,
    val status: String,
    val points: Double,
    val quali: Int?,
    val grid: Int? = null,
    val fastestRank: Int? = null,
    val sprintFinish: Int? = null,
    val sprintPoints: Double = 0.0,
) {
    /** Places made up from the grid (negative: lost). Null without a finish or a grid slot. */
    val gained: Int? get() = if (finish != null && grid != null && grid > 0) grid - finish else null
    val allPoints: Double get() = points + sprintPoints
}

/** A driver's season so far, round by round, from Jolpica. */
data class DriverSeason(
    val season: Int,
    val driverId: String,
    val code: String?,
    val number: String?,
    val rounds: List<DriverRound>,
) {
    val podiums: Int get() = rounds.count { (it.finish ?: 99) <= 3 }
    val dnfs: Int get() = rounds.count { it.finish == null }
    val bestFinish: Int? get() = rounds.mapNotNull { it.finish }.minOrNull()
    val avgQuali: Double? get() = rounds.mapNotNull { it.quali }.takeIf { it.isNotEmpty() }?.average()
    val poles: Int get() = rounds.count { it.quali == 1 }
    val fastestLaps: Int get() = rounds.count { it.fastestRank == 1 }
    val sprintPoints: Double get() = rounds.sumOf { it.sprintPoints }
    val totalPoints: Double get() = rounds.sumOf { it.allPoints }
    val pointsPerRace: Double get() = if (rounds.isEmpty()) 0.0 else totalPoints / rounds.size
    val placesGained: Int get() = rounds.sumOf { it.gained ?: 0 }

    /** Most race finishes in the points in a row. */
    val pointsStreak: Int
        get() {
            var best = 0; var run = 0
            for (r in rounds) { if (r.points > 0) { run++; best = maxOf(best, run) } else run = 0 }
            return best
        }

    /** Points total after each round, sprints included. */
    fun cumulative(): List<Pair<Int, Double>> {
        var sum = 0.0
        return rounds.map { r -> sum += r.allPoints; r.round to sum }
    }
}

/** Teammate head-to-head over the rounds both took part in. */
data class HeadToHead(val raceA: Int, val raceB: Int, val qualiA: Int, val qualiB: Int)

/** Who finished ahead at one round: 1 = the first driver, -1 = the teammate, 0 = no race for one of them. */
data class RoundDuel(val round: Int, val winner: Int)

/** Career totals across every season Jolpica has. [titles] counts finished seasons only. */
data class DriverCareer(
    val dateOfBirth: java.time.LocalDate?,
    val seasons: List<Int>,
    val firstRace: String?,
    val starts: Int,
    val wins: Int,
    val podiums: Int,
    val poles: Int,
    val titles: List<Int>,
)

object DriverStats {
    data class Entry(val id: String, val given: String, val family: String)

    /** Every driver in a season's Jolpica standings, for finding a driver's id by name. */
    fun entries(standingsJson: String): List<Entry> {
        val arr = JSONObject(standingsJson).optJSONObject("MRData")?.optJSONObject("StandingsTable")
            ?.optJSONArray("StandingsLists")?.optJSONObject(0)?.optJSONArray("DriverStandings") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i)?.optJSONObject("Driver") ?: return@mapNotNull null
            Entry(d.optString("driverId"), d.optString("givenName"), d.optString("familyName")).takeIf { it.id.isNotBlank() }
        }
    }

    fun findId(entries: List<Entry>, given: String, family: String): String? =
        entries.firstOrNull { Favourite(given, family, "", "").matches(it.given, it.family) }?.id
            ?: entries.filter { Names.norm(it.family) == Names.norm(family) }.singleOrNull()?.id

    private fun races(json: String?): JSONArray? =
        if (json.isNullOrBlank()) null
        else try { JSONObject(json).optJSONObject("MRData")?.optJSONObject("RaceTable")?.optJSONArray("Races") } catch (e: Exception) { null }

    /**
     * Races (/drivers/{id}/results) joined with qualifying (/qualifying) and sprints (/sprint).
     */
    fun season(season: Int, driverId: String, resultsJson: String, qualiJson: String?, sprintJson: String? = null): DriverSeason {
        val quali = HashMap<Int, Int>()
        races(qualiJson)?.let { rs ->
            for (i in 0 until rs.length()) {
                val r = rs.getJSONObject(i)
                val q = r.optJSONArray("QualifyingResults")?.optJSONObject(0) ?: continue
                val round = r.optString("round").toIntOrNull() ?: continue
                q.optString("position").toIntOrNull()?.let { quali[round] = it }
            }
        }
        val sprint = HashMap<Int, Pair<Int?, Double>>()
        races(sprintJson)?.let { rs ->
            for (i in 0 until rs.length()) {
                val r = rs.getJSONObject(i)
                val s = r.optJSONArray("SprintResults")?.optJSONObject(0) ?: continue
                val round = r.optString("round").toIntOrNull() ?: continue
                sprint[round] = s.optString("positionText").toIntOrNull() to (s.optString("points").toDoubleOrNull() ?: 0.0)
            }
        }
        var code: String? = null
        var number: String? = null
        val rounds = ArrayList<DriverRound>()
        races(resultsJson)?.let { rs ->
            for (i in 0 until rs.length()) {
                val r = rs.getJSONObject(i)
                val res = r.optJSONArray("Results")?.optJSONObject(0) ?: continue
                val round = r.optString("round").toIntOrNull() ?: continue
                res.optJSONObject("Driver")?.let { d ->
                    code = d.optString("code").takeIf { it.isNotBlank() } ?: code
                    number = d.optString("permanentNumber").takeIf { it.isNotBlank() } ?: number
                }
                val sp = sprint[round]
                rounds += DriverRound(
                    round = round,
                    raceName = r.optString("raceName"),
                    finish = res.optString("positionText").toIntOrNull(),
                    order = res.optString("position").toIntOrNull() ?: 99,
                    status = res.optString("status"),
                    points = res.optString("points").toDoubleOrNull() ?: 0.0,
                    quali = quali[round],
                    grid = res.optString("grid").toIntOrNull(),
                    fastestRank = res.optJSONObject("FastestLap")?.optString("rank")?.toIntOrNull(),
                    sprintFinish = sp?.first,
                    sprintPoints = sp?.second ?: 0.0,
                )
            }
        }
        return DriverSeason(season, driverId, code, number, rounds.sortedBy { it.round })
    }

    fun headToHead(a: DriverSeason, b: DriverSeason): HeadToHead {
        val bBy = b.rounds.associateBy { it.round }
        var ra = 0; var rb = 0; var qa = 0; var qb = 0
        for (x in a.rounds) {
            val y = bBy[x.round] ?: continue
            if (x.order < y.order) ra++ else if (y.order < x.order) rb++
            val xq = x.quali; val yq = y.quali
            if (xq != null && yq != null) { if (xq < yq) qa++ else if (yq < xq) qb++ }
        }
        return HeadToHead(ra, rb, qa, qb)
    }

    /** Round by round, who of the two finished ahead (for the teammate strip). */
    fun duels(a: DriverSeason, b: DriverSeason): List<RoundDuel> {
        val bBy = b.rounds.associateBy { it.round }
        val rounds = (a.rounds.map { it.round } + b.rounds.map { it.round }).distinct().sorted()
        val aBy = a.rounds.associateBy { it.round }
        return rounds.map { r ->
            val x = aBy[r]; val y = bBy[r]
            RoundDuel(r, if (x == null || y == null) 0 else if (x.order < y.order) 1 else -1)
        }
    }

    /**
     * Points behind [rival] after each round both have (negative: ahead of them). Used for the
     * gap-to-the-leader chart.
     */
    fun gap(driver: DriverSeason, rival: DriverSeason): List<Pair<Int, Double>> {
        val mine = driver.cumulative().toMap()
        val theirs = rival.cumulative().toMap()
        val rounds = (mine.keys + theirs.keys).distinct().sorted()
        var m = 0.0; var t = 0.0
        return rounds.map { r ->
            mine[r]?.let { m = it }
            theirs[r]?.let { t = it }
            r to (t - m)
        }
    }

    // ── Career (several small Jolpica answers) ──

    /** MRData.total: how many rows a query has (wins with ?limit=1, and so on). */
    fun total(json: String): Int = JSONObject(json).optJSONObject("MRData")?.optString("total")?.toIntOrNull() ?: 0

    fun dateOfBirth(driverJson: String): java.time.LocalDate? = try {
        val d = JSONObject(driverJson).optJSONObject("MRData")?.optJSONObject("DriverTable")?.optJSONArray("Drivers")?.optJSONObject(0)
        d?.optString("dateOfBirth")?.takeIf { it.isNotBlank() }?.let { java.time.LocalDate.parse(it) }
    } catch (e: Exception) { null }

    fun seasons(seasonsJson: String): List<Int> {
        val arr = JSONObject(seasonsJson).optJSONObject("MRData")?.optJSONObject("SeasonTable")?.optJSONArray("Seasons") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("season")?.toIntOrNull() }.sorted()
    }

    /** "2019 Australian Grand Prix" from the first row of /drivers/{id}/results. */
    fun firstRace(resultsJson: String): String? {
        val r = races(resultsJson)?.optJSONObject(0) ?: return null
        return "${r.optString("season")} ${r.optString("raceName")}".trim().takeIf { it.isNotBlank() }
    }

    /** Seasons finished as champion, from /drivers/{id}/driverStandings/1. */
    fun titleSeasons(standingsJson: String): List<Int> {
        val arr = JSONObject(standingsJson).optJSONObject("MRData")?.optJSONObject("StandingsTable")?.optJSONArray("StandingsLists") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("season")?.toIntOrNull() }.sorted()
    }

    // ── Headshots (OpenF1) ──

    /** Family name (normalised) → official headshot URL, from OpenF1's /drivers. */
    fun headshots(openF1Json: String): Map<String, String> {
        val arr = try { JSONArray(openF1Json) } catch (e: Exception) { return emptyMap() }
        val out = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("headshot_url").trim()
            val last = o.optString("last_name").trim()
            if (url.startsWith("https://") && last.isNotBlank()) out[Names.norm(last)] = url
        }
        return out
    }
}
