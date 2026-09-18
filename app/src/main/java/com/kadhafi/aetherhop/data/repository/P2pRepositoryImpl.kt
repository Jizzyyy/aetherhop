package com.kadhafi.aetherhop.data.repository

import android.content.Context
import android.location.Location
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import com.kadhafi.aetherhop.core.audio.PttStreamManager
import com.kadhafi.aetherhop.core.audio.PttUdpSocketManager
import com.kadhafi.aetherhop.core.location.RealLocationManager
import com.kadhafi.aetherhop.core.power.PowerOptimizationManager
import com.kadhafi.aetherhop.core.power.PowerProfile
import com.kadhafi.aetherhop.core.power.ThermalThrottleManager
import com.kadhafi.aetherhop.core.service.AetherHopNotificationManager
import com.kadhafi.aetherhop.core.util.CryptoManager
import com.kadhafi.aetherhop.core.util.DeviceIdentity
import com.kadhafi.aetherhop.core.util.EncryptedEnvelope
import com.kadhafi.aetherhop.core.util.KeyExchangeManager
import com.kadhafi.aetherhop.core.util.PanicWipeManager
import com.kadhafi.aetherhop.core.util.SecureMemoryZeroizer
import com.kadhafi.aetherhop.core.util.SymmetricKeyRatchet
import com.kadhafi.aetherhop.data.ble.BleManager
import com.kadhafi.aetherhop.data.dtn.StoreAndForwardBuffer
import com.kadhafi.aetherhop.data.local.AppDatabase
import com.kadhafi.aetherhop.data.local.entity.ChannelMessageEntity
import com.kadhafi.aetherhop.data.local.entity.ConversationEntity
import com.kadhafi.aetherhop.data.local.entity.MessageEntity
import com.kadhafi.aetherhop.data.local.entity.PeerEntity
import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import com.kadhafi.aetherhop.data.map.OfflineTileCacheManager
import com.kadhafi.aetherhop.data.map.TileCacheStats
import com.kadhafi.aetherhop.data.mesh.EpidemicGossipManager
import com.kadhafi.aetherhop.data.mesh.MeshTransportRouter
import com.kadhafi.aetherhop.data.mesh.RouteEntry
import com.kadhafi.aetherhop.data.mesh.RoutingTable
import com.kadhafi.aetherhop.data.mesh.TelemetryCollector
import com.kadhafi.aetherhop.data.mesh.TransportLinkType
import com.kadhafi.aetherhop.data.nan.AetherWifiAwareManager
import com.kadhafi.aetherhop.data.nan.WifiAwareState
import com.kadhafi.aetherhop.data.network.P2pSocketClient
import com.kadhafi.aetherhop.data.network.P2pSocketServer
import com.kadhafi.aetherhop.data.network.withDecompression
import com.kadhafi.aetherhop.data.p2p.WifiP2pDirectManager
import com.kadhafi.aetherhop.domain.model.AudioFramePayload
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.DeliveryReceiptPayload
import com.kadhafi.aetherhop.domain.model.FileChunkPayload
import com.kadhafi.aetherhop.domain.model.GossipDigestPayload
import com.kadhafi.aetherhop.domain.model.HandshakePayload
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.MessageStatus
import com.kadhafi.aetherhop.domain.model.P2pConnectionState
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.domain.model.PeerPairingPayload
import com.kadhafi.aetherhop.domain.model.ReactionPayload
import com.kadhafi.aetherhop.domain.model.RouteReplyPayload
import com.kadhafi.aetherhop.domain.model.RouteRequestPayload
import com.kadhafi.aetherhop.domain.model.SosPayload
import com.kadhafi.aetherhop.domain.model.TelemetryBroadcastPayload
import com.kadhafi.aetherhop.domain.model.TransportMedium
import com.kadhafi.aetherhop.domain.model.VoiceNotePayload
import com.kadhafi.aetherhop.domain.model.WaypointSyncPayload
import com.kadhafi.aetherhop.domain.repository.P2pRepository
import android.util.Base64
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    companion object {
        @Volatile
        private var instance: P2pRepository? = null

        fun getInstance(context: Context): P2pRepository {
            return instance ?: synchronized(this) {
                instance ?: P2pRepositoryImpl(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val bleManager = BleManager(appContext)
    private val wifiP2pManager = WifiP2pDirectManager(appContext)
    private val wifiAwareManager = AetherWifiAwareManager(appContext)
    private val transportRouter = MeshTransportRouter()
    private val socketServer = P2pSocketServer()
    private val socketClient = P2pSocketClient()
    private val pttStreamManager = PttStreamManager(appContext)
    private val pttUdpSocketManager = PttUdpSocketManager()
    private val notificationManager = AetherHopNotificationManager(appContext)
    private val realLocationManager = RealLocationManager(appContext)
    private val routingTable = RoutingTable()
    private val db = AppDatabase.getDatabase(appContext)
    private val messageDao = db.messageDao()
    private val peerDao = db.peerDao()
    private val conversationDao = db.conversationDao()
    private val waypointDao = db.tacticalWaypointDao()
    private val outboxDao = db.outboxDao()
    private val channelDao = db.channelDao()
    private val storeAndForwardBuffer = StoreAndForwardBuffer(outboxDao)
    private val powerManager = PowerOptimizationManager(appContext)
    private val thermalThrottleManager = ThermalThrottleManager(appContext)
    private val tileCacheManager = OfflineTileCacheManager(appContext)
    private val gossipManager by lazy { EpidemicGossipManager(deviceId) }

    private val _isGossipSyncing = MutableStateFlow(false)
    override val isGossipSyncing: StateFlow<Boolean> = _isGossipSyncing.asStateFlow()

    private val _tileCacheStats = MutableStateFlow(tileCacheManager.getCacheStats())
    override val tileCacheStats: StateFlow<TileCacheStats> = _tileCacheStats.asStateFlow()

    private val _blockedPeers = MutableStateFlow<Set<String>>(emptySet())
    override val blockedPeers: StateFlow<Set<String>> = _blockedPeers.asStateFlow()

    private val _peerAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    override val peerAliases: StateFlow<Map<String, String>> = _peerAliases.asStateFlow()

    override val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()
    override val waypoints: Flow<List<TacticalWaypointEntity>> = waypointDao.getAllWaypoints()
    override val liveLocation: Flow<Location> = realLocationManager.observeLocation()
    override val wifiAwareState: StateFlow<WifiAwareState> = wifiAwareManager.awareState
    override val activeRoutes: StateFlow<List<RouteEntry>> = routingTable.routesFlow
    override fun getChannelMessages(channelId: String): Flow<List<ChannelMessageEntity>> = channelDao.getMessagesForChannel(channelId)
    @Volatile private var lastKnownGpsLocation: Location? = null
    
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
    private val sendingRatchets = java.util.concurrent.ConcurrentHashMap<String, SymmetricKeyRatchet>()
    private val receivingRatchets = java.util.concurrent.ConcurrentHashMap<String, SymmetricKeyRatchet>()

    private val _peerIdentities = MutableStateFlow<Map<String, String>>(emptyMap())
    override val peerIdentities: StateFlow<Map<String, String>> = _peerIdentities.asStateFlow()

    private val _activeSosAlerts = MutableStateFlow<List<SosPayload>>(emptyList())
    override val activeSosAlerts: StateFlow<List<SosPayload>> = _activeSosAlerts.asStateFlow()

    private val _peerTelemetry = MutableStateFlow<Map<String, TelemetryBroadcastPayload>>(emptyMap())
    override val peerTelemetry: StateFlow<Map<String, TelemetryBroadcastPayload>> = _peerTelemetry.asStateFlow()

    init {
        if (wifiAwareManager.isAwareSupported()) {
            wifiAwareManager.attachSession()
        }
        scope.launch {
            bleManager.observeBluetoothState().collect { enabled ->
                if (enabled) {
                    bleManager.startAdvertising()
                } else {
                    bleManager.stopAdvertising()
                }
            }
        }
        scope.launch {
            powerManager.observePowerState().collect { pState ->
                if (bleManager.isBluetoothEnabled()) {
                    bleManager.startAdvertising(pState.recommendedProfile)
                }
            }
        }
        scope.launch {
            thermalThrottleManager.observeThermalState().collect { tState ->
                if (tState.isThrottled && bleManager.isBluetoothEnabled()) {
                    bleManager.startAdvertising(PowerProfile.SAVER_LOW_POWER)
                }
            }
        }
        scope.launch {
            peerDao.getAllPeers().collect { peers ->
                _blockedPeers.value = peers.filter { it.isBlocked }.map { it.id }.toSet()
                _peerAliases.value = peers.filter { it.customAlias.isNotBlank() }.associate { it.id to it.customAlias }
            }
        }
        scope.launch {
            liveLocation.collect { loc ->
                lastKnownGpsLocation = loc
            }
        }
        scope.launch {
            messageDao.getAllMessages().collect { entities ->
                val map = entities.groupBy { it.peerId }.mapValues { entry ->
                    entry.value.map { entity ->
                        val reactionsMap = if (entity.reactionsRaw.isNotBlank()) {
                            entity.reactionsRaw.split(",").mapNotNull { entry ->
                                val parts = entry.split(":")
                                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 1) else null
                            }.toMap()
                        } else emptyMap()

                        ChatMessage(
                            id = entity.id,
                            senderId = entity.senderId,
                            senderName = entity.senderName,
                            text = entity.text,
                            timestamp = entity.timestamp,
                            isMine = entity.isMine,
                            status = entity.status,
                            mediaUri = entity.mediaUri,
                            mediaDurationMs = entity.mediaDurationMs,
                            replyToId = entity.replyToId,
                            replySnippet = entity.replySnippet,
                            reactions = reactionsMap
                        )
                    }
                }
                _messages.value = map
            }
        }
        scope.launch {
            socketServer.startServer().collect { incoming ->
                handleIncomingPacket(incoming.packet, incoming.senderIp)
            }
        }
        // Real-time UDP walkie-talkie datagram listener on port 8889
        scope.launch {
            pttUdpSocketManager.startListening().collect { datagram ->
                val base64 = Base64.encodeToString(datagram.audioData, Base64.NO_WRAP)
                pttStreamManager.playPttFrame(base64, datagram.sequenceNumber)
            }
        }
        scope.launch {
            wifiP2pManager.discoverPeers().collect { devices ->
                _wifiPeers.value = devices
                devices.forEach { dev ->
                    dispatchOutboxForPeer(dev.deviceAddress)
                }
            }
        }
        scope.launch {
            connectionState.collect { state ->
                if (state is P2pConnectionState.Connected && state.groupOwnerAddress.isNotBlank()) {
                    sendHandshake(state.groupOwnerAddress)
                    dispatchOutboxForPeer(state.groupOwnerAddress)
                }
            }
        }
        // Periodic routing table pruning for stale mesh routes (> 60s) and expired DTN outbox bundles
        scope.launch {
            while (isActive) {
                delay(30000)
                routingTable.removeStaleRoutes(maxAgeMs = 60000)
                storeAndForwardBuffer.purgeExpired()
            }
        }
    }

    private val _handshookPeers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val _packetLock = Any()
    private val _processedPacketIds = object : java.util.LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 1000
        }
    }

    private fun resolveActiveTransportMedium(targetAddress: String): TransportMedium {
        val optimalLink = transportRouter.resolveOptimalLink(targetAddress)
        return when (optimalLink?.linkType) {
            TransportLinkType.WIFI_AWARE_NAN -> TransportMedium.WIFI_AWARE
            TransportLinkType.BLE_GATT -> TransportMedium.BLE
            else -> TransportMedium.WIFI_DIRECT
        }
    }

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
                payload = payload,
                transport = resolveActiveTransportMedium(targetIp)
            )
            socketClient.sendPacket(targetIp, packet)
            sendGossipDigest(targetIp)
        }
    }

    private fun sendGossipDigest(targetAddress: String) {
        scope.launch {
            val localWps = waypointDao.getWaypointsList().map { it.id }
            val digest = gossipManager.createLocalDigest(localWps)
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.GOSSIP_DIGEST,
                payload = Json.encodeToString(digest),
                transport = resolveActiveTransportMedium(targetAddress)
            )
            val destIp = routingTable.getNextHopIp(targetAddress) ?: targetAddress
            socketClient.sendPacket(destIp, packet)
        }
    }

    private fun dispatchOutboxForPeer(targetPeerId: String) {
        if (targetPeerId.isBlank()) return
        scope.launch {
            val pending = storeAndForwardBuffer.getPendingBundles(targetPeerId)
            if (pending.isEmpty()) return@launch

            val destIp = routingTable.getNextHopIp(targetPeerId) ?: when (val state = connectionState.value) {
                is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetPeerId }
                else -> targetPeerId
            }
            if (destIp.isBlank()) return@launch

            pending.forEach { bundle ->
                val type = try {
                    PacketType.valueOf(bundle.packetType)
                } catch (_: Exception) { PacketType.CHAT }

                val packet = MeshPacket(
                    id = bundle.bundleId,
                    senderId = deviceId,
                    targetId = bundle.targetPeerId,
                    type = type,
                    payload = bundle.payload,
                    transport = resolveActiveTransportMedium(bundle.targetPeerId)
                )

                val result = socketClient.sendPacket(destIp, packet)
                if (result.isSuccess) {
                    messageDao.updateMessageStatus(bundle.bundleId, MessageStatus.SENT.name)
                    storeAndForwardBuffer.removeBundle(bundle.bundleId)
                } else {
                    storeAndForwardBuffer.recordRetry(bundle.bundleId)
                }
            }
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
                payload = Json.encodeToString(msgToRetry.copy(status = MessageStatus.SENT)),
                transport = resolveActiveTransportMedium(targetAddress)
            )

            val result = socketClient.sendPacket(destIp, packet)
            val finalStatus = if (result.isSuccess) MessageStatus.SENT else MessageStatus.FAILED
            messageDao.updateMessageStatus(messageId, finalStatus.name)
            if (result.isSuccess) {
                storeAndForwardBuffer.removeBundle(messageId)
            } else {
                storeAndForwardBuffer.recordRetry(messageId)
            }
        }
    }

    override fun sendPing(targetAddress: String) {
        scope.launch {
            val destIp = routingTable.getNextHopIp(targetAddress) ?: when (val state = connectionState.value) {
                is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetAddress }
                else -> targetAddress
            }
            if (destIp.isBlank()) return@launch

            val pingPacket = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.PING,
                payload = System.currentTimeMillis().toString(),
                transport = resolveActiveTransportMedium(targetAddress)
            )
            socketClient.sendPacket(destIp, pingPacket)
        }
    }

    override fun broadcastSos(emergencyNote: String, latitude: Double?, longitude: Double?) {
        scope.launch {
            val lat = latitude ?: lastKnownGpsLocation?.latitude
            val lon = longitude ?: lastKnownGpsLocation?.longitude
            val sosPayload = SosPayload(
                senderId = deviceId,
                senderName = deviceName,
                emergencyNote = emergencyNote,
                latitude = lat,
                longitude = lon
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

    override fun broadcastRouteRequest(targetPeerId: String) {
        scope.launch {
            val rreq = RouteRequestPayload(
                requestId = UUID.randomUUID().toString(),
                sourceId = deviceId,
                targetDestinationId = targetPeerId,
                hopCount = 0
            )
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = deviceId,
                targetId = "BROADCAST",
                type = PacketType.RREQ,
                payload = Json.encodeToString(rreq),
                ttl = 5
            )
            val targets = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
            targets.forEach { targetIp ->
                if (targetIp.isNotBlank()) {
                    launch { socketClient.sendPacket(targetIp, packet) }
                }
            }
        }
    }

    override suspend fun addWaypoint(label: String, latitude: Double, longitude: Double, type: String) {
        val waypoint = TacticalWaypointEntity(
            id = UUID.randomUUID().toString(),
            label = label,
            latitude = latitude,
            longitude = longitude,
            type = type
        )
        waypointDao.insertWaypoint(waypoint)
    }

    override suspend fun deleteWaypoint(id: String) {
        waypointDao.deleteWaypoint(id)
    }

    override suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
        messageDao.clearMessagesForPeer(conversationId)
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    override suspend fun clearChatMessages(peerId: String) {
        messageDao.clearMessagesForPeer(peerId)
    }

    override fun broadcastTelemetry(batteryPercent: Int, isCharging: Boolean) {
        scope.launch {
            val telemetry = TelemetryBroadcastPayload(
                senderId = deviceId,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                activeNeighborsCount = _wifiPeers.value.size
            )
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = deviceId,
                targetId = "BROADCAST",
                type = PacketType.TELEMETRY,
                payload = Json.encodeToString(telemetry),
                ttl = 3
            )
            val targets = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
            targets.forEach { targetIp ->
                if (targetIp.isNotBlank()) {
                    launch { socketClient.sendPacket(targetIp, packet) }
                }
            }
        }
    }

    override fun sendAudioFrame(targetAddress: String, pttSessionId: String, sequenceIndex: Long, frameBase64: String) {
        scope.launch {
            val adpcmBytes = try {
                Base64.decode(frameBase64, Base64.NO_WRAP)
            } catch (_: Exception) { ByteArray(0) }
            if (adpcmBytes.isEmpty()) return@launch

            if (targetAddress.startsWith("#") || targetAddress == "BROADCAST") {
                val targets = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
                targets.forEach { targetIp ->
                    if (targetIp.isNotBlank()) {
                        launch { pttUdpSocketManager.sendUdpAudioFrame(targetIp, pttSessionId, sequenceIndex, adpcmBytes) }
                    }
                }
            } else {
                val destIp = routingTable.getNextHopIp(targetAddress) ?: when (val state = connectionState.value) {
                    is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetAddress }
                    else -> targetAddress
                }
                pttUdpSocketManager.sendUdpAudioFrame(destIp, pttSessionId, sequenceIndex, adpcmBytes)
            }
        }
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

            val voiceFile = java.io.File(appContext.cacheDir, "sent_voice_$voiceId.m4a").apply {
                try {
                    val bytes = Base64.decode(audioBase64, Base64.NO_WRAP)
                    writeBytes(bytes)
                } catch (_: Exception) {}
            }

            messageDao.insertMessage(
                MessageEntity(
                    id = voiceId,
                    peerId = targetAddress,
                    senderId = deviceId,
                    senderName = deviceName,
                    text = displayText,
                    timestamp = pendingMsg.timestamp,
                    isMine = true,
                    status = MessageStatus.PENDING,
                    mediaUri = voiceFile.absolutePath,
                    mediaDurationMs = durationMs
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

    private val panicWipeManager = PanicWipeManager(appContext)

    override suspend fun panicWipeNode(): Boolean {
        sessionKeys.clear()
        sendingRatchets.values.forEach { it.zeroize() }
        sendingRatchets.clear()
        receivingRatchets.values.forEach { it.zeroize() }
        receivingRatchets.clear()
        _handshookPeers.clear()
        _processedPacketIds.clear()
        _peerIdentities.value = emptyMap()
        _messages.value = emptyMap()
        return panicWipeManager.wipeAllDataAndResetNode()
    }

    override fun setDeviceName(name: String) {
        DeviceIdentity.setDeviceName(appContext, name)
    }

    override fun getDeviceId(): String = deviceId

    override suspend fun importPairingPayload(payload: PeerPairingPayload): Boolean {
        if (payload.deviceId.isBlank()) return false
        val expectedFingerprint = payload.deviceId.take(16)
        val isVerified = payload.checksumFingerprint == expectedFingerprint || payload.checksumFingerprint.isNotBlank()

        peerDao.insertPeer(
            PeerEntity(
                id = payload.deviceId,
                name = payload.deviceName.ifBlank { "Trusted Peer" },
                address = payload.deviceId,
                lastSeenTimestamp = System.currentTimeMillis(),
                isTrusted = isVerified,
                fingerprint = payload.checksumFingerprint
            )
        )
        _peerIdentities.update { it + (payload.deviceId to payload.deviceName) }
        return isVerified
    }

    override fun clearTileCache() {
        tileCacheManager.clearCache()
        _tileCacheStats.value = TileCacheStats(0, 0L)
    }

    override suspend fun setPeerAlias(peerId: String, alias: String) {
        val existing = peerDao.getPeerById(peerId)
        if (existing != null) {
            peerDao.updateCustomAlias(peerId, alias)
        } else {
            peerDao.insertPeer(
                PeerEntity(
                    id = peerId,
                    name = alias,
                    address = peerId,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    customAlias = alias
                )
            )
        }
        _peerAliases.update { it + (peerId to alias) }
    }

    override suspend fun setPeerBlocked(peerId: String, blocked: Boolean) {
        val existing = peerDao.getPeerById(peerId)
        if (existing != null) {
            peerDao.updateBlockedStatus(peerId, blocked)
        } else {
            peerDao.insertPeer(
                PeerEntity(
                    id = peerId,
                    name = peerId,
                    address = peerId,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isBlocked = blocked
                )
            )
        }
        _blockedPeers.update { if (blocked) it + peerId else it - peerId }
    }

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
                        status = MessageStatus.PENDING,
                        mediaUri = uri.toString()
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

    override fun scanBlePeers(): Flow<PeerNode> = bleManager.scanPeers().also { flow ->
        scope.launch {
            flow.collect { peer ->
                TelemetryCollector.recordRssi(peer.id, peer.rssi)
            }
        }
    }

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

            channelDao.insertChannelMessage(
                ChannelMessageEntity(
                    messageId = messageId,
                    channelId = channelId,
                    senderId = deviceId,
                    senderName = deviceName,
                    text = text,
                    timestamp = chatMsg.timestamp,
                    isMine = true
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
                payload = Json.encodeToString(chatMsg),
                transport = resolveActiveTransportMedium(channelId)
            )

            val targets = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
            targets.forEach { targetIp ->
                if (targetIp.isNotBlank()) {
                    launch { socketClient.sendPacket(targetIp, packet) }
                }
            }
        }
    }

    override fun sendQuotedMessage(
        targetAddress: String,
        text: String,
        senderName: String,
        replyToId: String,
        replySnippet: String
    ) {
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
                status = MessageStatus.PENDING,
                replyToId = replyToId,
                replySnippet = replySnippet
            )

            val entity = MessageEntity(
                id = messageId,
                peerId = targetAddress,
                senderId = deviceId,
                senderName = senderName,
                text = text,
                timestamp = pendingMsg.timestamp,
                isMine = true,
                status = MessageStatus.PENDING,
                replyToId = replyToId,
                replySnippet = replySnippet
            )
            messageDao.insertMessage(entity)

            val rawMsgJson = Json.encodeToString(pendingMsg.copy(status = MessageStatus.SENT))
            val finalPayload = sendingRatchets[targetAddress]?.let { ratchet ->
                try {
                    val envelope = CryptoManager.encryptWithRatchet(rawMsgJson, ratchet)
                    Json.encodeToString(envelope)
                } catch (_: Exception) { null }
            } ?: sessionKeys[targetAddress]?.let { key ->
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
                payload = finalPayload,
                transport = resolveActiveTransportMedium(targetAddress)
            )

            var result = socketClient.sendPacket(destIp, packet)
            if (result.isFailure) {
                kotlinx.coroutines.delay(300)
                result = socketClient.sendPacket(destIp, packet)
            }
            val finalStatus = if (result.isSuccess) MessageStatus.SENT else MessageStatus.FAILED
            messageDao.updateMessageStatus(messageId, finalStatus.name)
        }
    }

    override fun sendReaction(targetAddress: String, messageId: String, emoji: String) {
        scope.launch {
            val destIp = when (val state = connectionState.value) {
                is P2pConnectionState.Connected -> state.groupOwnerAddress.ifBlank { targetAddress }
                else -> targetAddress
            }
            val payload = Json.encodeToString(ReactionPayload(messageId, emoji, deviceId))
            val packet = MeshPacket(
                id = UUID.randomUUID().toString(),
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.REACTION,
                payload = payload,
                transport = resolveActiveTransportMedium(targetAddress)
            )

            // Update local DB message reaction
            val existing = messageDao.getMessageById(messageId)
            if (existing != null) {
                val currentReactions = if (existing.reactionsRaw.isNotBlank()) {
                    existing.reactionsRaw.split(",").mapNotNull { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 1) else null
                    }.toMap().toMutableMap()
                } else mutableMapOf()

                currentReactions[emoji] = (currentReactions[emoji] ?: 0) + 1
                val newRaw = currentReactions.entries.joinToString(",") { "${it.key}:${it.value}" }
                messageDao.insertMessage(existing.copy(reactionsRaw = newRaw))
            }

            socketClient.sendPacket(destIp, packet)
        }
    }

    override fun sendChatMessage(targetAddress: String, text: String, senderName: String) {
        scope.launch {
            if (_blockedPeers.value.contains(targetAddress)) return@launch
            val resolvedHopIp = routingTable.getNextHopIp(targetAddress)
            if (resolvedHopIp == null && !targetAddress.contains(".")) {
                broadcastRouteRequest(targetAddress)
            }
            val destIp = resolvedHopIp ?: when (val state = connectionState.value) {
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
            val finalPayload = sendingRatchets[targetAddress]?.let { ratchet ->
                try {
                    val envelope = CryptoManager.encryptWithRatchet(rawMsgJson, ratchet)
                    Json.encodeToString(envelope)
                } catch (_: Exception) { null }
            } ?: sessionKeys[targetAddress]?.let { key ->
                try {
                    val envelope = CryptoManager.encrypt(rawMsgJson, key)
                    Json.encodeToString(envelope)
                } catch (_: Exception) { rawMsgJson }
            } ?: rawMsgJson

            transportRouter.registerLink(targetAddress, TransportLinkType.WIFI_DIRECT, destIp)

            var packet = MeshPacket(
                id = messageId,
                senderId = deviceId,
                targetId = targetAddress,
                type = PacketType.CHAT,
                payload = finalPayload,
                transport = TransportMedium.WIFI_DIRECT
            )

            var result = socketClient.sendPacket(destIp, packet)
            if (result.isFailure) {
                // Check fallback to Wi-Fi Aware secondary link
                val awareLink = transportRouter.resolveOptimalLink("${targetAddress}_aware")
                if (awareLink != null && awareLink.ipAddress.isNotBlank()) {
                    val fallbackPacket = packet.copy(transport = TransportMedium.WIFI_AWARE)
                    result = socketClient.sendPacket(awareLink.ipAddress, fallbackPacket)
                } else {
                    // Immediate 1x auto-retry for transient socket drop
                    kotlinx.coroutines.delay(300)
                    result = socketClient.sendPacket(destIp, packet)
                }
            }
            val finalStatus = if (result.isSuccess) MessageStatus.SENT else MessageStatus.FAILED
            messageDao.updateMessageStatus(messageId, finalStatus.name)
            if (result.isFailure) {
                storeAndForwardBuffer.enqueueBundle(
                    bundleId = messageId,
                    targetPeerId = targetAddress,
                    packetType = PacketType.CHAT,
                    payload = finalPayload
                )
            }
        }
    }

    private val _incomingFileBuffers = java.util.concurrent.ConcurrentHashMap<String, MutableMap<Int, FileChunkPayload>>()

    private fun handleIncomingPacket(rawPacket: MeshPacket, senderIp: String = "") {
        synchronized(_packetLock) {
            if (_processedPacketIds.containsKey(rawPacket.id)) return
            _processedPacketIds[rawPacket.id] = true
        }

        // Rogue Node Filter: Drop all packets originating from or targeted to blocked nodes
        if (_blockedPeers.value.contains(rawPacket.senderId) || _blockedPeers.value.contains(rawPacket.targetId)) {
            return
        }

        // Dynamic Route Learning: Record next-hop route to packet sender
        if (rawPacket.senderId.isNotBlank() && senderIp.isNotBlank() && rawPacket.senderId != deviceId) {
            val hopCount = maxOf(1, 5 - rawPacket.ttl + 1)
            routingTable.updateRoute(rawPacket.senderId, senderIp, hopCount)
            dispatchOutboxForPeer(rawPacket.senderId)
        }

        // Multi-hop Mesh Routing: Forward packet if this node is not the final target (and not broadcast or channel)
        if (rawPacket.targetId.isNotBlank() && rawPacket.targetId != deviceId && rawPacket.targetId != "BROADCAST" && !rawPacket.targetId.startsWith("#")) {
            if (rawPacket.ttl > 1) {
                val nextHopIp = routingTable.getNextHopIp(rawPacket.targetId) ?: rawPacket.targetId
                scope.launch {
                    val forwardedPacket = rawPacket.copy(ttl = rawPacket.ttl - 1)
                    socketClient.sendPacket(nextHopIp, forwardedPacket)
                }
            }
            return
        }

        val packet = rawPacket.withDecompression()

        when (packet.type) {
            PacketType.HANDSHAKE -> {
                try {
                    val handshake = Json.decodeFromString<HandshakePayload>(packet.payload)
                    _peerIdentities.update { it + (handshake.deviceId to handshake.deviceName) }
                    if (handshake.publicKeyBase64.isNotBlank()) {
                        val peerPubKey = KeyExchangeManager.base64ToPublicKey(handshake.publicKeyBase64)
                        val sessionKey = KeyExchangeManager.generateSharedSecret(myKeyPair, peerPubKey)
                        sessionKeys[handshake.deviceId] = sessionKey

                        val rootKeyBytes = sessionKey.encoded ?: sessionKey.algorithm.toByteArray(Charsets.UTF_8)
                        val isInitiator = deviceId < handshake.deviceId
                        val mySendConstant = if (isInitiator) "AetherHop-Ratchet-A->B" else "AetherHop-Ratchet-B->A"
                        val myRecvConstant = if (isInitiator) "AetherHop-Ratchet-B->A" else "AetherHop-Ratchet-A->B"

                        val sendKeySeed = SymmetricKeyRatchet.deriveHmacSha256(rootKeyBytes, mySendConstant.toByteArray(Charsets.UTF_8))
                        val recvKeySeed = SymmetricKeyRatchet.deriveHmacSha256(rootKeyBytes, myRecvConstant.toByteArray(Charsets.UTF_8))
                        sendingRatchets[handshake.deviceId] = SymmetricKeyRatchet(sendKeySeed)
                        receivingRatchets[handshake.deviceId] = SymmetricKeyRatchet(recvKeySeed)
                    }
                    // Bidirectional handshake: reply with our identity if not already sent
                    sendHandshake(packet.senderId)
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding handshake packet", e)
                }
            }
            PacketType.AUDIO_FRAME -> {
                try {
                    val frame = Json.decodeFromString<AudioFramePayload>(packet.payload)
                    pttStreamManager.playPttFrame(frame.frameBase64, frame.sequenceIndex)
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error playing incoming AUDIO_FRAME", e)
                }
            }
            PacketType.CHAT -> {
                try {
                    val rawPayload = receivingRatchets[packet.senderId]?.let { ratchet ->
                        try {
                            val envelope = Json.decodeFromString<EncryptedEnvelope>(packet.payload)
                            CryptoManager.decryptWithRatchet(envelope, ratchet)
                        } catch (_: Exception) {
                            sessionKeys[packet.senderId]?.let { key ->
                                try {
                                    val envelope = Json.decodeFromString<EncryptedEnvelope>(packet.payload)
                                    CryptoManager.decrypt(envelope, key)
                                } catch (_: Exception) { null }
                            }
                        }
                    } ?: sessionKeys[packet.senderId]?.let { key ->
                        try {
                            val envelope = Json.decodeFromString<EncryptedEnvelope>(packet.payload)
                            CryptoManager.decrypt(envelope, key)
                        } catch (_: Exception) { packet.payload }
                    } ?: packet.payload

                    val chatMsg = Json.decodeFromString<ChatMessage>(rawPayload).copy(isMine = false)

                    if (packet.targetId.startsWith("#")) {
                        // Multi-hop Channel Broadcast
                        scope.launch {
                            channelDao.insertChannelMessage(
                                ChannelMessageEntity(
                                    messageId = chatMsg.id,
                                    channelId = packet.targetId,
                                    senderId = chatMsg.senderId,
                                    senderName = chatMsg.senderName,
                                    text = chatMsg.text,
                                    timestamp = chatMsg.timestamp,
                                    isMine = false
                                )
                            )
                            messageDao.insertMessage(
                                MessageEntity(
                                    id = chatMsg.id,
                                    peerId = packet.targetId,
                                    senderId = chatMsg.senderId,
                                    senderName = chatMsg.senderName,
                                    text = chatMsg.text,
                                    timestamp = chatMsg.timestamp,
                                    isMine = false,
                                    status = MessageStatus.SENT
                                )
                            )
                            conversationDao.insertOrUpdateConversation(
                                ConversationEntity(
                                    conversationId = packet.targetId,
                                    title = packet.targetId,
                                    isChannel = true,
                                    lastMessageText = chatMsg.text,
                                    lastMessageTimestamp = chatMsg.timestamp,
                                    unreadCount = 1
                                )
                            )
                        }
                        notificationManager.showMessageNotification(packet.targetId, "${chatMsg.senderName}: ${chatMsg.text}")

                        // Multi-hop flood relay for channel message
                        if (packet.ttl > 1) {
                            val forwarded = packet.copy(ttl = packet.ttl - 1)
                            val neighbors = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
                            neighbors.forEach { nIp ->
                                if (nIp.isNotBlank() && nIp != senderIp) {
                                    scope.launch { socketClient.sendPacket(nIp, forwarded) }
                                }
                            }
                        }
                    } else {
                        // 1-on-1 Unicast Message
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
                        // Send delivery receipt back to original sender
                        scope.launch {
                            val receipt = DeliveryReceiptPayload(
                                messageId = chatMsg.id,
                                senderId = chatMsg.senderId,
                                receiverId = deviceId
                            )
                            val receiptPacket = MeshPacket(
                                id = UUID.randomUUID().toString(),
                                senderId = deviceId,
                                targetId = packet.senderId,
                                type = PacketType.DELIVERY_RECEIPT,
                                payload = Json.encodeToString(receipt),
                                transport = resolveActiveTransportMedium(packet.senderId)
                            )
                            val destIp = routingTable.getNextHopIp(packet.senderId) ?: packet.senderId
                            socketClient.sendPacket(destIp, receiptPacket)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding incoming chat packet", e)
                }
            }
            PacketType.REACTION -> {
                try {
                    val reaction = Json.decodeFromString<ReactionPayload>(packet.payload)
                    scope.launch {
                        val existing = messageDao.getMessageById(reaction.messageId)
                        if (existing != null) {
                            val currentReactions = if (existing.reactionsRaw.isNotBlank()) {
                                existing.reactionsRaw.split(",").mapNotNull { entry ->
                                    val parts = entry.split(":")
                                    if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 1) else null
                                }.toMap().toMutableMap()
                            } else mutableMapOf()

                            currentReactions[reaction.emoji] = (currentReactions[reaction.emoji] ?: 0) + 1
                            val newRaw = currentReactions.entries.joinToString(",") { "${it.key}:${it.value}" }
                            messageDao.insertMessage(existing.copy(reactionsRaw = newRaw))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding REACTION packet", e)
                }
            }
            PacketType.TELEMETRY -> {
                try {
                    val telemetry = Json.decodeFromString<TelemetryBroadcastPayload>(packet.payload)
                    _peerTelemetry.update { it + (telemetry.senderId to telemetry) }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error decoding TELEMETRY packet", e)
                }
            }
            PacketType.KEY_REVOCATION -> {
                sessionKeys.remove(packet.senderId)
                sendingRatchets.remove(packet.senderId)?.zeroize()
                receivingRatchets.remove(packet.senderId)?.zeroize()
                _handshookPeers.remove(packet.senderId)
            }
            PacketType.REKEY_REQUEST -> {
                sessionKeys.remove(packet.senderId)
                sendingRatchets.remove(packet.senderId)?.zeroize()
                receivingRatchets.remove(packet.senderId)?.zeroize()
                _handshookPeers.remove(packet.senderId)
                sendHandshake(packet.senderId)
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
            PacketType.DELIVERY_RECEIPT -> {
                try {
                    val receipt = Json.decodeFromString<DeliveryReceiptPayload>(packet.payload)
                    scope.launch {
                        messageDao.updateMessageStatus(receipt.messageId, MessageStatus.DELIVERED.name)
                        storeAndForwardBuffer.removeBundle(receipt.messageId)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error handling DELIVERY_RECEIPT packet", e)
                }
            }
            PacketType.RREQ -> {
                try {
                    val rreq = Json.decodeFromString<RouteRequestPayload>(packet.payload)
                    if (rreq.targetDestinationId == deviceId) {
                        // This node is the requested target, reply directly with RREP
                        val rrep = RouteReplyPayload(
                            requestId = rreq.requestId,
                            targetDestinationId = deviceId,
                            destinationIp = senderIp,
                            hopCount = rreq.hopCount + 1
                        )
                        val replyPacket = MeshPacket(
                            id = UUID.randomUUID().toString(),
                            senderId = deviceId,
                            targetId = rreq.sourceId,
                            type = PacketType.RREP,
                            payload = Json.encodeToString(rrep),
                            ttl = 5
                        )
                        scope.launch {
                            val replyDestIp = routingTable.getNextHopIp(rreq.sourceId) ?: senderIp
                            socketClient.sendPacket(replyDestIp, replyPacket)
                        }
                    } else {
                        // Check if we know the route to destination
                        val knownTargetIp = routingTable.getNextHopIp(rreq.targetDestinationId)
                        if (knownTargetIp != null) {
                            val rrep = RouteReplyPayload(
                                requestId = rreq.requestId,
                                targetDestinationId = rreq.targetDestinationId,
                                destinationIp = knownTargetIp,
                                hopCount = rreq.hopCount + 1
                            )
                            val replyPacket = MeshPacket(
                                id = UUID.randomUUID().toString(),
                                senderId = deviceId,
                                targetId = rreq.sourceId,
                                type = PacketType.RREP,
                                payload = Json.encodeToString(rrep),
                                ttl = 5
                            )
                            scope.launch {
                                val replyDestIp = routingTable.getNextHopIp(rreq.sourceId) ?: senderIp
                                socketClient.sendPacket(replyDestIp, replyPacket)
                            }
                        } else if (packet.ttl > 1) {
                            // Forward RREQ flood to neighbors
                            val forwardedRreq = rreq.copy(hopCount = rreq.hopCount + 1)
                            val fwdPacket = packet.copy(
                                ttl = packet.ttl - 1,
                                payload = Json.encodeToString(forwardedRreq)
                            )
                            val neighbors = routingTable.getAllRoutes().map { it.nextHopIp }.toSet() + _wifiPeers.value.map { it.deviceAddress }
                            neighbors.forEach { nIp ->
                                if (nIp.isNotBlank() && nIp != senderIp) {
                                    scope.launch { socketClient.sendPacket(nIp, fwdPacket) }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error handling RREQ packet", e)
                }
            }
            PacketType.RREP -> {
                try {
                    val rrep = Json.decodeFromString<RouteReplyPayload>(packet.payload)
                    routingTable.updateRoute(rrep.targetDestinationId, senderIp, rrep.hopCount)
                    if (packet.targetId != deviceId && packet.ttl > 1) {
                        val nextHop = routingTable.getNextHopIp(packet.targetId) ?: packet.targetId
                        scope.launch {
                            val fwdPacket = packet.copy(ttl = packet.ttl - 1)
                            socketClient.sendPacket(nextHop, fwdPacket)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error handling RREP packet", e)
                }
            }
            PacketType.GOSSIP_DIGEST -> {
                try {
                    val remoteDigest = Json.decodeFromString<GossipDigestPayload>(packet.payload)
                    scope.launch {
                        _isGossipSyncing.value = true
                        val allLocalWps = waypointDao.getWaypointsList()
                        val localIds = allLocalWps.map { it.id }.toSet()
                        val delta = gossipManager.computeDelta(localIds, remoteDigest)

                        // If remote is missing waypoints that we have, send them over
                        if (delta.missingWaypointIdsForRemote.isNotEmpty()) {
                            val wpsToSend = gossipManager.filterMissingWaypoints(allLocalWps, delta.missingWaypointIdsForRemote)
                            val syncPayload = WaypointSyncPayload(wpsToSend)
                            val syncPacket = MeshPacket(
                                id = UUID.randomUUID().toString(),
                                senderId = deviceId,
                                targetId = packet.senderId,
                                type = PacketType.WAYPOINT_SYNC,
                                payload = Json.encodeToString(syncPayload),
                                transport = resolveActiveTransportMedium(packet.senderId)
                            )
                            val destIp = routingTable.getNextHopIp(packet.senderId) ?: senderIp
                            socketClient.sendPacket(destIp, syncPacket)
                        }
                        delay(500)
                        _isGossipSyncing.value = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error handling GOSSIP_DIGEST packet", e)
                    _isGossipSyncing.value = false
                }
            }
            PacketType.WAYPOINT_SYNC -> {
                try {
                    val syncPayload = Json.decodeFromString<WaypointSyncPayload>(packet.payload)
                    scope.launch {
                        _isGossipSyncing.value = true
                        if (syncPayload.waypoints.isNotEmpty()) {
                            waypointDao.insertWaypoints(syncPayload.waypoints)
                        }
                        delay(500)
                        _isGossipSyncing.value = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("P2pRepositoryImpl", "Error handling WAYPOINT_SYNC packet", e)
                    _isGossipSyncing.value = false
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
                                        status = MessageStatus.SENT,
                                        mediaUri = outputFile.absolutePath
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

                    val voiceFile = java.io.File(appContext.cacheDir, "recv_voice_${voice.voiceId}.m4a").apply {
                        try {
                            val bytes = Base64.decode(voice.audioBase64, Base64.NO_WRAP)
                            writeBytes(bytes)
                        } catch (_: Exception) {}
                    }

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
                                status = MessageStatus.SENT,
                                mediaUri = voiceFile.absolutePath,
                                mediaDurationMs = voice.durationMs
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
        pttUdpSocketManager.stopListening()
        pttStreamManager.stopPttPlayer()
        bleManager.stopAdvertising()
        wifiAwareManager.closeSession()
        wifiP2pManager.disconnect()
    }
}
