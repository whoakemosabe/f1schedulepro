package app.f1pro.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.f1pro.Fmt
import app.f1pro.MainActivity
import app.f1pro.Prefs
import app.f1pro.R
import app.f1pro.data.MIN_MS
import app.f1pro.data.SeasonCalendar
import app.f1pro.data.Planner
import app.f1pro.data.Slot

object Alerts {
    private const val CH_SESSIONS = "sessions"
    private const val CH_UPDATES = "updates"
    private const val CH_RESULTS = "results"
    const val EXTRA_RACE = "race_key"
    const val EXTRA_OPEN_UPDATES = "open_updates"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSIONS, "Session reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A heads-up before practice, qualifying and races start"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULTS, "Your driver's results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "How your favourite driver did in qualifying, sprints and races"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "When a new version of F1 Pro is out"
            }
        )
    }

    /**
     * The app's page in system notification settings. Once notifications have been refused
     * twice Android stops showing the prompt, so this is the only way back.
     */
    fun openSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Sends any reminders that are due and remembers them so each fires once. */
    fun fireDue(context: Context, seasons: List<SeasonCalendar>, now: Long = System.currentTimeMillis()) {
        val prefs = Prefs(context)
        if (!prefs.alertsOn || !canNotify(context)) return
        val due = Planner.dueReminders(seasons, now, prefs.leadMinutes * MIN_MS, prefs.alertWhich, prefs.sentReminders, prefs.sessionBells, true, prefs.sessionMutes)
        if (due.isEmpty()) return
        createChannels(context)
        val fmt = Fmt(prefs.zone(), prefs.is24h(context))
        for (slot in due) postSession(context, slot, now, fmt, countdown = prefs.countdownOn)
        prefs.sentReminders = prefs.sentReminders + due.map { it.key }
    }

    /**
     * When a counted-down session starts, swap its countdown for a plain "is starting" note
     * (quietly: it already buzzed once). Runs on every tick; the start time is one of the wakes.
     */
    fun finishCountdowns(context: Context, seasons: List<SeasonCalendar>, now: Long = System.currentTimeMillis()) {
        val prefs = Prefs(context)
        if (!canNotify(context)) return
        val sent = prefs.sentReminders
        val done = prefs.startedPosted
        val started = Planner.slots(seasons).filter {
            !it.session.tbc && it.session.start <= now + 1000 && it.session.start > now - 15 * MIN_MS &&
                it.key in sent && it.key !in done
        }
        if (started.isEmpty()) return
        createChannels(context)
        val fmt = Fmt(prefs.zone(), prefs.is24h(context))
        // Only swap countdowns still on screen (not ones swiped away or never shown).
        val showing = context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications?.map { it.id }?.toSet().orEmpty()
        for (slot in started) {
            if (slot.key.hashCode() !in showing) continue
            val text = "${slot.race.circuit.flag}  ${slot.race.name} · ${fmt.time(slot.session.start)} ${fmt.abbr(slot.session.start)}"
            post(context, CH_SESSIONS, slot.key.hashCode(), "${slot.session.name} is starting", text, slot.race.key, quiet = true)
        }
        prefs.startedPosted = done + started.map { it.key }
    }

    /** A one-off test so people can check reminders work on their phone. */
    fun postTest(context: Context, slot: Slot?) {
        createChannels(context)
        val prefs = Prefs(context)
        val fmt = Fmt(prefs.zone(), prefs.is24h(context))
        if (slot != null) {
            postSession(context, slot, slot.session.start - prefs.leadMinutes * MIN_MS, fmt)
        } else {
            post(context, CH_SESSIONS, 999, "Reminders are on", "You'll get a heads-up before each session.", null)
        }
    }

    private fun postSession(context: Context, slot: Slot, now: Long, fmt: Fmt, countdown: Boolean = false) {
        val mins = ((slot.session.start - now + 30_000) / MIN_MS).coerceAtLeast(0)
        val title = when {
            mins <= 0 -> "${slot.session.name} is starting"
            mins == 1L -> "${slot.session.name} starts in 1 minute"
            mins < 90 -> "${slot.session.name} starts in $mins minutes"
            else -> "${slot.session.name} at ${fmt.time(slot.session.start)}"
        }
        val text = "${slot.race.circuit.flag}  ${slot.race.name} · ${fmt.time(slot.session.start)} ${fmt.abbr(slot.session.start)}"
        if (countdown && slot.session.start > now + 30_000) {
            post(
                context, CH_SESSIONS, slot.key.hashCode(),
                "${slot.session.name} · ${slot.race.name}",
                "${slot.race.circuit.flag}  Lights out at ${fmt.time(slot.session.start)} ${fmt.abbr(slot.session.start)}",
                slot.race.key, countdownTo = slot.session.start, now = now,
            )
        } else {
            post(context, CH_SESSIONS, slot.key.hashCode(), title, text, slot.race.key)
        }
    }

    /** "Antonelli finished P2 in the Singapore GP" — once per session. Tapping opens the race. */
    fun postResult(context: Context, key: String, title: String, text: String, raceKey: String) {
        createChannels(context)
        post(context, CH_RESULTS, ("result-" + key).hashCode(), title, text, raceKey)
    }

    /** One quiet notification when a new version is out. */
    fun postUpdate(context: Context, version: String) {
        createChannels(context)
        post(context, CH_UPDATES, 4, "F1 Pro $version is ready", "Tap to update.", null, openUpdates = true)
    }

    @SuppressLint("MissingPermission")
    private fun post(
        context: Context, channel: String, id: Int, title: String, text: String,
        raceKey: String?, openUpdates: Boolean = false,
        countdownTo: Long? = null, now: Long = System.currentTimeMillis(), quiet: Boolean = false,
    ) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_UPDATES, openUpdates)
        if (raceKey != null) intent.putExtra(EXTRA_RACE, raceKey)
        val open = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_f1)
            .setColor(0xFFE10600.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setCategory(if (channel == CH_SESSIONS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (channel == CH_SESSIONS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
        if (quiet) b.setOnlyAlertOnce(true)
        if (countdownTo != null) {
            // A live countdown: stays up (lock screen included), ticks down to the start, and on
            // Android 16 asks to be a Live Update with the countdown in the status bar.
            b.setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(countdownTo)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setTimeoutAfter(countdownTo - now + 15 * MIN_MS)
                .setRequestPromotedOngoing(true)
        }
        NotificationManagerCompat.from(context).notify(id, b.build())
    }
}
