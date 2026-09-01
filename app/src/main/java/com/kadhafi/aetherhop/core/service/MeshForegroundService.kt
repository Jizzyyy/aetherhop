package com.kadhafi.aetherhop.core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kadhafi.aetherhop.data.repository.P2pRepositoryImpl
import com.kadhafi.aetherhop.domain.repository.P2pRepository

class MeshForegroundService : Service() {

    private lateinit var notificationManager: AetherHopNotificationManager
    private var repository: P2pRepository? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = AetherHopNotificationManager(this)
        repository = P2pRepositoryImpl(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationManager.buildForegroundNotification()
        startForeground(AetherHopNotificationManager.SERVICE_NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        repository?.stopServices()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
