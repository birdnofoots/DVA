package com.dva.app.domain.model

/**
 * 截图实体
 */
data class Screenshot(
    val id: String,
    val violationId: String,
    val type: ScreenshotType,
    val filePath: String,
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val format: ImageFormat = ImageFormat.PNG,
    val fileSize: Long
) {
    val formattedTimestamp: String
        get() {
            val seconds = timestampMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        }
}

/**
 * 截图类型
 * @param timeOffset 相对于违章时刻的时间偏移（毫秒）
 */
enum class ScreenshotType(
    val timeOffset: Long,
    val displayName: String
) {
    BEFORE(-5000, "违章前5秒"),
    MOMENT(0, "违章时刻"),
    AFTER(5000, "违章后5秒");
    
    companion object {
        fun fromOffset(offset: Long): ScreenshotType {
            return entries.minByOrNull { kotlin.math.abs(it.timeOffset - offset) } ?: MOMENT
        }
    }
}

/**
 * 图片格式
 */
enum class ImageFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp")
}
