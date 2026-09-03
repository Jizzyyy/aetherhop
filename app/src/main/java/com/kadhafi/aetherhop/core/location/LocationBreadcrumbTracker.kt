package com.kadhafi.aetherhop.core.location

import java.util.concurrent.ConcurrentLinkedQueue

data class BreadcrumbPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class LocationBreadcrumbTracker(private val maxPoints: Int = 50) {
    private val points = ConcurrentLinkedQueue<BreadcrumbPoint>()

    fun recordPoint(latitude: Double, longitude: Double) {
        if (points.size >= maxPoints) {
            points.poll()
        }
        points.add(BreadcrumbPoint(latitude, longitude))
    }

    fun getBreadcrumbs(): List<BreadcrumbPoint> = points.toList()

    fun clearBreadcrumbs() {
        points.clear()
    }
}
