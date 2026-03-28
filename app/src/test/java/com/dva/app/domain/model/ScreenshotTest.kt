package com.dva.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ScreenshotTest {

    @Test
    fun `formattedTimestamp returns correct format`() {
        val screenshot = createScreenshot(timestampMs = 3725000) // 1:02:05
        
        assertEquals("01:02:05", screenshot.formattedTimestamp)
    }

    @Test
    fun `ScreenshotType timeOffset is correct`() {
        assertEquals(-5000L, ScreenshotType.BEFORE.timeOffset)
        assertEquals(0L, ScreenshotType.MOMENT.timeOffset)
        assertEquals(5000L, ScreenshotType.AFTER.timeOffset)
    }

    @Test
    fun `ScreenshotType displayName is correct`() {
        assertEquals("违章前5秒", ScreenshotType.BEFORE.displayName)
        assertEquals("违章时刻", ScreenshotType.MOMENT.displayName)
        assertEquals("违章后5秒", ScreenshotType.AFTER.displayName)
    }

    @Test
    fun `ScreenshotType fromOffset returns correct type`() {
        assertEquals(ScreenshotType.BEFORE, ScreenshotType.fromOffset(-6000))
        assertEquals(ScreenshotType.MOMENT, ScreenshotType.fromOffset(100))
        assertEquals(ScreenshotType.AFTER, ScreenshotType.fromOffset(6000))
    }

    @Test
    fun `ImageFormat extension is correct`() {
        assertEquals("png", ImageFormat.PNG.extension)
        assertEquals("jpg", ImageFormat.JPEG.extension)
        assertEquals("webp", ImageFormat.WEBP.extension)
    }

    @Test
    fun `ImageFormat mimeType is correct`() {
        assertEquals("image/png", ImageFormat.PNG.mimeType)
        assertEquals("image/jpeg", ImageFormat.JPEG.mimeType)
        assertEquals("image/webp", ImageFormat.WEBP.mimeType)
    }

    private fun createScreenshot(timestampMs: Long): Screenshot {
        return Screenshot(
            id = "screenshot-1",
            violationId = "violation-1",
            type = ScreenshotType.MOMENT,
            filePath = "/path/to/screenshot.png",
            timestampMs = timestampMs,
            width = 1920,
            height = 1080,
            fileSize = 2_000_000
        )
    }
}
