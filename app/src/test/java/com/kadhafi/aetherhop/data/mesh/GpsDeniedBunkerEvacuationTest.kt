package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.core.location.DeadReckoningEngine
import com.kadhafi.aetherhop.core.location.GeodesicCalculator
import com.kadhafi.aetherhop.core.location.InertialStepDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsDeniedBunkerEvacuationTest {

    @Test
    fun testUndergroundBunkerEvacuationSimulation() {
        val bunkerEntranceLat = -6.2088
        val bunkerEntranceLon = 106.8456
        val initialAccuracy = 4.0f

        val engine = DeadReckoningEngine(strideLengthMeters = 0.75)
        val stepDetector = InertialStepDetector()

        // 1. Initial State: Soldier enters underground bunker, GPS locks last known coordinates
        engine.recalibrateWithGps(bunkerEntranceLat, bunkerEntranceLon, initialAccuracy)
        assertFalse(engine.state.value.isDeadReckoningActive)
        assertEquals(0, engine.state.value.accumulatedSteps)

        // 2. GPS satellite signal lost inside bunker. Activate Dead Reckoning mode
        engine.setDeadReckoningActive(true)
        assertTrue(engine.state.value.isDeadReckoningActive)

        // Corridor 1: 100 steps walking Due North (0°)
        var simTime = 1000L
        for (i in 1..100) {
            simTime += 400L
            stepDetector.processSample(12.5f, simTime)
            stepDetector.processSample(7.5f, simTime + 100L)
            engine.onStepTaken(0f)
        }
        val postCorridor1 = engine.state.value
        assertEquals(100, postCorridor1.accumulatedSteps)
        assertEquals(75.0, postCorridor1.accumulatedDistanceMeters, 0.01)
        assertTrue("Latitude should increase heading North", postCorridor1.currentLatitude > bunkerEntranceLat)
        assertEquals(bunkerEntranceLon, postCorridor1.currentLongitude, 0.000001)

        // Corridor 2: Turn right, 200 steps walking Due East (90°)
        for (i in 1..200) {
            engine.onStepTaken(90f)
        }
        val postCorridor2 = engine.state.value
        assertEquals(300, postCorridor2.accumulatedSteps)
        assertEquals(225.0, postCorridor2.accumulatedDistanceMeters, 0.01)
        assertTrue("Longitude should increase heading East", postCorridor2.currentLongitude > bunkerEntranceLon)

        // Corridor 3: Final sprint to bunker emergency exit hatch: 100 steps South (180°)
        for (i in 1..100) {
            engine.onStepTaken(180f)
        }
        val postCorridor3 = engine.state.value
        assertEquals(400, postCorridor3.accumulatedSteps)
        assertEquals(300.0, postCorridor3.accumulatedDistanceMeters, 0.01)

        // Verifying displacement from entrance
        val netDisplacement = GeodesicCalculator.calculateDistanceMeters(
            bunkerEntranceLat, bunkerEntranceLon,
            postCorridor3.currentLatitude, postCorridor3.currentLongitude
        )
        // 100 steps North then 100 steps South cancel out; net displacement should be purely East (~150m)
        assertEquals(150.0, netDisplacement, 2.0)
        // Drift accumulated proportionally
        assertTrue("Drift must have accumulated during dead reckoning", postCorridor3.estimatedDriftRadiusMeters > initialAccuracy)

        // 3. Soldier surfaces out of bunker exit hatch: GPS signal re-acquired!
        val surfaceGpsLat = postCorridor3.currentLatitude
        val surfaceGpsLon = postCorridor3.currentLongitude
        engine.recalibrateWithGps(surfaceGpsLat, surfaceGpsLon, 3.0f)

        val finalState = engine.state.value
        assertFalse("Dead reckoning should disengage when GPS locks", finalState.isDeadReckoningActive)
        assertEquals(0, finalState.accumulatedSteps)
        assertEquals(3.0f, finalState.estimatedDriftRadiusMeters, 0.01f)
    }
}
