package com.dva.app.infrastructure.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 帧提取器
 * 目前使用 MediaMetadataRetriever
 * 未来可升级为 FFmpegKit 以获得更好的性能
 */
class FfmpegFrameExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "FfmpegFrameExtractor"
    }
    
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress
    
    /**
     * 提取单帧
     */
    suspend fun extractFrame(
        inputPath: String,
        frameIndex: Int,
        outputPath: String? = null
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
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
            Log.e(TAG, "extractFrame failed", e)
            null
        }
    }
    
    /**
     * 批量提取帧（按间隔）
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
     * 获取视频信息
     */
    suspend fun getVideoInfo(inputPath: String): VideoInfo? = withContext(Dispatchers.IO) {
        try {
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
            Log.e(TAG, "getVideoInfo failed", e)
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
