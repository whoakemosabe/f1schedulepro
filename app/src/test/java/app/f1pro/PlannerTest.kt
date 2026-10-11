package app.f1pro

import app.f1pro.data.DAY_MS
import app.f1pro.data.MIN_MS
import app.f1pro.data.Parsers
import app.f1pro.data.Planner
import app.f1pro.data.SeasonCalendar
import app.f1pro.data.Sessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class PlannerTest {
    private val season: SeasonCalendar = Parsers.calendar(
        File("src/test/resources/calendar-2026-fixture.json").readText(),
        Parsers.extras(File("src/main/assets/extras-2026.json").readText()),
        live = false,
    )!!
    private val seasons = listOf(season)
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    // Singapore 2026: FP1 Fri 9 Oct 08:30Z … Race Sun 11 Oct 12:00Z (bundled data).
    private val sg = season.races.single { it.circuit.id == "marina_bay" }

    @Test
    fun remindersFireOnceInsideTheWindow() {
        val race = sg.raceSession!!.start
        val lead = 30 * MIN_MS
        val early = Planner.dueReminders(seasons, race - lead - 5 * MIN_MS, lead, "all", emptySet())
        assertTrue(early.none { it.session.name == "Race" && it.race.circuit.id == "marina_bay" })
        val due = Planner.dueReminders(seasons, race - lead, lead, "all", emptySet())
        val hit = due.single { it.race.circuit.id == "marina_bay" }
        assertEquals("Race", hit.session.name)
        val again = Planner.dueReminders(seasons, race - lead + MIN_MS, lead, "all", setOf(hit.key))
        assertTrue(again.none { it.key == hit.key })
        // Long after the start: never.
        assertTrue(Planner.dueReminders(seasons, race + 20 * MIN_MS, lead, "all", emptySet()).none { it.key == hit.key })
    }

    @Test
    fun reminderFilterSkipsPractice() {
        val fp1 = sg.sessions.first()
        assertEquals("Practice 1", fp1.name)
        val at = fp1.start - 15 * MIN_MS
        assertTrue(Planner.dueReminders(seasons, at, 15 * MIN_MS, "all", emptySet()).any { it.session == fp1 })
        assertTrue(Planner.dueReminders(seasons, at, 15 * MIN_MS, "quali", emptySet()).none { it.session == fp1 })
        assertTrue(Sessions.wanted("Sprint Qualifying", "quali"))
        assertTrue(!Sessions.wanted("Qualifying", "race"))
    }

    @Test
    fun nextWakeIsTheEarliestChange() {
        val fp1 = sg.sessions.first().start
        // Two hours out with 30-minute reminders: wake for the reminder.
        assertEquals(fp1 - 30 * MIN_MS, Planner.nextWake(seasons, fp1 - 120 * MIN_MS, true, 30 * MIN_MS, "all"))
        // Reminders off: wake when the session starts.
        assertEquals(fp1, Planner.nextWake(seasons, fp1 - 120 * MIN_MS, false, 30 * MIN_MS, "all"))
        // During FP1 (an hour long): wake when it ends.
        val mid = fp1 + 10 * MIN_MS
        val w = Planner.nextWake(seasons, mid, false, 30 * MIN_MS, "all")!!
        assertTrue(w <= fp1 + 60 * MIN_MS + 1000)
        // Three and a half days out: wake when the widget's day count ticks down.
        val far = fp1 - 3 * DAY_MS - 12 * 60 * MIN_MS
        assertEquals(fp1 - 3 * DAY_MS, Planner.nextWake(seasons, far, false, 30 * MIN_MS, "all"))
        // After the last session of the season there's nothing left.
        assertNull(Planner.nextWake(seasons, ms("2026-12-31T00:00:00Z"), true, 30 * MIN_MS, "all"))
    }

    @Test
    fun liveAndNextAcrossTheSeason() {
        val race = sg.raceSession!!
        val live = Planner.live(seasons, race.start + 30 * MIN_MS)
        assertNotNull(live)
        assertEquals("marina_bay", live!!.race.circuit.id)
        val next = Planner.next(seasons, race.start + 30 * MIN_MS)!!
        assertEquals("americas", next.race.circuit.id)
        assertEquals("marina_bay", Planner.weekend(seasons, race.start - DAY_MS)!!.circuit.id)
    }

    @Test
    fun bellsOverrideTheUsualSettingPerSession() {
        val fp1 = Planner.slots(seasons).first { it.session.name == "Practice 1" && !it.race.isTesting }
        val at = fp1.session.start - 30 * 60_000L
        val lead = 30 * 60_000L
        // Races-only: FP1 isn't due, unless its bell is on.
        assertTrue(Planner.dueReminders(seasons, at, lead, "race", emptySet()).none { it.key == fp1.key })
        assertTrue(Planner.dueReminders(seasons, at, lead, "race", emptySet(), setOf(fp1.key)).any { it.key == fp1.key })
        // All sessions: FP1 is due, unless its bell is off.
        assertTrue(Planner.dueReminders(seasons, at, lead, "all", emptySet()).any { it.key == fp1.key })
        assertTrue(Planner.dueReminders(seasons, at, lead, "all", emptySet(), mutes = setOf(fp1.key)).none { it.key == fp1.key })
        // Reminders off in Settings: nothing, bells included.
        assertTrue(Planner.dueReminders(seasons, at, lead, "race", emptySet(), setOf(fp1.key), alertsOn = false).isEmpty())
        assertEquals(at, Planner.nextWake(seasons, at - 60 * 60_000L, true, lead, "race", setOf(fp1.key)))
    }
}
