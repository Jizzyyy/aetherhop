package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryBroadcastPayload(
    val senderId: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val activeNeighborsCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
