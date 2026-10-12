package app.f1pro

import app.f1pro.data.QualiGaps
import app.f1pro.data.QualiResult
import org.junit.Assert.assertEquals
import org.junit.Test

class QualiGapsTest {
    private fun q(pos: Int, q1: String?, q2: String?, q3: String?) =
        QualiResult(pos, "1", "X$pos", "A", "B", "t", "T", q1, q2, q3)

    @Test
    fun gapsAreToTheFastestInTheSamePart() {
        val rows = listOf(
            q(1, "1:31.000", "1:30.500", "1:30.000"),
            q(2, "1:31.200", "1:30.400", "1:30.123"),
            q(11, "1:31.100", "1:30.900", null),
            q(16, "1:31.900", null, null),
            q(20, null, null, null),
        )
        assertEquals(listOf(0L, 123L, 500L, 900L, null), QualiGaps.gaps(rows))
        assertEquals("+0.123", QualiGaps.format(123))
        assertEquals("+1.050", QualiGaps.format(1050))
        assertEquals(59_876L, QualiGaps.ms("59.876"))
        assertEquals(83_400L, QualiGaps.ms("1:23.4"))
    }
}
