package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import com.kadhafi.aetherhop.domain.model.GossipDigestPayload

data class GossipDeltaResult(
    val missingWaypointIdsForRemote: List<String>,
    val neededWaypointIdsFromRemote: List<String>
)

class EpidemicGossipManager(private val myNodeId: String) {

    fun createLocalDigest(
        knownWaypointIds: List<String>,
        knownMessageIds: List<String> = emptyList()
    ): GossipDigestPayload {
        return GossipDigestPayload(
            nodeId = myNodeId,
            knownWaypointIds = knownWaypointIds.sorted(),
            knownMessageIds = knownMessageIds.sorted(),
            timestamp = System.currentTimeMillis()
        )
    }

    fun computeDelta(
        localWaypointIds: Set<String>,
        remoteDigest: GossipDigestPayload
    ): GossipDeltaResult {
        val remoteKnownSet = remoteDigest.knownWaypointIds.toSet()

        // Waypoints that we have but remote doesn't -> we should send these to remote
        val toSendToRemote = localWaypointIds.filter { !remoteKnownSet.contains(it) }

        // Waypoints that remote has but we don't -> we need to request/receive these
        val neededFromRemote = remoteKnownSet.filter { !localWaypointIds.contains(it) }

        return GossipDeltaResult(
            missingWaypointIdsForRemote = toSendToRemote,
            neededWaypointIdsFromRemote = neededFromRemote
        )
    }

    fun filterMissingWaypoints(
        allLocalWaypoints: List<TacticalWaypointEntity>,
        missingIds: List<String>
    ): List<TacticalWaypointEntity> {
        val missingSet = missingIds.toSet()
        return allLocalWaypoints.filter { missingSet.contains(it.id) }
    }
}
