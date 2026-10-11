package app.f1pro

import app.f1pro.data.Favourite
import app.f1pro.data.Parsers
import app.f1pro.data.Planner
import app.f1pro.data.Report
import app.f1pro.data.ResultParsers
import app.f1pro.data.Sky
import app.f1pro.data.StandingAfter
import app.f1pro.data.WeekendResults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class ResultsTest {
    private fun driver(id: String, code: String, given: String, family: String) =
        """{"driverId":"$id","permanentNumber":"1","code":"$code","givenName":"$given","familyName":"$family","nationality":"X"}"""

    private fun result(pos: String, text: String, d: String, team: String, grid: String, laps: String, status: String, time: String?, pts: String, fl: String? = null) =
        """{"number":"1","position":"$pos","positionText":"$text","points":"$pts","Driver":$d,"Constructor":{"constructorId":"$team","name":"${team.uppercase()}"},
            "grid":"$grid","laps":"$laps","status":"$status"${if (time != null) ""","Time":{"time":"$time"}""" else ""}${if (fl != null) ""","FastestLap":{"rank":"$fl","lap":"55","Time":{"time":"1:38.220"}}""" else ""}}"""

    // Shapes as Jolpica serves them (Sepang 2026, trimmed).
    private val raceJson = """{"MRData":{"RaceTable":{"season":"2026","round":"16","Races":[{"season":"2026","round":"16","raceName":"Bahrain Grand Prix in Malaysia","Results":[
        ${result("1", "1", driver("max_verstappen", "VER", "Max", "Verstappen"), "red_bull", "1", "55", "Finished", "1:47:14.808", "25", "1")},
        ${result("2", "2", driver("antonelli", "ANT", "Andrea Kimi", "Antonelli"), "mercedes", "3", "55", "Finished", "+2.307", "18")},
        ${result("3", "3", driver("hamilton", "HAM", "Lewis", "Hamilton"), "ferrari", "2", "55", "Finished", "+4.919", "15")},
        ${result("10", "10", driver("lindblad", "LIN", "Arvid", "Lindblad"), "rb", "22", "55", "Finished", "+15.928", "1")},
        ${result("19", "19", driver("perez", "PER", "Sergio", "Pérez"), "cadillac", "20", "54", "+1 Lap", null, "0")},
        ${result("20", "R", driver("russell", "RUS", "George", "Russell"), "mercedes", "7", "49", "Retired", null, "0")}
    ]}]}}}"""

    @Test
    fun raceResultsParse() {
        val r = ResultParsers.race(raceJson)
        assertEquals(6, r.size)
        val win = r[0]
        assertEquals("Max Verstappen", win.name)
        assertEquals("1:47:14.808", win.gap)
        assertEquals(1, win.fastestRank)
        assertEquals(25.0, win.points, 0.0)
        assertEquals(0, win.gained)
        assertEquals(1, r[1].gained) // P3 → P2
        assertEquals(12, r.single { it.code == "LIN" }.gained)
        val lapped = r.single { it.code == "PER" }
        assertEquals("+1 Lap", lapped.gap)
        assertFalse(lapped.out)
        val dnf = r.single { it.code == "RUS" }
        assertTrue(dnf.out)
        assertEquals("Retired", dnf.gap)
        assertEquals("R", dnf.positionText)
        assertTrue(ResultParsers.race("""{"MRData":{"RaceTable":{"Races":[]}}}""").isEmpty())
    }

    @Test
    fun qualifyingParsesMissingAndEmptyTimes() {
        val json = """{"MRData":{"RaceTable":{"Races":[{"round":"16","QualifyingResults":[
            {"number":"3","position":"1","Driver":${driver("max_verstappen", "VER", "Max", "Verstappen")},"Constructor":{"constructorId":"red_bull","name":"Red Bull"},"Q1":"1:36.477","Q2":"1:35.696","Q3":"1:35.130"},
            {"number":"43","position":"15","Driver":${driver("colapinto", "COL", "Franco", "Colapinto")},"Constructor":{"constructorId":"alpine","name":"Alpine F1 Team"},"Q1":"1:36.9","Q2":""},
            {"number":"27","position":"17","Driver":${driver("hulkenberg", "HUL", "Nico", "Hülkenberg")},"Constructor":{"constructorId":"audi","name":"Audi"},"Q1":"1:37.1"}
        ]}]}}}"""
        val q = ResultParsers.qualifying(json)
        assertEquals(3, q.size)
        assertEquals("1:35.130", q[0].best)
        assertEquals("Q3", q[0].part)
        assertEquals("Q1", q[1].part) // empty Q2 string counts as no time
        assertEquals("1:36.9", q[1].best)
        assertEquals("Q1", q[2].part)
    }

    @Test
    fun podiumsFromPositionFeeds() {
        fun feed(pos: Int, rounds: List<Pair<Int, String>>) = """{"MRData":{"RaceTable":{"Races":[${
            rounds.joinToString(",") { (r, fam) -> """{"round":"$r","Results":[${result("$pos", "$pos", driver(fam, fam.take(3).uppercase(), "A", fam), "mercedes", "1", "55", "Finished", "+1", "0")}]}""" }
        }]}}}"""
        val p = ResultParsers.podiums(listOf(
            feed(1, listOf(1 to "Russell", 16 to "Verstappen")),
            feed(2, listOf(1 to "Antonelli", 16 to "Antonelli")),
            feed(3, listOf(16 to "Hamilton")),
            "not json",
        ))
        assertEquals(listOf("VER", "ANT", "HAM"), p[16]!!.map { it.code })
        assertEquals(listOf(1, 2), p[1]!!.map { it.position })
    }

    @Test
    fun forecastUsesTheTracksOffset() {
        // Austin is UTC-5 in October: 12:00 local = 17:00Z.
        val json = """{"utc_offset_seconds":-18000,
            "hourly":{"time":["2026-10-23T12:00","2026-10-23T13:00"],"temperature_2m":[27.4,28.1],"precipitation_probability":[0,null],"weather_code":[0,61],"wind_speed_10m":[9.0,11.2]},
            "daily":{"time":["2026-10-23","2026-10-24"],"temperature_2m_max":[29.0,27.0],"temperature_2m_min":[18.0,17.0],"precipitation_probability_max":[0,10],"weather_code":[0,2]}}"""
        val f = ResultParsers.forecast(json, 1L)
        assertEquals(2, f.hours.size)
        assertEquals(Instant.parse("2026-10-23T17:00:00Z").toEpochMilli(), f.hours[0].time)
        assertEquals(Sky.CLEAR, f.hours[0].sky)
        assertNull(f.hours[1].rainChance)
        assertEquals(Sky.RAIN, f.hours[1].sky)
        // FP1 at 17:30Z is nearest to the 12:00 local hour.
        val fp1 = Instant.parse("2026-10-23T17:30:00Z").toEpochMilli()
        assertEquals(27.4, f.at(fp1)!!.tempC, 0.0)
        assertNull(f.at(Instant.parse("2026-10-25T20:00:00Z").toEpochMilli()))
        // A 21:00Z session is still Friday at the track.
        assertEquals(LocalDate.parse("2026-10-23"), f.localDate(Instant.parse("2026-10-24T01:00:00Z").toEpochMilli()))
        assertEquals(Sky.PARTLY, f.day(LocalDate.parse("2026-10-24"))!!.sky)
    }

    @Test
    fun favouriteMatchesAcrossFeeds() {
        val ant = Favourite("Andrea Kimi", "Antonelli", "mercedes", "Mercedes Formula 1 Team")
        assertTrue(ant.matches("Andrea Kimi", "Antonelli"))
        assertFalse(ant.matches("George", "Russell"))
        val hul = Favourite("Nico", "Hülkenberg", "audi", "Audi")
        assertTrue(hul.matches("Nico", "Hulkenberg"))
        val per = Favourite("Sergio", "Perez", "cadillac", "Cadillac")
        assertTrue(per.matches("Sergio", "Pérez"))
    }

    private val season = Parsers.calendar(
        File("src/main/assets/calendar-2026.json").readText(),
        Parsers.extras(File("src/main/assets/extras-2026.json").readText()),
        live = false,
    )!!

    @Test
    fun reportMessages() {
        val sepang = season.races.single { it.circuit.id == "sepang" }
        val res = WeekendResults(2026, 16, ResultParsers.race(raceJson), emptyList(), emptyList(), 0)
        val ant = Favourite("Andrea Kimi", "Antonelli", "mercedes", "Mercedes")
        val after = listOf(StandingAfter(1, 320.0, "Andrea Kimi", "Antonelli"), StandingAfter(2, 236.0, "George", "Russell"))
        val (title, text) = Report.message(ant, sepang, "Race", res, after)!!
        assertEquals("Antonelli finished P2 in the Bahrain GP in Malaysia", title)
        assertEquals("+18 points · up 1 from P3 · leads by 84", text)

        val ver = Report.message(Favourite("Max", "Verstappen", "red_bull", "Red Bull"), sepang, "Race", res, null)!!
        assertEquals("Verstappen wins the Bahrain GP in Malaysia", ver.first)
        assertTrue(ver.second.contains("fastest lap"))

        val rus = Report.message(Favourite("George", "Russell", "mercedes", "Mercedes"), sepang, "Race", res, after)!!
        assertEquals("Russell didn't finish the Bahrain GP in Malaysia", rus.first)
        assertTrue(rus.second.startsWith("Retired on lap 49"))
        assertTrue(rus.second.endsWith("P2 in the championship, 84 behind"))

        // Not in the results (yet): nothing to say.
        assertNull(Report.message(Favourite("Oliver", "Bearman", "haas", "Haas"), sepang, "Race", res, null))
        assertNull(Report.message(ant, sepang, "Qualifying", res, null))
    }

    @Test
    fun standingsAfterRoundParse() {
        val json = """{"MRData":{"StandingsTable":{"season":"2026","round":"16","StandingsLists":[{"season":"2026","round":"16","DriverStandings":[
            {"position":"1","points":"320","wins":"8","Driver":{"givenName":"Andrea Kimi","familyName":"Antonelli"}},
            {"position":"2","points":"236","wins":"3","Driver":{"givenName":"George","familyName":"Russell"}}]}]}}}"""
        val (round, rows) = Report.standingsAfter(json)!!
        assertEquals(16, round)
        assertEquals(84.0, rows[0].points - rows[1].points, 0.0)
    }

    @Test
    fun finishedSessionsToReport() {
        val sg = season.races.single { it.circuit.id == "marina_bay" }
        val race = sg.raceSession!!
        val q = sg.sessions.single { it.name == "Qualifying" }
        val justAfterRace = race.end + 60_000
        val due = Planner.finishedForReport(listOf(season), justAfterRace)
        assertTrue(due.any { it.race == sg && it.session.name == "Race" })
        // Qualifying was the day before, more than 12 hours earlier: not reported again.
        assertEquals(q.end < justAfterRace - 12 * 3_600_000L, due.none { it.race == sg && it.session.name == "Qualifying" })
        assertTrue(due.none { it.session.name.startsWith("Practice") })
        assertTrue(Planner.finishedForReport(listOf(season), race.start).none { it.session == race })
        assertNotNull(due)
    }
}
