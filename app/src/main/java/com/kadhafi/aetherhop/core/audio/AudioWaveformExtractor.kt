package com.kadhafi.aetherhop.core.audio

import java.util.Locale
import kotlin.math.abs

object AudioWaveformExtractor {
    const val DEFAULT_BAR_COUNT = 32

    fun extractWaveformFromBytes(bytes: ByteArray, barCount: Int = DEFAULT_BAR_COUNT): List<Float> {
        if (bytes.isEmpty() || barCount <= 0) return List(barCount) { 0.15f }
        val chunkSize = (bytes.size / barCount).coerceAtLeast(1)
        val result = mutableListOf<Float>()

        var maxVal = 0.0
        val rawAvgs = DoubleArray(barCount)

        for (i in 0 until barCount) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, bytes.size)
            var sum = 0.0
            var count = 0
            for (j in start until end) {
                sum += abs(bytes[j].toInt())
                count++
            }
            val avg = if (count > 0) sum / count else 0.0
            rawAvgs[i] = avg
            if (avg > maxVal) maxVal = avg
        }

        val scale = if (maxVal > 0.0) maxVal else 1.0
        for (avg in rawAvgs) {
            val normalized = (avg / scale).toFloat().coerceIn(0.15f, 1.0f)
            result.add(normalized)
        }

        return result
    }

    fun serializeWaveform(bars: List<Float>): String {
        return bars.joinToString(",") { String.format(Locale.US, "%.2f", it) }
    }

    fun deserializeWaveform(raw: String): List<Float> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
    }
}
