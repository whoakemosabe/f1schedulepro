package app.f1pro

import app.f1pro.data.Tracks
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TracksTest {
    private val tracks = Tracks.parse(File("src/main/assets/circuits.json").readText())

    @Test
    fun everyCircuitOnTheCalendarHasAnOutline() {
        // The fixed copy: the bundled calendar is refreshed from the live feed before tests run.
        run {
            val f = File("src/test/resources/calendar-2026-fixture.json")
            val races = JSONObject(f.readText()).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races")
            for (i in 0 until races.length()) {
                val id = races.getJSONObject(i).getJSONObject("Circuit").getString("circuitId")
                assertTrue("no outline for $id", id in tracks)
            }
        }
    }

    @Test
    fun outlinesFitTheUnitSquare() {
        for ((id, t) in tracks) {
            assertTrue("$id too few points", t.size >= 40)
            assertTrue("$id out of range", t.points.all { it in -0.001f..1.001f })
            assertTrue("$id size", t.w in 0.05f..1.001f && t.h in 0.05f..1.001f && maxOf(t.w, t.h) > 0.99f)
        }
    }
}
