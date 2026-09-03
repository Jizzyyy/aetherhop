package com.kadhafi.aetherhop.core.theme

import android.content.Context
import com.kadhafi.aetherhop.core.theme.ThemePreset

object ThemeManager {
    private const val PREFS_NAME = "aetherhop_prefs"
    private const val KEY_THEME_PRESET = "key_theme_preset"

    fun getSelectedTheme(context: Context): ThemePreset {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME_PRESET, ThemePreset.DEFAULT.name) ?: ThemePreset.DEFAULT.name
        return try {
            enumValueOf<ThemePreset>(name)
        } catch (_: Exception) {
            ThemePreset.DEFAULT
        }
    }

    fun setSelectedTheme(context: Context, preset: ThemePreset) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_PRESET, preset.name).apply()
    }
}
