package com.kadhafi.aetherhop.core.audio

import com.kadhafi.aetherhop.data.mesh.LinkQualityCalculator

data class AudioBitrateConfig(
    val frameIntervalMs: Long,
    val sampleRateHz: Int,
    val isDownsampled: Boolean,
    val nominalBitrateKbps: Int,
    val label: String
)

object AdaptiveAudioBitrateController {
    const val NORMAL_SAMPLE_RATE = 16000
    const val LOW_BANDWIDTH_SAMPLE_RATE = 8000

    fun calculateBitrateConfig(lqiScore: Int, packetLossPercentage: Float): AudioBitrateConfig {
        val rating = LinkQualityCalculator.getLqiRating(lqiScore)
        return when {
            packetLossPercentage > 20f || rating == "POOR" -> {
                AudioBitrateConfig(
                    frameIntervalMs = 60L,
                    sampleRateHz = LOW_BANDWIDTH_SAMPLE_RATE,
                    isDownsampled = true,
                    nominalBitrateKbps = 16,
                    label = "8kHz • 16kbps (Low-BW)"
                )
            }
            rating == "FAIR" -> {
                AudioBitrateConfig(
                    frameIntervalMs = 40L,
                    sampleRateHz = NORMAL_SAMPLE_RATE,
                    isDownsampled = false,
                    nominalBitrateKbps = 32,
                    label = "16kHz • 32kbps (Fair)"
                )
            }
            rating == "GOOD" -> {
                AudioBitrateConfig(
                    frameIntervalMs = 30L,
                    sampleRateHz = NORMAL_SAMPLE_RATE,
                    isDownsampled = false,
                    nominalBitrateKbps = 32,
                    label = "16kHz • 32kbps"
                )
            }
            else -> { // EXCELLENT
                AudioBitrateConfig(
                    frameIntervalMs = 20L,
                    sampleRateHz = NORMAL_SAMPLE_RATE,
                    isDownsampled = false,
                    nominalBitrateKbps = 32,
                    label = "16kHz • 32kbps (HD)"
                )
            }
        }
    }

    fun downsample16kTo8k(pcm16k: ByteArray): ByteArray {
        if (pcm16k.size < 4) return pcm16k
        val numSamples = pcm16k.size / 2
        val downsampledSamples = numSamples / 2
        val out = ByteArray(downsampledSamples * 2)
        var outIdx = 0
        for (i in 0 until downsampledSamples) {
            val srcIdx = i * 4
            out[outIdx++] = pcm16k[srcIdx]
            out[outIdx++] = pcm16k[srcIdx + 1]
        }
        return out
    }

    fun upsample8kTo16k(pcm8k: ByteArray): ByteArray {
        if (pcm8k.size < 2) return pcm8k
        val numSamples = pcm8k.size / 2
        val out = ByteArray(numSamples * 4)
        var outIdx = 0
        for (i in 0 until numSamples) {
            val b0 = pcm8k[i * 2]
            val b1 = pcm8k[i * 2 + 1]
            out[outIdx++] = b0
            out[outIdx++] = b1
            out[outIdx++] = b0
            out[outIdx++] = b1
        }
        return out
    }
}
