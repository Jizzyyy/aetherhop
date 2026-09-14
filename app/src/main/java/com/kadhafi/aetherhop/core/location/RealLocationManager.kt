package com.kadhafi.aetherhop.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

data class TacticalLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float = 0f,
    val verticalAccuracyMeters: Float? = null,
    val speedMps: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

fun Location.toTacticalSnapshot(): TacticalLocationSnapshot {
    val alt = if (hasAltitude()) altitude else null
    val vertAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) verticalAccuracyMeters else null
    val spd = if (hasSpeed()) speed else 0f
    return TacticalLocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = alt,
        accuracyMeters = if (hasAccuracy()) accuracy else 0f,
        verticalAccuracyMeters = vertAcc,
        speedMps = spd,
        timestamp = if (time > 0) time else System.currentTimeMillis()
    )
}

class RealLocationManager(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    fun observeLocation(minTimeMs: Long = 2000L, minDistanceMeters: Float = 2.0f): Flow<Location> = callbackFlow {
        if (locationManager == null) {
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Try GPS provider first, fallback to network
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        // Seed with last known location if available
        val lastGps = if (hasGps) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
        val lastNetwork = if (hasNetwork) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
        val bestLast = lastGps ?: lastNetwork
        bestLast?.let { trySend(it) }

        if (hasGps) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minTimeMs,
                minDistanceMeters,
                listener
            )
        } else if (hasNetwork) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                minTimeMs,
                minDistanceMeters,
                listener
            )
        }

        awaitClose {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: Exception) {}
        }
    }

    fun observeTacticalLocation(minTimeMs: Long = 2000L, minDistanceMeters: Float = 2.0f): Flow<TacticalLocationSnapshot> {
        return observeLocation(minTimeMs, minDistanceMeters).map { it.toTacticalSnapshot() }
    }
}
