package app.f1pro

import app.f1pro.data.Net
import app.f1pro.data.Parsers
import app.f1pro.data.Planner
import app.f1pro.data.Repo
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/**
 * Hits the real feeds and writes a short report for the release notes. Only runs when
 * LIVE_CHECK=1 (CI sets it in a separate, non-blocking step).
 */
class LiveCheck {
    @Test
    fun feeds() {
        assumeTrue(System.getenv("LIVE_CHECK") == "1")
        val lines = ArrayList<String>()
        fun line(ok: Boolean, text: String) { lines += (if (ok) "✅ " else "⚠️ ") + text }
        // Data the app shows as fact: a miss here fails loudly (CI turns ❌ into an error).
        fun must(ok: Boolean, text: String) { lines += (if (ok) "✅ " else "❌ ") + text }

        val now = System.currentTimeMillis()
        val year = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).year
        for (y in listOf(year, year + 1)) {
            try {
                val s = Parsers.calendar(Net.get("${Repo.JOLPICA}/$y.json?limit=100"), null, true)
                if (s == null) line(y != year, "Calendar $y: not published yet")
                else {
                    val next = Planner.next(listOf(s), now)
                    line(true, "Calendar $y: ${s.races.size} rounds" + (next?.let { ", next ${it.session.name} at ${it.race.name}" } ?: ""))
                }
            } catch (e: Exception) { line(false, "Calendar $y: ${e.message}") }
        }
        try {
            val (season, drivers) = Parsers.drivers(Net.get("${Repo.F1API}/drivers-championship"))
            line(drivers.isNotEmpty(), "Standings $season: ${drivers.size} drivers, leader ${drivers.firstOrNull()?.let { it.name + " " + it.surname } ?: "—"}")
        } catch (e: Exception) { line(false, "Driver standings: ${e.message}") }
        try {
            val teams = Parsers.teams(Net.get("${Repo.F1API}/constructors-championship"))
            line(teams.isNotEmpty(), "Constructors: ${teams.size} teams")
        } catch (e: Exception) { line(false, "Constructors: ${e.message}") }
        try {
            val raw = (1..3).map { Net.get("${Repo.JOLPICA}/$year/results/$it.json?limit=40") }
            val p = app.f1pro.data.ResultParsers.podiums(raw)
            val last = p.keys.maxOrNull()
            line(p.isNotEmpty(), "Podiums $year: ${p.size} races" + (last?.let { r -> ", R$r: " + p[r]!!.joinToString(" ") { "${it.position}.${it.code}" } } ?: ""))
            if (last != null) {
                val r = app.f1pro.data.ResultParsers.race(Net.get("${Repo.JOLPICA}/$year/$last/results.json?limit=40"))
                val q = app.f1pro.data.ResultParsers.qualifying(Net.get("${Repo.JOLPICA}/$year/$last/qualifying.json?limit=40"))
                line(r.isNotEmpty() && q.isNotEmpty(), "Results R$last: ${r.size} classified, ${q.size} in qualifying")
                val after = app.f1pro.data.Report.standingsAfter(Net.get("${Repo.JOLPICA}/$year/$last/driverStandings.json"))
                line(after != null, "Standings after R$last: ${after?.second?.firstOrNull()?.familyName ?: "—"} leads")
            }
        } catch (e: Exception) { line(false, "Results: ${e.message}") }
        try {
            // The panels' whole-season download: every page of results, qualifying and sprints.
            fun pages(url: String): List<String> {
                val first = Net.get("$url?limit=100")
                return listOf(first) + app.f1pro.data.DriverStats.pageOffsets(app.f1pro.data.DriverStats.total(first)).map { Net.get("$url?limit=100&offset=$it") }
            }
            val r = pages("${Repo.JOLPICA}/$year/results.json")
            val q = pages("${Repo.JOLPICA}/$year/qualifying.json")
            val sp = pages("${Repo.JOLPICA}/$year/sprint.json")
            val all = app.f1pro.data.DriverStats.seasonAll(year, r, q, sp)
            val rounds = all.drivers.values.maxOfOrNull { it.rounds.size } ?: 0
            val short = all.drivers.values.filter { it.rounds.size < rounds }.map { it.driverId }
            line(all.drivers.size >= 20, "Season data $year: ${r.size + q.size + sp.size} requests, ${all.drivers.size} drivers, $rounds rounds" +
                (if (short.isNotEmpty()) " (fewer for ${short.joinToString(", ")})" else ""))
            // Every driver in the standings must find their season data and a photo.
            val (_, standing) = Parsers.drivers(Net.get("${Repo.F1API}/drivers-championship"))
            val heads = app.f1pro.data.DriverStats.headshots(Net.get("https://api.openf1.org/v1/drivers?session_key=latest"))
            val noData = standing.filter { d -> app.f1pro.data.DriverStats.idFor(all.entries, d)?.let { all.drivers[it] } == null }.map { it.code }
            val noPhoto = standing.filter { app.f1pro.data.DriverStats.headshotFor(heads, it) == null }.map { it.code }
            must(noData.isEmpty() && noPhoto.isEmpty(), "Every driver matched: ${standing.size} in standings" +
                (if (noData.isNotEmpty()) "; no season data for ${noData.joinToString(", ")}" else "") +
                (if (noPhoto.isNotEmpty()) "; no photo for ${noPhoto.joinToString(", ")}" else ""))
            // Codes: the standings' own, else the race results'; no two drivers may share one.
            val coded = app.f1pro.data.DriverStats.withCodes(standing, all)
            val fromResults = coded.count { it.shortCode != null } - standing.count { it.shortCode != null }
            val dupes = app.f1pro.data.DriverStats.duplicateCodes(coded)
            must(dupes.isEmpty(), "Driver codes: ${coded.size} unique" + (if (fromResults > 0) ", $fromResults from race results" else "") +
                (if (dupes.isNotEmpty()) "; shared: " + dupes.entries.joinToString("; ") { "${it.key} = ${it.value.joinToString("/")}" } else ""))
            // Photos: every sharpened link must actually load (the CDN path is unofficial).
            val broken = ArrayList<String>()
            var loaded = 0
            for (d in standing) {
                val u = app.f1pro.data.DriverStats.headshotFor(heads, d) ?: continue
                val sharp = app.f1pro.data.DriverStats.sharperHeadshot(u)
                val ok = try { Net.bytes(sharp).size > 2_000 } catch (e: Exception) { false }
                if (ok) loaded++ else broken += d.code
            }
            must(broken.isEmpty() && loaded == standing.size, "Sharp photos: $loaded of ${standing.size} load" +
                (if (broken.isNotEmpty()) "; broken for ${broken.joinToString(", ")}" else ""))
            // Teams: both feeds must name every team the same way (after the translation table).
            val f1apiTeams = Parsers.teams(Net.get("${Repo.F1API}/constructors-championship")).map { it.teamId }.toSet()
            val jolpicaTeams = Regex("\"constructorId\"\\s*:\\s*\"([^\"]+)\"")
                .findAll(Net.get("${Repo.JOLPICA}/$year/constructorStandings.json")).map { app.f1pro.data.Teams.canonical(it.groupValues[1]) }.toSet()
            val onlyF1api = f1apiTeams - jolpicaTeams
            val onlyJolpica = jolpicaTeams - f1apiTeams
            val unknown = (f1apiTeams + jolpicaTeams) - app.f1pro.data.Teams.known
            must(jolpicaTeams.isEmpty() || (onlyF1api.isEmpty() && onlyJolpica.isEmpty()),
                "Team ids: ${f1apiTeams.size} in both feeds" +
                    (if (onlyF1api.isNotEmpty()) "; standings only: ${onlyF1api.joinToString(", ")}" else "") +
                    (if (onlyJolpica.isNotEmpty()) "; results only: ${onlyJolpica.joinToString(", ")}" else ""))
            if (unknown.isNotEmpty()) line(false, "Teams without a built-in colour: ${unknown.joinToString(", ")}")
        } catch (e: Exception) { line(false, "Season data: ${e.message}") }
        try {
            // The driver panel: the standings leader's season, round by round, and vs teammate.
            val entries = app.f1pro.data.DriverStats.entries(Net.get("${Repo.JOLPICA}/$year/driverStandings.json"))
            val lead = entries.firstOrNull()
            if (lead == null) line(true, "Driver panel: no $year standings yet")
            else {
                val season = app.f1pro.data.DriverStats.season(
                    year, lead.id,
                    Net.get("${Repo.JOLPICA}/$year/drivers/${lead.id}/results.json?limit=100"),
                    Net.get("${Repo.JOLPICA}/$year/drivers/${lead.id}/qualifying.json?limit=100"),
                )
                line(season.rounds.isNotEmpty(), "Driver panel: ${lead.family} ${season.rounds.size} races, ${season.podiums} podiums, " +
                    "${season.rounds.count { it.quali != null }} quali, #${season.number ?: "?"}")
                val sprint = app.f1pro.data.DriverStats.season(
                    year, lead.id,
                    Net.get("${Repo.JOLPICA}/$year/drivers/${lead.id}/results.json?limit=100"), null,
                    Net.get("${Repo.JOLPICA}/$year/drivers/${lead.id}/sprint.json?limit=100"),
                )
                line(true, "Driver highlights: ${sprint.fastestLaps} fastest laps, ${sprint.sprintPoints.toInt()} sprint pts, " +
                    "${sprint.placesGained} places gained, streak ${sprint.pointsStreak}")
                val base = "${Repo.JOLPICA}/drivers/${lead.id}"
                val wins = app.f1pro.data.DriverStats.total(Net.get("$base/results/1.json?limit=1"))
                val starts = Net.get("$base/results.json?limit=1")
                line(wins > 0, "Driver career: ${app.f1pro.data.DriverStats.total(starts)} starts, $wins wins, first ${app.f1pro.data.DriverStats.firstRace(starts)}")
                val poleRows = Net.get("$base/qualifying/1.json?limit=100")
                val poles = app.f1pro.data.DriverStats.career(emptyList(), listOf(poleRows), year + 1, null, null).poles
                val champ = app.f1pro.data.DriverStats.champion(Net.get("${Repo.JOLPICA}/${year - 1}/driverStandings/1.json"))
                line(champ != null, "Career extras: $poles poles; ${year - 1} champion $champ")
                val track = app.f1pro.data.DriverStats.trackRecord(Net.get("${Repo.JOLPICA}/circuits/americas/drivers/max_verstappen/results.json?limit=100"))
                line(track.starts >= 5, "Track record (Verstappen at COTA): ${track.starts} starts, ${track.wins} wins, best P${track.best}")
                val heads = app.f1pro.data.DriverStats.headshots(Net.get("https://api.openf1.org/v1/drivers?session_key=latest"))
                line(heads.size >= 15, "Headshots (OpenF1): ${heads.size} drivers")
                heads.values.firstOrNull()?.let { u ->
                    line(true, "Headshot link: $u → ${app.f1pro.data.DriverStats.sharperHeadshot(u)}")
                }
            }
        } catch (e: Exception) { line(false, "Driver panel: ${e.message}") }
        try {
            val f = app.f1pro.data.ResultParsers.forecast(
                Net.get("https://api.open-meteo.com/v1/forecast?latitude=30.1328&longitude=-97.6411&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=16"),
                now,
            )
            line(f.hours.size > 300 && f.days.size >= 14, "Weather (Austin): ${f.hours.size} hours, ${f.days.size} days")
        } catch (e: Exception) { line(false, "Weather: ${e.message}") }
        try {
            val c = app.f1pro.data.ResultParsers.teamColours(Net.get("https://api.openf1.org/v1/drivers?session_key=latest"))
            line(c.size >= 8, "Team colours (OpenF1): " + c.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=#%06X".format(it.value and 0xFFFFFF) })
        } catch (e: Exception) { line(false, "Team colours: ${e.message}") }
        for (src in Repo.NEWS) {
            try {
                val items = Parsers.rss(Net.get(src.url, "application/rss+xml, application/xml, text/xml"), src.name)
                line(items.isNotEmpty(), "${src.name}: ${items.size} articles")
            } catch (e: Exception) { line(false, "${src.name}: ${e.message}") }
        }
        val report = "**Live feed check**\n\n" + lines.joinToString("\n") { "- $it" } + "\n"
        File("build").mkdirs()
        File("build/live-check.md").writeText(report)
        println(report)
    }
}
