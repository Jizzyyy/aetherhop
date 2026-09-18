package com.kadhafi.aetherhop.presentation.components

import com.kadhafi.aetherhop.core.location.GeodesicCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MapCanvasProjectionTest {

    @Test
    fun testZoomScaleClamping() {
        fun clampZoom(current: Float, factor: Float): Float {
            return (current * factor).coerceIn(0.5f, 5.0f)
        }

        assertEquals(1.0f, clampZoom(1.0f, 1.0f), 0.001f)
        assertEquals(2.5f, clampZoom(1.0f, 2.5f), 0.001f)
        // Zooming out below 0.5f clamps to 0.5f
        assertEquals(0.5f, clampZoom(0.6f, 0.5f), 0.001f)
        assertEquals(0.5f, clampZoom(0.5f, 0.1f), 0.001f)
        // Zooming in beyond 5.0f clamps to 5.0f
        assertEquals(5.0f, clampZoom(3.0f, 2.0f), 0.001f)
        assertEquals(5.0f, clampZoom(5.0f, 1.5f), 0.001f)
    }

    @Test
    fun testPixelToDistanceMetersUnprojection() {
        val maxRadiusPx = 400.0 // 400 pixels represents 200 meters (scale is 200m at outer ring)
        val outerScaleMeters = 200.0

        fun unprojectDistanceMeters(distPx: Double, radiusPx: Double): Double {
            return (distPx / radiusPx) * outerScaleMeters
        }

        assertEquals(0.0, unprojectDistanceMeters(0.0, maxRadiusPx), 0.001)
        assertEquals(50.0, unprojectDistanceMeters(100.0, maxRadiusPx), 0.001)
        assertEquals(100.0, unprojectDistanceMeters(200.0, maxRadiusPx), 0.001)
        assertEquals(200.0, unprojectDistanceMeters(400.0, maxRadiusPx), 0.001)
    }

    @Test
    fun testCursorUnprojectionCoordinatesDueNorth() {
        val originLat = -6.2088
        val originLon = 106.8456
        val distM = 100.0
        val bearing = 0.0 // Due North

        val dLat = (distM * cos(Math.toRadians(bearing))) / 111320.0
        val dLon = (distM * sin(Math.toRadians(bearing))) / (111320.0 * cos(Math.toRadians(originLat)))

        val targetLat = originLat + dLat
        val targetLon = originLon + dLon

        assertTrue("Latitude should increase (move north)", targetLat > originLat)
        assertEquals(originLon, targetLon, 0.000001)

        val checkDist = GeodesicCalculator.calculateDistanceMeters(originLat, originLon, targetLat, targetLon)
        assertEquals(100.0, checkDist, 0.5)
    }

    @Test
    fun testCursorUnprojectionCoordinatesDueEast() {
        val originLat = -6.2088
        val originLon = 106.8456
        val distM = 100.0
        val bearing = 90.0 // Due East

        val dLat = (distM * cos(Math.toRadians(bearing))) / 111320.0
        val dLon = (distM * sin(Math.toRadians(bearing))) / (111320.0 * cos(Math.toRadians(originLat)))

        val targetLat = originLat + dLat
        val targetLon = originLon + dLon

        assertEquals(originLat, targetLat, 0.000001)
        assertTrue("Longitude should increase (move east)", targetLon > originLon)

        val checkDist = GeodesicCalculator.calculateDistanceMeters(originLat, originLon, targetLat, targetLon)
        assertEquals(100.0, checkDist, 0.5)
    }

    @Test
    fun testPanOffsetTranslationIntegrity() {
        val center = Pair(500f, 500f)
        val pan = Pair(-50f, 100f)

        val translatedCenter = Pair(center.first + pan.first, center.second + pan.second)
        assertEquals(450f, translatedCenter.first, 0.001f)
        assertEquals(600f, translatedCenter.second, 0.001f)

        // Reset pan
        val resetPan = Pair(0f, 0f)
        val restoredCenter = Pair(center.first + resetPan.first, center.second + resetPan.second)
        assertEquals(500f, restoredCenter.first, 0.001f)
        assertEquals(500f, restoredCenter.second, 0.001f)
    }
}
