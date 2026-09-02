package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HandshakePayload(
    val deviceId: String,
    val deviceName: String,
    val publicKeyBase64: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)
