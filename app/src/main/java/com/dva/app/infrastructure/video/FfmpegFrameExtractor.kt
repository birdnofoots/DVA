package com.dva.app.infrastructure.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 基于 FFmpeg 的快速帧提取器
 * 比 MediaMetadataRetriever 更快更灵活
 */
class FfmpegFrameExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "FfmpegFrameExtractor"
        
        // 临时目录
        private const val TEMP_DIR = "frame_extract_temp"
    }
    
    private val tempDir: File by lazy {
        File(context.cacheDir, TEMP_DIR).also { it.mkdirs() }
    }
    
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress
    
    /**
     * 提取单帧
     * @param inputPath 文件路径或 content:// URI
     * @param frameIndex 帧索引
     * @param outputPath 可选，输出文件路径
     */
    suspend fun extractFrame(
        inputPath: String,
        frameIndex: Int,
        outputPath: String? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        val targetPath = outputPath ?: File(tempDir, "frame_${System.currentTimeMillis()}_$frameIndex.png").absolutePath
        
        // FFmpeg 命令：提取指定帧
        // -ss 指定时间位置（秒）
        // -i 输入文件
        // -frames:v 1 输出1帧
        // -q:v 2 质量 2-31，越小越清晰
        val timeSeconds = frameIndex / 25.0  // 假设25fps
        val command = "-ss $timeSeconds -i \"$inputPath\" -frames:v 1 -q:v 2 \"$targetPath\" -y"
        
        Log.d(TAG, "FFmpeg extract frame: $command")
        
        try {
            val session = FFmpegKit.execute(command)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                val file = File(targetPath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    if (outputPath == null) {
                        file.delete()  // 临时文件用完删除
                    }
                    return@withContext bytes
                }
            } else {
                Log.e(TAG, "FFmpeg failed: ${session.failStackTrace}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg exception", e)
        }
        
        // FFmpeg 失败时回退到 MediaMetadataRetriever
        fallbackExtractFrame(inputPath, frameIndex)
    }
    
    /**
     * 批量提取帧（按间隔）
     * @param inputPath 输入路径
     * @param startFrame 起始帧
     * @param endFrame 结束帧
     * @param interval 间隔（每多少帧取一帧）
     * @param onProgress 进度回调
     */
    suspend fun extractFrameRange(
        inputPath: String,
        startFrame: Int,
        endFrame: Int,
        interval: Int = 1,
        onProgress: ((Int) -> Unit)? = null
    ): List<Pair<Int, ByteArray>> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        val totalFrames = ((endFrame - startFrame) / interval).coerceAtLeast(1)
        var processed = 0
        
        for (frameIndex in startFrame until endFrame step interval) {
            val frameData = extractFrame(inputPath, frameIndex)
            if (frameData != null) {
                frames.add(Pair(frameIndex, frameData))
            }
            
            processed++
            val progressPercent = (processed * 100) / totalFrames
            _progress.value = progressPercent
            onProgress?.invoke(progressPercent)
        }
        
        frames
    }
    
    /**
     * 使用 FFmpeg 提取帧并保存到文件
     */
    suspend fun extractFrameToFile(
        inputPath: String,
        frameIndex: Int,
        outputPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val timeSeconds = frameIndex / 25.0
        val command = "-ss $timeSeconds -i \"$inputPath\" -frames:v 1 -q:v 2 \"$outputPath\" -y"
        
        try {
            val session = FFmpegKit.execute(command)
            ReturnCode.isSuccess(session.returnCode)
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg extract to file failed", e)
            false
        }
    }
    
    /**
     * 获取视频信息
     */
    suspend fun getVideoInfo(inputPath: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            // 使用 FFmpeg 获取视频信息
            val session = FFmpegKit.execute("-i \"$inputPath\"")
            val output = session.output
            
            // 解析输出获取信息
            // 格式: Duration: 00:01:30.50, bitrate: 2000 kb/s
            // Stream #0:0: Video: h264, ...
            
            var duration = 0L
            var width = 0
            var height = 0
            var fps = 25f
            
            // 解析 Duration
            val durationRegex = "Duration: (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{2})".toRegex()
            durationRegex.find(output)?.let { match ->
                val (h, m, s, cs) = match.destructured
                duration = (h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + cs.toLong() * 10
            }
            
            // 解析 Resolution
            val resRegex = "(\\d{2,5})x(\\d{2,5})".toRegex()
            resRegex.find(output)?.let { match ->
                width = match.groupValues[1].toInt()
                height = match.groupValues[2].toInt()
            }
            
            // 解析 FPS
            val fpsRegex = "(\\d+(?:\\.\\d+)?)\\s*fps".toRegex()
            fpsRegex.find(output)?.let { match ->
                fps = match.groupValues[1].toFloat()
            }
            
            val frameCount = ((duration / 1000.0) * fps).toInt()
            
            VideoInfo(
                duration = duration,
                width = width,
                height = height,
                fps = fps,
                frameCount = frameCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "getVideoInfo failed", e)
            // 回退到 MediaMetadataRetriever
            fallbackGetVideoInfo(inputPath)
        }
    }
    
    /**
     * 清理临时文件
     */
    fun cleanup() {
        tempDir.listFiles()?.forEach { it.delete() }
    }
    
    /**
     * 回退方案：使用 MediaMetadataRetriever
     */
    private fun fallbackExtractFrame(inputPath: String, frameIndex: Int): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            
            if (inputPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(inputPath))
            } else {
                retriever.setDataSource(inputPath)
            }
            
            val timeUs = (frameIndex * 1000000L / 25)
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            retriever.release()
            
            frame?.let { bitmapToJpeg(it) }
        } catch (e: Exception) {
            Log.e(TAG, "fallbackExtractFrame failed", e)
            null
        }
    }
    
    private fun fallbackGetVideoInfo(inputPath: String): VideoInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            
            if (inputPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(inputPath))
            } else {
                retriever.setDataSource(inputPath)
            }
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            
            retriever.release()
            
            val fps = 25f
            val frameCount = ((duration / 1000.0) * fps).toInt()
            
            VideoInfo(duration, width, height, fps, frameCount)
        } catch (e: Exception) {
            Log.e(TAG, "fallbackGetVideoInfo failed", e)
            null
        }
    }
    
    private fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
    
    data class VideoInfo(
        val duration: Long,
        val width: Int,
        val height: Int,
        val fps: Float,
        val frameCount: Int
    )
}
