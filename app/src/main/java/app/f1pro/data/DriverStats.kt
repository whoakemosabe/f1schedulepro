package app.f1pro.data

import org.json.JSONObject

/** One round for one driver: where they qualified and finished. [finish] is null for DNF/DSQ. */
data class DriverRound(
    val round: Int,
    val raceName: String,
    val finish: Int?,
    val order: Int,
    val status: String,
    val points: Double,
    val quali: Int?,
)

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
}

/** Teammate head-to-head over the rounds both took part in. */
data class HeadToHead(val raceA: Int, val raceB: Int, val qualiA: Int, val qualiB: Int)

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

    /** Races (from /drivers/{id}/results) joined with qualifying (from /drivers/{id}/qualifying). */
    fun season(season: Int, driverId: String, resultsJson: String, qualiJson: String?): DriverSeason {
        val quali = HashMap<Int, Int>()
        if (!qualiJson.isNullOrBlank()) {
            val races = JSONObject(qualiJson).optJSONObject("MRData")?.optJSONObject("RaceTable")?.optJSONArray("Races")
            if (races != null) for (i in 0 until races.length()) {
                val r = races.getJSONObject(i)
                val q = r.optJSONArray("QualifyingResults")?.optJSONObject(0) ?: continue
                val round = r.optString("round").toIntOrNull() ?: continue
                q.optString("position").toIntOrNull()?.let { quali[round] = it }
            }
        }
        var code: String? = null
        var number: String? = null
        val rounds = ArrayList<DriverRound>()
        val races = JSONObject(resultsJson).optJSONObject("MRData")?.optJSONObject("RaceTable")?.optJSONArray("Races")
        if (races != null) for (i in 0 until races.length()) {
            val r = races.getJSONObject(i)
            val res = r.optJSONArray("Results")?.optJSONObject(0) ?: continue
            val round = r.optString("round").toIntOrNull() ?: continue
            res.optJSONObject("Driver")?.let { d ->
                code = d.optString("code").takeIf { it.isNotBlank() } ?: code
                number = d.optString("permanentNumber").takeIf { it.isNotBlank() } ?: number
            }
            val text = res.optString("positionText")
            rounds += DriverRound(
                round = round,
                raceName = r.optString("raceName"),
                finish = text.toIntOrNull(),
                order = res.optString("position").toIntOrNull() ?: 99,
                status = res.optString("status"),
                points = res.optString("points").toDoubleOrNull() ?: 0.0,
                quali = quali[round],
            )
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
}
