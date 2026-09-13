package com.kadhafi.aetherhop.core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kadhafi.aetherhop.data.repository.P2pRepositoryImpl
import com.kadhafi.aetherhop.domain.repository.P2pRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MeshForegroundService : Service() {

    private lateinit var notificationManager: AetherHopNotificationManager
    private var repository: P2pRepository? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        notificationManager = AetherHopNotificationManager(this)
        repository = P2pRepositoryImpl.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationManager.buildForegroundNotification()
        startForeground(AetherHopNotificationManager.SERVICE_NOTIFICATION_ID, notification)

        serviceScope.launch {
            repository?.wifiPeers?.collect { peers ->
                notificationManager.updateForegroundNotification(peers.size)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
