package com.kadhafi.aetherhop.data.mesh

object LinkQualityCalculator {

    fun calculateLqi(rssi: Int, rttMs: Long, packetLossPercentage: Float): Int {
        val rssiScore = when {
            rssi >= -60 -> 100
            rssi >= -75 -> 75
            rssi >= -85 -> 50
            rssi >= -95 -> 25
            else -> 10
        }

        val rttScore = when {
            rttMs <= 0 -> 80
            rttMs < 50 -> 100
            rttMs < 150 -> 75
            rttMs < 300 -> 50
            rttMs < 600 -> 25
            else -> 10
        }

        val lossScore = (100f - packetLossPercentage).coerceIn(0f, 100f).toInt()
        val composite = (rssiScore * 0.4f) + (rttScore * 0.3f) + (lossScore * 0.3f)
        return composite.toInt().coerceIn(0, 100)
    }

    fun getLqiRating(lqiScore: Int): String {
        return when {
            lqiScore >= 80 -> "EXCELLENT"
            lqiScore >= 60 -> "GOOD"
            lqiScore >= 40 -> "FAIR"
            else -> "POOR"
        }
    }
}
