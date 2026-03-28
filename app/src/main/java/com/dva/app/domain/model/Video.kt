package com.dva.app.domain.model

import android.graphics.RectF

/**
 * 视频文件实体
 */
data class Video(
    val id: String,
    val filePath: String,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val totalFrames: Long,
    val fileSize: Long,
    val createdAt: Long,
    val lastModified: Long,
    val thumbnailPath: String? = null
) {
    val formattedDuration: String
        get() {
            val seconds = durationMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
            } else {
                String.format("%02d:%02d", minutes, seconds % 60)
            }
        }

    val formattedFileSize: String
        get() {
            return when {
                fileSize >= 1_073_741_824 -> String.format("%.2f GB", fileSize / 1_073_741_824.0)
                fileSize >= 1_048_576 -> String.format("%.2f MB", fileSize / 1_048_576.0)
                fileSize >= 1024 -> String.format("%.2f KB", fileSize / 1024.0)
                else -> "$fileSize B"
            }
        }
}
