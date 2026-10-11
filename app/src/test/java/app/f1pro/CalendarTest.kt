package app.f1pro

import app.f1pro.data.Parsers
import app.f1pro.data.Planner
import app.f1pro.data.RaceStatus
import app.f1pro.data.RaceType
import app.f1pro.data.SeasonCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class CalendarTest {
    private fun asset(name: String) = File("src/main/assets/$name").readText()

    private fun season(): SeasonCalendar {
        val extras = Parsers.extras(asset("extras-2026.json"))
        // A fixed copy: CI refreshes the bundled calendar from the live feed before building.
        return Parsers.calendar(File("src/test/resources/calendar-2026-fixture.json").readText(), extras, live = false)!!
    }

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun bundledSeasonMatchesTheRealCalendar() {
        val s = season()
        assertEquals(2026, s.year)
        val gps = s.races.filter { !it.isTesting }
        assertEquals(23, gps.size)
        assertEquals((1..23).toList(), gps.map { it.round })
        assertEquals("albert_park", gps.first().circuit.id)
        assertEquals("yas_marina", gps.last().circuit.id)
        // Bahrain moved to Sepang; Saudi Arabia is off the calendar.
        val sepang = gps.single { it.circuit.id == "sepang" }
        assertEquals(16, sepang.round)
        assertEquals(ms("2026-10-04T07:00:00Z"), sepang.raceSession!!.start)
        assertTrue(gps.none { it.circuit.id == "jeddah" })
        assertTrue(gps.none { it.circuit.id == "bahrain" })
        // Testing comes from the extras and sorts first.
        assertEquals(RaceType.TESTING, s.races.first().type)
    }

    @Test
    fun sessionsAreInOrderAndSprintsAreMarked() {
        val s = season()
        for (r in s.races) {
            assertEquals(r.sessions.sortedBy { it.start }, r.sessions)
            if (!r.isTesting) assertEquals("Race", r.sessions.last().name)
        }
        val china = s.races.single { it.circuit.id == "shanghai" }
        assertEquals(RaceType.SPRINT, china.type)
        assertEquals(listOf("Practice 1", "Sprint Qualifying", "Sprint", "Qualifying", "Race"), china.sessions.map { it.name })
        val japan = s.races.single { it.circuit.id == "suzuka" }
        assertEquals(RaceType.CONVENTIONAL, japan.type)
        assertEquals(5, japan.sessions.size)
    }

    @Test
    fun extrasFillInOfficialNamesAndResultLinks() {
        val s = season()
        val aus = s.races.single { it.circuit.id == "albert_park" }
        assertNotNull(aus.officialName)
        assertEquals(
            "https://www.formula1.com/en/results/2026/races/1279/australia/race-result",
            aus.resultsUrl(aus.raceSession!!),
        )
        assertEquals("my", s.races.single { it.circuit.id == "sepang" }.circuit.countryCode)
        assertEquals("gb", s.races.single { it.circuit.id == "silverstone" }.circuit.countryCode)
        assertEquals("us", s.races.single { it.circuit.id == "vegas" }.circuit.countryCode)
        assertEquals("ae", s.races.single { it.circuit.id == "yas_marina" }.circuit.countryCode)
    }

    @Test
    fun everyCircuitHasStats() {
        val circuits = Parsers.circuits(asset("circuits.json"))
        for (r in season().races.filter { !it.isTesting }) {
            assertTrue("missing stats for ${r.circuit.id}", circuits.containsKey(r.circuit.id))
        }
    }

    @Test
    fun statusFollowsThePwaRules() {
        val r = season().races.single { it.circuit.id == "marina_bay" }
        val first = r.first
        val race = r.raceSession!!.start
        assertEquals(RaceStatus.UPCOMING, r.status(first - 1))
        assertEquals(RaceStatus.ACTIVE, r.status(first + 1))
        assertEquals(RaceStatus.ACTIVE, r.status(race + 2 * 3_600_000L))
        assertEquals(RaceStatus.COMPLETED, r.status(race + 3 * 3_600_000L + 1))
        assertTrue(r.raceSession!!.isLive(race + 60_000))
        assertFalse(r.raceSession!!.isLive(race + 151 * 60_000L))
    }

    @Test
    fun liveFeedShapeParses() {
        // Ergast/Jolpica shape, including a session without a time yet and a sprint shootout key.
        val json = """
            {"MRData":{"RaceTable":{"season":"2027","Races":[
              {"season":"2027","round":"1","raceName":"Australian Grand Prix",
               "Circuit":{"circuitId":"albert_park","circuitName":"Albert Park","Location":{"locality":"Melbourne","country":"Australia"}},
               "date":"2027-03-14","time":"04:00:00Z",
               "FirstPractice":{"date":"2027-03-12","time":"01:30:00Z"},
               "SprintShootout":{"date":"2027-03-12","time":"05:30:00Z"},
               "Sprint":{"date":"2027-03-13","time":"02:00:00Z"},
               "Qualifying":{"date":"2027-03-13"}}
            ]}}}
        """.trimIndent()
        val s = Parsers.calendar(json, null, live = true)!!
        assertEquals(2027, s.year)
        val r = s.races.single()
        assertEquals(RaceType.SPRINT, r.type)
        val q = r.sessions.single { it.name == "Qualifying" }
        assertTrue(q.tbc)
        assertTrue(r.sessions.any { it.name == "Sprint Qualifying" })
        assertNull(r.officialName)
        assertNull(Parsers.calendar("""{"MRData":{"RaceTable":{"season":"2028","Races":[]}}}""", null, true))
    }

    @Test
    fun currentSeasonRollsOverWhenTheYearIsDone() {
        val s26 = season()
        val json27 = """
            {"MRData":{"RaceTable":{"season":"2027","Races":[
              {"season":"2027","round":"1","raceName":"Australian Grand Prix",
               "Circuit":{"circuitId":"albert_park","circuitName":"Albert Park","Location":{"locality":"Melbourne","country":"Australia"}},
               "date":"2027-03-14","time":"04:00:00Z"}
            ]}}}
        """.trimIndent()
        val s27 = Parsers.calendar(json27, null, live = true)!!
        val both = listOf(s26, s27)
        assertEquals(2026, Planner.currentSeason(both, ms("2026-10-11T12:00:00Z"))!!.year)
        assertEquals(2027, Planner.currentSeason(both, ms("2026-12-20T12:00:00Z"))!!.year)
        assertEquals(2026, Planner.currentSeason(listOf(s26), ms("2027-01-20T12:00:00Z"))!!.year)
        // Next session after Abu Dhabi is next year's opener.
        val next = Planner.next(both, ms("2026-12-07T00:00:00Z"))!!
        assertEquals(2027, next.race.season)
    }
}
