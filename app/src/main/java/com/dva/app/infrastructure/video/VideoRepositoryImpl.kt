package com.dva.app.infrastructure.video

import android.content.Context
import android.database.Cursor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
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
 * 使用 MediaCodec 和 FFmpegKit 进行视频解码和帧提取
 */
class VideoRepositoryImpl(
    private val context: Context
) : VideoRepository {
    
    companion object {
        private const val TAG = "VideoRepository"
    }
    
    private val processingStates = mutableMapOf<String, MutableStateFlow<VideoProcessingState>>()
    
    // FFmpeg 帧提取器（用于支持更多视频格式如 .dav）
    private val ffmpegExtractor by lazy { FfmpegFrameExtractor(context) }
    
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
    
    /**
     * 使用 SAF URI 扫描视频文件
     * 优先使用 DocumentFile，失败时回退到 MediaStore
     */
    override suspend fun scanUri(uri: Uri): List<VideoFile> = withContext(Dispatchers.IO) {
        // 方案1：尝试使用 DocumentFile
        var videos = scanWithDocumentFile(uri)
        
        // 方案2：如果 DocumentFile 返回空，尝试使用 MediaStore
        // 这对百度网盘等虚拟文件系统更友好
        if (videos.isEmpty()) {
            videos = scanWithMediaStore(uri)
        }
        
        // 方案3：如果还是空，尝试使用 content resolver 直接查询
        if (videos.isEmpty()) {
            videos = scanWithContentResolver(uri)
        }
        
        videos
    }
    
    /**
     * 使用 DocumentFile 扫描（标准 SAF）
     */
    private suspend fun scanWithDocumentFile(uri: Uri): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        try {
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            
            documentFile?.listFiles()?.forEach { file ->
                if (file.isFile && isVideoFile(file.name ?: "")) {
                    val videoInfo = getVideoInfoFromUri(file.uri)
                    if (videoInfo != null) {
                        videos.add(videoInfo)
                    }
                }
            }
        } catch (e: Exception) {
            // DocumentFile 失败，返回空列表让调用方尝试其他方案
        }
        
        videos
    }
    
    /**
     * 使用 MediaStore 扫描（对虚拟文件系统更友好）
     */
    private suspend fun scanWithMediaStore(treeUri: Uri): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        try {
            // 从 tree URI 提取 document ID
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            
            // 使用 MediaStore 查询该目录下的视频
            val projection = arrayOf(
                android.provider.MediaStore.Video.Media._ID,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                android.provider.MediaStore.Video.Media.DURATION,
                android.provider.MediaStore.Video.Media.SIZE,
                android.provider.MediaStore.Video.Media.WIDTH,
                android.provider.MediaStore.Video.Media.HEIGHT
            )
            
            // 构建 selection 和 selectionArgs 来限定目录
            val selection = "${android.provider.MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            val relativePath = getRelativePathFromDocId(docId)
            val selectionArgs = arrayOf("$relativePath%")
            
            context.contentResolver.query(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                if (relativePath.isNotEmpty()) selection else null,
                if (relativePath.isNotEmpty()) selectionArgs else null,
                "${android.provider.MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
                val widthColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.HEIGHT)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "unknown"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    
                    val contentUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    
                    videos.add(
                        VideoFile(
                            path = contentUri.toString(),
                            name = name,
                            durationMs = duration,
                            width = width,
                            height = height,
                            fps = 25f,
                            frameCount = ((duration / 1000.0) * 25).toInt(),
                            fileSize = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // MediaStore 也失败，返回空
        }
        
        videos
    }
    
    /**
     * 使用 ContentResolver 直接查询
     * 注意：此方法在某些设备上可能不可用
     */
    private suspend fun scanWithContentResolver(uri: Uri): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        try {
            // 使用 ContentResolver.openInputStream 逐个检查文件
            val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            
            documentFile?.listFiles()?.forEach { file ->
                if (file.isFile && isVideoFile(file.name ?: "")) {
                    try {
                        // 尝试打开输入流验证文件
                        context.contentResolver.openInputStream(file.uri)?.use { stream ->
                            // 文件可读，添加到列表
                            val videoInfo = getVideoInfoFromUri(file.uri)
                            if (videoInfo != null) {
                                videos.add(videoInfo)
                            }
                        }
                    } catch (e: Exception) {
                        // 文件无法打开，跳过
                    }
                }
            }
        } catch (e: Exception) {
            // ContentResolver 也失败
        }
        
        videos
    }
    
    /**
     * 从 DocId 提取相对路径
     */
    private fun getRelativePathFromDocId(docId: String): String {
        // docId 格式通常是 "primary:DCIM/Camera" 或 "com.baidu.netdisk:/DCIM/Camera"
        return if (docId.contains(":")) {
            docId.substringAfter(":")
        } else {
            docId
        }
    }
    
    /**
     * 从 URI 获取视频信息（SAF 方式）
     */
    private suspend fun getVideoInfoFromUri(uri: Uri): VideoFile? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            retriever.release()
            
            // 从 URI 获取文件名
            val fileName = getFileNameFromUri(uri) ?: "unknown"
            
            // 获取文件大小
            val fileSize = getFileSizeFromUri(uri)
            
            VideoFile(
                path = uri.toString(), // 使用 URI 作为路径
                name = fileName,
                durationMs = durationMs,
                width = if (rotation == 90 || rotation == 270) height else width,
                height = if (rotation == 90 || rotation == 270) width else height,
                fps = 25f, // SAF 无法直接获取帧率，使用默认值
                frameCount = ((durationMs / 1000.0) * 25).toInt(),
                fileSize = fileSize
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从 URI 获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            } else null
        } catch (e: Exception) {
            // Fallback: 从 URI 解析
            uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }
    
    /**
     * 从 URI 获取文件大小
     */
    private fun getFileSizeFromUri(uri: Uri): Long {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    cursor.getLong(sizeIndex)
                } else 0L
            } else 0L
        } catch (e: Exception) {
            0L
        } finally {
            cursor?.close()
        }
    }
    
    /**
     * 检查是否为视频文件
     */
    private fun isVideoFile(fileName: String): Boolean {
        val videoExtensions = setOf("mp4", "mov", "avi", "mkv", "3gp", "webm", "3g2", "m4v")
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in videoExtensions
    }
    
    override suspend fun getVideoInfo(videoPath: String): VideoFile? = withContext(Dispatchers.IO) {
        // 首先尝试使用 FFmpegKit 获取视频信息（支持更多格式如 .dav）
        if (!videoPath.startsWith("content://")) {
            try {
                val ffmpegInfo = ffmpegExtractor.getVideoInfo(videoPath)
                if (ffmpegInfo != null) {
                    val file = File(videoPath)
                    return@withContext VideoFile(
                        path = videoPath,
                        name = file.name,
                        durationMs = ffmpegInfo.durationMs,
                        width = ffmpegInfo.width,
                        height = ffmpegInfo.height,
                        fps = ffmpegInfo.fps,
                        frameCount = ffmpegInfo.frameCount,
                        fileSize = file.length()
                    )
                }
            } catch (e: Exception) {
                // FFmpegKit 失败，继续使用 MediaMetadataRetriever
            }
        }
        
        // 降级方案：使用 MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            
            // 判断是文件路径还是 content:// URI
            if (videoPath.startsWith("content://")) {
                val uri = Uri.parse(videoPath)
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(videoPath)
            }
            
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            retriever.release()
            
            // 根据路径类型获取文件名和大小
            val (name, fileSize, fps) = if (videoPath.startsWith("content://")) {
                val uri = Uri.parse(videoPath)
                Triple(
                    getFileNameFromUri(uri) ?: "unknown",
                    getFileSizeFromUri(uri),
                    25f  // SAF 无法获取帧率，使用默认值
                )
            } else {
                val file = File(videoPath)
                Triple(file.name, file.length(), estimateFps(videoPath))
            }
            
            val frameCount = ((durationMs / 1000.0) * fps).toInt()
            
            VideoFile(
                path = videoPath,
                name = name,
                durationMs = durationMs,
                width = if (rotation == 90 || rotation == 270) height else width,
                height = if (rotation == 90 || rotation == 270) width else height,
                fps = fps,
                frameCount = frameCount,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun copyToLocalCache(videoUri: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!videoUri.startsWith("content://")) {
                return@withContext videoUri // 已经是本地文件，不需要复制
            }
            
            val uri = Uri.parse(videoUri)
            val fileName = getFileNameFromUri(uri) ?: "video_${System.currentTimeMillis()}.mp4"
            val cacheDir = File(context.cacheDir, "video_cache")
            cacheDir.mkdirs()
            val outputFile = File(cacheDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            outputFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (!cacheDir.exists()) return@withContext 0L
            
            var size = 0L
            cacheDir.listFiles()?.forEach { file ->
                size += file.length()
            }
            size
        } catch (e: Exception) {
            0L
        }
    }
    
    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // 忽略清理失败
        }
    }
    
    override suspend fun extractFrame(videoPath: String, frameIndex: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            
            // 判断是文件路径还是 content:// URI
            if (videoPath.startsWith("content://")) {
                val uri = Uri.parse(videoPath)
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(videoPath)
            }
            
            val fps = getFps(videoPath) ?: 25f
            val timeUs = (frameIndex * 1000000L / fps).toLong()
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            
            retriever.release()
            
            // 返回 JPEG 编码的图片数据（已缩小到 640x640）
            frame?.let { bitmap ->
                // 缩小到 640x640
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 640, 640, true)
                val outputStream = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
                if (scaled != bitmap) scaled.recycle()
                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 使用 FFmpegKit 批量提取帧（优化版）
     * 直接缩小到 640x640 并输出 JPEG，大幅提升性能
     */
    override suspend fun extractFrameRange(
        videoPath: String,
        startFrame: Int,
        endFrame: Int,
        interval: Int
    ): List<Pair<Int, ByteArray>> = withContext(Dispatchers.IO) {
        // 计算要提取的时间点
        val frameIndices = (startFrame until endFrame step interval).toList()
        if (frameIndices.isEmpty()) {
            return@withContext emptyList()
        }
        
        // 简单方案: 用 FFmpeg 一次seek到一个时间点，提取单帧
        // 这样比 MediaMetadataRetriever 快很多
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        
        for (frameIdx in frameIndices) {
            try {
                val frameBytes = extractSingleFrameWithFfmpeg(videoPath, frameIdx)
                if (frameBytes != null) {
                    frames.add(Pair(frameIdx, frameBytes))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract frame $frameIdx", e)
            }
        }
        
        return@withContext frames
    }
    
    /**
     * 使用 FFmpeg 提取单帧
     * 比 MediaMetadataRetriever 快
     */
    private suspend fun extractSingleFrameWithFfmpeg(videoPath: String, frameIndex: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val fps = getFps(videoPath) ?: 25f
            val timeSeconds = frameIndex / fps
            
            // 创建临时文件
            val tempFile = File(context.cacheDir, "frame_${System.currentTimeMillis()}_$frameIndex.jpg")
            
            // FFmpeg 命令: seek到时间点，提取一帧，缩放到 640x640
            val command = "-y -ss %.3f -i \"$videoPath\" -vframes 1 -vf \"scale=640:640:force_original_aspect_ratio=decrease,pad=640:640:(ow-iw)/2:(oh-ih)/2\" -q:v 2 \"${tempFile.absolutePath}\"".format(timeSeconds)
            
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode
            
            return@withContext if (ReturnCode.isSuccess(returnCode) && tempFile.exists()) {
                val bytes = tempFile.readBytes()
                tempFile.delete()
                bytes
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg extract failed for frame $frameIndex", e)
            null
        }
    }
    
    /**
     * 降级方案: 使用 MediaMetadataRetriever 提取帧
     */
    private suspend fun extractFramesWithMediaRetriever(
        videoPath: String,
        frameIndices: List<Int>,
        fps: Float
    ): List<Pair<Int, ByteArray>> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Pair<Int, ByteArray>>()
        
        for (frameIndex in frameIndices) {
            extractFrame(videoPath, frameIndex)?.let { frameData ->
                frames.add(Pair(frameIndex, frameData))
            }
        }
        
        frames
    }
    
    /**
     * 获取视频 FPS
     */
    private suspend fun getFps(videoPath: String): Float? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }
            
            val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            retriever.release()
            
            fpsStr?.toFloatOrNull() ?: 25f
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FPS", e)
            25f
        }
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
     * 注意：content:// URI 可能无法正确获取帧率，默认返回 25fps
     */
    private fun estimateFps(videoPath: String): Float {
        // SAF URI 无法使用 MediaExtractor 获取帧率，直接返回默认值
        if (videoPath.startsWith("content://")) {
            return 25f
        }
        
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
