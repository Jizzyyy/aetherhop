package com.kadhafi.aetherhop.core.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ThermalLevel {
    NOMINAL,   // < 38°C
    WARM,      // 38°C - 43°C
    HOT,       // 43°C - 48°C (Throttled)
    CRITICAL   // > 48°C (Severe Throttling)
}

data class ThermalState(
    val temperatureCelsius: Float,
    val thermalLevel: ThermalLevel,
    val isThrottled: Boolean
)

class ThermalThrottleManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        const val THRESHOLD_WARM = 38.0f
        const val THRESHOLD_HOT = 43.0f
        const val THRESHOLD_CRITICAL = 48.0f

        fun evaluateThermalLevel(temperatureCelsius: Float): ThermalLevel {
            return when {
                temperatureCelsius >= THRESHOLD_CRITICAL -> ThermalLevel.CRITICAL
                temperatureCelsius >= THRESHOLD_HOT -> ThermalLevel.HOT
                temperatureCelsius >= THRESHOLD_WARM -> ThermalLevel.WARM
                else -> ThermalLevel.NOMINAL
            }
        }

        fun getThrottledInterval(baseIntervalMs: Long, level: ThermalLevel): Long {
            return when (level) {
                ThermalLevel.NOMINAL -> baseIntervalMs
                ThermalLevel.WARM -> (baseIntervalMs * 1.5).toLong()
                ThermalLevel.HOT -> baseIntervalMs * 2
                ThermalLevel.CRITICAL -> baseIntervalMs * 4
            }
        }
    }

    fun observeThermalState(): Flow<ThermalState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
                    val tempC = rawTemp / 10.0f
                    val level = evaluateThermalLevel(tempC)
                    val throttled = level == ThermalLevel.HOT || level == ThermalLevel.CRITICAL
                    trySend(ThermalState(tempC, level, throttled))
                }
            }
        }

        appContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}
