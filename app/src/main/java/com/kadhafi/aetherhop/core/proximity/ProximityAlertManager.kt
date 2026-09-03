package com.kadhafi.aetherhop.core.proximity

import com.kadhafi.aetherhop.domain.model.PeerNode

enum class ProximityZone {
    SAFE,
    WARNING,
    LOST
}

data class ProximityAlertEvent(
    val peer: PeerNode,
    val zone: ProximityZone
)

class ProximityAlertManager(
    private var safeDistanceMeters: Double = 15.0,
    private var warningDistanceMeters: Double = 35.0
) {
    fun setThresholds(safe: Double, warning: Double) {
        this.safeDistanceMeters = safe
        this.warningDistanceMeters = warning
    }

    fun evaluatePeerProximity(peer: PeerNode): ProximityZone {
        val dist = peer.distanceMeters
        return when {
            dist <= 0 -> ProximityZone.SAFE
            dist <= safeDistanceMeters -> ProximityZone.SAFE
            dist <= warningDistanceMeters -> ProximityZone.WARNING
            else -> ProximityZone.LOST
        }
    }
}
