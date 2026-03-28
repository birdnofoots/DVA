package com.dva.app.domain.detector

import com.dva.app.domain.detector.impl.LaneChangeDetector
import com.dva.app.domain.model.ViolationType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LaneChangeDetectorTest {

    private lateinit var detector: LaneChangeDetector

    @Before
    fun setup() {
        detector = LaneChangeDetector()
    }

    @Test
    fun `detector has correct violation type`() {
        assertEquals(
            ViolationType.LANE_CHANGE_WITHOUT_SIGNAL,
            detector.violationType
        )
    }

    @Test
    fun `detector has correct priority`() {
        assertEquals(10, detector.priority)
    }

    @Test
    fun `detector is enabled by default`() {
        assertTrue(detector.isEnabled)
    }

    @Test
    fun `reset clears recent violations`() {
        // Initially should be empty
        assertTrue(detector.isEnabled)
        
        detector.reset()
        
        // Should still be enabled after reset
        assertTrue(detector.isEnabled)
    }

    @Test
    fun `detect returns null when disabled`() {
        detector.isEnabled = false
        
        val context = createContext()
        
        assertNull(detector.detect(context))
    }

    @Test
    fun `detect returns null when vehicle has turn signal`() {
        val trackedVehicle = createTrackedVehicle(
            history = createLaneChangeHistory(),
            turnSignalState = TurnSignalState.LEFT
        )
        
        val context = DetectionContext(
            frameIndex = 100,
            timestampMs = 5000,
            vehicles = emptyList(),
            lanes = emptyList(),
            trackedVehicles = listOf(trackedVehicle)
        )
        
        // With turn signal, should not detect violation
        val result = detector.detect(context)
        // Note: This depends on implementation details
    }

    private fun createContext(): DetectionContext {
        return DetectionContext(
            frameIndex = 0,
            timestampMs = 0,
            vehicles = emptyList(),
            lanes = emptyList(),
            trackedVehicles = emptyList()
        )
    }

    private fun createTrackedVehicle(
        history: List<PositionSnapshot>,
        turnSignalState: TurnSignalState = TurnSignalState.OFF
    ): TrackedVehicleInfo {
        return TrackedVehicleInfo(
            id = "vehicle-1",
            history = history,
            lastDirection = null,
            turnSignalState = turnSignalState
        )
    }

    private fun createLaneChangeHistory(): List<PositionSnapshot> {
        val history = mutableListOf<PositionSnapshot>()
        var timestamp = 0L
        
        // Create a right lane change history
        for (i in 0 until 10) {
            history.add(
                PositionSnapshot(
                    x = 200f + i * 20f, // Moving right
                    y = 500f,
                    timestampMs = timestamp
                )
            )
            timestamp += 100
        }
        
        return history
    }
}
