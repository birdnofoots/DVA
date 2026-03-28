package com.dva.app.domain.model

import android.graphics.Bitmap

/**
 * 视频帧实体
 */
data class Frame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val frameIndex: Long,
    val width: Int = bitmap.width,
    val height: Int = bitmap.height
) {
    val formattedTimestamp: String
        get() {
            val seconds = timestampMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val millis = timestampMs % 1000
            return String.format("%02d:%02d:%02d.%03d", hours, minutes % 60, seconds % 60, millis)
        }
}

/**
 * 分析进度
 */
data class AnalysisProgress(
    val taskId: String,
    val currentFrame: Long,
    val totalFrames: Long,
    val progressPercent: Float,
    val currentTimeMs: Long,
    val fps: Float,
    val violationsFound: Int = 0
) {
    val formattedTime: String
        get() {
            val seconds = currentTimeMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        }
}
