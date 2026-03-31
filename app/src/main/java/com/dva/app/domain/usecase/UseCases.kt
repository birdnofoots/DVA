package com.dva.app.domain.usecase

import android.net.Uri
import com.dva.app.domain.model.*
import com.dva.app.domain.repository.VideoRepository
import com.dva.app.domain.repository.ViolationRepository
import com.dva.app.infrastructure.ml.PlateRecognizer
import com.dva.app.infrastructure.ml.VehicleDetector
import com.dva.app.infrastructure.ml.LaneDetector
import com.dva.app.infrastructure.ml.ViolationAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 扫描视频目录用例
 */
class ScanVideosUseCase(
    private val videoRepository: VideoRepository
) {
    /**
     * 使用文件路径扫描（传统方式）
     */
    suspend operator fun invoke(directoryPath: String): Result<List<VideoFile>> {
        return try {
            val videos = videoRepository.scanDirectory(directoryPath)
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 使用 Content URI 扫描（SAF 方式）
     */
    suspend fun invoke(uri: Uri): Result<List<VideoFile>> {
        return try {
            val videos = videoRepository.scanUri(uri)
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 分析视频用例
 */
class AnalyzeVideoUseCase(
    private val videoRepository: VideoRepository,
    private val violationRepository: ViolationRepository,
    private val vehicleDetector: VehicleDetector,
    private val laneDetector: LaneDetector,
    private val plateRecognizer: PlateRecognizer,
    private val violationAnalyzer: ViolationAnalyzer
) {
    /**
     * 执行视频分析
     * @param videoPath 视频路径
     * @param onProgress 进度回调
     */
    suspend operator fun invoke(
        videoPath: String,
        onProgress: (Int) -> Unit
    ): Result<List<ViolationRecord>> {
        return try {
            // 获取视频信息
            val videoInfo = videoRepository.getVideoInfo(videoPath)
                ?: return Result.failure(Exception("无法读取视频信息"))
            
            // 初始化处理状态
            videoRepository.updateProcessingState(
                VideoProcessingState(
                    videoPath = videoPath,
                    status = ProcessingStatus.PROCESSING,
                    progress = 0,
                    currentFrame = 0,
                    totalFrames = videoInfo.frameCount,
                    violationCount = 0,
                    errorMessage = null
                )
            )
            
            val violations = mutableListOf<ViolationRecord>()
            val frameInterval = 5 // 每5帧检测一次（可调整）
            
            // 分段处理视频
            for (startFrame in 0 until videoInfo.frameCount step frameInterval) {
                val endFrame = minOf(startFrame + frameInterval, videoInfo.frameCount)
                
                // 提取帧
                val frames = videoRepository.extractFrameRange(videoPath, startFrame, endFrame)
                
                // 车辆检测
                val vehicles = vehicleDetector.detect(frames)
                
                // 车道线检测
                val lanes = laneDetector.detect(frames)
                
                // 违章分析
                val detectedViolations = violationAnalyzer.analyze(
                    videoPath = videoPath,
                    frameIndex = startFrame,
                    vehicles = vehicles,
                    lanes = lanes
                )
                
                // 处理每个违章
                for (violation in detectedViolations) {
                    // 提取三阶段截图
                    val beforeFrame = maxOf(0, violation.frameIndex - 125) // 约5秒（25fps）
                    val afterFrame = minOf(videoInfo.frameCount - 1, violation.frameIndex + 125)
                    
                    // 车牌识别
                    val plateResult = plateRecognizer.recognize(
                        videoRepository.extractFrame(videoPath, violation.frameIndex)!!
                    )
                    
                    // 保存记录
                    val record = ViolationRecord(
                        videoPath = videoPath,
                        violationType = violation.type,
                        plateNumber = plateResult?.plateNumber,
                        plateConfidence = plateResult?.confidence ?: 0f,
                        timestamp = (videoInfo.durationMs * violation.frameIndex) / videoInfo.frameCount,
                        frameIndex = violation.frameIndex,
                        beforeImagePath = "", // 后续保存
                        duringImagePath = "", // 后续保存
                        afterImagePath = "",   // 后续保存
                        annotatedImagePath = null,
                        confidence = violation.confidence
                    )
                    
                    val id = violationRepository.insertViolation(record)
                    violations.add(record.copy(id = id))
                }
                
                // 更新进度
                val progress = (endFrame * 100) / videoInfo.frameCount
                onProgress(progress)
                videoRepository.updateProcessingState(
                    VideoProcessingState(
                        videoPath = videoPath,
                        status = if (endFrame >= videoInfo.frameCount) ProcessingStatus.COMPLETED else ProcessingStatus.PROCESSING,
                        progress = progress,
                        currentFrame = endFrame,
                        totalFrames = videoInfo.frameCount,
                        violationCount = violations.size,
                        errorMessage = null
                    )
                )
            }
            
            Result.success(violations)
        } catch (e: Exception) {
            videoRepository.updateProcessingState(
                VideoProcessingState(
                    videoPath = videoPath,
                    status = ProcessingStatus.FAILED,
                    progress = 0,
                    currentFrame = 0,
                    totalFrames = 0,
                    violationCount = 0,
                    errorMessage = e.message
                )
            )
            Result.failure(e)
        }
    }
}

/**
 * 获取违章列表用例
 */
class GetViolationsUseCase(
    private val violationRepository: ViolationRepository
) {
    operator fun invoke(): Flow<List<ViolationRecord>> {
        return violationRepository.getAllViolations()
    }
}

/**
 * 获取处理进度用例
 */
class GetProcessingProgressUseCase(
    private val videoRepository: VideoRepository
) {
    operator fun invoke(videoPath: String): Flow<VideoProcessingState?> {
        return videoRepository.observeProcessingState(videoPath)
    }
}

/**
 * 获取视频信息用例
 */
class GetVideoInfoUseCase(
    private val videoRepository: VideoRepository
) {
    suspend operator fun invoke(videoPath: String): VideoFile? {
        return videoRepository.getVideoInfo(videoPath)
    }
}
