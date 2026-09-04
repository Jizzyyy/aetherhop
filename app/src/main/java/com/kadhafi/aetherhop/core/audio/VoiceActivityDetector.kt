package com.kadhafi.aetherhop.core.audio

import kotlin.math.sqrt

object VoiceActivityDetector {
    private const val DEFAULT_RMS_THRESHOLD = 500.0

    fun calculateRms(pcm: ByteArray): Double {
        if (pcm.size < 2) return 0.0
        var sumSquares = 0.0
        val sampleCount = pcm.size / 2

        var i = 0
        while (i < pcm.size - 1) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sumSquares += sampleShort * sampleShort
            i += 2
        }

        return sqrt(sumSquares / sampleCount)
    }

    fun isSpeechDetected(pcm: ByteArray, threshold: Double = DEFAULT_RMS_THRESHOLD): Boolean {
        return calculateRms(pcm) >= threshold
    }
}
