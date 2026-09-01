package com.kadhafi.aetherhop.data.repository

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import com.kadhafi.aetherhop.core.util.DeviceIdentity
import com.kadhafi.aetherhop.data.ble.BleManager
import com.kadhafi.aetherhop.data.local.AppDatabase
import com.kadhafi.aetherhop.data.mesh.RoutingTable
import com.kadhafi.aetherhop.data.local.entity.MessageEntity
import com.kadhafi.aetherhop.data.local.entity.PeerEntity
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
import com.kadhafi.aetherhop.domain.repository.P2pRepository
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

class P2pRepositoryImpl(context: Context) : P2pRepository {
    private val appContext = context.applicationContext
    private val bleManager = BleManager(appContext)
    private val wifiP2pManager = WifiP2pDirectManager(appContext)
    private val socketServer = P2pSocketServer()
    private val socketClient = P2pSocketClient()
    private val routingTable = RoutingTable()
    private val db = AppDatabase.getDatabase(appContext)
    private val messageDao = db.messageDao()
    private val peerDao = db.peerDao()
    
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    override val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    override val connectionState: StateFlow<P2pConnectionState> = wifiP2pManager.connectionState

    private val _wifiPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    override val wifiPeers: StateFlow<List<WifiP2pDevice>> = _wifiPeers.asStateFlow()

    private val deviceId = DeviceIdentity.getDeviceId(appContext)
    private val deviceName = DeviceIdentity.getDeviceName(appContext)

    private val _peerIdentities = MutableStateFlow<Map<String, String>>(emptyMap())
    override val peerIdentities: StateFlow<Map<String, String>> = _peerIdentities.asStateFlow()

    init {
        scope.launch {
            messageDao.getAllMessages().collect { entities ->
                val map = entities.groupBy { it.peerId }.mapValues { entry ->
                    entry.value.map { entity ->
                        ChatMessage(
                            id = entity.id,
                            senderId = entity.senderId,
                            senderName = entity.senderName,
                            text = entity.text,
                            timestamp = entity.timestamp,
                            isMine = entity.isMine,
                            status = entity.status
                        )
                    }
                }
                _messages.value = map
            }
        }
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

    private val _handshookPeers = mutableSetOf<String>()
    private val _processedPacketIds = mutableSetOf<String>()

    private fun sendHandshake(targetIp: String) {
        if (_handshookPeers.contains(targetIp)) return
        _handshookPeers.add(targetIp)
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

    override fun connectToPeer(peer: PeerNode): Boolean {
        val targetDevice = _wifiPeers.value.find { it.deviceAddress == peer.address || it.deviceName == peer.name }
        if (targetDevice != null) {
            wifiP2pManager.connectToDevice(
                device = targetDevice,
                onSuccess = {},
                onError = {}
            )
            return true
        }
        return false
    }

    override fun retrySendMessage(messageId: String, targetAddress: String) {
        scope.launch {
            val peerMsgs = _messages.value[targetAddress] ?: return@launch
            val msgToRetry = peerMsgs.find { it.id == messageId && it.status == MessageStatus.FAILED } ?: return@launch

            messageDao.updateMessageStatus(messageId, MessageStatus.PENDING.name)

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
            messageDao.updateMessageStatus(messageId, finalStatus.name)
        }
    }

    override fun disconnectPeer() {
        wifiP2pManager.disconnect()
    }

    override fun setDeviceName(name: String) {
        DeviceIdentity.setDeviceName(appContext, name)
    }

    override fun getDeviceId(): String = deviceId

    override fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()

    override fun observeBluetoothState(): Flow<Boolean> = bleManager.observeBluetoothState()

    override fun scanBlePeers(): Flow<PeerNode> = bleManager.scanPeers()

    override fun sendChatMessage(targetAddress: String, text: String, senderName: String) {
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

            val entity = MessageEntity(
                id = messageId,
                peerId = targetAddress,
                senderId = deviceId,
                senderName = senderName,
                text = text,
                timestamp = pendingMsg.timestamp,
                isMine = true,
                status = MessageStatus.PENDING
            )
            messageDao.insertMessage(entity)

            val packet = MeshPacket(
                id = messageId,
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.CHAT,
                payload = Json.encodeToString(pendingMsg.copy(status = MessageStatus.SENT))
            )

            var result = socketClient.sendPacket(destIp, packet)
            if (result.isFailure) {
                // Immediate 1x auto-retry for transient socket drop
                kotlinx.coroutines.delay(300)
                result = socketClient.sendPacket(destIp, packet)
            }
            val finalStatus = if (result.isSuccess) MessageStatus.SENT else MessageStatus.FAILED
            messageDao.updateMessageStatus(messageId, finalStatus.name)
        }
    }

    private fun handleIncomingPacket(packet: MeshPacket) {
        if (_processedPacketIds.contains(packet.id)) return
        _processedPacketIds.add(packet.id)

        // Multi-hop Mesh Routing: Forward packet if this node is not the final target
        if (packet.targetId.isNotBlank() && packet.targetId != deviceId && packet.targetId != "BROADCAST") {
            if (packet.ttl > 1) {
                val nextHopIp = routingTable.getNextHopIp(packet.targetId) ?: packet.targetId
                scope.launch {
                    val forwardedPacket = packet.copy(ttl = packet.ttl - 1)
                    socketClient.sendPacket(nextHopIp, forwardedPacket)
                }
            }
            return
        }
            PacketType.HANDSHAKE -> {
                try {
                    val handshake = Json.decodeFromString<HandshakePayload>(packet.payload)
                    _peerIdentities.update { it + (handshake.deviceId to handshake.deviceName) }
                    // Bidirectional handshake: reply with our identity if not already sent
                    sendHandshake(packet.senderId)
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding handshake packet", e)
                }
            }
            PacketType.CHAT -> {
                try {
                    val chatMsg = Json.decodeFromString<ChatMessage>(packet.payload).copy(isMine = false)
                    scope.launch {
                        messageDao.insertMessage(
                            MessageEntity(
                                id = chatMsg.id,
                                peerId = packet.senderId,
                                senderId = chatMsg.senderId,
                                senderName = chatMsg.senderName,
                                text = chatMsg.text,
                                timestamp = chatMsg.timestamp,
                                isMine = false,
                                status = chatMsg.status
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding incoming chat packet", e)
                }
            }
            else -> {}
        }
    }

    override fun stopServices() {
        job.cancel()
        socketServer.stopServer()
        wifiP2pManager.disconnect()
    }
}
