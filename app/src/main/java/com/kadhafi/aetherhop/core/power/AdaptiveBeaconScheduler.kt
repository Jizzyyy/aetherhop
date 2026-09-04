package com.kadhafi.aetherhop.core.power

object AdaptiveBeaconScheduler {

    fun getScanIntervalMillis(powerProfile: PowerProfile): Long {
        return when (powerProfile) {
            PowerProfile.EMERGENCY_MAX -> 5000L // 5 seconds high frequency
            PowerProfile.BALANCED -> 15000L // 15 seconds balanced
            PowerProfile.SAVER_LOW_POWER -> 45000L // 45 seconds low power
        }
    }

    fun getScanDurationMillis(powerProfile: PowerProfile): Long {
        return when (powerProfile) {
            PowerProfile.EMERGENCY_MAX -> 4000L
            PowerProfile.BALANCED -> 3000L
            PowerProfile.SAVER_LOW_POWER -> 2000L
        }
    }
}
