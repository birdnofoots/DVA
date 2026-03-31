package com.dva.app.infrastructure.video

import android.content.Context
import android.database.Cursor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
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
     */
    private suspend fun scanWithContentResolver(uri: Uri): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        try {
            val childrenUri = android.provider.DocumentsContract.buildDocumentChildrenUri(
                uri.authority ?: return@withContext videos,
                android.provider.DocumentsContract.getTreeDocumentId(uri)
            )
            
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_SIZE,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                
                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    
                    if (name != null && isVideoFile(name)) {
                        val videoInfo = getVideoInfoFromUri(uri)
                        if (videoInfo != null) {
                            videos.add(videoInfo)
                        }
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
