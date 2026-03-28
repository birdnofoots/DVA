package com.dva.app.data.local.file

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "3gp", "webm")
        
        val VIDEO_FOLDER_NAMES = listOf(
            "DCIM",
            "Movies",
            "Download",
            "Download/DashCam",
            "DashCam"
        )
    }

    suspend fun scanVideoFolder(folderPath: String? = null): List<File> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<File>()
        
        if (folderPath != null) {
            scanDirectory(File(folderPath), videos)
        } else {
            VIDEO_FOLDER_NAMES.forEach { folderName ->
                val path = getExternalPath(folderName)
                scanDirectory(File(path), videos)
            }
        }
        
        videos.sortedByDescending { it.lastModified() }
    }

    private fun scanDirectory(directory: File, results: MutableList<File>) {
        if (!directory.exists() || !directory.isDirectory) return
        
        directory.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> scanDirectory(file, results)
                isVideoFile(file) -> results.add(file)
            }
        }
    }

    private fun isVideoFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    suspend fun getVideoMetadata(filePath: String): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            
            retriever.release()
            
            VideoMetadata(
                durationMs = duration,
                width = width,
                height = height,
                frameRate = frameRate,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun getExternalPath(folderName: String): String {
        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        return when (folderName) {
            "DCIM" -> "${dcim.absolutePath}/Camera"
            else -> "${Environment.getExternalStorageDirectory().absolutePath}/$folderName"
        }
    }

    data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val mimeType: String
    ) {
        val totalFrames: Long
            get() = ((durationMs / 1000.0) * frameRate).toLong()
    }
}
