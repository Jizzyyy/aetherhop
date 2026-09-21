package com.kadhafi.aetherhop.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DualWatchState(
    val isDualWatchEnabled: Boolean = true,
    val primaryChannel: String = "",
    val priorityChannel: String = PRIORITY_EMERGENCY_CHANNEL,
    val isPriorityActive: Boolean = false,
    val activePrioritySender: String? = null,
    val lastPriorityActivityTimestamp: Long = 0L
) {
    companion object {
        const val PRIORITY_EMERGENCY_CHANNEL = "#emergency"
        const val PRIORITY_TIMEOUT_MS = 2500L
    }
}

class DualWatchScanner {
    companion object {
        const val DUCKED_VOLUME_GAIN = 0.20f // Fade-down 80% volume
        const val NORMAL_VOLUME_GAIN = 1.00f
    }

    private val lock = Any()
    private val _state = MutableStateFlow(DualWatchState())
    val state: StateFlow<DualWatchState> = _state.asStateFlow()

    fun setDualWatchEnabled(enabled: Boolean) = synchronized(lock) {
        _state.value = _state.value.copy(
            isDualWatchEnabled = enabled,
            isPriorityActive = if (!enabled) false else _state.value.isPriorityActive
        )
    }

    fun setPrimaryChannel(channelId: String) = synchronized(lock) {
        _state.value = _state.value.copy(primaryChannel = channelId)
    }

    fun setPriorityChannel(channelId: String) = synchronized(lock) {
        _state.value = _state.value.copy(priorityChannel = channelId)
    }

    fun onPacketReceived(
        channelOrTargetId: String,
        senderName: String,
        isEmergency: Boolean = false,
        timestampMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        val current = _state.value
        if (!current.isDualWatchEnabled) return false

        val isPriorityHit = isEmergency ||
                channelOrTargetId.equals(current.priorityChannel, ignoreCase = true) ||
                channelOrTargetId.equals("BROADCAST", ignoreCase = true)

        if (isPriorityHit) {
            _state.value = current.copy(
                isPriorityActive = true,
                activePrioritySender = senderName,
                lastPriorityActivityTimestamp = timestampMs
            )
            return true
        }
        return false
    }

    fun evaluateTimeout(currentTimeMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val current = _state.value
        if (current.isPriorityActive && currentTimeMs - current.lastPriorityActivityTimestamp > DualWatchState.PRIORITY_TIMEOUT_MS) {
            _state.value = current.copy(
                isPriorityActive = false,
                activePrioritySender = null
            )
            return true
        }
        return false
    }

    fun shouldDuckNormalAudio(): Boolean {
        val current = _state.value
        return current.isDualWatchEnabled && current.isPriorityActive
    }

    fun shouldPreemptNormalAudio(isIncomingSos: Boolean): Boolean {
        val current = _state.value
        return current.isDualWatchEnabled && (isIncomingSos || current.isPriorityActive)
    }
}
