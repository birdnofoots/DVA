package com.dva.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class VideoTest {

    @Test
    fun `formattedDuration returns correct format for short video`() {
        val video = Video(
            id = "test-id",
            filePath = "/test/video.mp4",
            fileName = "video.mp4",
            durationMs = 125000, // 2 minutes 5 seconds
            width = 1920,
            height = 1080,
            frameRate = 30f,
            totalFrames = 3750,
            fileSize = 100_000_000,
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        
        assertEquals("02:05", video.formattedDuration)
    }

    @Test
    fun `formattedDuration returns correct format for long video`() {
        val video = Video(
            id = "test-id",
            filePath = "/test/video.mp4",
            fileName = "video.mp4",
            durationMs = 3725000, // 1 hour 2 minutes 5 seconds
            width = 1920,
            height = 1080,
            frameRate = 30f,
            totalFrames = 111750,
            fileSize = 1_000_000_000,
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        
        assertEquals("01:02:05", video.formattedDuration)
    }

    @Test
    fun `formattedFileSize returns correct format for MB`() {
        val video = Video(
            id = "test-id",
            filePath = "/test/video.mp4",
            fileName = "video.mp4",
            durationMs = 60000,
            width = 1920,
            height = 1080,
            frameRate = 30f,
            totalFrames = 1800,
            fileSize = 52_428_800, // 50 MB
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        
        assertEquals("50.00 MB", video.formattedFileSize)
    }

    @Test
    fun `formattedFileSize returns correct format for GB`() {
        val video = Video(
            id = "test-id",
            filePath = "/test/video.mp4",
            fileName = "video.mp4",
            durationMs = 600000,
            width = 1920,
            height = 1080,
            frameRate = 30f,
            totalFrames = 18_000_000,
            fileSize = 2_147_483_648, // 2 GB
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        
        assertEquals("2.00 GB", video.formattedFileSize)
    }
}
