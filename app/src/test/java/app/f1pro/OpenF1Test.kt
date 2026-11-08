package app.f1pro

import app.f1pro.data.OpenF1
import app.f1pro.data.Race
import app.f1pro.data.RaceResult
import app.f1pro.data.RaceType
import app.f1pro.data.Session
import app.f1pro.data.Circuit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OpenF1Test {
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    private val race = Race(
        2026, 21, "São Paulo Grand Prix", null, Circuit("interlagos", "Interlagos", "São Paulo", "Brazil"),
        listOf(
            Session("Practice 1", t("2026-11-06T14:30:00Z")),
            Session("Sprint Qualifying", t("2026-11-06T18:30:00Z")),
            Session("Sprint", t("2026-11-07T14:00:00Z")),
            Session("Qualifying", t("2026-11-07T18:00:00Z")),
            Session("Race", t("2026-11-08T17:00:00Z")),
        ),
        RaceType.SPRINT,
    )

    // Shapes as OpenF1 serves them (trimmed).
    private val sessions = """[
        {"meeting_key":1300,"session_key":9900,"session_name":"Practice 1","date_start":"2026-11-06T14:30:00+00:00","is_cancelled":false},
        {"meeting_key":1300,"session_key":9901,"session_name":"Sprint Qualifying","date_start":"2026-11-06T18:30:00+00:00","is_cancelled":false},
        {"meeting_key":1300,"session_key":9902,"session_name":"Sprint","date_start":"2026-11-07T14:00:00+00:00","is_cancelled":false},
        {"meeting_key":1300,"session_key":9903,"session_name":"Qualifying","date_start":"2026-11-07T18:00:00+00:00","is_cancelled":false},
        {"meeting_key":1300,"session_key":9904,"session_name":"Race","date_start":"2026-11-08T17:00:00+00:00","is_cancelled":false},
        {"meeting_key":1299,"session_key":9850,"session_name":"Race","date_start":"2026-10-25T20:00:00+00:00","is_cancelled":false}
    ]"""

    private val drivers = """[
        {"driver_number":1,"first_name":"Lando","last_name":"Norris","name_acronym":"NOR","team_name":"McLaren","session_key":9903},
        {"driver_number":3,"first_name":"Max","last_name":"Verstappen","name_acronym":"VER","team_name":"Red Bull Racing","session_key":9903},
        {"driver_number":44,"first_name":"Lewis","last_name":"Hamilton","name_acronym":"HAM","team_name":"Ferrari","session_key":9903},
        {"driver_number":27,"first_name":"Nico","last_name":"Hulkenberg","name_acronym":"HUL","team_name":"Audi","session_key":9903}
    ]"""

    private val results = """[
        {"session_key":9903,"meeting_key":1300,"driver_number":3,"position":1,"duration":[70.512,70.101,69.802],"gap_to_leader":[0,0,0],"number_of_laps":20,"dnf":false,"dns":false,"dsq":false},
        {"session_key":9903,"meeting_key":1300,"driver_number":1,"position":2,"duration":[70.700,70.250,69.950],"gap_to_leader":[0.188,0.149,0.148],"number_of_laps":19,"dnf":false,"dns":false,"dsq":false},
        {"session_key":9903,"meeting_key":1300,"driver_number":27,"position":16,"duration":[71.400,null,null],"gap_to_leader":[0.888,null,null],"number_of_laps":8,"dnf":false,"dns":false,"dsq":false},
        {"session_key":9903,"meeting_key":1300,"driver_number":44,"position":null,"duration":[null,null,null],"gap_to_leader":[null,null,null],"number_of_laps":1,"dnf":true,"dns":false,"dsq":false},
        {"session_key":9902,"meeting_key":1300,"driver_number":1,"position":1,"duration":1820.456,"gap_to_leader":0,"number_of_laps":24,"dnf":false,"dns":false,"dsq":false},
        {"session_key":9902,"meeting_key":1300,"driver_number":3,"position":2,"duration":1822.763,"gap_to_leader":2.307,"number_of_laps":24,"dnf":false,"dns":false,"dsq":false},
        {"session_key":9902,"meeting_key":1300,"driver_number":27,"position":3,"duration":null,"gap_to_leader":"+1 LAP","number_of_laps":23,"dnf":false,"dns":false,"dsq":false},
        {"session_key":9902,"meeting_key":1300,"driver_number":44,"position":null,"duration":null,"gap_to_leader":null,"number_of_laps":4,"dnf":true,"dns":false,"dsq":false}
    ]"""

    private val grid = """[
        {"session_key":9901,"meeting_key":1300,"driver_number":3,"position":1,"lap_duration":70.0},
        {"session_key":9901,"meeting_key":1300,"driver_number":1,"position":4,"lap_duration":70.4},
        {"session_key":9903,"meeting_key":1300,"driver_number":3,"position":1,"lap_duration":69.802}
    ]"""

    @Test
    fun matchesSessionsToTheWeekend() {
        val k = OpenF1.keys(sessions, race)
        assertNotNull(k)
        assertEquals(1300, k!!.meeting)
        assertEquals(9903, k.sessions["Qualifying"])
        assertEquals(9902, k.sessions["Sprint"])
        assertEquals(9904, k.sessions["Race"])
        assertEquals(k, OpenF1.Keys.fromJson(k.toJson()))
    }

    @Test
    fun qualifyingTimesBeforeTheRace() {
        val q = OpenF1.qualifying(results, drivers, 9903)
        assertEquals(listOf("VER", "NOR", "HUL", "HAM"), q.map { it.code })
        assertEquals("1:09.802", q[0].q3)
        assertEquals("1:10.512", q[0].q1)
        assertEquals("Q1", q[2].part)
        assertNull(q[2].q2)
        assertNull(q[3].best)
        assertEquals("red_bull", q[0].constructorId)
    }

    @Test
    fun sprintClassification() {
        val r = OpenF1.race(results, drivers, grid, 9902, setOf(9902, 9901), sprint = true)
        assertEquals(listOf("NOR", "VER", "HUL", "HAM"), r.map { it.code })
        assertEquals("30:20.456", r[0].time)
        assertEquals("+2.307", r[1].time)
        assertEquals("+1 Lap", r[2].gap)
        assertEquals("Retired", r[3].gap)
        assertEquals("R", r[3].positionText)
        assertEquals(8.0, r[0].points, 0.0)
        assertEquals(6.0, r[2].points, 0.0)
        assertEquals(0.0, r[3].points, 0.0)
        assertEquals(3, r[0].gained) // from 4th on the sprint grid
    }

    @Test
    fun weekendPrefersOpenF1AndFallsBackToJolpica() {
        val keys = OpenF1.keys(sessions, race)
        val jolpicaRace = listOf(
            RaceResult(1, "1", "3", "VER", "Max", "Verstappen", "Dutch", "red_bull", "Red Bull", 1, 71, "Finished", "1:32:00.000", 25.0, 1, "1:12.000"),
        )
        val w = OpenF1.weekend(2026, 21, true, jolpicaRace, emptyList(), emptyList(), OpenF1.Raw(keys, results, drivers, grid), 0L)
        assertEquals(4, w.quali.size)    // OpenF1 has qualifying; Jolpica doesn't yet
        assertEquals(4, w.sprint.size)
        assertEquals(1, w.race.size)     // no OpenF1 race result: Jolpica's
        val none = OpenF1.weekend(2026, 21, true, emptyList(), emptyList(), emptyList(), null, 0L)
        assertTrue(none.isEmpty)
    }

    @Test
    fun jolpicaFillsInWhatOpenF1Lacks() {
        val o = OpenF1.race(results, drivers, grid, 9902, setOf(9902, 9901), sprint = true)
        val j = listOf(RaceResult(null, "R", "44", "HAM", "Lewis", "Hamilton", "British", "ferrari", "Ferrari", 9, 4, "Gearbox", null, 0.0, 3, "1:13.100"))
        val e = OpenF1.enrich(o, j)
        val ham = e.first { it.code == "HAM" }
        assertEquals("Gearbox", ham.status)
        assertEquals("British", ham.nationality)
        assertEquals("1:13.100", ham.fastestLap)
        assertEquals(9, ham.grid)
    }

    @Test
    fun formatting() {
        assertEquals("59.900", OpenF1.lap(59.9))
        assertEquals("1:23.456", OpenF1.lap(83.456))
        assertEquals("1:47:14.808", OpenF1.raceTime(6434.808))
    }

    @Test
    fun ignoresOtherWeekends() {
        val other = race.copy(sessions = listOf(Session("Race", t("2026-12-06T13:00:00Z"))))
        assertNull(OpenF1.keys(sessions, other))
    }
}
