package com.kadhafi.aetherhop.core.proximity

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeofenceZone(
    val id: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Double
)

object GeofenceBeaconEvaluator {
    private const val EARTH_RADIUS_METERS = 6371000.0

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun isInsideGeofence(latitude: Double, longitude: Double, zone: GeofenceZone): Boolean {
        val dist = calculateDistanceMeters(latitude, longitude, zone.centerLat, zone.centerLon)
        return dist <= zone.radiusMeters
    }
}
