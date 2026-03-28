package com.dva.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class AnalysisTaskTest {

    @Test
    fun `isRunning returns true for RUNNING status`() {
        val task = createTask(status = TaskStatus.RUNNING)
        assertTrue(task.isRunning)
    }

    @Test
    fun `isRunning returns false for other statuses`() {
        assertFalse(createTask(status = TaskStatus.PENDING).isRunning)
        assertFalse(createTask(status = TaskStatus.PAUSED).isRunning)
        assertFalse(createTask(status = TaskStatus.COMPLETED).isRunning)
    }

    @Test
    fun `isPaused returns true for PAUSED status`() {
        val task = createTask(status = TaskStatus.PAUSED)
        assertTrue(task.isPaused)
    }

    @Test
    fun `isCompleted returns true for COMPLETED status`() {
        val task = createTask(status = TaskStatus.COMPLETED)
        assertTrue(task.isCompleted)
    }

    @Test
    fun `isFailed returns true for FAILED status`() {
        val task = createTask(status = TaskStatus.FAILED)
        assertTrue(task.isFailed)
    }

    @Test
    fun `progressPercent returns correct value`() {
        val task = createTask(progress = 0.5f)
        assertEquals(50, task.progressPercent)
    }

    @Test
    fun `progressPercent caps at 100`() {
        val task = createTask(progress = 1.5f)
        assertEquals(100, task.progressPercent)
    }

    @Test
    fun `estimatedTimeRemaining returns null when no start time`() {
        val task = createTask(startedAt = null)
        assertNull(task.estimatedTimeRemaining)
    }

    @Test
    fun `estimatedTimeRemaining calculates correctly`() {
        val startTime = System.currentTimeMillis() - 10000 // 10 seconds ago
        val task = AnalysisTask(
            id = "test",
            videoPath = "/test.mp4",
            status = TaskStatus.RUNNING,
            progress = 0.5f,
            currentFrame = 500,
            totalFrames = 1000,
            violationsFound = 0,
            startedAt = startTime,
            completedAt = null
        )
        
        val estimated = task.estimatedTimeRemaining
        assertNotNull(estimated)
        // Should be approximately 10 seconds remaining
        assertTrue(estimated!! in 5000..20000)
    }

    @Test
    fun `TaskStatus displayName is capitalized`() {
        assertEquals("Running", TaskStatus.RUNNING.displayName)
        assertEquals("Completed", TaskStatus.COMPLETED.displayName)
        assertEquals("Pending", TaskStatus.PENDING.displayName)
    }

    private fun createTask(
        status: TaskStatus = TaskStatus.PENDING,
        progress: Float = 0f,
        startedAt: Long? = System.currentTimeMillis()
    ): AnalysisTask {
        return AnalysisTask(
            id = "test-task",
            videoPath = "/test/video.mp4",
            status = status,
            progress = progress,
            currentFrame = 0,
            totalFrames = 1000,
            violationsFound = 0,
            startedAt = startedAt,
            completedAt = null
        )
    }
}
