package com.termdroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AgentService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                sendBroadcast(Intent(ACTION_CANCEL_REQUESTED).setPackage(packageName))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val texto = intent?.getStringExtra(EXTRA_STATUS) ?: getString(R.string.agente_trabajando)
        startForeground(NOTIFICATION_ID, buildNotification(texto))
        return START_STICKY
    }

    private fun buildNotification(texto: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Agente", NotificationManager.IMPORTANCE_LOW),
            )
        }

        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancelar = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Termdroid")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(abrir)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.cancelar), cancelar).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_CANCEL = "com.termdroid.CANCEL"
        const val ACTION_STOP = "com.termdroid.STOP"
        const val ACTION_CANCEL_REQUESTED = "com.termdroid.CANCEL_REQUESTED"

        private const val CHANNEL = "agente"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_STATUS = "status"

        fun trabajando(context: Context, status: String) {
            val i = Intent(context, AgentService::class.java).putExtra(EXTRA_STATUS, status)
            context.startForegroundService(i)
        }

        fun listo(context: Context) {
            context.startService(Intent(context, AgentService::class.java).setAction(ACTION_STOP))
        }
    }
}
