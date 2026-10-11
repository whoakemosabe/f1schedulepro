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
