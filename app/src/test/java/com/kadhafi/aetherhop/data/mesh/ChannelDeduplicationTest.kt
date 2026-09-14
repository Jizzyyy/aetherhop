package com.kadhafi.aetherhop.data.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.LinkedHashMap

class ChannelDeduplicationTest {

    @Test
    fun testLruCacheEvictionAtCapacity() {
        val maxCapacity = 50
        val lruCache = object : LinkedHashMap<String, Boolean>(maxCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > maxCapacity
            }
        }

        // Fill capacity to 50
        for (i in 1..50) {
            lruCache["packet_$i"] = true
        }
        assertEquals(50, lruCache.size)
        assertTrue(lruCache.containsKey("packet_1"))

        // Add 51st packet: oldest ("packet_1") must be evicted
        lruCache["packet_51"] = true
        assertEquals(50, lruCache.size)
        assertFalse(lruCache.containsKey("packet_1"))
        assertTrue(lruCache.containsKey("packet_2"))
        assertTrue(lruCache.containsKey("packet_51"))
    }

    @Test
    fun testBroadcastStormSuppression() {
        val processedPacketIds = object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 100
            }
        }

        val stormPacketId = "ch_msg_flood_999"
        var forwardActionCount = 0

        fun onPacketReceived(packetId: String) {
            val isDuplicate = synchronized(processedPacketIds) {
                if (processedPacketIds.containsKey(packetId)) {
                    true
                } else {
                    processedPacketIds[packetId] = true
                    false
                }
            }
            if (!isDuplicate) {
                forwardActionCount++
            }
        }

        // Simulate identical packet arriving from 10 different mesh neighbors in parallel
        val threads = (1..10).map {
            Thread { onPacketReceived(stormPacketId) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Must ONLY be forwarded exactly once despite 10 arrivals
        assertEquals(1, forwardActionCount)
    }

    @Test
    fun testChannelIdSyntaxValidation() {
        fun isValidChannelId(id: String): Boolean {
            return id.startsWith("#") && id.length in 2..32 && !id.contains(" ")
        }

        assertTrue(isValidChannelId("#general"))
        assertTrue(isValidChannelId("#ops_alfa"))
        assertTrue(isValidChannelId("#emergency-1"))
        assertFalse(isValidChannelId("general"))
        assertFalse(isValidChannelId("#"))
        assertFalse(isValidChannelId("#channel with space"))
    }
}
