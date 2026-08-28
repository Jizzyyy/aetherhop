package com.kadhafi.aetherhop.data.repository

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import com.kadhafi.aetherhop.core.util.DeviceIdentity
import com.kadhafi.aetherhop.data.ble.BleManager
import com.kadhafi.aetherhop.data.network.P2pSocketClient
import com.kadhafi.aetherhop.data.network.P2pSocketServer
import com.kadhafi.aetherhop.data.p2p.WifiP2pDirectManager
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.HandshakePayload
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.MessageStatus
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

    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    val connectionState: StateFlow<P2pConnectionState> = wifiP2pManager.connectionState

    private val _wifiPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val wifiPeers: StateFlow<List<WifiP2pDevice>> = _wifiPeers.asStateFlow()

    private val deviceId = DeviceIdentity.getDeviceId(appContext)
    private val deviceName = DeviceIdentity.getDeviceName(appContext)

    private val _peerIdentities = MutableStateFlow<Map<String, String>>(emptyMap())
    val peerIdentities: StateFlow<Map<String, String>> = _peerIdentities.asStateFlow()

    init {
        scope.launch {
            socketServer.startServer().collect { packet ->
                handleIncomingPacket(packet)
            }
        }
        scope.launch {
            wifiP2pManager.discoverPeers().collect { devices ->
                _wifiPeers.value = devices
            }
        }
        scope.launch {
            connectionState.collect { state ->
                if (state is P2pConnectionState.Connected && state.groupOwnerAddress.isNotBlank()) {
                    sendHandshake(state.groupOwnerAddress)
                }
            }
        }
    }

    private fun sendHandshake(targetIp: String) {
        scope.launch {
            val payload = Json.encodeToString(HandshakePayload(deviceId, deviceName))
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = deviceId,
                targetId = targetIp,
                type = PacketType.HANDSHAKE,
                payload = payload
            )
            socketClient.sendPacket(targetIp, packet)
        }
    }

    fun connectToPeer(peer: PeerNode) {
        val targetDevice = _wifiPeers.value.find { it.deviceAddress == peer.address || it.deviceName == peer.name }
        if (targetDevice != null) {
            wifiP2pManager.connectToDevice(
                device = targetDevice,
                onSuccess = {},
                onError = {}
            )
        }
    }

    fun retrySendMessage(messageId: String, targetAddress: String) {
        scope.launch {
            val peerMsgs = _messages.value[targetAddress] ?: return@launch
            val msgToRetry = peerMsgs.find { it.id == messageId && it.status == MessageStatus.FAILED } ?: return@launch

            // Set to PENDING first
            _messages.update { currentMap ->
                val updated = (currentMap[targetAddress] ?: emptyList()).map {
                    if (it.id == messageId) it.copy(status = MessageStatus.PENDING) else it
                }
                currentMap + (targetAddress to updated)
            }

            val destIp = when (val state = connectionState.value) {
                is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetAddress }
                else -> targetAddress
            }

            val packet = MeshPacket(
                id = messageId,
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.CHAT,
                payload = Json.encodeToString(msgToRetry.copy(status = MessageStatus.SENT))
            )

            val result = socketClient.sendPacket(destIp, packet)
            val finalStatus = if (result.isSuccess) MessageStatus.SENT else MessageStatus.FAILED

            _messages.update { currentMap ->
                val updated = (currentMap[targetAddress] ?: emptyList()).map {
                    if (it.id == messageId) it.copy(status = finalStatus) else it
                }
                currentMap + (targetAddress to updated)
            }
        }
    }

    fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()

    fun scanBlePeers(): Flow<PeerNode> = bleManager.scanPeers()

    fun sendChatMessage(targetAddress: String, text: String, senderName: String) {
        scope.launch {
            val destIp = when (val state = connectionState.value) {
                is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetAddress }
                else -> targetAddress
            }

            val messageId = UUID.randomUUID().toString()
            val pendingMsg = ChatMessage(
                id = messageId,
                senderId = deviceId,
                senderName = senderName,
                text = text,
                isMine = true,
                status = MessageStatus.PENDING
            )

            // Immediately add message in PENDING status to UI state
            _messages.update { currentMap ->
                val peerMsgs = currentMap[targetAddress] ?: emptyList()
                currentMap + (targetAddress to (peerMsgs + pendingMsg))
            }

            val packet = MeshPacket(
                id = messageId,
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.CHAT,
                payload = Json.encodeToString(pendingMsg.copy(status = MessageStatus.SENT))
            )

            val result = socketClient.sendPacket(destIp, packet)
            val finalStatus = if (result.isSuccess) MessageStatus.SENT else MessageStatus.FAILED

            _messages.update { currentMap ->
                val peerMsgs = currentMap[targetAddress] ?: emptyList()
                val updatedMsgs = peerMsgs.map { msg ->
                    if (msg.id == messageId) msg.copy(status = finalStatus) else msg
                }
                currentMap + (targetAddress to updatedMsgs)
            }
        }
    }

    private fun handleIncomingPacket(packet: MeshPacket) {
        when (packet.type) {
            PacketType.HANDSHAKE -> {
                try {
                    val handshake = Json.decodeFromString<HandshakePayload>(packet.payload)
                    _peerIdentities.update { it + (handshake.deviceId to handshake.deviceName) }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding handshake packet", e)
                }
            }
            PacketType.CHAT -> {
                try {
                    val chatMsg = Json.decodeFromString<ChatMessage>(packet.payload).copy(isMine = false)
                    _messages.update { currentMap ->
                        val peerMsgs = currentMap[packet.senderId] ?: emptyList()
                        currentMap + (packet.senderId to (peerMsgs + chatMsg))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding incoming chat packet", e)
                }
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
