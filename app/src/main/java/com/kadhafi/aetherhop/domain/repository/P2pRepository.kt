package com.kadhafi.aetherhop.domain.repository

import android.net.wifi.p2p.WifiP2pDevice
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.domain.model.SosPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface P2pRepository {
    val messages: StateFlow<Map<String, List<ChatMessage>>>
    val connectionState: StateFlow<P2pConnectionState>
    val wifiPeers: StateFlow<List<WifiP2pDevice>>
    val peerIdentities: StateFlow<Map<String, String>>
    val activeSosAlerts: StateFlow<List<SosPayload>>

    fun connectToPeer(peer: PeerNode): Boolean
    fun disconnectPeer()
    fun isBluetoothEnabled(): Boolean
    fun observeBluetoothState(): Flow<Boolean>
    fun scanBlePeers(): Flow<PeerNode>
    fun sendChatMessage(targetAddress: String, text: String, senderName: String)
    fun retrySendMessage(messageId: String, targetAddress: String)
    fun broadcastSos(emergencyNote: String, latitude: Double? = null, longitude: Double? = null)
    fun dismissSosAlert(senderId: String)
    fun setDeviceName(name: String)
    fun getDeviceId(): String
    fun stopServices()
}
