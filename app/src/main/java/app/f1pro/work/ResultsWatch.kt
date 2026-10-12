package app.f1pro.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.f1pro.Prefs
import app.f1pro.data.MIN_MS
import app.f1pro.data.Planner
import app.f1pro.data.Report
import app.f1pro.data.Repo
import app.f1pro.data.SeasonCalendar
import app.f1pro.widget.Widgets
import java.util.concurrent.TimeUnit

/**
 * After qualifying, a sprint or a race ends, waits for Jolpica to publish the classification and
 * sends one notification about the favourite driver. Results usually land within an hour or two;
 * the worker retries every 20, 40, 60… minutes until they do (about a day at most).
 */
object ResultsWatch {
    private const val MAX_ATTEMPTS = 12

    fun schedule(context: Context, seasons: List<SeasonCalendar>, now: Long = System.currentTimeMillis()) {
        val prefs = Prefs(context)
        scheduleStandings(context, seasons, now)
        if (prefs.favourite == null || !prefs.resultAlerts) return
        val reported = prefs.reported
        val wm = WorkManager.getInstance(context)
        for (slot in Planner.finishedForReport(seasons, now)) {
            if (slot.key in reported) continue
            val delay = (slot.session.end + 20 * MIN_MS - now).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ResultsWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        "key" to slot.key,
                        "season" to slot.race.season,
                        "round" to slot.race.round,
                        "session" to slot.session.name,
                    )
                )
                .build()
            wm.enqueueUniqueWork("result-${slot.key}", ExistingWorkPolicy.KEEP, request)
        }
    }

    internal fun attemptsLeft(run: Int) = run < MAX_ATTEMPTS

    /**
     * About two hours after every race (and sprint), fetch the standings by themselves, so the
     * numbers are already right when the app opens. Retries until the table has moved.
     */
    private fun scheduleStandings(context: Context, seasons: List<SeasonCalendar>, now: Long) {
        val done = Prefs(context).standingsRefreshed
        val wm = WorkManager.getInstance(context)
        for (slot in Planner.finishedForReport(seasons, now, windowMs = 24 * 60 * MIN_MS)) {
            if (slot.session.name != "Race" && slot.session.name != "Sprint") continue
            if (slot.key in done) continue
            val delay = (slot.session.end + 120 * MIN_MS - now).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<StandingsWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .setInputData(workDataOf("key" to slot.key))
                .build()
            wm.enqueueUniqueWork("standings-${slot.key}", ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** The post-race standings refresh: done once the table's points total has gone up. */
class StandingsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val key = inputData.getString("key") ?: return Result.success()
        if (key in prefs.standingsRefreshed) return Result.success()
        val repo = Repo(ctx)
        val before = repo.cachedStandings()?.drivers?.sumOf { it.points } ?: 0.0
        val after = try { repo.fetchStandings().drivers.sumOf { it.points } } catch (e: Exception) { null }
        val moved = after != null && after > before
        if (!moved && runAttemptCount < 8) return Result.retry()
        prefs.standingsRefreshed = prefs.standingsRefreshed + key
        try { Widgets.updateAll(ctx) } catch (e: Exception) { }
        return Result.success()
    }
}

class ResultsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val fav = prefs.favourite ?: return Result.success()
        if (!prefs.resultAlerts) return Result.success()
        val key = inputData.getString("key") ?: return Result.success()
        if (key in prefs.reported) return Result.success()
        val season = inputData.getInt("season", 0)
        val round = inputData.getInt("round", 0)
        val session = inputData.getString("session") ?: return Result.success()

        val repo = Repo(ctx)
        val race = repo.seasons().flatMap { it.races }
            .firstOrNull { it.season == season && it.round == round && !it.isTesting } ?: return Result.success()

        val results = try { repo.fetchResults(race) } catch (e: Exception) {
            return if (ResultsWatch.attemptsLeft(runAttemptCount)) Result.retry() else Result.success()
        }
        val after = if (session == "Race") repo.standingsAfter(season, round) else null
        val msg = Report.message(fav, race, session, results, after)
        if (msg == null) {
            // Not published yet: try again later. Published without them: nothing to say.
            val published = when (session) {
                "Race" -> results.race.isNotEmpty()
                "Sprint" -> results.sprint.isNotEmpty()
                else -> results.quali.isNotEmpty()
            }
            return if (!published && ResultsWatch.attemptsLeft(runAttemptCount)) Result.retry() else Result.success()
        }
        Alerts.postResult(ctx, key, msg.first, msg.second, race.key)
        prefs.reported = prefs.reported + key
        if (session == "Race") {
            try { repo.fetchPodiums(season) } catch (e: Exception) { }
            try { repo.fetchStandings() } catch (e: Exception) { }
            try { Widgets.updateAll(ctx) } catch (e: Exception) { }
        }
        return Result.success()
    }
}
