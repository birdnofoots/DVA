package com.dva.app.domain.model

/**
 * 分析任务实体
 */
data class AnalysisTask(
    val id: String,
    val videoPath: String,
    val status: TaskStatus,
    val progress: Float = 0f,
    val currentFrame: Long = 0,
    val totalFrames: Long = 0,
    val violationsFound: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val lastResumePosition: Long? = null
) {
    val isRunning: Boolean
        get() = status == TaskStatus.RUNNING
    
    val isPaused: Boolean
        get() = status == TaskStatus.PAUSED
    
    val isCompleted: Boolean
        get() = status == TaskStatus.COMPLETED
    
    val isFailed: Boolean
        get() = status == TaskStatus.FAILED
    
    val progressPercent: Int
        get() = (progress * 100).toInt()
    
    val estimatedTimeRemaining: Long?
        get() {
            if (startedAt == null || currentFrame == 0L || totalFrames == 0L) return null
            val elapsed = System.currentTimeMillis() - startedAt
            val remainingFrames = totalFrames - currentFrame
            val timePerFrame = elapsed / currentFrame
            return (remainingFrames * timePerFrame).toLong()
        }
}

/**
 * 任务状态
 */
enum class TaskStatus(val displayName: String) {
    PENDING("等待中"),
    RUNNING("运行中"),
    PAUSED("已暂停"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    FAILED("失败");
}
