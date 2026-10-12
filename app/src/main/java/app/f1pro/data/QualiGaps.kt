package app.f1pro.data

/**
 * Qualifying deltas the way the TV shows them: each driver against the fastest time in the
 * last part they ran (Q3 runners against pole, Q2 knock-outs against the best Q2, and so on).
 */
object QualiGaps {
    /** "1:23.456" or "59.876" → milliseconds; null for no time. */
    fun ms(t: String?): Long? {
        val s = t?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val (m, rest) = if (':' in s) s.substringBefore(':').toLong() to s.substringAfter(':') else 0L to s
            val sec = rest.substringBefore('.').toLong()
            val frac = rest.substringAfter('.', "0").padEnd(3, '0').take(3).toLong()
            (m * 60 + sec) * 1000 + frac
        } catch (e: NumberFormatException) { null }
    }

    /** "+0.123", "+1:02.004" for very large gaps; null when either time is missing. */
    fun format(delta: Long): String {
        val s = delta / 1000
        val f = (delta % 1000).toString().padStart(3, '0')
        return if (s >= 60) "+${s / 60}:${(s % 60).toString().padStart(2, '0')}.$f" else "+$s.$f"
    }

    /** Each row's gap (ms) to the fastest time in its own last part; 0 for that part's fastest. */
    fun gaps(rows: List<QualiResult>): List<Long?> {
        fun timeIn(q: QualiResult, part: String) = ms(when (part) { "Q3" -> q.q3; "Q2" -> q.q2; else -> q.q1 })
        val best = listOf("Q1", "Q2", "Q3").associateWith { p -> rows.mapNotNull { timeIn(it, p) }.minOrNull() }
        return rows.map { q ->
            if (q.part.isEmpty()) null
            else {
                val mine = timeIn(q, q.part)
                val top = best[q.part]
                if (mine == null || top == null) null else mine - top
            }
        }
    }
}
