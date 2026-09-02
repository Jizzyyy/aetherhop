package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PeerPairingPayload(
    val deviceId: String,
    val deviceName: String,
    val publicKeyBase64: String,
    val checksumFingerprint: String
)
