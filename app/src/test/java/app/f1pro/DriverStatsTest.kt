package app.f1pro

import app.f1pro.data.DriverStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverStatsTest {
    private fun results(id: String, rows: List<Triple<Int, String, String>>) = """
        {"MRData":{"RaceTable":{"Races":[${rows.joinToString(",") { (round, pos, text) ->
            """{"round":"$round","raceName":"Race $round","Results":[{"position":"$pos","positionText":"$text","points":"${if (text == "1") 25 else 0}",
               "status":"${if (text.toIntOrNull() != null) "Finished" else "Retired"}",
               "Driver":{"driverId":"$id","permanentNumber":"4","code":"NOR","givenName":"Lando","familyName":"Norris"}}]}"""
        }}]}}}
    """
    private fun quali(rows: List<Pair<Int, Int>>) = """
        {"MRData":{"RaceTable":{"Races":[${rows.joinToString(",") { (round, pos) ->
            """{"round":"$round","QualifyingResults":[{"position":"$pos"}]}"""
        }}]}}}
    """

    @Test
    fun parsesRoundsWithQualifyingAndRetirements() {
        val s = DriverStats.season(
            2025, "norris",
            results("norris", listOf(Triple(1, "1", "1"), Triple(2, "18", "R"), Triple(3, "3", "3"))),
            quali(listOf(1 to 1, 3 to 4)),
        )
        assertEquals(3, s.rounds.size)
        assertEquals("NOR", s.code)
        assertEquals("4", s.number)
        assertEquals(2, s.podiums)
        assertEquals(1, s.dnfs)
        assertNull(s.rounds[1].finish)
        assertEquals(18, s.rounds[1].order)
        assertEquals(1, s.rounds[0].quali)
        assertNull(s.rounds[1].quali)
        assertEquals(25.0, s.rounds[0].points, 0.0)
    }

    @Test
    fun headToHeadCountsSharedRounds() {
        val a = DriverStats.season(2025, "a", results("a", listOf(Triple(1, "1", "1"), Triple(2, "5", "5"), Triple(3, "2", "2"))), quali(listOf(1 to 2, 2 to 1, 3 to 3)))
        val b = DriverStats.season(2025, "b", results("b", listOf(Triple(1, "2", "2"), Triple(2, "3", "3"))), quali(listOf(1 to 1, 2 to 4)))
        val h = DriverStats.headToHead(a, b)
        assertEquals(1, h.raceA); assertEquals(1, h.raceB)
        assertEquals(1, h.qualiA); assertEquals(1, h.qualiB)
    }

    @Test
    fun findsDriverIdByNameWithAccents() {
        val json = """{"MRData":{"StandingsTable":{"StandingsLists":[{"DriverStandings":[
            {"Driver":{"driverId":"hulkenberg","givenName":"Nico","familyName":"Hülkenberg"}},
            {"Driver":{"driverId":"antonelli","givenName":"Andrea Kimi","familyName":"Antonelli"}}]}]}}}"""
        val e = DriverStats.entries(json)
        assertEquals("hulkenberg", DriverStats.findId(e, "Nico", "Hulkenberg"))
        assertEquals("antonelli", DriverStats.findId(e, "Kimi", "Antonelli").let { it ?: DriverStats.findId(e, "Andrea Kimi", "Antonelli") })
    }
}
