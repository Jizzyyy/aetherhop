package com.kadhafi.aetherhop.core.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionRekeyingDaemonTest {

    @Test
    fun testRekeyTriggeredAtHundredMessages() {
        val triggerCount = AtomicInteger(0)
        var triggeredTarget: String? = null

        val daemon = SessionRekeyingDaemon(CoroutineScope(Dispatchers.Unconfined)) { targetId ->
            triggerCount.incrementAndGet()
            triggeredTarget = targetId
        }

        val peerId = "peer_test_node_rekey"

        // 99 messages should not trigger
        for (i in 1..99) {
            daemon.recordSessionMessage(peerId)
        }
        assertEquals(0, triggerCount.get())

        // 100th message triggers rekeying
        daemon.recordSessionMessage(peerId)
        assertEquals(1, triggerCount.get())
        assertEquals(peerId, triggeredTarget)

        // Subsequent message starts new cycle at 1
        daemon.recordSessionMessage(peerId)
        assertEquals(1, triggerCount.get())
    }

    @Test
    fun testResetSessionClearsCounter() {
        val triggerCount = AtomicInteger(0)
        val daemon = SessionRekeyingDaemon(CoroutineScope(Dispatchers.Unconfined)) {
            triggerCount.incrementAndGet()
        }

        val peerId = "peer_reset_test"

        // Record 50 messages
        for (i in 1..50) {
            daemon.recordSessionMessage(peerId)
        }
        assertEquals(0, triggerCount.get())

        // Reset session
        daemon.resetSession(peerId)

        // Record another 50 messages (would be 100 without reset)
        for (i in 1..50) {
            daemon.recordSessionMessage(peerId)
        }
        assertEquals(0, triggerCount.get())
    }

    @Test
    fun testBlankTargetIgnored() {
        val triggerCount = AtomicInteger(0)
        val daemon = SessionRekeyingDaemon(CoroutineScope(Dispatchers.Unconfined)) {
            triggerCount.incrementAndGet()
        }

        for (i in 1..150) {
            daemon.recordSessionMessage("")
            daemon.recordSessionMessage("   ")
        }
        assertEquals(0, triggerCount.get())
    }
}
