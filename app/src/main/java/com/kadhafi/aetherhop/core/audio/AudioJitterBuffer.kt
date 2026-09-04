package com.kadhafi.aetherhop.core.audio

import java.util.concurrent.ConcurrentSkipListMap

class AudioJitterBuffer(private val maxBufferSize: Int = 10) {
    private val buffer = ConcurrentSkipListMap<Long, ByteArray>()
    private var nextExpectedSeq: Long = 0L

    fun pushFrame(seq: Long, pcmData: ByteArray) {
        if (buffer.size >= maxBufferSize) {
            buffer.pollFirstEntry()
        }
        buffer[seq] = pcmData
    }

    fun popNextFrame(): ByteArray? {
        if (buffer.isEmpty()) return null
        val firstEntry = buffer.firstEntry() ?: return null

        return if (firstEntry.key <= nextExpectedSeq || buffer.size >= 4) {
            buffer.remove(firstEntry.key)
            nextExpectedSeq = firstEntry.key + 1
            firstEntry.value
        } else {
            // Packet loss concealment: return empty silence or interpolate
            nextExpectedSeq++
            null
        }
    }

    fun clear() {
        buffer.clear()
        nextExpectedSeq = 0L
    }
}
