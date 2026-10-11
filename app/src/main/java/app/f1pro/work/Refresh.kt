package app.f1pro.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.f1pro.data.Repo
import java.util.concurrent.TimeUnit

/** Every ~6 hours: refresh the calendar and standings, then reset alarms and widgets. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repo(applicationContext)
        try { repo.refreshCalendar() } catch (e: Exception) { }
        try { repo.fetchStandings() } catch (e: Exception) { }
        try { repo.refreshTeamColours() } catch (e: Exception) { }
        try { Ticker.tick(applicationContext) } catch (e: Exception) { }
        try { UpdateWatch.checkAndNotify(applicationContext) } catch (e: Exception) { }
        return Result.success()
    }
}

object Scheduler {
    private const val PERIODIC = "f1pro-refresh"

    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
