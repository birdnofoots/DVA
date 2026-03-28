package com.dva.app.infrastructure.video

import android.graphics.Bitmap
import com.dva.app.data.local.file.CacheManager
import com.dva.app.infrastructure.video.FrameExtractor.VideoMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频处理器
 * 整合帧提取和缓存，提供高效的视频帧访问
 */
@Singleton
class VideoProcessor @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val frameExtractor: FrameExtractor,
    private val cacheManager: CacheManager
) {
    companion object {
        /**
         * 正常检测帧率
         */
        const val NORMAL_FPS = 15
        
        /**
         * 违章前后高帧率
         */
        const val DETAIL_FPS = 30
        
        /**
         * 默认帧间隔（毫秒）
         */
        const val DEFAULT_FRAME_INTERVAL_MS = 1000L / NORMAL_FPS
    }

    private val mutex = Mutex()
    private var currentVideoPath: String? = null
    private var durationMs: Long = 0
    private var frameRate: Float = 30f

    /**
     * 打开视频
     */
    suspend fun open(videoPath: String): Result<VideoMetadata> = Dispatchers.IO.let {
        mutex.withLock {
            val result = frameExtractor.open(videoPath)
            if (result.isSuccess) {
                currentVideoPath = videoPath
                val metadata = result.getOrNull()!!
                durationMs = metadata.durationMs
                frameRate = metadata.frameRate
                cacheManager.clear()
            }
            result
        }
    }

    /**
     * 获取帧（优先从缓存获取）
     */
    suspend fun getFrame(timestampMs: Long): Result<FrameResult> {
        // 检查缓存
        val cached = cacheManager.getCachedFrame(timestampMs)
        if (cached != null) {
            return Result.success(
                FrameResult(
                    bitmap = cached,
                    timestampMs = timestampMs,
                    fromCache = true
                )
            )
        }
        
        // 从视频提取
        val result = frameExtractor.extractFrame(timestampMs)
        if (result.isSuccess) {
            val frame = result.getOrNull()!!
            // 缓存帧
            cacheManager.cacheFrame(timestampMs, frame.bitmap)
            // 异步保存到磁盘缓存
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                cacheManager.saveFrameToDisk(timestampMs, frame.bitmap)
            }
            
            return Result.success(
                FrameResult(
                    bitmap = frame.bitmap,
                    timestampMs = frame.timestampMs,
                    fromCache = false
                )
            )
        }
        
        return Result.failure(result.exceptionOrNull() ?: Exception("Failed to get frame"))
    }

    /**
     * 批量获取帧
     */
    suspend fun getFrames(timestamps: List<Long>): List<Result<FrameResult>> {
        return timestamps.map { getFrame(it) }
    }

    /**
     * 生成帧采样时间点
     * @param startMs 开始时间
     * @param endMs 结束时间
     * @param fps 采样帧率
     */
    fun generateFrameTimestamps(
        startMs: Long,
        endMs: Long,
        fps: Int = NORMAL_FPS
    ): List<Long> {
        val interval = 1000L / fps
        val timestamps = mutableListOf<Long>()
        var current = startMs
        while (current <= endMs) {
            timestamps.add(current)
            current += interval
        }
        return timestamps
    }

    /**
     * 生成高密度采样时间点（用于违章检测）
     * @param baseTimeMs 基准时间
     * @param rangeMs 前后范围
     */
    fun generateHighDensityTimestamps(
        baseTimeMs: Long,
        rangeMs: Long = 1000L
    ): List<Long> {
        return generateFrameTimestamps(
            startMs = (baseTimeMs - rangeMs).coerceAtLeast(0),
            endMs = (baseTimeMs + rangeMs).coerceAtMost(durationMs),
            fps = DETAIL_FPS
        )
    }

    /**
     * 创建帧处理流
     * @param onProgress 进度回调
     */
    fun createFrameStream(
        onProgress: ((FrameProgress) -> Unit)? = null
    ): Flow<FrameProgress> = flow {
        val totalFrames = ((durationMs / 1000.0) * frameRate).toLong()
        var processedFrames = 0L
        var lastProgressUpdate = 0L
        
        while (processedFrames < totalFrames) {
            val timestampMs = (processedFrames * 1000 / frameRate).toLong()
            
            val frameResult = getFrame(timestampMs)
            frameResult.onSuccess { frame ->
                emit(
                    FrameProgress(
                        currentFrame = processedFrames,
                        totalFrames = totalFrames,
                        timestampMs = timestampMs,
                        bitmap = frame.bitmap,
                        progress = processedFrames.toFloat() / totalFrames
                    )
                )
            }
            
            processedFrames++
            
            // 每100帧更新一次进度
            if (processedFrames - lastProgressUpdate >= 100) {
                onProgress?.invoke(
                    FrameProgress(
                        currentFrame = processedFrames,
                        totalFrames = totalFrames,
                        timestampMs = timestampMs,
                        bitmap = null,
                        progress = processedFrames.toFloat() / totalFrames
                    )
                )
                lastProgressUpdate = processedFrames
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * 获取视频时长
     */
    fun getDurationMs(): Long = durationMs

    /**
     * 获取帧率
     */
    fun getFrameRate(): Float = frameRate

    /**
     * 获取总帧数
     */
    fun getTotalFrames(): Long = ((durationMs / 1000.0) * frameRate).toLong()

    /**
     * 检查是否已打开
     */
    fun isOpened(): Boolean = frameExtractor.isOpened()

    /**
     * 释放资源
     */
    fun release() {
        frameExtractor.release()
        cacheManager.clear()
        currentVideoPath = null
        durationMs = 0
        frameRate = 30f
    }

    /**
     * 帧结果
     */
    data class FrameResult(
        val bitmap: Bitmap,
        val timestampMs: Long,
        val fromCache: Boolean = false
    )

    /**
     * 帧处理进度
     */
    data class FrameProgress(
        val currentFrame: Long,
        val totalFrames: Long,
        val timestampMs: Long,
        val bitmap: Bitmap?,
        val progress: Float
    ) {
        val progressPercent: Int get() = (progress * 100).toInt()
    }
}
