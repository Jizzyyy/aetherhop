package com.kadhafi.aetherhop.core.power

object CriticalDutyCycleScheduler {
    const val CRITICAL_BATTERY_THRESHOLD = 15
    const val ACTIVE_WINDOW_SECONDS = 10L
    const val DORMANT_WINDOW_SECONDS = 50L
    const val CYCLE_PERIOD_SECONDS = ACTIVE_WINDOW_SECONDS + DORMANT_WINDOW_SECONDS // 60s

    fun shouldActivateSurvivalMode(batteryPercent: Int, isCharging: Boolean, manualOverride: Boolean): Boolean {
        if (isCharging) return false
        return manualOverride || batteryPercent <= CRITICAL_BATTERY_THRESHOLD
    }

    fun isRadioActive(elapsedSeconds: Long): Boolean {
        val mod = (elapsedSeconds % CYCLE_PERIOD_SECONDS)
        return mod < ACTIVE_WINDOW_SECONDS
    }

    fun getRemainingWindowSeconds(elapsedSeconds: Long): Long {
        val mod = (elapsedSeconds % CYCLE_PERIOD_SECONDS)
        return if (mod < ACTIVE_WINDOW_SECONDS) {
            ACTIVE_WINDOW_SECONDS - mod
        } else {
            CYCLE_PERIOD_SECONDS - mod
        }
    }

    fun getDutyCycleRatio(): Float {
        return ACTIVE_WINDOW_SECONDS.toFloat() / CYCLE_PERIOD_SECONDS.toFloat()
    }
}
