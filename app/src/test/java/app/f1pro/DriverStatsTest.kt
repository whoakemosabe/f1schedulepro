package app.f1pro

import app.f1pro.data.DriverStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverStatsTest {
    private fun results(id: String, rows: List<Triple<Int, String, String>>) = """
        {"MRData":{"RaceTable":{"Races":[${rows.joinToString(",") { (round, pos, text) ->
            """{"round":"$round","raceName":"Race $round","Results":[{"position":"$pos","positionText":"$text","points":"${if (text == "1") 25 else if (text == "3") 15 else 0}","grid":"${round + 2}",
               "FastestLap":{"rank":"${if (round == 3) 1 else 5}"},
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

    @Test
    fun highlightsSprintsGapAndDuels() {
        val sprint = """{"MRData":{"RaceTable":{"Races":[{"round":"3","SprintResults":[{"positionText":"2","points":"7"}]}]}}}"""
        val a = DriverStats.season(2025, "a", results("a", listOf(Triple(1, "1", "1"), Triple(2, "18", "R"), Triple(3, "3", "3"))), quali(listOf(1 to 1, 3 to 4)), sprint)
        assertEquals(1, a.poles)
        assertEquals(1, a.fastestLaps)
        assertEquals(7.0, a.sprintPoints, 0.0)
        assertEquals(47.0, a.totalPoints, 0.0)
        // grid = round + 2: R1 3→1 (+2), R3 5→3 (+2), R2 retired (not counted)
        assertEquals(4, a.placesGained)
        assertEquals(1, a.pointsStreak)
        assertEquals(listOf(1 to 25.0, 2 to 25.0, 3 to 47.0), a.cumulative())

        val b = DriverStats.season(2025, "b", results("b", listOf(Triple(1, "2", "2"), Triple(2, "4", "4"), Triple(3, "1", "1"))), quali(emptyList()))
        // b: 0, 0, 25 → gap from a's view: b - a after each round
        assertEquals(listOf(1 to -25.0, 2 to -25.0, 3 to -22.0), DriverStats.gap(a, b))
        assertEquals(listOf(1, -1, -1), DriverStats.duels(a, b).map { it.winner })
    }

    @Test
    fun careerAnswers() {
        assertEquals(42, DriverStats.total("""{"MRData":{"total":"42"}}"""))
        assertEquals(java.time.LocalDate.of(1999, 11, 13), DriverStats.dateOfBirth("""{"MRData":{"DriverTable":{"Drivers":[{"dateOfBirth":"1999-11-13"}]}}}"""))
        assertEquals(listOf(2019, 2020), DriverStats.seasons("""{"MRData":{"SeasonTable":{"Seasons":[{"season":"2020"},{"season":"2019"}]}}}"""))
        assertEquals("2019 Australian Grand Prix", DriverStats.firstRace("""{"MRData":{"RaceTable":{"Races":[{"season":"2019","raceName":"Australian Grand Prix"}]}}}"""))
        assertEquals(listOf(2025), DriverStats.titleSeasons("""{"MRData":{"StandingsTable":{"StandingsLists":[{"season":"2025"}]}}}"""))
        val h = DriverStats.headshots("""[{"last_name":"Hülkenberg","headshot_url":"https://media.formula1.com/x.png"},{"last_name":"Norris","headshot_url":null}]""")
        assertEquals("https://media.formula1.com/x.png", h["hulkenberg"])
        assertEquals(null, h["norris"])
    }

    @Test
    fun wholeSeasonPagesMergeByRoundAndDriver() {
        fun row(id: String, given: String, family: String, pos: Int, pts: Int) =
            """{"position":"$pos","positionText":"$pos","points":"$pts","grid":"$pos","status":"Finished",
               "Driver":{"driverId":"$id","permanentNumber":"${pos + 10}","code":"${id.take(3).uppercase()}","givenName":"$given","familyName":"$family"}}"""
        // Round 2 is split across the two pages, as Jolpica does at a page boundary.
        val page1 = """{"MRData":{"total":"6","RaceTable":{"Races":[
            {"round":"1","raceName":"Race 1","Results":[${row("norris", "Lando", "Norris", 1, 25)},${row("piastri", "Oscar", "Piastri", 2, 18)}]},
            {"round":"2","raceName":"Race 2","Results":[${row("piastri", "Oscar", "Piastri", 1, 25)}]}]}}}"""
        val page2 = """{"MRData":{"total":"6","RaceTable":{"Races":[
            {"round":"2","raceName":"Race 2","Results":[${row("norris", "Lando", "Norris", 2, 18)}]},
            {"round":"3","raceName":"Race 3","Results":[${row("norris", "Lando", "Norris", 1, 25)},${row("hulkenberg", "Nico", "Hülkenberg", 3, 15)}]}]}}}"""
        val quali = """{"MRData":{"RaceTable":{"Races":[{"round":"1","QualifyingResults":[
            {"position":"1","Driver":{"driverId":"piastri"}},{"position":"2","Driver":{"driverId":"norris"}}]}]}}}"""
        val sprint = """{"MRData":{"RaceTable":{"Races":[{"round":"3","SprintResults":[
            {"positionText":"1","points":"8","Driver":{"driverId":"norris"}}]}]}}}"""
        val s = DriverStats.seasonAll(2025, listOf(page1, page2), listOf(quali), listOf(sprint), 123L)
        assertEquals(3, s.entries.size)
        assertEquals(123L, s.fetchedAt)
        val nor = s.drivers.getValue("norris")
        assertEquals(listOf(1, 2, 3), nor.rounds.map { it.round })
        assertEquals(76.0, nor.totalPoints, 0.0)
        assertEquals(2, nor.rounds.first().quali)
        assertEquals(8.0, nor.rounds.last().sprintPoints, 0.0)
        assertEquals("11", nor.number)
        assertEquals(listOf(1, 2), s.drivers.getValue("piastri").rounds.map { it.round })
        assertEquals("hulkenberg", DriverStats.findId(s.entries, "Nico", "Hulkenberg"))
        assertEquals(listOf(100, 200, 300), DriverStats.pageOffsets(350))
        assertEquals(emptyList<Int>(), DriverStats.pageOffsets(100))
    }

    @Test
    fun championTrackRecordAndPointsLeft() {
        assertEquals("max_verstappen", DriverStats.champion("""{"MRData":{"StandingsTable":{"StandingsLists":[{"season":"2021","DriverStandings":[{"Driver":{"driverId":"max_verstappen"}}]}]}}}"""))
        assertEquals(null, DriverStats.champion("""{"MRData":{"StandingsTable":{"StandingsLists":[]}}}"""))
        val t = DriverStats.trackRecord("""{"MRData":{"RaceTable":{"Races":[
            {"season":"2016","Results":[{"positionText":"R","grid":"4"}]},
            {"season":"2021","Results":[{"positionText":"1","grid":"1"}]},
            {"season":"2024","Results":[{"positionText":"3","grid":"2"}]}]}}}""")
        assertEquals(3, t.starts); assertEquals(1, t.wins); assertEquals(2, t.podiums); assertEquals(1, t.best)
        assertEquals(2024, t.lastBefore(2026)?.season)
        assertEquals(2021, t.lastBefore(2024)?.season)
        assertEquals(25 * 6 + 8 * 2, DriverStats.pointsLeft(6, 2))
    }

    @Test
    fun sprintWithoutARaceStillShowsTheRound() {
        val results = """{"MRData":{"total":"1","RaceTable":{"Races":[{"round":"1","raceName":"Race 1","Results":[
            {"position":"1","positionText":"1","points":"25","Driver":{"driverId":"a","givenName":"A","familyName":"Aa"}}]}]}}}"""
        val sprint = """{"MRData":{"RaceTable":{"Races":[{"round":"2","raceName":"Race 2","SprintResults":[
            {"positionText":"3","points":"6","Driver":{"driverId":"a"}}]}]}}}"""
        val s = DriverStats.seasonAll(2025, listOf(results), emptyList(), listOf(sprint))
        val a = s.drivers.getValue("a")
        assertEquals(listOf(1, 2), a.rounds.map { it.round })
        assertEquals("Race 2", a.rounds[1].raceName)
        assertNull(a.rounds[1].finish)
        assertEquals(6.0, a.rounds[1].sprintPoints, 0.0)
        assertEquals(31.0, a.totalPoints, 0.0)
    }

    @Test
    fun careerFromHistoryPlusThisSeason() {
        fun race(season: Int, round: Int, pos: String) =
            """{"season":"$season","round":"$round","raceName":"GP $season-$round","Results":[{"positionText":"$pos","Driver":{"dateOfBirth":"2006-08-25"}}]}"""
        val history = """{"MRData":{"total":"5","RaceTable":{"Races":[${race(2025, 1, "1")},${race(2025, 2, "3")},${race(2025, 3, "R")},${race(2026, 1, "2")},${race(2026, 2, "1")}]}}}"""
        // qualifying/1 also returns non-pole rows: only position 1 counts.
        val poles = """{"MRData":{"RaceTable":{"Races":[
            {"season":"2025","QualifyingResults":[{"position":"1"}]},
            {"season":"2025","QualifyingResults":[{"position":"4"}]},
            {"season":"2026","QualifyingResults":[{"position":"1"}]}]}}}"""
        val now = DriverStats.seasonAll(2026, listOf("""{"MRData":{"RaceTable":{"Races":[
            {"round":"1","raceName":"R1","Results":[{"position":"2","positionText":"2","points":"18","Driver":{"driverId":"a","givenName":"A","familyName":"Aa"}}]},
            {"round":"2","raceName":"R2","Results":[{"position":"1","positionText":"1","points":"25","Driver":{"driverId":"a","givenName":"A","familyName":"Aa"}}]}]}}}"""),
            listOf("""{"MRData":{"RaceTable":{"Races":[{"round":"2","QualifyingResults":[{"position":"1","Driver":{"driverId":"a"}}]}]}}}"""), emptyList()).drivers.getValue("a")
        val c = DriverStats.career(listOf(history), listOf(poles), 2026, now, listOf(2025))
        assertEquals(5, c.starts)
        assertEquals(2, c.wins)
        assertEquals(4, c.podiums)
        assertEquals(2, c.poles)
        assertEquals(listOf(2025, 2026), c.seasons)
        assertEquals("2025 GP 2025-1", c.firstRace)
        assertEquals(java.time.LocalDate.of(2006, 8, 25), c.dateOfBirth)
        assertEquals(listOf(2025), c.titles)
    }

    @Test
    fun sharperHeadshotsAndCodes() {
        assertEquals(
            "https://media.formula1.com/d.png/content/dam/fom-website/drivers/L/LANNOR01_Lando_Norris/lannor01.png.transform/4col/image.png",
            DriverStats.sharperHeadshot("https://media.formula1.com/d.png/content/dam/fom-website/drivers/L/LANNOR01_Lando_Norris/lannor01.png.transform/1col/image.png"),
        )
        assertEquals(
            "https://media.formula1.com/image/upload/c_fill,w_720/q_auto/v1/x.webp",
            DriverStats.sharperHeadshot("https://media.formula1.com/image/upload/c_fill,w_93/q_auto/v1/x.webp"),
        )
        assertEquals("https://example.com/a.png", DriverStats.sharperHeadshot("https://example.com/a.png"))
        fun d(surname: String, code: String? = null) = app.f1pro.data.DriverStanding(1, 0.0, 0, "X", surname, "", null, "t", "T", code)
        assertEquals("HUL", d("Hülkenberg").code)
        assertEquals("NOR", d("Norris", "nor").code)
        assertEquals("VER", d("Verstappen", "VERS").code)
    }

    @Test
    fun namesSplitDifferentlyStillMatch() {
        // f1api.dev: "Andrea" + "Kimi Antonelli"; Jolpica: "Andrea Kimi" + "Antonelli".
        val e = listOf(DriverStats.Entry("antonelli", "Andrea Kimi", "Antonelli"), DriverStats.Entry("russell", "George", "Russell"))
        assertEquals("antonelli", DriverStats.findId(e, "Andrea", "Kimi Antonelli"))
        val d = app.f1pro.data.DriverStanding(1, 0.0, 0, "Andrea", "Kimi Antonelli", "", "12", "mercedes", "Mercedes", "ANT", "antonelli")
        assertEquals("antonelli", DriverStats.idFor(e, d))
        assertEquals("ANT", d.code)
        val heads = DriverStats.headshots("""[{"name_acronym":"ANT","last_name":"Antonelli","headshot_url":"https://x/ant.png"}]""")
        assertEquals("https://x/ant.png", DriverStats.headshotFor(heads, d))
        assertEquals("https://x/ant.png", DriverStats.headshotFor(heads, d.copy(shortCode = null)))
        val fav = app.f1pro.data.Favourite("Andrea", "Kimi Antonelli", "mercedes", "Mercedes")
        assertTrue(fav.matches("Andrea Kimi", "Antonelli"))
        assertTrue(fav.matches("Kimi", "Antonelli"))
        assertFalse(fav.matches("George", "Russell"))
    }

    @Test
    fun teamTotalsFollowTheSeatNotTheDriver() {
        fun row(id: String, team: String, pts: Int) =
            """{"position":"1","positionText":"1","points":"$pts","Driver":{"driverId":"$id","givenName":"G","familyName":"$id"},"Constructor":{"constructorId":"$team"}}"""
        val page = """{"MRData":{"RaceTable":{"Races":[
            {"round":"1","raceName":"R1","Results":[${row("a", "red_bull", 25)},${row("b", "rb", 10)}]},
            {"round":"2","raceName":"R2","Results":[${row("a", "red_bull", 18)},${row("b", "red_bull", 6)}]}]}}}"""
        val s = DriverStats.seasonAll(2025, listOf(page), emptyList(), emptyList())
        assertEquals("rb", s.drivers.getValue("b").rounds[0].constructorId)
        assertEquals(listOf(1 to 25.0, 2 to 49.0), DriverStats.teamCumulative(s.drivers.values, "red_bull"))
        assertEquals(listOf(1 to 10.0, 2 to 10.0), DriverStats.teamCumulative(s.drivers.values, "rb"))
    }
}
