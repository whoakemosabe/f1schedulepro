package app.f1pro.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.f1pro.Prefs
import app.f1pro.data.MIN_MS
import app.f1pro.data.Planner
import app.f1pro.data.Repo
import app.f1pro.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One alarm at a time, always set for the next moment something changes: a reminder is due,
 * a session starts or ends, or the widget countdown rolls over a day. Each firing does its
 * work and sets the next one, so nothing depends on a server or the app being open.
 */
object Ticker {
    private const val REQUEST = 4201
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val seasons = Repo(app).seasons()
        val at = Planner.nextWake(seasons, now, prefs.alertsOn, prefs.leadMinutes * MIN_MS, prefs.alertWhich)
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            app, REQUEST, Intent(app, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            am.cancel(pi)
            return
        }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Reminders, widgets and the next alarm, all from the cached calendar. */
    suspend fun tick(context: Context) {
        val app = context.applicationContext
        val seasons = Repo(app).seasons()
        try { Alerts.fireDue(app, seasons) } catch (e: Exception) { }
        try { Widgets.updateAll(app) } catch (e: Exception) { }
        schedule(app)
    }

    fun tickAsync(context: Context, done: (() -> Unit)? = null) {
        val app = context.applicationContext
        scope.launch {
            try { tick(app) } finally { done?.invoke() }
        }
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Ticker.tickAsync(context) { pending.finish() }
    }
}

/** After a reboot, an app update or a clock change, alarms are gone or wrong: set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.ensure(context)
        val pending = goAsync()
        Ticker.tickAsync(context) { pending.finish() }
    }
}
