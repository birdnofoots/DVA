package com.dva.app.infrastructure.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.dva.app.domain.model.VideoFile
import com.dva.app.domain.model.VideoProcessingState
import com.dva.app.domain.model.ProcessingStatus
import com.dva.app.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Android 视频仓库实现
 * 使用 MediaCodec 进行视频解码和帧提取
 */
class VideoRepositoryImpl(
    private val context: Context
) : VideoRepository {
    
    private val processingStates = mutableMapOf<String, MutableStateFlow<VideoProcessingState>>()
    
    override suspend fun scanDirectory(directoryPath: String): List<VideoFile> = withContext(Dispatchers.IO) {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }
        
        val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "3gp", "webm")
        
        directory.listFiles()?.filter { file ->
            file.isFile && file.extension.lowercase() in videoExtensions
        }?.mapNotNull { file ->
            getVideoInfo(file.absolutePath)
        } ?: emptyList()
    }
    
    override suspend fun getVideoInfo(videoPath: String): VideoFile? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            retriever.release()
            
            val file = File(videoPath)
            val fps = estimateFps(videoPath)
            val frameCount = ((durationMs / 1000.0) * fps).toInt()
            
            VideoFile(
                path = videoPath,
                name = file.name,
                durationMs = durationMs,
                width = if (rotation == 90 || rotation == 270) height else width,
                height = if (rotation == 90 || rotation == 270) width else height,
                fps = fps,
                frameCount = frameCount,
                fileSize = file.length()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun extractFrame(videoPath: String, frameIndex: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            
            val timeUs = (frameIndex * 1000000L / 25) // 假设25fps
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            
            retriever.release()
            
            frame?.let { extractRgbFromBitmap(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun extractFrameRange(
        videoPath: String,
        startFrame: Int,
        endFrame: Int,
        interval: Int
    ): List<Pair<Int, ByteArray>> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        
        for (frameIndex in startFrame until endFrame step interval) {
            extractFrame(videoPath, frameIndex)?.let { frameData ->
                frames.add(Pair(frameIndex, frameData))
            }
        }
        
        frames
    }
    
    override suspend fun saveFrame(frameData: ByteArray, outputPath: String): String = withContext(Dispatchers.IO) {
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        
        FileOutputStream(file).use { fos ->
            fos.write(frameData)
        }
        
        outputPath
    }
    
    override fun observeProcessingState(videoPath: String): Flow<VideoProcessingState> {
        return processingStates.getOrPut(videoPath) {
            MutableStateFlow(
                VideoProcessingState(
                    videoPath = videoPath,
                    status = ProcessingStatus.PENDING,
                    progress = 0,
                    currentFrame = 0,
                    totalFrames = 0,
                    violationCount = 0,
                    errorMessage = null
                )
            )
        }
    }
    
    override suspend fun updateProcessingState(state: VideoProcessingState) {
        val flow = processingStates.getOrPut(state.videoPath) {
            MutableStateFlow(state)
        }
        flow.value = state
    }
    
    /**
     * 估算视频帧率
     */
    private fun estimateFps(videoPath: String): Float {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(videoPath)
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                if (mime.startsWith("video/")) {
                    val frameRate = format.getIntegerOrDefault(MediaFormat.KEY_FRAME_RATE, 25)
                    extractor.release()
                    return frameRate.toFloat()
                }
            }
            
            extractor.release()
            25f
        } catch (e: Exception) {
            25f
        }
    }
    
    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
        return try {
            if (containsKey(key)) getInteger(key) else default
        } catch (e: Exception) {
            default
        }
    }
    
    /**
     * 从 Bitmap 提取 RGB 数据
     */
    private fun extractRgbFromBitmap(bitmap: android.graphics.Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val rgbData = ByteArray(width * height * 3)
        var index = 0
        
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            rgbData[index++] = r.toByte()
            rgbData[index++] = g.toByte()
            rgbData[index++] = b.toByte()
        }
        
        return rgbData
    }
}
