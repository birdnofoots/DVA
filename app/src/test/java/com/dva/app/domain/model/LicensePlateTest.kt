package com.dva.app.domain.model

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test

class LicensePlateTest {

    @Test
    fun `isValid returns true for valid plate with high confidence`() {
        val plate = createPlate(
            number = "京A12345",
            confidence = 0.9f
        )
        
        assertTrue(plate.isValid)
    }

    @Test
    fun `isValid returns false for low confidence`() {
        val plate = createPlate(
            number = "京A12345",
            confidence = 0.5f // Below MIN_CONFIDENCE (0.7)
        )
        
        assertFalse(plate.isValid)
    }

    @Test
    fun `isValid returns false for short number`() {
        val plate = createPlate(
            number = "京A123", // Only 6 characters
            confidence = 0.9f
        )
        
        assertFalse(plate.isValid)
    }

    @Test
    fun `PROVINCES contains all Chinese provinces`() {
        assertEquals(31, LicensePlate.PROVINCES.size)
        assertTrue(LicensePlate.PROVINCES.contains("京"))
        assertTrue(LicensePlate.PROVINCES.contains("粤"))
        assertTrue(LicensePlate.PROVINCES.contains("沪"))
    }

    @Test
    fun `PlateType displayName is correct`() {
        assertEquals("蓝牌", PlateType.BLUE.displayName)
        assertEquals("绿牌(新能源)", PlateType.GREEN.displayName)
        assertEquals("黄牌", PlateType.YELLOW.displayName)
    }

    private fun createPlate(
        number: String,
        confidence: Float
    ): LicensePlate {
        return LicensePlate(
            number = number,
            province = number.firstOrNull()?.toString() ?: "",
            letter = "",
            digits = "",
            plateType = PlateType.BLUE,
            confidence = confidence,
            boundingBox = RectF(0f, 0f, 100f, 50f),
            color = PlateColor.BLUE
        )
    }
}
