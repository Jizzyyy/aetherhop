package com.kadhafi.aetherhop.core.theme

import android.content.Context

object HapticPreferenceManager {
    private const val PREFS_NAME = "aetherhop_prefs"
    private const val KEY_HAPTIC_ENABLED = "key_haptic_enabled"

    fun isHapticEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }
}
