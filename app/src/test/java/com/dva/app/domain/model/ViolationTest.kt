package com.dva.app.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ViolationTest {

    @Test
    fun `formattedTimestamp returns correct format`() {
        val violation = createViolation(timestampMs = 3725000) // 1:02:05
        
        assertEquals("01:02:05", violation.formattedTimestamp)
    }

    @Test
    fun `ViolationType fromCode returns correct type`() {
        assertEquals(ViolationType.LANE_CHANGE_WITHOUT_SIGNAL, 
            ViolationType.fromCode("LCWS"))
    }

    @Test
    fun `ViolationType fromCode returns UNKNOWN for invalid code`() {
        assertEquals(ViolationType.UNKNOWN, 
            ViolationType.fromCode("INVALID"))
    }

    @Test
    fun `ViolationType displayName is correct`() {
        assertEquals("变道不打灯", 
            ViolationType.LANE_CHANGE_WITHOUT_SIGNAL.displayName)
    }

    private fun createViolation(timestampMs: Long): Violation {
        return Violation(
            id = UUID.randomUUID().toString(),
            taskId = "test-task",
            type = ViolationType.LANE_CHANGE_WITHOUT_SIGNAL,
            timestampMs = timestampMs,
            vehicleId = "vehicle-1",
            licensePlate = null,
            vehicleSnapshot = null,
            screenshots = emptyList(),
            confidence = 0.95f
        )
    }
}
