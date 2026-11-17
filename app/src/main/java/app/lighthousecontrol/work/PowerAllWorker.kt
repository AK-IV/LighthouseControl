package app.lighthousecontrol.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.lighthousecontrol.BlePermissions
import app.lighthousecontrol.R
import app.lighthousecontrol.ble.PowerAction
import app.lighthousecontrol.lighthouseApp
import app.lighthousecontrol.tile.PowerTileService

/**
 * Runs an "all stations" command in the background. Uses WorkManager so the
 * operation completes even if Quick Settings is closed or the app is backgrounded
 * (going through all stations can take several seconds).
 */
class PowerAllWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION)
            ?.let { runCatching { PowerAction.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        if (!BlePermissions.granted(applicationContext)) return Result.failure()

        applicationContext.lighthouseApp.controller.applyToAll(action)
        PowerTileService.requestUpdate(applicationContext)
        return Result.success()
    }

    /** Only needed on Android 11 and below, where expedited work runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lighthouse)
            .setContentTitle(applicationContext.getString(R.string.notif_sending))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val KEY_ACTION = "action"
        private const val CHANNEL_ID = "power"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "power_all"

        fun enqueue(context: Context, action: PowerAction) {
            val request = OneTimeWorkRequestBuilder<PowerAllWorker>()
                .setInputData(workDataOf(KEY_ACTION to action.name))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
