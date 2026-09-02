package com.kadhafi.aetherhop.core.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class PowerProfile {
    EMERGENCY_MAX,
    BALANCED,
    SAVER_LOW_POWER
}

data class PowerState(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val recommendedProfile: PowerProfile
)

class PowerOptimizationManager(context: Context) {
    private val appContext = context.applicationContext

    fun observePowerState(): Flow<PowerState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                    val profile = when {
                        isCharging || pct > 50 -> PowerProfile.EMERGENCY_MAX
                        pct in 20..50 -> PowerProfile.BALANCED
                        else -> PowerProfile.SAVER_LOW_POWER
                    }

                    trySend(PowerState(pct, isCharging, profile))
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
