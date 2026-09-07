package com.kadhafi.aetherhop.data.mesh

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class NodeTelemetry(
    val peerId: String,
    val rttMs: Long = 0,
    val totalPacketsSent: Long = 0,
    val totalPacketsReceived: Long = 0,
    val packetLossPercentage: Float = 0f,
    val rssiHistory: List<Int> = emptyList()
)

object TelemetryCollector {
    private val rttMap = ConcurrentHashMap<String, Long>()
    private val packetsSentMap = ConcurrentHashMap<String, AtomicLong>()
    private val packetsReceivedMap = ConcurrentHashMap<String, AtomicLong>()
    private val rssiHistoryMap = ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<Int>>()

    fun recordRssi(peerId: String, rssi: Int) {
        if (peerId.isBlank()) return
        val queue = rssiHistoryMap.getOrPut(peerId) { java.util.concurrent.ConcurrentLinkedQueue() }
        if (queue.size >= 15) {
            queue.poll()
        }
        queue.add(rssi)
    }

    fun recordRtt(peerId: String, rttMs: Long) {
        if (peerId.isBlank()) return
        rttMap[peerId] = rttMs
    }

    fun incrementSent(peerId: String) {
        if (peerId.isBlank()) return
        packetsSentMap.getOrPut(peerId) { AtomicLong(0) }.incrementAndGet()
    }

    fun incrementReceived(peerId: String) {
        if (peerId.isBlank()) return
        packetsReceivedMap.getOrPut(peerId) { AtomicLong(0) }.incrementAndGet()
    }

    fun getTelemetryForPeer(peerId: String): NodeTelemetry {
        val rtt = rttMap[peerId] ?: 0L
        val sent = packetsSentMap[peerId]?.get() ?: 0L
        val received = packetsReceivedMap[peerId]?.get() ?: 0L
        val loss = if (sent > 0) {
            ((sent - received).coerceAtLeast(0) / sent.toFloat()) * 100f
        } else 0f
        val history = rssiHistoryMap[peerId]?.toList() ?: emptyList()

        return NodeTelemetry(
            peerId = peerId,
            rttMs = rtt,
            totalPacketsSent = sent,
            totalPacketsReceived = received,
            packetLossPercentage = loss,
            rssiHistory = history
        )
    }

    fun getAllTelemetry(): List<NodeTelemetry> {
        val keys = (rttMap.keys.toList() + packetsSentMap.keys.toList() + packetsReceivedMap.keys.toList()).toSet()
        return keys.map { getTelemetryForPeer(it) }
    }
}
