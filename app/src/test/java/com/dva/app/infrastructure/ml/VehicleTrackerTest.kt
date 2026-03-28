package com.dva.app.infrastructure.ml

import android.graphics.RectF
import com.dva.app.domain.model.VehicleCategory
import com.dva.app.infrastructure.ml.models.VehicleDetectionResult
import com.dva.app.infrastructure.ml.models.VehiclePosition
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VehicleTrackerTest {

    private lateinit var tracker: VehicleTracker

    @Before
    fun setup() {
        tracker = VehicleTracker()
    }

    @Test
    fun `update returns empty list for empty detections`() {
        val result = tracker.update(emptyList(), 0, 0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `update creates new track for detection`() {
        val detections = listOf(
            createDetection("car", RectF(100f, 100f, 200f, 200f))
        )
        
        val result = tracker.update(detections, 0, 0)
        
        assertEquals(1, result.size)
        assertEquals("vehicle_0", result[0].id)
    }

    @Test
    fun `update maintains same track for nearby detection`() {
        // First frame
        val detections1 = listOf(
            createDetection("car", RectF(100f, 100f, 200f, 200f))
        )
        tracker.update(detections1, 0, 0)
        
        // Second frame - slightly moved
        val detections2 = listOf(
            createDetection("car", RectF(105f, 100f, 205f, 200f))
        )
        val result = tracker.update(detections2, 1, 100)
        
        assertEquals(1, result.size)
        assertEquals("vehicle_0", result[0].id)
        assertEquals(1, result[0].history.size)
    }

    @Test
    fun `update removes disappeared tracks after threshold`() {
        // First frame with detection
        val detections1 = listOf(
            createDetection("car", RectF(100f, 100f, 200f, 200f))
        )
        tracker.update(detections1, 0, 0)
        
        // Many frames without detection
        for (i in 1..10) {
            tracker.update(emptyList(), i.toLong(), i * 100L)
        }
        
        val result = tracker.getTrackedVehicles()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `reset clears all tracks`() {
        val detections = listOf(
            createDetection("car", RectF(100f, 100f, 200f, 200f)),
            createDetection("truck", RectF(300f, 100f, 450f, 250f))
        )
        tracker.update(detections, 0, 0)
        
        assertEquals(2, tracker.getTrackedVehicles().size)
        
        tracker.reset()
        
        assertTrue(tracker.getTrackedVehicles().isEmpty())
    }

    @Test
    fun `getVehicle returns correct vehicle`() {
        val detections = listOf(
            createDetection("car", RectF(100f, 100f, 200f, 200f))
        )
        tracker.update(detections, 0, 0)
        
        val vehicle = tracker.getVehicle("vehicle_0")
        
        assertNotNull(vehicle)
        assertEquals(VehicleCategory.CAR, vehicle?.category)
    }

    @Test
    fun `getVehicle returns null for unknown id`() {
        val vehicle = tracker.getVehicle("unknown")
        assertNull(vehicle)
    }

    private fun createDetection(
        category: String,
        box: RectF
    ): VehicleDetectionResult {
        return VehicleDetectionResult(
            boundingBox = box,
            category = when (category) {
                "car" -> VehicleCategory.CAR
                "truck" -> VehicleCategory.TRUCK
                "bus" -> VehicleCategory.BUS
                else -> VehicleCategory.UNKNOWN
            },
            confidence = 0.9f,
            classId = 0
        )
    }
}
