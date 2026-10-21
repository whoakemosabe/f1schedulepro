package app.f1pro

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.ZoneId

class Prefs(context: Context) {
    private val p: SharedPreferences = context.applicationContext.getSharedPreferences("f1pro", Context.MODE_PRIVATE)

    /** "" follows the phone's time zone; otherwise an IANA id like "America/New_York". */
    var timezone: String
        get() = p.getString("timezone", "") ?: ""
        set(v) = p.edit { putString("timezone", v) }

    /** "auto" follows the phone; "12" or "24" forces a clock style. */
    var clock: String
        get() = p.getString("clock", "auto") ?: "auto"
        set(v) = p.edit { putString("clock", v) }

    var alertsOn: Boolean
        get() = p.getBoolean("alerts_on", false)
        set(v) = p.edit { putBoolean("alerts_on", v) }

    var leadMinutes: Int
        get() = p.getInt("lead_minutes", 30)
        set(v) = p.edit { putInt("lead_minutes", v) }

    /** "all", "quali" (qualifying + races) or "race". */
    var alertWhich: String
        get() = p.getString("alert_which", "all") ?: "all"
        set(v) = p.edit { putString("alert_which", v) }

    /** The reminder stays up and counts down to the start, on the lock screen too. */
    var countdownOn: Boolean
        get() = p.getBoolean("countdown_on", true)
        set(v) = p.edit { putBoolean("countdown_on", v) }

    /** Countdowns already switched to "is starting", so each is swapped once. */
    var startedPosted: Set<String>
        get() = p.getStringSet("started_posted", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("started_posted", v.toSortedSet().toList().takeLast(150).toSet()) }

    /** Sessions with their own bell on (race key + session name), reminded whatever the usual setting. */
    var sessionBells: Set<String>
        get() = p.getStringSet("session_bells", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("session_bells", v.toSortedSet().toList().takeLast(60).toSet()) }

    /** Sessions with their bell turned off on the race page: no reminder, whatever the usual setting. */
    var sessionMutes: Set<String>
        get() = p.getStringSet("session_mutes", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("session_mutes", v.toSortedSet().toList().takeLast(60).toSet()) }

    var sentReminders: Set<String>
        get() = p.getStringSet("sent_reminders", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("sent_reminders", v.toSortedSet().toList().takeLast(150).toSet()) }

    /** The "Tap a driver or team" hint on Standings has been seen (dismissed or a panel opened). */
    var standingsHintSeen: Boolean
        get() = p.getBoolean("standings_hint_seen", false)
        set(v) = p.edit { putBoolean("standings_hint_seen", v) }

    /** The title fight card on Standings is open (true) or folded. */
    var titleFightOpen: Boolean
        get() = p.getBoolean("title_fight_open", true)
        set(v) = p.edit { putBoolean("title_fight_open", v) }

    /** The starting grid card on race pages is open. */
    var gridOpen: Boolean
        get() = p.getBoolean("grid_open", true)
        set(v) = p.edit { putBoolean("grid_open", v) }

    var standingsView: String
        get() = p.getString("standings_view", "drivers") ?: "drivers"
        set(v) = p.edit { putString("standings_view", v) }

    var newsSort: String
        get() = p.getString("news_sort", "newest") ?: "newest"
        set(v) = p.edit { putString("news_sort", v) }

    /** "dark", "light" or "system". */
    var theme: String
        get() = p.getString("theme", "dark") ?: "dark"
        set(v) = p.edit { putString("theme", v) }

    var fahrenheit: Boolean
        get() = p.getBoolean("fahrenheit", false)
        set(v) = p.edit { putBoolean("fahrenheit", v) }

    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit { putBoolean("haptics", v) }

    // ── Favourite driver ──

    /** The favourite driver, or null. Stored by name: driver ids differ between the feeds. */
    var favourite: app.f1pro.data.Favourite?
        get() {
            val family = p.getString("fav_family", "") ?: ""
            if (family.isBlank()) return null
            return app.f1pro.data.Favourite(
                p.getString("fav_given", "") ?: "",
                family,
                p.getString("fav_team_id", "") ?: "",
                p.getString("fav_team_name", "") ?: "",
            )
        }
        set(v) = p.edit {
            putString("fav_given", v?.givenName ?: "")
            putString("fav_family", v?.familyName ?: "")
            putString("fav_team_id", v?.teamId ?: "")
            putString("fav_team_name", v?.teamName ?: "")
        }

    /** Tell me how my driver did after qualifying, sprints and races. */
    var resultAlerts: Boolean
        get() = p.getBoolean("result_alerts", true)
        set(v) = p.edit { putBoolean("result_alerts", v) }

    /** Session keys whose result has been reported, so each is sent once. */
    /** Races whose standings were refreshed on their own after the flag. */
    var standingsRefreshed: Set<String>
        get() = p.getStringSet("standings_refreshed", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("standings_refreshed", v.toSortedSet().toList().takeLast(60).toSet()) }

    var reported: Set<String>
        get() = p.getStringSet("reported", emptySet()) ?: emptySet()
        set(v) = p.edit { putStringSet("reported", v.toSortedSet().toList().takeLast(150).toSet()) }

    // ── Updates ──

    var lastUpdateCheck: Long
        get() = p.getLong("last_update_check", 0)
        set(v) = p.edit { putLong("last_update_check", v) }

    var availableVersion: String
        get() = p.getString("available_version", "") ?: ""
        set(v) = p.edit { putString("available_version", v) }

    /** The waiting version's highlights, one per line (for the "What's new" popup). */
    var availableNotes: String
        get() = p.getString("available_notes", "") ?: ""
        set(v) = p.edit { putString("available_notes", v) }

    /** How many releases the waiting version is ahead of the installed one. */
    var availableBehind: Int
        get() = p.getInt("available_behind", 1)
        set(v) = p.edit { putInt("available_behind", v) }

    /** The version whose "What's new" popup has been shown (once per version). */
    var whatsNewShown: String
        get() = p.getString("whats_new_shown", "") ?: ""
        set(v) = p.edit { putString("whats_new_shown", v) }

    var notifiedVersion: String
        get() = p.getString("notified_version", "") ?: ""
        set(v) = p.edit { putString("notified_version", v) }

    fun zone(): ZoneId = timezone.takeIf { it.isNotBlank() }?.let {
        try { ZoneId.of(it) } catch (e: Exception) { null }
    } ?: ZoneId.systemDefault()

    fun is24h(context: Context): Boolean = when (clock) {
        "12" -> false
        "24" -> true
        else -> android.text.format.DateFormat.is24HourFormat(context)
    }
}
