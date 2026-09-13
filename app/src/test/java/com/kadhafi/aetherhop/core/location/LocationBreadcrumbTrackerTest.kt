package com.kadhafi.aetherhop.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationBreadcrumbTrackerTest {

    @Test
    fun testRecordPointAddsBreadcrumb() {
        val tracker = LocationBreadcrumbTracker(maxPoints = 10)
        assertEquals(0, tracker.getBreadcrumbs().size)

        tracker.recordPoint(-6.2088, 106.8456)
        val breadcrumbs = tracker.getBreadcrumbs()
        assertEquals(1, breadcrumbs.size)
        assertEquals(-6.2088, breadcrumbs[0].latitude, 0.0001)
        assertEquals(106.8456, breadcrumbs[0].longitude, 0.0001)
        assertTrue(breadcrumbs[0].timestamp > 0)
    }

    @Test
    fun testCapacityCeilingDiscardsOldestPoints() {
        val capacity = 5
        val tracker = LocationBreadcrumbTracker(maxPoints = capacity)

        for (i in 1..8) {
            tracker.recordPoint(i.toDouble(), (i * 10).toDouble())
        }

        val breadcrumbs = tracker.getBreadcrumbs()
        // Must not exceed maxPoints capacity
        assertEquals(capacity, breadcrumbs.size)

        // Points 1, 2, 3 should have been polled out. Oldest remaining is 4.0
        assertEquals(4.0, breadcrumbs.first().latitude, 0.0001)
        assertEquals(8.0, breadcrumbs.last().latitude, 0.0001)
    }

    @Test
    fun testClearBreadcrumbs() {
        val tracker = LocationBreadcrumbTracker(maxPoints = 10)
        tracker.recordPoint(-6.1, 106.1)
        tracker.recordPoint(-6.2, 106.2)
        assertEquals(2, tracker.getBreadcrumbs().size)

        tracker.clearBreadcrumbs()
        assertEquals(0, tracker.getBreadcrumbs().size)
    }
}
