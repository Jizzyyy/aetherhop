package com.kadhafi.aetherhop.core.util

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class SessionRekeyingDaemon(
    private val scope: CoroutineScope,
    private val onRekeyTrigger: (targetId: String) -> Unit
) {
    private val messageCounters = ConcurrentHashMap<String, Int>()
    private val sessionStartTimes = ConcurrentHashMap<String, Long>()

    fun recordSessionMessage(targetId: String) {
        if (targetId.isBlank()) return
        val count = messageCounters.compute(targetId) { _, current -> (current ?: 0) + 1 } ?: 1
        val startTime = sessionStartTimes.getOrPut(targetId) { System.currentTimeMillis() }

        val isMessageLimitHit = count >= 100
        val isTimeLimitHit = (System.currentTimeMillis() - startTime) >= (30 * 60 * 1000) // 30 minutes

        if (isMessageLimitHit || isTimeLimitHit) {
            messageCounters[targetId] = 0
            sessionStartTimes[targetId] = System.currentTimeMillis()
            onRekeyTrigger(targetId)
        }
    }

    fun resetSession(targetId: String) {
        messageCounters.remove(targetId)
        sessionStartTimes.remove(targetId)
    }
}
