package app.f1pro

import app.f1pro.data.DAY_MS
import app.f1pro.data.HOUR_MS
import app.f1pro.data.MIN_MS
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Times in the chosen time zone and clock style. */
data class Fmt(val zone: ZoneId, val h24: Boolean) {
    private val timeF = DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "hh:mm a", Locale.US)
    private val clockF = DateTimeFormatter.ofPattern(if (h24) "HH:mm:ss" else "hh:mm:ss", Locale.US)
    private val dateF = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val dayF = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val shortTimeF = DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mma", Locale.US)

    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)

    /** "01:30 PM" or "13:30". */
    fun time(ms: Long): String = timeF.format(at(ms))

    /** "1:30PM" or "13:30", for widgets. */
    fun shortTime(ms: Long): String = shortTimeF.format(at(ms)).replace("AM", "a").replace("PM", "p")

    /** "Sat, Mar 7". */
    fun date(ms: Long): String = dateF.format(at(ms))

    /** "Sat". */
    fun day(ms: Long): String = dayF.format(at(ms))

    /** Header clock: "01:30:05" and "PM" (empty in 24h). */
    fun clock(ms: Long): Pair<String, String> =
        clockF.format(at(ms)) to if (h24) "" else (if (at(ms).hour < 12) "AM" else "PM")

    /** "GMT", "EDT", "CET"… */
    fun abbr(ms: Long = System.currentTimeMillis()): String =
        DateTimeFormatter.ofPattern("zzz", Locale.US).format(at(ms))

    companion object {
        data class Parts(val days: Long, val hours: Long, val minutes: Long, val seconds: Long, val total: Long)

        fun countdown(target: Long, now: Long): Parts {
            val d = target - now
            if (d <= 0) return Parts(0, 0, 0, 0, 0)
            return Parts(d / DAY_MS, (d / HOUR_MS) % 24, (d / MIN_MS) % 60, (d / 1000) % 60, d)
        }

        /** "in 3d 4h", "in 2h 05m", "in 12 min". */
        fun until(target: Long, now: Long): String {
            val p = countdown(target, now)
            return when {
                p.total <= 0 -> "now"
                p.days > 0 -> "in ${p.days}d ${p.hours}h"
                p.hours > 0 -> "in ${p.hours}h ${p.minutes.toString().padStart(2, '0')}m"
                else -> "in ${maxOf(1, p.minutes + if (p.seconds > 0) 1 else 0)} min"
            }
        }

        /** "just now", "5m ago", "3h ago", "2d ago", or "Mar 4". */
        fun ago(ms: Long, now: Long): String {
            if (ms <= 0) return ""
            val s = (now - ms) / 1000
            return when {
                s < 60 -> "just now"
                s < 3600 -> "${s / 60}m ago"
                s < 86400 -> "${s / 3600}h ago"
                s < 7 * 86400 -> "${s / 86400}d ago"
                else -> DateTimeFormatter.ofPattern("MMM d", Locale.US).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
            }
        }

        /** 25.0 → "25", 12.5 → "12.5". */
        fun points(p: Double): String = if (p % 1.0 == 0.0) p.toLong().toString() else p.toString()

        fun zoneLabel(id: String): String =
            try { ZoneId.of(id).getDisplayName(TextStyle.SHORT, Locale.US) } catch (e: Exception) { id }
    }
}
