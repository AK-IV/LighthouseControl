package app.lighthousecontrol.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.lighthousecontrol.BlePermissions
import app.lighthousecontrol.MainActivity
import app.lighthousecontrol.R
import app.lighthousecontrol.ble.PowerAction
import app.lighthousecontrol.lighthouseApp
import app.lighthousecontrol.work.PowerAllWorker

/**
 * Quick Settings tile.
 * On tap: if stations are "on", put them all to sleep; otherwise turn them all on.
 */
class PowerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val app = lighthouseApp
        val canRun = BlePermissions.granted(this) &&
            app.ble.isEnabled &&
            app.repository.stations.value.isNotEmpty()
        if (!canRun) {
            // Missing permissions, Bluetooth off, or no stations added: open the app instead.
            openApp()
            return
        }
        val next =
            if (app.repository.lastAllAction == PowerAction.ON) PowerAction.SLEEP else PowerAction.ON
        app.repository.lastAllAction = next
        PowerAllWorker.enqueue(this, next)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = lighthouseApp.repository.lastAllAction == PowerAction.ON
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (on) R.string.tile_subtitle_on else R.string.tile_subtitle_sleep)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            TileService.requestListeningState(context,ComponentName(context, PowerTileService::class.java))
        }
    }
}
