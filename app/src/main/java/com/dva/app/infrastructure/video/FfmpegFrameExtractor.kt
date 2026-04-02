package com.dva.app.infrastructure.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * FFmpegKit 帧提取器
 * 用于从各种格式的视频中提取帧，包括 .dav 监控格式
 */
class FfmpegFrameExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "FfmpegFrameExtractor"
    }
    
    /**
     * 使用 FFmpeg 提取指定时间戳的帧
     * @param videoPath 视频路径（本地文件路径）
     * @param timeUs 时间戳（微秒）
     * @param outputPath 输出图片路径
     * @return 是否成功
     */
    suspend fun extractFrame(videoPath: String, timeUs: Long, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // FFmpeg 时间格式: HH:MM:SS.microseconds
            val timeStr = formatTime(timeUs / 1000000.0)
            
            val command = "-y -ss $timeStr -i \"$videoPath\" -vframes 1 -q:v 2 \"$outputPath\""
            
            Log.d(TAG, "FFmpeg extract: $command")
            
            val session = FFmpegKit.execute(command)
            
            val returnCode = session.returnCode
            
            if (ReturnCode.isSuccess(returnCode)) {
                Log.d(TAG, "Frame extracted successfully: $outputPath")
                true
            } else {
                Log.e(TAG, "FFmpeg extract failed: ${session.failStackTrace}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg extract exception", e)
            false
        }
    }
    
    /**
     * 使用 FFmpeg 获取视频信息
     */
    suspend fun getVideoInfo(videoPath: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            // 使用 FFprobe 获取视频信息
            val command = "-v quiet -print_format json -show_format -show_streams \"$videoPath\""
            
            val session = FFmpegKit.execute(command)
            val output = session.output
            
            if (ReturnCode.isSuccess(session.returnCode) && output != null) {
                parseVideoInfo(output)
            } else {
                Log.e(TAG, "FFprobe failed: ${session.failStackTrace}")
                // 降级到 MediaMetadataRetriever
                getVideoInfoFallback(videoPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFprobe exception", e)
            getVideoInfoFallback(videoPath)
        }
    }
    
    /**
     * 解析 FFprobe 输出
     */
    private fun parseVideoInfo(json: String): VideoInfo? {
        try {
            // 简单的 JSON 解析（避免引入 Gson 依赖）
            val width = json.extractJsonInt("width")
            val height = json.extractJsonInt("height")
            val duration = json.extractJsonDouble("duration")
            val fps = json.extractJsonDouble("r_frame_rate")
            
            if (width != null && height != null) {
                val fpsValue = if (fps != null) {
                    // fps 可能是 "30/1" 格式
                    val parts = fps.toString().split("/")
                    if (parts.size == 2) {
                        parts[0].toDouble() / parts[1].toDouble()
                    } else {
                        fps
                    }
                } else 25.0
                
                val frameCount = if (duration != null && fpsValue > 0) {
                    (duration * fpsValue).toInt()
                } else 0
                
                return VideoInfo(
                    width = width,
                    height = height,
                    durationMs = ((duration ?: 0.0) * 1000).toLong(),
                    fps = fpsValue.toFloat(),
                    frameCount = frameCount
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error", e)
        }
        return null
    }
    
    /**
     * 降级方案：使用 MediaMetadataRetriever 获取视频信息
     */
    private fun getVideoInfoFallback(videoPath: String): VideoInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val fps = fpsStr?.toFloatOrNull() ?: 25f
            
            retriever.release()
            
            VideoInfo(
                width = width,
                height = height,
                durationMs = duration,
                fps = fps,
                frameCount = ((duration / 1000.0) * fps).toInt()
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaMetadataRetriever fallback failed", e)
            null
        }
    }
    
    /**
     * 格式化为 FFmpeg 时间格式
     */
    private fun formatTime(seconds: Double): String {
        val hours = (seconds / 3600).toInt()
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = seconds % 60
        return String.format("%02d:%02d:%06.3f", hours, minutes, secs)
    }
    
    data class VideoInfo(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val fps: Float,
        val frameCount: Int
    )
}

// 扩展函数：简单的 JSON 解析
private fun String.extractJsonInt(key: String): Int? {
    val pattern = "\"$key\"\\s*:\\s*(\\d+)"
    val regex = Regex(pattern)
    return regex.find(this)?.groupValues?.get(1)?.toIntOrNull()
}

private fun String.extractJsonDouble(key: String): Double? {
    val pattern = "\"$key\"\\s*:\\s*([\\d.]+)"
    val regex = Regex(pattern)
    return regex.find(this)?.groupValues?.get(1)?.toDoubleOrNull()
}
