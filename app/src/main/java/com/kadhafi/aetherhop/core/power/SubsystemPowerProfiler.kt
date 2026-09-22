package com.kadhafi.aetherhop.core.power

data class SubsystemDrainEstimate(
    val bleDrainMah: Float,
    val wifiDirectDrainMah: Float,
    val gpsDrainMah: Float,
    val screenAndCpuDrainMah: Float,
    val totalDrainRateMahPerHour: Float,
    val projectedRemainingHours: Float
)

object SubsystemPowerProfiler {
    const val DRAIN_BLE_ADVERTISING_MA = 12.0f
    const val DRAIN_BLE_SCANNING_MA = 18.0f
    const val DRAIN_WIFI_DIRECT_MA = 85.0f
    const val DRAIN_GPS_HIGH_ACCURACY_MA = 45.0f
    const val DRAIN_GPS_SAVER_MA = 15.0f
    const val DRAIN_SCREEN_BASE_MA = 120.0f
    const val DRAIN_AUDIO_PTT_MA = 35.0f

    const val DEFAULT_BATTERY_CAPACITY_MAH = 4000f

    fun calculateTotalDrainRate(
        isBleAdvertising: Boolean = true,
        isWifiDirectConnected: Boolean = true,
        isGpsActive: Boolean = true,
        isPowerSaver: Boolean = false,
        isTransmittingAudio: Boolean = false
    ): Float {
        var total = DRAIN_SCREEN_BASE_MA
        if (isBleAdvertising) total += if (isPowerSaver) DRAIN_BLE_ADVERTISING_MA * 0.4f else DRAIN_BLE_ADVERTISING_MA
        total += if (isPowerSaver) DRAIN_BLE_SCANNING_MA * 0.3f else DRAIN_BLE_SCANNING_MA
        if (isWifiDirectConnected) total += if (isPowerSaver) DRAIN_WIFI_DIRECT_MA * 0.6f else DRAIN_WIFI_DIRECT_MA
        if (isGpsActive) total += if (isPowerSaver) DRAIN_GPS_SAVER_MA else DRAIN_GPS_HIGH_ACCURACY_MA
        if (isTransmittingAudio) total += DRAIN_AUDIO_PTT_MA
        return total
    }

    fun estimateRemainingHours(
        batteryPercent: Int,
        totalDrainRateMahPerHour: Float,
        batteryCapacityMah: Float = DEFAULT_BATTERY_CAPACITY_MAH
    ): Float {
        if (totalDrainRateMahPerHour <= 0f) return 24.0f
        val remainingMah = (batteryPercent.coerceIn(0, 100) / 100.0f) * batteryCapacityMah
        val hours = remainingMah / totalDrainRateMahPerHour
        return ((hours * 10).toInt()) / 10.0f
    }

    fun calculateDischargeSlope(
        prevPercent: Int,
        prevTimestampMs: Long,
        currPercent: Int,
        currTimestampMs: Long
    ): Float {
        val dtHours = (currTimestampMs - prevTimestampMs).coerceAtLeast(1000L) / (1000.0f * 3600.0f)
        if (dtHours <= 0f) return 0f
        val dPct = (prevPercent - currPercent).coerceAtLeast(0)
        return dPct / dtHours
    }

    fun isHighDrainSpike(pctPerHourDrop: Float): Boolean {
        return pctPerHourDrop > 25.0f
    }
}
