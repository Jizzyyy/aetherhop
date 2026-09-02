package com.kadhafi.aetherhop.data.repository

import android.content.Context
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import com.kadhafi.aetherhop.core.service.AetherHopNotificationManager
import com.kadhafi.aetherhop.core.util.CryptoManager
import com.kadhafi.aetherhop.core.util.DeviceIdentity
import com.kadhafi.aetherhop.core.util.EncryptedEnvelope
import com.kadhafi.aetherhop.core.util.KeyExchangeManager
import com.kadhafi.aetherhop.data.ble.BleManager
import com.kadhafi.aetherhop.data.local.AppDatabase
import com.kadhafi.aetherhop.data.local.entity.ConversationEntity
import com.kadhafi.aetherhop.data.local.entity.MessageEntity
import com.kadhafi.aetherhop.data.mesh.RoutingTable
import com.kadhafi.aetherhop.data.mesh.TelemetryCollector
import com.kadhafi.aetherhop.data.network.P2pSocketClient
import com.kadhafi.aetherhop.data.network.P2pSocketServer
import com.kadhafi.aetherhop.data.p2p.WifiP2pDirectManager
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.FileChunkPayload
import com.kadhafi.aetherhop.domain.model.HandshakePayload
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.MessageStatus
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.domain.model.SosPayload
import com.kadhafi.aetherhop.domain.model.VoiceNotePayload
import com.kadhafi.aetherhop.domain.repository.P2pRepository
import android.util.Base64
import java.io.InputStream
import java.security.MessageDigest
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
    private val notificationManager = AetherHopNotificationManager(appContext)
    private val routingTable = RoutingTable()
    private val db = AppDatabase.getDatabase(appContext)
    private val messageDao = db.messageDao()
    private val peerDao = db.peerDao()
    private val conversationDao = db.conversationDao()

    override val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    override val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    override val connectionState: StateFlow<P2pConnectionState> = wifiP2pManager.connectionState

    private val _wifiPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    override val wifiPeers: StateFlow<List<WifiP2pDevice>> = _wifiPeers.asStateFlow()

    private val deviceId = DeviceIdentity.getDeviceId(appContext)
    private val deviceName = DeviceIdentity.getDeviceName(appContext)
    private val myKeyPair = KeyExchangeManager.generateKeyPair()
    private val sessionKeys = java.util.concurrent.ConcurrentHashMap<String, javax.crypto.SecretKey>()

    private val _peerIdentities = MutableStateFlow<Map<String, String>>(emptyMap())
    override val peerIdentities: StateFlow<Map<String, String>> = _peerIdentities.asStateFlow()

    private val _activeSosAlerts = MutableStateFlow<List<SosPayload>>(emptyList())
    override val activeSosAlerts: StateFlow<List<SosPayload>> = _activeSosAlerts.asStateFlow()

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
    private val _processedPacketIds = java.util.Collections.newSetFromMap(
        object : java.util.LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 1000
            }
        }
    )

    private fun sendHandshake(targetIp: String) {
        if (_handshookPeers.contains(targetIp)) return
        _handshookPeers.add(targetIp)
        scope.launch {
            val pubKeyBase64 = KeyExchangeManager.publicKeyToBase64(myKeyPair.public)
            val payload = Json.encodeToString(HandshakePayload(deviceId, deviceName, pubKeyBase64))
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

    override fun broadcastSos(emergencyNote: String, latitude: Double?, longitude: Double?) {
        scope.launch {
            val sosPayload = SosPayload(
                senderId = deviceId,
                senderName = deviceName,
                emergencyNote = emergencyNote,
                latitude = latitude,
                longitude = longitude
            )
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = deviceId,
                targetId = "BROADCAST",
                type = PacketType.SOS_ALERT,
                payload = Json.encodeToString(sosPayload),
                ttl = 10
            )
            _activeSosAlerts.update { it + sosPayload }
            // Flood broadcast to all known active WiFi Direct / next hop IPs
            val targets = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
            targets.forEach { targetIp ->
                if (targetIp.isNotBlank()) {
                    launch { socketClient.sendPacket(targetIp, packet) }
                }
            }
        }
    }

    override fun dismissSosAlert(senderId: String) {
        _activeSosAlerts.update { list -> list.filter { it.senderId != senderId } }
    }

    override fun sendVoiceNote(targetAddress: String, audioBase64: String, durationMs: Long) {
        scope.launch {
            val destIp = when (val state = connectionState.value) {
                is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetAddress }
                else -> targetAddress
            }

            val voiceId = UUID.randomUUID().toString()
            val seconds = (durationMs / 1000).coerceAtLeast(1)
            val displayText = "[Pesan Suara] $seconds detik"

            val pendingMsg = ChatMessage(
                id = voiceId,
                senderId = deviceId,
                senderName = deviceName,
                text = displayText,
                isMine = true,
                status = MessageStatus.PENDING
            )

            messageDao.insertMessage(
                MessageEntity(
                    id = voiceId,
                    peerId = targetAddress,
                    senderId = deviceId,
                    senderName = deviceName,
                    text = displayText,
                    timestamp = pendingMsg.timestamp,
                    isMine = true,
                    status = MessageStatus.PENDING
                )
            )

            val payload = Json.encodeToString(VoiceNotePayload(voiceId, durationMs, audioBase64))
            val packet = MeshPacket(
                id = voiceId,
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.VOICE_NOTE,
                payload = payload
            )

            var result = socketClient.sendPacket(destIp, packet)
            if (result.isFailure) {
                kotlinx.coroutines.delay(300)
                result = socketClient.sendPacket(destIp, packet)
            }
            val finalStatus = if (result.isSuccess) MessageStatus.SENT else MessageStatus.FAILED
            messageDao.updateMessageStatus(voiceId, finalStatus.name)
        }
    }
        DeviceIdentity.setDeviceName(appContext, name)
    }

    override fun getDeviceId(): String = deviceId

    override fun sendFileAttachment(targetAddress: String, uri: Uri, fileName: String) {
        scope.launch {
            val destIp = when (val state = connectionState.value) {
                is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetAddress }
                else -> targetAddress
            }

            try {
                val inputStream: InputStream = appContext.contentResolver.openInputStream(uri) ?: return@launch
                val fileBytes = inputStream.readBytes()
                inputStream.close()

                val fileId = UUID.randomUUID().toString()
                val chunkSize = 32 * 1024 // 32 KB chunks
                val totalChunks = (fileBytes.size + chunkSize - 1) / chunkSize

                val messageId = UUID.randomUUID().toString()
                val chatMsgText = "[Berkas] $fileName (${fileBytes.size / 1024} KB)"
                val pendingMsg = ChatMessage(
                    id = messageId,
                    senderId = deviceId,
                    senderName = deviceName,
                    text = chatMsgText,
                    isMine = true,
                    status = MessageStatus.PENDING
                )
                messageDao.insertMessage(
                    MessageEntity(
                        id = messageId,
                        peerId = targetAddress,
                        senderId = deviceId,
                        senderName = deviceName,
                        text = chatMsgText,
                        timestamp = pendingMsg.timestamp,
                        isMine = true,
                        status = MessageStatus.PENDING
                    )
                )

                var allSent = true
                val digest = MessageDigest.getInstance("SHA-256")
                val overallChecksum = Base64.encodeToString(digest.digest(fileBytes), Base64.NO_WRAP)

                for (index in 0 until totalChunks) {
                    val start = index * chunkSize
                    val end = minOf(start + chunkSize, fileBytes.size)
                    val chunkBytes = fileBytes.copyOfRange(start, end)
                    val chunkBase64 = Base64.encodeToString(chunkBytes, Base64.NO_WRAP)

                    val chunkPayload = FileChunkPayload(
                        fileId = fileId,
                        fileName = fileName,
                        chunkIndex = index,
                        totalChunks = totalChunks,
                        dataBase64 = chunkBase64,
                        checksum = overallChecksum
                    )

                    val packet = MeshPacket(
                        id = UUID.randomUUID().toString(),
                        senderId = deviceId,
                        targetId = targetAddress,
                        type = PacketType.FILE_CHUNK,
                        payload = Json.encodeToString(chunkPayload)
                    )

                    val res = socketClient.sendPacket(destIp, packet)
                    if (res.isFailure) {
                        allSent = false
                        break
                    }
                    kotlinx.coroutines.delay(50) // Throttling 50ms between chunks
                }

                val finalStatus = if (allSent) MessageStatus.SENT else MessageStatus.FAILED
                messageDao.updateMessageStatus(messageId, finalStatus.name)
            } catch (e: Exception) {
                android.util.Log.e("P2pRepositoryImpl", "Error sending file chunk", e)
            }
        }
    }

    override fun disconnectPeer() {
        wifiP2pManager.disconnect()
    }

    override fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()

    override fun observeBluetoothState(): Flow<Boolean> = bleManager.observeBluetoothState()

    override fun scanBlePeers(): Flow<PeerNode> = bleManager.scanPeers()

    override fun sendChannelBroadcast(channelId: String, text: String) {
        scope.launch {
            val messageId = UUID.randomUUID().toString()
            val chatMsg = ChatMessage(
                id = messageId,
                senderId = deviceId,
                senderName = deviceName,
                text = text,
                isMine = true,
                status = MessageStatus.SENT
            )

            messageDao.insertMessage(
                MessageEntity(
                    id = messageId,
                    peerId = channelId,
                    senderId = deviceId,
                    senderName = deviceName,
                    text = text,
                    timestamp = chatMsg.timestamp,
                    isMine = true,
                    status = MessageStatus.SENT
                )
            )

            conversationDao.insertOrUpdateConversation(
                ConversationEntity(
                    conversationId = channelId,
                    title = channelId,
                    isChannel = true,
                    lastMessageText = text,
                    lastMessageTimestamp = chatMsg.timestamp,
                    unreadCount = 0
                )
            )

            val packet = MeshPacket(
                id = messageId,
                senderId = deviceId,
                targetId = channelId,
                type = PacketType.CHAT,
                payload = Json.encodeToString(chatMsg)
            )

            val targets = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
            targets.forEach { targetIp ->
                if (targetIp.isNotBlank()) {
                    launch { socketClient.sendPacket(targetIp, packet) }
                }
            }
        }
    }

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

            conversationDao.insertOrUpdateConversation(
                ConversationEntity(
                    conversationId = targetAddress,
                    title = peerIdentities.value[targetAddress] ?: targetAddress,
                    isChannel = false,
                    lastMessageText = text,
                    lastMessageTimestamp = pendingMsg.timestamp,
                    unreadCount = 0
                )
            )

            val rawMsgJson = Json.encodeToString(pendingMsg.copy(status = MessageStatus.SENT))
            val finalPayload = sessionKeys[targetAddress]?.let { key ->
                try {
                    val envelope = CryptoManager.encrypt(rawMsgJson, key)
                    Json.encodeToString(envelope)
                } catch (_: Exception) { rawMsgJson }
            } ?: rawMsgJson

            val packet = MeshPacket(
                id = messageId,
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.CHAT,
                payload = finalPayload
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

    private val _incomingFileBuffers = java.util.concurrent.ConcurrentHashMap<String, MutableMap<Int, FileChunkPayload>>()

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

        when (packet.type) {
            PacketType.HANDSHAKE -> {
                try {
                    val handshake = Json.decodeFromString<HandshakePayload>(packet.payload)
                    _peerIdentities.update { it + (handshake.deviceId to handshake.deviceName) }
                    if (handshake.publicKeyBase64.isNotBlank()) {
                        val peerPubKey = KeyExchangeManager.base64ToPublicKey(handshake.publicKeyBase64)
                        val sessionKey = KeyExchangeManager.generateSharedSecret(myKeyPair, peerPubKey)
                        sessionKeys[handshake.deviceId] = sessionKey
                    }
                    // Bidirectional handshake: reply with our identity if not already sent
                    sendHandshake(packet.senderId)
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding handshake packet", e)
                }
            }
            PacketType.CHAT -> {
                try {
                    val rawPayload = sessionKeys[packet.senderId]?.let { key ->
                        try {
                            val envelope = Json.decodeFromString<EncryptedEnvelope>(packet.payload)
                            CryptoManager.decrypt(envelope, key)
                        } catch (_: Exception) { packet.payload }
                    } ?: packet.payload

                    val chatMsg = Json.decodeFromString<ChatMessage>(rawPayload).copy(isMine = false)
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
                    notificationManager.showMessageNotification(chatMsg.senderName, chatMsg.text)
                    // Send delivery ACK receipt back to original sender
                    scope.launch {
                        val ackPacket = MeshPacket(
                            id = UUID.randomUUID().toString(),
                            senderId = deviceId,
                            targetId = packet.senderId,
                            type = PacketType.ACK,
                            payload = packet.id
                        )
                        val destIp = routingTable.getNextHopIp(packet.senderId) ?: packet.senderId
                        socketClient.sendPacket(destIp, ackPacket)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding incoming chat packet", e)
                }
            }
            PacketType.PING -> {
                scope.launch {
                    val pongPacket = MeshPacket(
                        id = UUID.randomUUID().toString(),
                        senderId = deviceId,
                        targetId = packet.senderId,
                        type = PacketType.PONG,
                        payload = packet.timestamp.toString()
                    )
                    val destIp = routingTable.getNextHopIp(packet.senderId) ?: packet.senderId
                    socketClient.sendPacket(destIp, pongPacket)
                }
            }
            PacketType.PONG -> {
                try {
                    val pingTimestamp = packet.payload.toLongOrNull() ?: 0L
                    if (pingTimestamp > 0) {
                        val rtt = (System.currentTimeMillis() - pingTimestamp).coerceAtLeast(1)
                        TelemetryCollector.recordRtt(packet.senderId, rtt)
                    }
                } catch (_: Exception) {}
            }
            PacketType.ACK -> {
                scope.launch {
                    val originalMessageId = packet.payload
                    messageDao.updateMessageStatus(originalMessageId, MessageStatus.SENT.name)
                }
            }
            PacketType.FILE_CHUNK -> {
                try {
                    val chunk = Json.decodeFromString<FileChunkPayload>(packet.payload)
                    val buffer = _incomingFileBuffers.getOrPut(chunk.fileId) { java.util.concurrent.ConcurrentHashMap() }
                    buffer[chunk.chunkIndex] = chunk

                    if (buffer.size == chunk.totalChunks) {
                        val fileId = chunk.fileId
                        val fileName = chunk.fileName
                        val expectedChecksum = chunk.checksum

                        scope.launch {
                            val baos = java.io.ByteArrayOutputStream()
                            for (i in 0 until chunk.totalChunks) {
                                val c = buffer[i]
                                if (c != null) {
                                    val bytes = Base64.decode(c.dataBase64, Base64.NO_WRAP)
                                    baos.write(bytes)
                                }
                            }
                            val fullBytes = baos.toByteArray()
                            val digest = MessageDigest.getInstance("SHA-256")
                            val actualChecksum = Base64.encodeToString(digest.digest(fullBytes), Base64.NO_WRAP)

                            if (actualChecksum == expectedChecksum || expectedChecksum.isBlank()) {
                                val downloadsDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                val outputFile = java.io.File(downloadsDir, fileName)
                                outputFile.writeBytes(fullBytes)

                                val senderName = peerIdentities.value[packet.senderId] ?: "Peer"
                                val chatMsgText = "[Berkas Diterima] $fileName (${fullBytes.size / 1024} KB)"
                                val messageId = UUID.randomUUID().toString()

                                messageDao.insertMessage(
                                    MessageEntity(
                                        id = messageId,
                                        peerId = packet.senderId,
                                        senderId = packet.senderId,
                                        senderName = senderName,
                                        text = chatMsgText,
                                        timestamp = System.currentTimeMillis(),
                                        isMine = false,
                                        status = MessageStatus.SENT
                                    )
                                )
                            }
                            _incomingFileBuffers.remove(fileId)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error receiving file chunk", e)
                }
            }
            PacketType.VOICE_NOTE -> {
                try {
                    val voice = Json.decodeFromString<VoiceNotePayload>(packet.payload)
                    val senderName = peerIdentities.value[packet.senderId] ?: "Peer"
                    val seconds = (voice.durationMs / 1000).coerceAtLeast(1)
                    val chatMsgText = "[Pesan Suara] $seconds detik"

                    scope.launch {
                        messageDao.insertMessage(
                            MessageEntity(
                                id = voice.voiceId,
                                peerId = packet.senderId,
                                senderId = packet.senderId,
                                senderName = senderName,
                                text = chatMsgText,
                                timestamp = System.currentTimeMillis(),
                                isMine = false,
                                status = MessageStatus.SENT
                            )
                        )
                    }
                    notificationManager.showMessageNotification(senderName, chatMsgText)
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding VOICE_NOTE packet", e)
                }
            }
            PacketType.SOS_ALERT -> {
                try {
                    val sos = Json.decodeFromString<SosPayload>(packet.payload)
                    _activeSosAlerts.update { current ->
                        if (current.none { it.senderId == sos.senderId && it.timestamp == sos.timestamp }) {
                            current + sos
                        } else current
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding SOS packet", e)
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
