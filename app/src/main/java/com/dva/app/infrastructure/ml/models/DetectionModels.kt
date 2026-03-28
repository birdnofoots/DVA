package com.dva.app.infrastructure.ml.models

import android.graphics.PointF
import android.graphics.RectF
import com.dva.app.domain.model.LaneType
import com.dva.app.domain.model.VehicleCategory

/**
 * 车辆检测结果
 */
data class VehicleDetectionResult(
    val boundingBox: RectF,
    val category: VehicleCategory,
    val confidence: Float,
    val classId: Int
)

/**
 * 车道线检测结果
 */
data class LaneDetectionResult(
    val points: List<PointF>,
    val laneType: LaneType,
    val laneId: Int,
    val confidence: Float
)

/**
 * 车牌识别结果
 */
data class LprResult(
    val plateNumber: String,
    val province: String,
    val letter: String,
    val digits: String,
    val confidence: Float,
    val boundingBox: RectF?,
    val plateColor: PlateColorResult
) {
    companion object {
        fun empty() = LprResult("", "", "", "", 0f, null, PlateColorResult.UNKNOWN)
    }
}

/**
 * 车牌颜色检测结果
 */
enum class PlateColorResult {
    BLUE, YELLOW, WHITE, BLACK, GREEN, UNKNOWN
}

/**
 * 追踪的车辆
 */
data class TrackedVehicle(
    val id: String,
    val currentBox: RectF,
    val history: List<VehiclePosition>,
    val category: VehicleCategory,
    val color: String?,
    val firstSeenFrame: Long,
    val lastSeenFrame: Long,
    val disappearedFrames: Int = 0
) {
    val latestPosition: VehiclePosition?
        get() = history.lastOrNull()
}

/**
 * 车辆位置
 */
data class VehiclePosition(
    val centerX: Float,
    val centerY: Float,
    val timestampMs: Long,
    val frameIndex: Long
)

/**
 * 变道事件
 */
data class LaneChangeEvent(
    val vehicleId: String,
    val fromLaneId: Int,
    val toLaneId: Int,
    val direction: LaneChangeDirection,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val trajectory: List<VehiclePosition>,
    val confidence: Float
)

/**
 * 变道方向
 */
enum class LaneChangeDirection {
    LEFT, RIGHT
}

/**
 * 检测器配置
 */
data class DetectorConfig(
    val confidenceThreshold: Float = 0.5f,
    val iouThreshold: Float = 0.45f,
    val maxDetections: Int = 100,
    val inputWidth: Int = 640,
    val inputHeight: Int = 640
)

/**
 * LPR 配置
 */
data class LprConfig(
    val confidenceThreshold: Float = 0.7f,
    val plateMinWidth: Int = 60,
    val plateMinHeight: Int = 30
)
