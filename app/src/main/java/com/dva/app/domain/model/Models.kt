package com.dva.app.domain.model

/**
 * 违章类型枚举
 */
enum class ViolationType {
    LANE_CHANGE_NO_SIGNAL,  // 不打灯变道
    RED_LIGHT,              // 闯红灯（待实现）
    WRONG_LANE              // 不按规定车道行驶（待实现）
}

/**
 * 违章记录
 */
data class ViolationRecord(
    val id: Long = 0,
    val videoPath: String,
    val violationType: ViolationType,
    val plateNumber: String?,
    val plateConfidence: Float,
    val timestamp: Long,
    val frameIndex: Int,
    val beforeImagePath: String,
    val duringImagePath: String,
    val afterImagePath: String,
    val annotatedImagePath: String?,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 视频文件
 */
data class VideoFile(
    val path: String,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val frameCount: Int,
    val fileSize: Long
)

/**
 * 处理状态
 */
enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

/**
 * 视频处理进度
 */
data class VideoProcessingState(
    val videoPath: String,
    val status: ProcessingStatus,
    val progress: Int,          // 0-100
    val currentFrame: Int,
    val totalFrames: Int,
    val violationCount: Int,
    val errorMessage: String?
)

/**
 * 车辆检测结果
 */
data class VehicleDetection(
    val frameIndex: Int,
    val trackId: Int,
    val classId: Int,
    val className: String,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val centerX: Float,
    val centerY: Float
)

/**
 * 边界框
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + width / 2
    val centerY: Float get() = top + height / 2
}

/**
 * 车道线检测结果
 */
data class LaneDetection(
    val frameIndex: Int,
    val lanes: List<LaneLine>
)

/**
 * 车道线
 */
data class LaneLine(
    val points: List<PointF>,
    val isSolid: Boolean,
    val isLeft: Boolean
)

/**
 * 点坐标
 */
data class PointF(
    val x: Float,
    val y: Float
)
