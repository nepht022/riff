package com.rodrigomachado.riff.core.separation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.rodrigomachado.riff.core.model.SeparationFailure
import com.rodrigomachado.riff.core.model.SeparationStatus
import kotlin.math.roundToInt

/** A notificação do Android: progresso enquanto separa, aviso de fim ou de falha. Tocar abre a Biblioteca. */
class SeparationNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** [queued]: quantas esperam depois desta. */
    fun foregroundInfo(status: SeparationStatus.Running, queued: Int = 0): ForegroundInfo {
        val percent = (status.progress * PERCENT).roundToInt()
        val state = if (status.pausedForHeat) {
            context.getString(R.string.separation_paused)
        } else {
            context.getString(R.string.separation_running, percent)
        }
        val text = if (queued > 0) context.getString(R.string.separation_queued, state, queued) else state
        val notification = builder().setContentTitle(status.title).setContentText(text).setOngoing(true)
            .setProgress(PERCENT, percent, false).build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        return ForegroundInfo(PROGRESS_ID, notification, type)
    }

    /** Um aviso por música: na fila, o "pronta" da seguinte não apaga a falha desta. */
    fun done(chartId: String, title: String) = notify(chartId, context.getString(R.string.separation_done, title))

    fun failed(chartId: String, failure: SeparationStatus.Failed) = notify(
        chartId,
        if (failure.reason == SeparationFailure.NO_SPACE) {
            context.getString(R.string.separation_no_space)
        } else {
            context.getString(R.string.separation_failed, failure.title)
        },
    )

    private fun notify(chartId: String, text: String) =
        manager.notify(RESULT_TAG + chartId, RESULT_ID, builder().setContentTitle(text).setAutoCancel(true).build())

    private fun builder(): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.separation_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(OPEN_LIBRARY_EXTRA, true)
        val pending = open?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_separation)
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
    }

    companion object {
        const val OPEN_LIBRARY_EXTRA = "riff.abrir_biblioteca"
        private const val CHANNEL = "separacao"
        private const val PROGRESS_ID = 41
        private const val RESULT_ID = 42
        private const val RESULT_TAG = "separacao:"
        private const val PERCENT = 100
    }
}
