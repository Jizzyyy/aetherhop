package com.kadhafi.aetherhop.data.repository

import android.content.Context
import com.kadhafi.aetherhop.data.ble.BleManager
import com.kadhafi.aetherhop.data.network.P2pSocketClient
import com.kadhafi.aetherhop.data.network.P2pSocketServer
import com.kadhafi.aetherhop.data.p2p.WifiP2pDirectManager
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class P2pRepositoryImpl(context: Context) {
    private val appContext = context.applicationContext
    private val bleManager = BleManager(appContext)
    private val wifiP2pManager = WifiP2pDirectManager(appContext)
    private val socketServer = P2pSocketServer()
    private val socketClient = P2pSocketClient()
    
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val connectionState: StateFlow<P2pConnectionState> = wifiP2pManager.connectionState

    init {
        scope.launch {
            socketServer.startServer().collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    fun scanBlePeers(): Flow<PeerNode> = bleManager.scanPeers()

    fun scanWifiPeers() = wifiP2pManager.discoverPeers()

    fun sendChatMessage(targetAddress: String, text: String, senderName: String) {
        scope.launch {
            val messageId = UUID.randomUUID().toString()
            val chatMsg = ChatMessage(
                id = messageId,
                senderId = "MY_ID",
                senderName = senderName,
                text = text,
                isMine = true
            )

            val packet = MeshPacket(
                id = messageId,
                senderId = "MY_ID",
                targetId = targetAddress,
                type = PacketType.CHAT,
                payload = Json.encodeToString(chatMsg)
            )

            val result = socketClient.sendPacket(targetAddress, packet)
            if (result.isSuccess) {
                _messages.update { it + chatMsg }
            }
        }
    }

    private fun handleIncomingPacket(packet: MeshPacket) {
        when (packet.type) {
            PacketType.CHAT -> {
                try {
                    val chatMsg = Json.decodeFromString<ChatMessage>(packet.payload).copy(isMine = false)
                    _messages.update { it + chatMsg }
                } catch (_: Exception) {}
            }
            else -> {}
        }
    }

    fun stopServices() {
        job.cancel()
        socketServer.stopServer()
        wifiP2pManager.disconnect()
    }
}
