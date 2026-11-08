package app.f1pro.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

/**
 * Session results from OpenF1, the primary results source (2023 on). OpenF1 publishes a
 * classification a few minutes after F1 does, and free access opens 30 minutes after a session
 * ends; Jolpica often takes hours. Jolpica stays as the fallback, for older seasons, and to fill
 * in what OpenF1 doesn't carry (fastest lap, nationality, the exact retirement reason).
 *
 * One weekend costs three requests: session_result, drivers and starting_grid, all by meeting.
 */
object OpenF1 {
    const val BASE = "https://api.openf1.org/v1"
    const val FIRST_SEASON = 2023

    /** The OpenF1 session keys of one weekend, by our session name. */
    data class Keys(val meeting: Int, val sessions: Map<String, Int>) {
        fun toJson(): JSONObject = JSONObject().put("meeting", meeting).put("sessions", JSONObject(sessions as Map<*, *>))

        companion object {
            fun fromJson(o: JSONObject?): Keys? {
                if (o == null || !o.has("meeting")) return null
                val s = o.optJSONObject("sessions") ?: return null
                return Keys(o.getInt("meeting"), s.keys().asSequence().associateWith { s.getInt(it) })
            }
        }
    }

    /** OpenF1's session names → ours. "Sprint Shootout" was 2023's sprint qualifying. */
    private fun ourName(name: String): String? = when (name.trim().lowercase()) {
        "race" -> "Race"
        "qualifying" -> "Qualifying"
        "sprint" -> "Sprint"
        "sprint qualifying", "sprint shootout" -> "Sprint Qualifying"
        else -> null
    }

    /**
     * Matches [race]'s sessions to a /sessions?year= listing: same session, starting within six
     * hours of our time for it (calendars disagree by minutes, not days).
     */
    fun keys(sessionsJson: String, race: Race): Keys? {
        val arr = try { JSONArray(sessionsJson) } catch (e: Exception) { return null }
        val wanted = race.sessions.filter { !it.tbc }.associateBy { it.name }
        val found = HashMap<String, Int>()
        var meeting: Int? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("is_cancelled", false)) continue
            val name = ourName(o.optString("session_name")) ?: continue
            val ours = wanted[name] ?: continue
            val start = time(o.optString("date_start")) ?: continue
            if (kotlin.math.abs(start - ours.start) > 6 * HOUR_MS) continue
            found[name] = o.optInt("session_key")
            meeting = o.optInt("meeting_key")
        }
        return meeting?.let { Keys(it, found) }?.takeIf { it.sessions.isNotEmpty() }
    }

    private fun time(iso: String): Long? = try { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() } catch (e: Exception) {
        try { Instant.parse(iso).toEpochMilli() } catch (e2: Exception) { null }
    }

    private class Driver(val number: String, val given: String, val family: String, val code: String, val teamName: String)

    /** /drivers?meeting_key=: one row per driver per session; the latest session's row wins. */
    private fun drivers(json: String?): Map<String, Driver> {
        val arr = try { JSONArray(json ?: return emptyMap()) } catch (e: Exception) { return emptyMap() }
        val out = HashMap<String, Pair<Int, Driver>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optInt("driver_number", -1).takeIf { it >= 0 }?.toString() ?: continue
            val key = o.optInt("session_key")
            val last = str(o, "last_name") ?: str(o, "full_name")?.substringAfterLast(' ')?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
            val d = Driver(n, str(o, "first_name").orEmpty(), last, str(o, "name_acronym") ?: last.take(3).uppercase(), str(o, "team_name").orEmpty())
            if ((out[n]?.first ?: Int.MIN_VALUE) <= key) out[n] = key to d
        }
        return out.mapValues { it.value.second }
    }

    private fun str(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }

    /** Rows of one session from /session_result?meeting_key=. */
    private fun rows(json: String?, sessionKey: Int?): List<JSONObject> {
        if (sessionKey == null) return emptyList()
        val arr = try { JSONArray(json ?: return emptyList()) } catch (e: Exception) { return emptyList() }
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optInt("session_key") == sessionKey }
    }

    /** 83.456 → "1:23.456"; 59.9 → "59.900". */
    fun lap(sec: Double): String {
        val ms = Math.round(sec * 1000)
        val m = ms / 60_000
        val s = (ms % 60_000) / 1000.0
        return if (m > 0) String.format(Locale.US, "%d:%06.3f", m, s) else String.format(Locale.US, "%.3f", s)
    }

    /** 6434.808 → "1:47:14.808" (a race winner's time). */
    fun raceTime(sec: Double): String {
        val ms = Math.round(sec * 1000)
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000.0
        return if (h > 0) String.format(Locale.US, "%d:%02d:%06.3f", h, m, s) else String.format(Locale.US, "%d:%06.3f", m, s)
    }

    private val RACE_POINTS = listOf(25, 18, 15, 12, 10, 8, 6, 4, 2, 1)
    private val SPRINT_POINTS = listOf(8, 7, 6, 5, 4, 3, 2, 1)

    /**
     * Race or sprint classification. [gridKeys] are the sessions whose starting grid applies
     * (OpenF1 files it under the race or under the qualifying that set it, so both are accepted).
     */
    fun race(resultsJson: String?, driversJson: String?, gridJson: String?, sessionKey: Int?, gridKeys: Set<Int>, sprint: Boolean): List<RaceResult> {
        val rows = rows(resultsJson, sessionKey)
        if (rows.isEmpty()) return emptyList()
        val drivers = drivers(driversJson)
        val grid = HashMap<String, Int>()
        try {
            val g = JSONArray(gridJson ?: "[]")
            for (i in 0 until g.length()) {
                val o = g.optJSONObject(i) ?: continue
                if (o.optInt("session_key") !in gridKeys) continue
                val p = o.optInt("position", 0).takeIf { it > 0 } ?: continue
                grid[o.optInt("driver_number").toString()] = p
            }
        } catch (e: Exception) { }
        val table = if (sprint) SPRINT_POINTS else RACE_POINTS
        return rows.map { o ->
            val n = o.optInt("driver_number").toString()
            val d = drivers[n]
            val dnf = o.optBoolean("dnf"); val dns = o.optBoolean("dns"); val dsq = o.optBoolean("dsq")
            val pos = if (o.isNull("position")) null else o.optInt("position", 0).takeIf { it > 0 }
            val gapRaw = if (o.isNull("gap_to_leader")) null else o.opt("gap_to_leader")
            val gap = num(gapRaw)
            val lapsBehind = (gapRaw as? String)?.let { Regex("\\+?(\\d+)\\s*LAP", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toInt() }
            val status = when {
                dsq -> "Disqualified"
                dns -> "Did not start"
                dnf -> "Retired"
                lapsBehind != null -> "+$lapsBehind Lap" + if (lapsBehind > 1) "s" else ""
                else -> "Finished"
            }
            val time = when {
                dnf || dns || dsq || lapsBehind != null -> null
                gap == 0.0 || pos == 1 -> num(o.opt("duration"))?.let { raceTime(it) }
                gap != null -> "+" + lap(gap)
                else -> null
            }
            val points = num(o.opt("points"))
                ?: if (pos != null && !dsq && !dns && !dnf) table.getOrNull(pos - 1)?.toDouble() ?: 0.0 else 0.0
            RaceResult(
                position = pos,
                positionText = pos?.toString() ?: when { dsq -> "D"; dns -> "W"; else -> "R" },
                number = n,
                code = d?.code ?: "",
                givenName = d?.given ?: "",
                familyName = d?.family ?: "#$n",
                nationality = "",
                constructorId = d?.teamName?.let { Teams.idFor(it) } ?: "",
                constructorName = d?.teamName ?: "",
                grid = grid[n],
                laps = o.optInt("number_of_laps", 0),
                status = status,
                time = time,
                points = points,
                fastestRank = null,
                fastestLap = null,
            )
        }.sortedBy { it.position ?: Int.MAX_VALUE }
    }

    /** Qualifying: duration and gap_to_leader are [Q1, Q2, Q3], null for parts not reached. */
    fun qualifying(resultsJson: String?, driversJson: String?, sessionKey: Int?): List<QualiResult> {
        val rows = rows(resultsJson, sessionKey)
        if (rows.isEmpty()) return emptyList()
        val drivers = drivers(driversJson)
        return rows.map { o ->
            val n = o.optInt("driver_number").toString()
            val d = drivers[n]
            val parts: List<Double?> = when (val v = if (o.isNull("duration")) null else o.opt("duration")) {
                is JSONArray -> (0 until 3).map { if (it < v.length() && !v.isNull(it)) num(v.opt(it)) else null }
                else -> listOf(num(v), null, null)
            }
            QualiResult(
                position = if (o.isNull("position")) null else o.optInt("position", 0).takeIf { it > 0 },
                number = n,
                code = d?.code ?: "",
                givenName = d?.given ?: "",
                familyName = d?.family ?: "#$n",
                constructorId = d?.teamName?.let { Teams.idFor(it) } ?: "",
                constructorName = d?.teamName ?: "",
                q1 = parts[0]?.let { lap(it) },
                q2 = parts[1]?.let { lap(it) },
                q3 = parts[2]?.let { lap(it) },
            )
        }.sortedBy { it.position ?: Int.MAX_VALUE }
    }

    /** Each OpenF1 row, with the details only Jolpica has filled in from its matching row. */
    fun enrich(primary: List<RaceResult>, jolpica: List<RaceResult>): List<RaceResult> {
        if (jolpica.isEmpty()) return primary
        return primary.map { p ->
            val j = jolpica.firstOrNull { it.number == p.number } ?: return@map p
            p.copy(
                code = j.code.ifBlank { p.code },
                givenName = j.givenName.ifBlank { p.givenName },
                familyName = j.familyName.ifBlank { p.familyName },
                nationality = j.nationality,
                constructorId = j.constructorId.ifBlank { p.constructorId },
                constructorName = j.constructorName.ifBlank { p.constructorName },
                grid = p.grid ?: j.grid,
                // "Gearbox" says more than "Retired".
                status = if (p.status == "Retired" && j.status.isNotBlank()) j.status else p.status,
                points = j.points,
                fastestRank = j.fastestRank,
                fastestLap = j.fastestLap,
            )
        }
    }

    fun enrichQuali(primary: List<QualiResult>, jolpica: List<QualiResult>): List<QualiResult> {
        if (jolpica.isEmpty()) return primary
        return primary.map { p ->
            val j = jolpica.firstOrNull { it.number == p.number } ?: return@map p
            p.copy(
                code = j.code.ifBlank { p.code },
                givenName = j.givenName.ifBlank { p.givenName },
                familyName = j.familyName.ifBlank { p.familyName },
                constructorId = j.constructorId.ifBlank { p.constructorId },
                constructorName = j.constructorName.ifBlank { p.constructorName },
            )
        }
    }

    /** The raw OpenF1 feeds for one weekend, as saved in the results cache. */
    data class Raw(val keys: Keys?, val results: String?, val drivers: String?, val grid: String?)

    /**
     * The weekend's results: OpenF1 for each session it has classified, Jolpica for the rest,
     * with Jolpica's extra details folded into OpenF1's rows when both have them.
     */
    fun weekend(
        season: Int, round: Int, isSprint: Boolean,
        jRace: List<RaceResult>, jSprint: List<RaceResult>, jQuali: List<QualiResult>,
        raw: Raw?, fetchedAt: Long,
    ): WeekendResults {
        val k = raw?.keys?.sessions.orEmpty()
        fun keySet(vararg names: String) = names.mapNotNull { k[it] }.toSet()
        val oRace = race(raw?.results, raw?.drivers, raw?.grid, k["Race"], keySet("Race", "Qualifying"), sprint = false)
        val oSprint = if (isSprint) race(raw?.results, raw?.drivers, raw?.grid, k["Sprint"], keySet("Sprint", "Sprint Qualifying"), sprint = true) else emptyList()
        val oQuali = qualifying(raw?.results, raw?.drivers, k["Qualifying"])
        return WeekendResults(
            season, round,
            race = if (oRace.isNotEmpty()) enrich(oRace, jRace) else jRace,
            sprint = if (oSprint.isNotEmpty()) enrich(oSprint, jSprint) else jSprint,
            quali = if (oQuali.isNotEmpty()) enrichQuali(oQuali, jQuali) else jQuali,
            fetchedAt = fetchedAt,
        )
    }
}
