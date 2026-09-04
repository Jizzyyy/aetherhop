package com.kadhafi.aetherhop.core.proximity

import com.kadhafi.aetherhop.domain.model.PeerNode
import java.util.concurrent.ConcurrentHashMap

data class LostPeerAlert(
    val peerId: String,
    val peerName: String,
    val lastKnownLat: Double?,
    val lastKnownLon: Double?,
    val lastSeenTimestamp: Long
)

class LostPeerBeaconAlarm {
    private val lostPeersCache = ConcurrentHashMap<String, LostPeerAlert>()

    fun checkAndEmitLostPeers(
        currentPeers: List<PeerNode>,
        timeoutThresholdMs: Long = 45000
    ): List<LostPeerAlert> {
        val now = System.currentTimeMillis()
        val lostList = mutableListOf<LostPeerAlert>()

        currentPeers.forEach { peer ->
            if (now - peer.lastSeenTimestamp > timeoutThresholdMs) {
                val alert = LostPeerAlert(
                    peerId = peer.id,
                    peerName = peer.name,
                    lastKnownLat = peer.latitude,
                    lastKnownLon = peer.longitude,
                    lastSeenTimestamp = peer.lastSeenTimestamp
                )
                if (!lostPeersCache.containsKey(peer.id)) {
                    lostPeersCache[peer.id] = alert
                    lostList.add(alert)
                }
            } else {
                lostPeersCache.remove(peer.id)
            }
        }
        return lostList
    }
}
