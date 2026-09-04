package com.kadhafi.aetherhop.data.mesh

enum class TransportLinkType {
    WIFI_DIRECT,
    WIFI_AWARE_NAN,
    BLE_GATT
}

data class ActiveTransportLink(
    val peerId: String,
    val linkType: TransportLinkType,
    val ipAddress: String,
    val isStable: Boolean = true
)

class MeshTransportRouter {
    private val activeLinks = java.util.concurrent.ConcurrentHashMap<String, ActiveTransportLink>()

    fun registerLink(peerId: String, linkType: TransportLinkType, ipAddress: String) {
        if (peerId.isBlank() || ipAddress.isBlank()) return
        activeLinks[peerId] = ActiveTransportLink(peerId, linkType, ipAddress)
    }

    fun resolveOptimalLink(peerId: String): ActiveTransportLink? {
        return activeLinks[peerId]
    }

    fun getFallbackTransport(peerId: String): TransportLinkType {
        val current = activeLinks[peerId]
        return when (current?.linkType) {
            TransportLinkType.WIFI_DIRECT -> TransportLinkType.WIFI_AWARE_NAN
            TransportLinkType.WIFI_AWARE_NAN -> TransportLinkType.BLE_GATT
            else -> TransportLinkType.WIFI_DIRECT
        }
    }

    fun invalidateLink(peerId: String) {
        activeLinks.remove(peerId)
    }
}
