package com.dva.app.domain.usecase.analysis

import com.dva.app.domain.model.*
import com.dva.app.domain.repository.TaskRepository
import com.dva.app.domain.repository.ViolationRepository
import com.dva.app.domain.repository.VideoRepository
import com.dva.app.infrastructure.video.ScreenshotCapturer
import com.dva.app.infrastructure.video.VideoProcessor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动视频分析用例
 */
@Singleton
class StartAnalysisUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
    private val taskRepository: TaskRepository,
    private val violationRepository: ViolationRepository,
    private val videoProcessor: VideoProcessor,
    private val screenshotCapturer: ScreenshotCapturer
) {
    /**
     * 执行分析
     * @param videoId 视频ID
     * @param onProgress 进度回调
     * @param onViolationFound 发现违章回调
     */
    suspend operator fun invoke(
        videoId: String,
        onProgress: (AnalysisProgress) -> Unit = {},
        onViolationFound: (Violation) -> Unit = {}
    ): Result<String> {
        // 1. 获取视频信息
        val videoResult = videoRepository.getVideoById(videoId)
        if (videoResult.isFailure) {
            return Result.failure(
                videoResult.exceptionOrNull() ?: Exception("Failed to get video")
            )
        }
        
        val video = videoResult.getOrNull()
            ?: return Result.failure(Exception("Video not found"))
        
        // 2. 创建分析任务
        val taskResult = taskRepository.createTask(video.filePath)
        if (taskResult.isFailure) {
            return Result.failure(
                taskResult.exceptionOrNull() ?: Exception("Failed to create task")
            )
        }
        
        val task = taskResult.getOrNull()!!
        
        // 3. 打开视频
        val openResult = videoProcessor.open(video.filePath)
        if (openResult.isFailure) {
            taskRepository.failTask(task.id, "Failed to open video")
            return Result.failure(
                openResult.exceptionOrNull() ?: Exception("Failed to open video")
            )
        }
        
        // 4. 执行分析
        return executeAnalysis(
            task = task,
            video = video,
            onProgress = onProgress,
            onViolationFound = onViolationFound
        )
    }
    
    private suspend fun executeAnalysis(
        task: AnalysisTask,
        video: Video,
        onProgress: (AnalysisProgress) -> Unit,
        onViolationFound: (Violation) -> Unit
    ): Result<String> {
        try {
            // 更新任务状态为运行中
            taskRepository.updateStatus(task.id, TaskStatus.RUNNING)
            
            val totalFrames = videoProcessor.getTotalFrames()
            val frameRate = videoProcessor.getFrameRate()
            val durationMs = videoProcessor.getDurationMs()
            var violationsFound = 0
            var processedFrames = 0L
            val startTime = System.currentTimeMillis()
            
            // 帧处理流
            videoProcessor.createFrameStream().collect { frameProgress ->
                processedFrames = frameProgress.currentFrame
                
                // 计算进度
                val progress = AnalysisProgress(
                    taskId = task.id,
                    currentFrame = processedFrames,
                    totalFrames = totalFrames,
                    progressPercent = frameProgress.progress,
                    currentTimeMs = frameProgress.timestampMs,
                    fps = if (processedFrames > 0) {
                        processedFrames * 1000f / (System.currentTimeMillis() - startTime)
                    } else 0f,
                    violationsFound = violationsFound
                )
                
                onProgress(progress)
                
                // 更新数据库进度（每5秒一次）
                if (processedFrames % (frameRate * 5).toLong() == 0L) {
                    taskRepository.updateProgress(task.id, processedFrames, violationsFound)
                }
                
                // TODO: 在这里调用检测器检测违章
                // 目前是模拟数据
                
                // 如果帧bitmap不为空，后续处理完后需要回收
                frameProgress.bitmap?.let { bitmap ->
                    // 检测完成后处理bitmap
                    // bitmap.recycle() // 暂时不回收，由cacheManager管理
                }
            }
            
            // 分析完成
            taskRepository.completeTask(task.id)
            
            return Result.success(task.id)
        } catch (e: Exception) {
            taskRepository.failTask(task.id, e.message ?: "Analysis failed")
            return Result.failure(e)
        } finally {
            videoProcessor.release()
        }
    }

    /**
     * 恢复暂停的分析
     */
    suspend fun resume(
        taskId: String,
        onProgress: (AnalysisProgress) -> Unit = {},
        onViolationFound: (Violation) -> Unit = {}
    ): Result<String> {
        val taskResult = taskRepository.getTaskById(taskId)
        if (taskResult.isFailure) {
            return Result.failure(
                taskResult.exceptionOrNull() ?: Exception("Task not found")
            )
        }
        
        val task = taskResult.getOrNull()
            ?: return Result.failure(Exception("Task not found"))
        
        // 获取视频信息
        val videoResult = videoRepository.getVideoById(task.videoPath)
        if (videoResult.isFailure) {
            return Result.failure(
                videoResult.exceptionOrNull() ?: Exception("Video not found")
            )
        }
        
        val video = videoResult.getOrNull()
            ?: return Result.failure(Exception("Video not found"))
        
        // 打开视频
        val openResult = videoProcessor.open(video.filePath)
        if (openResult.isFailure) {
            return Result.failure(
                openResult.exceptionOrNull() ?: Exception("Failed to open video")
            )
        }
        
        // 执行分析（从断点继续）
        return executeAnalysis(task, video, onProgress, onViolationFound)
    }
}
