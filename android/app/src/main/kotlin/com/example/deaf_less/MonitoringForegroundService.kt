package com.example.deaf_less

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.app.Service

class MonitoringForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "monitoring_channel"
        const val CHANNEL_NAME = "Monitoreo"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.deaf_less.action.START"
        const val ACTION_UPDATE = "com.example.deaf_less.action.UPDATE"
        const val ACTION_STOP = "com.example.deaf_less.action.STOP"
        const val EXTRA_CONTENT = "extra_content"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.description = "Notificación persistente del monitoreo de sonidos"
                manager.createNotificationChannel(channel)
            }
        }

        fun buildNotification(context: Context, content: String): Notification {
            ensureChannel(context)

            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Listening to alerts")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val content = intent?.getStringExtra(EXTRA_CONTENT) ?: "Nothing"
        when (action) {
            ACTION_START -> {
                val notification = buildNotification(this, content)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_UPDATE -> {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notification = buildNotification(this, content)
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // Default to start if action is missing
                val notification = buildNotification(this, content)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY
    }
}
