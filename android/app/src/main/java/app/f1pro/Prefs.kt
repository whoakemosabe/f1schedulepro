package app.f1pro

import android.content.Context
import android.content.SharedPreferences
import java.time.ZoneId

class Prefs(context: Context) {
    private val p: SharedPreferences = context.applicationContext.getSharedPreferences("f1pro", Context.MODE_PRIVATE)

    /** "" follows the phone's time zone; otherwise an IANA id like "America/New_York". */
    var timezone: String
        get() = p.getString("timezone", "") ?: ""
        set(v) = p.edit().putString("timezone", v).apply()

    /** "auto" follows the phone; "12" or "24" forces a clock style. */
    var clock: String
        get() = p.getString("clock", "auto") ?: "auto"
        set(v) = p.edit().putString("clock", v).apply()

    var alertsOn: Boolean
        get() = p.getBoolean("alerts_on", false)
        set(v) = p.edit().putBoolean("alerts_on", v).apply()

    var leadMinutes: Int
        get() = p.getInt("lead_minutes", 30)
        set(v) = p.edit().putInt("lead_minutes", v).apply()

    /** "all", "quali" (qualifying + races) or "race". */
    var alertWhich: String
        get() = p.getString("alert_which", "all") ?: "all"
        set(v) = p.edit().putString("alert_which", v).apply()

    var sentReminders: Set<String>
        get() = p.getStringSet("sent_reminders", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("sent_reminders", v.toSortedSet().toList().takeLast(150).toSet()).apply()

    var standingsView: String
        get() = p.getString("standings_view", "drivers") ?: "drivers"
        set(v) = p.edit().putString("standings_view", v).apply()

    var newsSort: String
        get() = p.getString("news_sort", "newest") ?: "newest"
        set(v) = p.edit().putString("news_sort", v).apply()

    /** "dark", "light" or "system". */
    var theme: String
        get() = p.getString("theme", "dark") ?: "dark"
        set(v) = p.edit().putString("theme", v).apply()

    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()

    // ── Updates ──

    var lastUpdateCheck: Long
        get() = p.getLong("last_update_check", 0)
        set(v) = p.edit().putLong("last_update_check", v).apply()

    var availableVersion: String
        get() = p.getString("available_version", "") ?: ""
        set(v) = p.edit().putString("available_version", v).apply()

    var notifiedVersion: String
        get() = p.getString("notified_version", "") ?: ""
        set(v) = p.edit().putString("notified_version", v).apply()

    fun zone(): ZoneId = timezone.takeIf { it.isNotBlank() }?.let {
        try { ZoneId.of(it) } catch (e: Exception) { null }
    } ?: ZoneId.systemDefault()

    fun is24h(context: Context): Boolean = when (clock) {
        "12" -> false
        "24" -> true
        else -> android.text.format.DateFormat.is24HourFormat(context)
    }
}
