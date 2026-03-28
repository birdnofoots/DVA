package com.dva.app.infrastructure.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 视频帧提取器
 * 提供从视频中提取指定时间点的帧的能力
 */
@Singleton
class FrameExtractor @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) {
    private var retriever: MediaMetadataRetriever? = null
    private var currentFilePath: String? = null
    private val mutex = Mutex()
    
    companion object {
        private const val FRAME_EXTRACTION_TIMEOUT = 5000L
    }

    /**
     * 帧提取结果
     */
    data class ExtractionResult(
        val bitmap: Bitmap,
        val timestampMs: Long,
        val frameIndex: Long
    )

    /**
     * 视频信息
     */
    data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val totalFrames: Long,
        val rotation: Int = 0
    )

    /**
     * 打开视频文件
     */
    suspend fun open(filePath: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                release()
                
                retriever = MediaMetadataRetriever().apply {
                    setDataSource(filePath)
                }
                
                currentFilePath = filePath
                
                val duration = retriever?.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                
                val width = retriever?.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 0
                
                val height = retriever?.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 0
                
                val frameRate = retriever?.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
                )?.toFloatOrNull() ?: 30f
                
                val rotation = retriever?.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0
                
                val totalFrames = if (frameRate > 0) {
                    ((duration / 1000.0) * frameRate).toLong()
                } else {
                    0L
                }
                
                Result.success(
                    VideoMetadata(
                        durationMs = duration,
                        width = width,
                        height = height,
                        frameRate = frameRate,
                        totalFrames = totalFrames,
                        rotation = rotation
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 提取指定时间点的帧
     * @param timestampMs 时间戳（毫秒）
     * @param quality 提取质量
     */
    suspend fun extractFrame(
        timestampMs: Long,
        quality: FrameQuality = FrameQuality.HIGH
    ): Result<ExtractionResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ret = retriever ?: return@withContext Result.failure(
                Exception("FrameExtractor not opened")
            )
            
            try {
                val metadata = getMetadataOrThrow()
                
                // 确保时间在有效范围内
                val clampedTime = timestampMs.coerceIn(0, metadata.durationMs)
                
                val frame = ret.getFrameAtTime(
                    clampedTime * 1000, // 转换为微秒
                    when (quality) {
                        FrameQuality.HIGHEST -> MediaMetadataRetriever.OPTION_CLOSEST
                        FrameQuality.HIGH -> MediaMetadataRetriever.OPTION_CLOSEST
                        FrameQuality.MEDIUM -> MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        FrameQuality.LOW -> MediaMetadataRetriever.OPTION_PREVIOUS_SYNC
                    }
                )
                
                if (frame == null) {
                    return@withContext Result.failure(Exception("Failed to extract frame at $timestampMs"))
                }
                
                val frameIndex = if (metadata.frameRate > 0) {
                    ((clampedTime / 1000.0) * metadata.frameRate).toLong()
                } else {
                    0L
                }
                
                Result.success(
                    ExtractionResult(
                        bitmap = frame,
                        timestampMs = clampedTime,
                        frameIndex = frameIndex
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 批量提取帧
     * @param timestamps 时间戳列表
     */
    suspend fun extractFrames(
        timestamps: List<Long>,
        quality: FrameQuality = FrameQuality.HIGH
    ): List<Result<ExtractionResult>> = withContext(Dispatchers.IO) {
        timestamps.map { timestamp ->
            extractFrame(timestamp, quality)
        }
    }

    /**
     * 提取多个时间点的帧（相对于基准时间）
     * @param baseTimeMs 基准时间
     * @param offsets 时间偏移列表（毫秒）
     */
    suspend fun extractFramesRelative(
        baseTimeMs: Long,
        offsets: List<Long>,
        quality: FrameQuality = FrameQuality.HIGH
    ): List<Result<ExtractionResult>> {
        val timestamps = offsets.map { baseTimeMs + it }
        return extractFrames(timestamps, quality)
    }

    /**
     * 获取缩略图
     */
    suspend fun getThumbnail(
        timeMs: Long = 0,
        width: Int = 200,
        height: Int = 150
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ret = retriever ?: return@withContext Result.failure(
                Exception("FrameExtractor not opened")
            )
            
            try {
                val frame = ret.getFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                
                if (frame == null) {
                    return@withContext Result.failure(Exception("Failed to get thumbnail"))
                }
                
                val thumbnail = Bitmap.createScaledBitmap(frame, width, height, true)
                if (thumbnail != frame) {
                    frame.recycle()
                }
                
                Result.success(thumbnail)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取视频元数据
     */
    suspend fun getMetadata(): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                Result.success(getMetadataOrThrow())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun getMetadataOrThrow(): VideoMetadata {
        val ret = retriever ?: throw Exception("FrameExtractor not opened")
        
        return VideoMetadata(
            durationMs = ret.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L,
            width = ret.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0,
            height = ret.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0,
            frameRate = ret.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull() ?: 30f,
            totalFrames = 0L,
            rotation = ret.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            retriever?.release()
            retriever = null
            currentFilePath = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * 检查是否已打开
     */
    fun isOpened(): Boolean = retriever != null

    /**
     * 获取当前文件路径
     */
    fun getCurrentFilePath(): String? = currentFilePath

    /**
     * 帧质量枚举
     */
    enum class FrameQuality {
        HIGHEST, // 最高质量，接近关键帧
        HIGH,    // 高质量
        MEDIUM,  // 中等质量
        LOW      // 低质量，接近关键帧
    }
}
