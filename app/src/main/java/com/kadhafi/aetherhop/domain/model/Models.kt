package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PeerNode(
    val id: String,
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val distanceMeters: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val operationalStatus: String = "STANDBY",
    val transport: TransportMedium = TransportMedium.WIFI_DIRECT,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class PacketType {
    HANDSHAKE,
    CHAT,
    FILE_CHUNK,
    SOS_ALERT,
    ACK,
    VOICE_NOTE,
    PING,
    PONG,
    KEY_REVOCATION,
    REKEY_REQUEST,
    AUDIO_FRAME,
    TELEMETRY,
    REACTION,
    RREQ,
    RREP,
    DELIVERY_RECEIPT,
    GOSSIP_DIGEST,
    WAYPOINT_SYNC
}

@Serializable
data class GossipDigestPayload(
    val nodeId: String,
    val knownWaypointIds: List<String> = emptyList(),
    val knownMessageIds: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class WaypointSyncPayload(
    val waypoints: List<com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity> = emptyList()
)

@Serializable
data class DeliveryReceiptPayload(
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val deliveredTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class RouteRequestPayload(
    val requestId: String,
    val sourceId: String,
    val targetDestinationId: String,
    val hopCount: Int = 0
)

@Serializable
data class RouteReplyPayload(
    val requestId: String,
    val targetDestinationId: String,
    val destinationIp: String,
    val hopCount: Int = 0
)

@Serializable
enum class TransportMedium {
    WIFI_DIRECT,
    WIFI_AWARE,
    BLE
}

@Serializable
data class MeshPacket(
    val id: String,
    val senderId: String,
    val targetId: String,
    val type: PacketType,
    val payload: String,
    val ttl: Int = 5,
    val transport: TransportMedium = TransportMedium.WIFI_DIRECT,
    val isCompressed: Boolean = false,
    val signatureBase64: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
