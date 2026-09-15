package com.kadhafi.aetherhop.core.location

import kotlin.math.*

data class UtmCoordinate(
    val zoneNumber: Int,
    val zoneLetter: Char,
    val easting: Double,
    val northing: Double
) {
    fun toFormattedString(): String {
        return String.format(java.util.Locale.US, "%d%c %.0fE %.0fN", zoneNumber, zoneLetter, easting, northing)
    }
}

object UtmConverter {
    private const val A = 6378137.0 // WGS84 major axis
    private const val F = 1.0 / 298.257223563 // Flattening
    private const val K0 = 0.9996 // UTM scale factor
    private const val E2 = 2.0 * F - F * F // First eccentricity squared
    private const val E_PRIME_2 = E2 / (1.0 - E2) // Second eccentricity squared

    fun getUtmLetterDesignator(latitude: Double): Char {
        return when {
            latitude >= 84.0 || latitude < -80.0 -> 'Z' // out of UTM bounds
            latitude >= 72.0 -> 'X'
            latitude >= 64.0 -> 'W'
            latitude >= 56.0 -> 'V'
            latitude >= 48.0 -> 'U'
            latitude >= 40.0 -> 'T'
            latitude >= 32.0 -> 'S'
            latitude >= 24.0 -> 'R'
            latitude >= 16.0 -> 'Q'
            latitude >= 8.0 -> 'P'
            latitude >= 0.0 -> 'N'
            latitude >= -8.0 -> 'M'
            latitude >= -16.0 -> 'L'
            latitude >= -24.0 -> 'K'
            latitude >= -32.0 -> 'J'
            latitude >= -40.0 -> 'H'
            latitude >= -48.0 -> 'G'
            latitude >= -56.0 -> 'F'
            latitude >= -64.0 -> 'E'
            latitude >= -72.0 -> 'D'
            else -> 'C'
        }
    }

    fun toUtm(latitude: Double, longitude: Double): UtmCoordinate {
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)

        var zoneNumber = floor((longitude + 180.0) / 6.0).toInt() + 1
        if (zoneNumber > 60) zoneNumber = 60
        if (zoneNumber < 1) zoneNumber = 1

        val lonOrigin = (zoneNumber - 1) * 6 - 180 + 3 // central meridian in degrees
        val lonOriginRad = Math.toRadians(lonOrigin.toDouble())

        val zoneLetter = getUtmLetterDesignator(latitude)

        val n = A / sqrt(1.0 - E2 * sin(latRad) * sin(latRad))
        val t = tan(latRad) * tan(latRad)
        val c = E_PRIME_2 * cos(latRad) * cos(latRad)
        val aCoeff = cos(latRad) * (lonRad - lonOriginRad)

        val m = A * (
            (1.0 - E2 / 4.0 - 3.0 * E2 * E2 / 64.0 - 5.0 * E2 * E2 * E2 / 256.0) * latRad
            - (3.0 * E2 / 8.0 + 3.0 * E2 * E2 / 32.0 + 45.0 * E2 * E2 * E2 / 1024.0) * sin(2.0 * latRad)
            + (15.0 * E2 * E2 / 256.0 + 45.0 * E2 * E2 * E2 / 1024.0) * sin(4.0 * latRad)
            - (35.0 * E2 * E2 * E2 / 3072.0) * sin(6.0 * latRad)
        )

        val easting = K0 * n * (
            aCoeff + (1.0 - t + c) * aCoeff.pow(3) / 6.0
            + (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * E_PRIME_2) * aCoeff.pow(5) / 120.0
        ) + 500000.0

        var northing = K0 * (
            m + n * tan(latRad) * (
                aCoeff.pow(2) / 2.0
                + (5.0 - t + 9.0 * c + 4.0 * c * c) * aCoeff.pow(4) / 24.0
                + (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * E_PRIME_2) * aCoeff.pow(6) / 720.0
            )
        )

        if (latitude < 0.0) {
            northing += 10000000.0 // 10,000,000 meter false northing for southern hemisphere
        }

        return UtmCoordinate(zoneNumber, zoneLetter, easting, northing)
    }
}
