package com.kadhafi.aetherhop.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kadhafi.aetherhop.MainActivity
import com.kadhafi.aetherhop.R

class AetherHopNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val SERVICE_CHANNEL_ID = "aetherhop_service_channel"
        const val MESSAGE_CHANNEL_ID = "aetherhop_message_channel"
        const val SERVICE_NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "AetherHop Service Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status latar belakang radio mesh P2P"
            }

            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Pesan P2P & Darurat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pesan masuk dan sinyal darurat SOS"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }

    fun buildForegroundNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("AetherHop Radio Mesh Aktif")
            .setContentText("Mendengarkan jaringan P2P BLE & Wi-Fi Direct di latar belakang")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showMessageNotification(senderName: String, text: String, notificationId: Int = System.currentTimeMillis().toInt()) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setContentTitle("Pesan P2P dari $senderName")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
