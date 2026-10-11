package app.f1pro.data

import org.json.JSONObject

/** One row of the official standings after a given round (Jolpica driverStandings). */
data class StandingAfter(val position: Int, val points: Double, val givenName: String, val familyName: String)

/** What to tell someone about their favourite driver once a session's results are out. */
object Report {
    /** The sessions that produce results worth reporting. */
    val SESSIONS = setOf("Race", "Sprint", "Qualifying")

    fun standingsAfter(json: String): Pair<Int, List<StandingAfter>>? {
        val list = JSONObject(json).optJSONObject("MRData")?.optJSONObject("StandingsTable")
            ?.optJSONArray("StandingsLists")?.optJSONObject(0) ?: return null
        val round = list.optString("round").toIntOrNull() ?: return null
        val arr = list.optJSONArray("DriverStandings") ?: return null
        val rows = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val d = o.optJSONObject("Driver") ?: return@mapNotNull null
            StandingAfter(
                o.optString("position").toIntOrNull() ?: (i + 1),
                o.optString("points").toDoubleOrNull() ?: 0.0,
                d.optString("givenName"),
                d.optString("familyName"),
            )
        }.sortedBy { it.position }
        return round to rows
    }

    private fun shortRace(name: String) = name.replace("Grand Prix", "GP")

    private fun ordinalPts(p: Double) = if (p == 1.0) "+1 point" else "+${pointsText(p)} points"

    private fun pointsText(p: Double) = if (p % 1.0 == 0.0) p.toLong().toString() else p.toString()

    /**
     * Title and text for [session] of [race], or null when [fav] isn't in the results yet
     * (results not out, or they didn't take part). [after] = standings after this round, if out.
     */
    fun message(
        fav: Favourite,
        race: Race,
        session: String,
        results: WeekendResults,
        after: List<StandingAfter>?,
    ): Pair<String, String>? {
        val who = fav.familyName
        val gp = shortRace(race.name)
        return when (session) {
            "Qualifying" -> {
                val q = results.quali.firstOrNull { fav.matches(it.givenName, it.familyName) } ?: return null
                val pos = q.position ?: return null
                val title = if (pos == 1) "$who takes pole for the $gp" else "$who qualified P$pos for the $gp"
                val best = q.best
                val text = when {
                    best == null -> "No time set"
                    q.part == "Q3" -> "$best in Q3"
                    else -> "Out in ${q.part} · $best"
                }
                title to text
            }
            "Sprint", "Race" -> {
                val list = if (session == "Sprint") results.sprint else results.race
                val r = list.firstOrNull { fav.matches(it.givenName, it.familyName) } ?: return null
                val pos = r.position
                val where = if (session == "Sprint") "the $gp sprint" else "the $gp"
                val title = when {
                    r.out -> "$who didn't finish $where"
                    pos == 1 -> "$who wins $where"
                    else -> "$who finished P${pos ?: r.positionText} in $where"
                }
                val parts = ArrayList<String>()
                if (r.out) parts += "${r.status} on lap ${r.laps}" else if (r.points > 0) parts += ordinalPts(r.points) else parts += "No points"
                r.gained?.let { if (it > 0) parts += "up $it from P${r.grid}" else if (it < 0) parts += "down ${-it} from P${r.grid}" }
                if (r.fastestRank == 1) parts += "fastest lap"
                if (session == "Race" && after != null) {
                    val me = after.firstOrNull { fav.matches(it.givenName, it.familyName) }
                    if (me != null) {
                        val leader = after.first()
                        parts += if (me.position == 1) {
                            val second = after.getOrNull(1)
                            if (second != null) "leads by ${pointsText(me.points - second.points)}" else "leads the championship"
                        } else {
                            "P${me.position} in the championship, ${pointsText(leader.points - me.points)} behind"
                        }
                    }
                }
                title to parts.joinToString(" · ")
            }
            else -> null
        }
    }
}
