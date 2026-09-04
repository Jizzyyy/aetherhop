package com.kadhafi.aetherhop.data.nan

import android.net.wifi.aware.*
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AwarePeerMessage(
    val peerHandle: PeerHandle,
    val messageBytes: ByteArray
)

class WifiAwareDiscoveryService {

    companion object {
        const val AETHERHOP_AWARE_SERVICE_NAME = "aetherhop_mesh_service"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun publishService(session: WifiAwareSession): Flow<AwarePeerMessage> = callbackFlow {
        val config = PublishConfig.Builder()
            .setServiceName(AETHERHOP_AWARE_SERVICE_NAME)
            .build()

        var publishDiscoverySession: PublishDiscoverySession? = null

        session.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                publishDiscoverySession = session
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                trySend(AwarePeerMessage(peerHandle, message))
            }
        }, null)

        awaitClose {
            publishDiscoverySession?.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun subscribeService(session: WifiAwareSession): Flow<AwarePeerMessage> = callbackFlow {
        val config = SubscribeConfig.Builder()
            .setServiceName(AETHERHOP_AWARE_SERVICE_NAME)
            .build()

        var subscribeDiscoverySession: SubscribeDiscoverySession? = null

        session.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                subscribeDiscoverySession = session
            }

            override fun onServiceDiscovered(
                peerHandle: PeerHandle,
                serviceSpecificInfo: ByteArray?,
                matchFilter: MutableList<ByteArray>?
            ) {
                // Service discovered, ready for message exchange or datapath initiation
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                trySend(AwarePeerMessage(peerHandle, message))
            }
        }, null)

        awaitClose {
            subscribeDiscoverySession?.close()
        }
    }
}
