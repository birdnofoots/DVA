package com.dva.app.infrastructure.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dva.app.data.local.file.OutputManager
import com.dva.app.domain.model.ImageFormat
import com.dva.app.domain.model.Screenshot
import com.dva.app.domain.model.ScreenshotType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 截图采集器
 * 负责从视频中截取违章证据截图
 */
@Singleton
class ScreenshotCapturer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameExtractor: FrameExtractor,
    private val outputManager: OutputManager
) {
    companion object {
        /**
         * 违章截图时间偏移（毫秒）
         */
        val VIOLATION_SCREENSHOT_OFFSETS = listOf(-5000L, 0L, 5000L)
        
        /**
         * PNG 无损压缩质量
         */
        const val LOSSLESS_QUALITY = 100
    }

    /**
     * 截图配置
     */
    data class CaptureConfig(
        val format: ImageFormat = ImageFormat.PNG,
        val quality: Int = LOSSLESS_QUALITY,
        val maintainAspectRatio: Boolean = true
    )

    /**
     * 采集结果
     */
    data class CaptureResult(
        val screenshots: List<Screenshot>,
        val outputDirectory: String
    )

    /**
     * 截取违章证据截图
     * @param videoFilePath 视频文件路径
     * @param violationTimeMs 违章发生时间（毫秒）
     * @param violationId 违章ID
     * @param plateNumber 车牌号（用于命名）
     * @param config 截图配置
     */
    suspend fun captureViolationScreenshots(
        videoFilePath: String,
        violationTimeMs: Long,
        violationId: String,
        plateNumber: String? = null,
        config: CaptureConfig = CaptureConfig()
    ): Result<CaptureResult> = withContext(Dispatchers.IO) {
        try {
            // 确保视频已打开
            if (!frameExtractor.isOpened() || frameExtractor.getCurrentFilePath() != videoFilePath) {
                val openResult = frameExtractor.open(videoFilePath)
                if (openResult.isFailure) {
                    return@withContext Result.failure(
                        openResult.exceptionOrNull() ?: Exception("Failed to open video")
                    )
                }
            }

            val metadata = frameExtractor.getMetadata()
                .getOrNull() ?: return@withContext Result.failure(
                Exception("Failed to get video metadata")
            )

            val videoFileName = File(videoFilePath).name
            val outputDir = outputManager.getViolationScreenshotsDir(videoFileName, violationId)
            
            val screenshots = mutableListOf<Screenshot>()
            
            // 截取三帧：T-5s, T0, T+5s
            for ((index, offset) in VIOLATION_SCREENSHOT_OFFSETS.withIndex()) {
                val timestamp = (violationTimeMs + offset).coerceIn(0, metadata.durationMs)
                val screenshotType = when (index) {
                    0 -> ScreenshotType.BEFORE
                    1 -> ScreenshotType.MOMENT
                    else -> ScreenshotType.AFTER
                }
                
                val frameResult = frameExtractor.extractFrame(timestamp)
                
                if (frameResult.isFailure) {
                    continue // 跳过失败的帧
                }
                
                val frame = frameResult.getOrNull() ?: continue
                
                // 构建文件名
                val prefix = plateNumber?.replace(" ", "_")?.replace("[^a-zA-Z0-9_]", "") ?: "unknown"
                val fileName = "${prefix}_${screenshotType.name}_${timestamp}.${config.format.extension}"
                val outputPath = File(outputDir, fileName).absolutePath
                
                // 保存截图
                val saveResult = saveBitmap(
                    bitmap = frame.bitmap,
                    outputPath = outputPath,
                    format = config.format,
                    quality = config.quality
                )
                
                if (saveResult.isSuccess) {
                    val file = saveResult.getOrNull()
                    screenshots.add(
                        Screenshot(
                            id = UUID.randomUUID().toString(),
                            violationId = violationId,
                            type = screenshotType,
                            filePath = outputPath,
                            timestampMs = timestamp,
                            width = frame.bitmap.width,
                            height = frame.bitmap.height,
                            format = config.format,
                            fileSize = file?.length() ?: 0L
                        )
                    )
                }
                
                // 回收 bitmap
                frame.bitmap.recycle()
            }
            
            if (screenshots.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to capture any screenshots"))
            }
            
            Result.success(
                CaptureResult(
                    screenshots = screenshots,
                    outputDirectory = outputDir.absolutePath
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 截取单帧截图
     */
    suspend fun captureSingleFrame(
        videoFilePath: String,
        timestampMs: Long,
        outputPath: String,
        format: ImageFormat = ImageFormat.PNG,
        quality: Int = LOSSLESS_QUALITY
    ): Result<Screenshot> = withContext(Dispatchers.IO) {
        try {
            // 确保视频已打开
            if (!frameExtractor.isOpened() || frameExtractor.getCurrentFilePath() != videoFilePath) {
                val openResult = frameExtractor.open(videoFilePath)
                if (openResult.isFailure) {
                    return@withContext Result.failure(
                        openResult.exceptionOrNull() ?: Exception("Failed to open video")
                    )
                }
            }
            
            val frameResult = frameExtractor.extractFrame(timestampMs)
            if (frameResult.isFailure) {
                return@withContext Result.failure(
                    frameResult.exceptionOrNull() ?: Exception("Failed to extract frame")
                )
            }
            
            val frame = frameResult.getOrNull() ?: return@withContext Result.failure(
                Exception("Frame is null")
            )
            
            val saveResult = saveBitmap(
                bitmap = frame.bitmap,
                outputPath = outputPath,
                format = format,
                quality = quality
            )
            
            frame.bitmap.recycle()
            
            if (saveResult.isFailure) {
                return@withContext Result.failure(
                    saveResult.exceptionOrNull() ?: Exception("Failed to save screenshot")
                )
            }
            
            val file = saveResult.getOrNull()
            Result.success(
                Screenshot(
                    id = UUID.randomUUID().toString(),
                    violationId = "",
                    type = ScreenshotType.MOMENT,
                    filePath = outputPath,
                    timestampMs = timestampMs,
                    width = frame.bitmap.width,
                    height = frame.bitmap.height,
                    format = format,
                    fileSize = file?.length() ?: 0L
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 保存 Bitmap 到文件
     */
    private fun saveBitmap(
        bitmap: Bitmap,
        outputPath: String,
        format: ImageFormat,
        quality: Int
    ): Result<File> {
        return try {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { out ->
                bitmap.compress(
                    when (format) {
                        ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                        ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                        ImageFormat.WEBP -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    },
                    quality,
                    out
                )
            }
            
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从文件加载截图
     */
    fun loadScreenshot(filePath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 删除截图文件
     */
    fun deleteScreenshot(filePath: String): Boolean {
        return try {
            File(filePath).delete()
        } catch (e: Exception) {
            false
        }
    }
}
