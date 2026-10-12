package app.f1pro

import app.f1pro.data.Circuit
import app.f1pro.data.DAY_MS
import app.f1pro.data.DriverRound
import app.f1pro.data.DriverSeason
import app.f1pro.data.DriverStanding
import app.f1pro.data.DriverStats
import app.f1pro.data.Planner
import app.f1pro.data.Race
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
