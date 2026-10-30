package com.gamershub.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gamershub.MainActivity
import com.gamershub.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_CORE = "gh_core"
        const val CHANNEL_RECORDER = "gh_recorder"
        const val CHANNEL_ALERTS = "gh_alerts"

        const val ID_CORE = 100
        const val ID_RECORDER = 200
        const val ID_ALERT = 300
    }

    fun createChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_CORE, "Boost engine", NotificationManager.IMPORTANCE_MIN
                ).apply { description = "Keeps the game boost engine alive" },
                NotificationChannel(
                    CHANNEL_RECORDER, "Recorder & streaming", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Screen capture status" },
                NotificationChannel(
                    CHANNEL_ALERTS, "Thermal alerts", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Temperature warnings" }
            )
        )
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun coreNotification(statusLine: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_CORE)
            .setSmallIcon(R.drawable.ic_stat_gh)
            .setContentTitle("GamersHub engine")
            .setContentText(statusLine)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun recorderNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_RECORDER)
            .setSmallIcon(R.drawable.ic_stat_gh)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun thermalAlert(message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_gh)
            .setContentTitle("Thermal warning")
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { nm.notify(ID_ALERT, n) }
    }
}
