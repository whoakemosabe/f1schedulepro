package app.f1pro.work

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.f1pro.Fmt
import app.f1pro.MainActivity
import app.f1pro.R
import app.f1pro.data.Planner
import app.f1pro.data.Repo
import kotlinx.coroutines.launch

/**
 * Quick settings tile: the next session ("FP1 · United States GP") and how long until it
 * starts, or LIVE while one is running. Read from the cached calendar each time the shade
 * opens. Tapping it opens the race.
 */
class NextSessionTile : TileService() {

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        // Read the saved calendar off the main thread, then fill the tile in.
        scope.launch {
            val seasons = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { Repo(this@NextSessionTile).seasons() } catch (e: Exception) { emptyList() }
            }
            show(seasons)
        }
    }

    private fun show(seasons: List<app.f1pro.data.SeasonCalendar>) {
        val tile = qsTile ?: return
        val now = System.currentTimeMillis()
        val live = Planner.live(seasons, now)
        val next = Planner.next(seasons, now)
        tile.icon = Icon.createWithResource(this, R.drawable.lc_flag)
        when {
            live != null -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "LIVE · ${live.session.short}"
                setSubtitle(tile, shortName(live.race.name))
            }
            next != null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "${next.session.short} · ${shortName(next.race.name)}"
                setSubtitle(tile, Fmt.until(next.session.start, now))
            }
            else -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "F1 Pro"
                setSubtitle(tile, "No sessions scheduled")
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NEXT_RACE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 77, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun setSubtitle(tile: Tile, text: String) {
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = text
    }

    private fun shortName(name: String) = name.replace("Grand Prix", "GP")
}
