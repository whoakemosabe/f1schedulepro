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

/**
 * Quick settings tile: the next session ("FP1 · United States GP") and how long until it
 * starts, or LIVE while one is running. Read from the cached calendar each time the shade
 * opens. Tapping it opens the race.
 */
class NextSessionTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val now = System.currentTimeMillis()
        val seasons = try { Repo(this).seasons() } catch (e: Exception) { emptyList() }
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
