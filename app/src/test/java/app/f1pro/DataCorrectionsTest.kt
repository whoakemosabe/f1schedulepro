package app.f1pro

import app.f1pro.data.Circuit
import app.f1pro.data.DAY_MS
import app.f1pro.data.DriverRound
import app.f1pro.data.DriverSeason
import app.f1pro.data.DriverStanding
import app.f1pro.data.DriverStats
import app.f1pro.data.GridKind
import app.f1pro.data.Grids
import app.f1pro.data.Planner
import app.f1pro.data.QualiResult
import app.f1pro.data.Race
import app.f1pro.data.RaceResult
import app.f1pro.data.RaceType
import app.f1pro.data.SeasonCalendar
import app.f1pro.data.Session
import app.f1pro.data.Teams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCorrectionsTest {
    @Test
    fun chartLinesEndOnTheOfficialTotal() {
        val line = listOf(1 to 25.0, 2 to 43.0, 3 to 58.0)
        val s = DriverStats.snapTo(line, 48.0)
        assertEquals(48.0, s.points.last().second, 0.0)
        assertEquals(3, s.points.last().first)
        assertEquals(-10.0, s.adjustment, 0.0)
        assertEquals("ALO: official total includes a −10 pt penalty after R3", DriverStats.adjustmentNote("ALO", s.adjustment, 3))
        // Agreeing totals: untouched, no note.
        val same = DriverStats.snapTo(line, 58.0)
        assertEquals(line, same.points)
        assertNull(DriverStats.adjustmentNote("ALO", same.adjustment, 3))
        assertEquals("X: official total includes a +2.5 pt correction", DriverStats.adjustmentNote("X", 2.5, null))
    }

    private fun result(code: String, family: String, pos: Int?, grid: Int?) = RaceResult(
        pos, pos?.toString() ?: "R", "1", code, "A", family, "", "mclaren", "McLaren", grid, 50, "Finished", null, 0.0, null, null,
    )

    private fun quali(code: String, family: String, pos: Int) =
        QualiResult(pos, "1", code, "A", family, "mclaren", "McLaren", "1:30", null, null)

    @Test
    fun officialGridBeatsQualifyingAndPitLaneGoesLast() {
        val q = listOf(quali("NOR", "Norris", 1), quali("PIA", "Piastri", 2), quali("VER", "Verstappen", 3))
        val before = Grids.of(emptyList(), emptyList(), q)
        assertEquals(GridKind.Provisional, before.single().kind)
        assertEquals(listOf("NOR", "PIA", "VER"), before.single().cars.map { it.code })
        // Norris took a 5-place drop; Verstappen started from the pit lane (grid 0).
        val race = listOf(result("PIA", "Piastri", 1, 1), result("NOR", "Norris", 2, 6), result("VER", "Verstappen", 3, 0))
        val sprint = listOf(result("VER", "Verstappen", 1, 2), result("NOR", "Norris", 2, 1))
        val after = Grids.of(race, sprint, q)
        assertEquals(GridKind.Official, after[0].kind)
        assertEquals(listOf("P1" to "PIA", "P6" to "NOR", "PL" to "VER"), after[0].cars.map { it.slot to it.code })
        assertEquals(GridKind.Sprint, after[1].kind)
        assertEquals(listOf("NOR", "VER"), after[1].cars.map { it.code })
        // Results without a grid column fall back to qualifying.
        assertEquals(GridKind.Provisional, Grids.of(listOf(result("PIA", "Piastri", 1, null)), emptyList(), q).single().kind)
    }

    @Test
    fun teamIdsFromBothFeedsMeetInOneId() {
        assertEquals("rb", Teams.canonical("racing_bulls"))
        assertEquals("rb", Teams.canonical("RB"))
        assertEquals("red_bull", Teams.canonical("red-bull"))
        assertEquals("sauber", Teams.canonical("kick_sauber"))
        assertEquals("audi", Teams.canonical("audi"))
        assertEquals("cadillac", Teams.canonical("cadillac"))
    }

    private fun standing(name: String, surname: String, code: String?, id: String?) =
        DriverStanding(1, 10.0, 0, name, surname, "", "12", "mercedes", "Mercedes", code, id)

    @Test
    fun missingCodesComeFromTheResultsFeed() {
        val stats = DriverStats.SeasonStats(
            2026,
            listOf(DriverStats.Entry("antonelli", "Andrea Kimi", "Antonelli")),
            mapOf("antonelli" to DriverSeason(2026, "antonelli", "ANT", "12", listOf(DriverRound(1, "GP", 1, 1, "Finished", 25.0, 1)))),
        )
        val fixed = DriverStats.withCodes(listOf(standing("Andrea", "Kimi Antonelli", null, "antonelli")), stats)
        assertEquals("ANT", fixed.single().code)
        // Without a code anywhere the surname is used, and the clash check catches twins.
        val twins = listOf(standing("Max", "Verstappen", null, null), standing("Jos", "Verstappen", null, null))
        assertEquals(mapOf("VER" to listOf("Verstappen", "Verstappen")), DriverStats.duplicateCodes(twins))
    }

    private fun race(season: Int, round: Int, raceStart: Long) = Race(
        season, round, "GP $round", null, Circuit("c$round", "C", "L", "Italy"),
        listOf(Session("Qualifying", raceStart - DAY_MS), Session("Race", raceStart)), RaceType.CONVENTIONAL,
    )

    @Test
    fun betweenSeasonsShowsFinalAndCountsDown() {
        val t0 = 1_800_000_000_000L
        val old = SeasonCalendar(2026, listOf(race(2026, 1, t0), race(2026, 2, t0 + 7 * DAY_MS)), false)
        val new = SeasonCalendar(2027, listOf(race(2027, 1, t0 + 100 * DAY_MS)), false)
        // Mid-season: nothing.
        assertNull(Planner.offSeason(listOf(old, new), 2026, t0 + DAY_MS))
        // After the last race: final, with the countdown.
        val now = t0 + 10 * DAY_MS
        val off = Planner.offSeason(listOf(old, new), 2026, now)
        assertNotNull(off)
        assertEquals(2027, off!!.nextYear)
        assertEquals(90, off.days(now))
        // No calendar for next year yet: still final, no countdown.
        val alone = Planner.offSeason(listOf(old), 2026, now)!!
        assertNull(alone.days(now))
        assertTrue(alone.finished == 2026)
    }
}
