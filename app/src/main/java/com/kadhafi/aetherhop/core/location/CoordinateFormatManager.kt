package com.kadhafi.aetherhop.core.location

import android.content.Context

enum class CoordinateFormat {
    DECIMAL,
    MGRS,
    UTM
}

object CoordinateFormatManager {
    private const val PREFS_NAME = "aetherhop_prefs"
    private const val KEY_COORD_FORMAT = "key_coord_format"

    fun getSelectedFormat(context: Context): CoordinateFormat {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_COORD_FORMAT, CoordinateFormat.DECIMAL.name) ?: CoordinateFormat.DECIMAL.name
        return try {
            enumValueOf<CoordinateFormat>(name)
        } catch (_: Exception) {
            CoordinateFormat.DECIMAL
        }
    }

    fun setSelectedFormat(context: Context, format: CoordinateFormat) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COORD_FORMAT, format.name).apply()
    }

    fun formatCoordinates(latitude: Double, longitude: Double, format: CoordinateFormat): String {
        return when (format) {
            CoordinateFormat.DECIMAL -> String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude)
            CoordinateFormat.MGRS -> MgrsConverter.toMgrs(latitude, longitude).toFormatted10DigitString()
            CoordinateFormat.UTM -> UtmConverter.toUtm(latitude, longitude).toFormattedString()
        }
    }
}
