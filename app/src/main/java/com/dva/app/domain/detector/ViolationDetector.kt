package com.dva.app.domain.detector

import com.dva.app.domain.model.ViolationType

/**
 * 违章检测器接口
 * 使用策略模式，支持插件化扩展
 */
interface ViolationDetector {
    /**
     * 违章类型
     */
    val violationType: ViolationType
    
    /**
     * 检测优先级（数字越小优先级越高）
     */
    val priority: Int
    
    /**
     * 是否启用
     */
    var isEnabled: Boolean
    
    /**
     * 检测违章
     * @param context 检测上下文
     * @return 违章事件
     */
    suspend fun detect(context: DetectionContext): ViolationEvent?
    
    /**
     * 重置检测器状态
     */
    fun reset()
}

/**
 * 检测上下文
 */
data class DetectionContext(
    val frameIndex: Long,
    val timestampMs: Long,
    val vehicles: List<VehicleInfo>,
    val lanes: List<LaneInfo>,
    val trackedVehicles: List<TrackedVehicleInfo>
)

/**
 * 车辆信息
 */
data class VehicleInfo(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val category: String,
    val confidence: Float
)

/**
 * 车道线信息
 */
data class LaneInfo(
    val id: Int,
    val points: List<Pair<Float, Float>>,
    val type: String
)

/**
 * 追踪车辆信息
 */
data class TrackedVehicleInfo(
    val id: String,
    val history: List<PositionSnapshot>,
    val lastDirection: String?,
    val turnSignalState: TurnSignalState
)

/**
 * 位置快照
 */
data class PositionSnapshot(
    val x: Float,
    val y: Float,
    val timestampMs: Long
)

/**
 * 转向灯状态
 */
enum class TurnSignalState {
    OFF,
    LEFT,
    RIGHT,
    UNKNOWN
}

/**
 * 违章事件
 */
data class ViolationEvent(
    val type: ViolationType,
    val vehicleId: String,
    val timestampMs: Long,
    val confidence: Float,
    val laneChangeInfo: LaneChangeDetails?,
    val evidence: Map<String, Any> = emptyMap()
)

/**
 * 变道详情
 */
data class LaneChangeDetails(
    val fromLaneId: Int,
    val toLaneId: Int,
    val direction: String,
    val displacementX: Float,
    val trajectory: List<Pair<Float, Float>>
)
