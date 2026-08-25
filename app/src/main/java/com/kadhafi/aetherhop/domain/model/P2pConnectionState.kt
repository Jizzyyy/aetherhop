package com.kadhafi.aetherhop.domain.model

sealed class P2pConnectionState {
    object Idle : P2pConnectionState()
    object Discovering : P2pConnectionState()
    data class Connecting(val deviceName: String) : P2pConnectionState()
    data class Connected(
        val groupOwnerAddress: String,
        val isGroupOwner: Boolean,
        val deviceName: String
    ) : P2pConnectionState()
    data class Error(val message: String) : P2pConnectionState()
}
