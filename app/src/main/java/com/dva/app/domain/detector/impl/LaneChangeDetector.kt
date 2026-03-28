package com.dva.app.domain.detector.impl

import com.dva.app.domain.detector.DetectionContext
import com.dva.app.domain.detector.LaneChangeDetails
import com.dva.app.domain.detector.LaneInfo
import com.dva.app.domain.detector.PositionSnapshot
import com.dva.app.domain.detector.TurnSignalState
import com.dva.app.domain.detector.TrackedVehicleInfo
import com.dva.app.domain.detector.ViolationDetector
import com.dva.app.domain.detector.ViolationEvent
import com.dva.app.domain.model.ViolationType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 变道不打灯违章检测器
 * 
 * 检测逻辑：
 * 1. 检测车辆跨越车道线
 * 2. 检查转向灯状态（目前通过轨迹分析推断）
 * 3. 若跨越车道线但转向灯未开启，则判定为违章
 */
@Singleton
class LaneChangeDetector @Inject constructor() : ViolationDetector {

    companion object {
        /**
         * 最小变道位移（像素）
         */
        private const val MIN_LANE_CHANGE_DISPLACEMENT = 60f
        
        /**
         * 最小轨迹长度
         */
        private const val MIN_TRAJECTORY_LENGTH = 5
        
        /**
         * 变道时间窗口（毫秒）
         */
        private const val LANE_CHANGE_TIME_WINDOW = 3000L
        
        /**
         * 违章确认所需最小置信度
         */
        private const val MIN_CONFIDENCE = 0.6f
        
        /**
         * 斜向行驶阈值
         */
        private const val DIAGONAL_THRESHOLD = 0.4f
    }

    override val violationType: ViolationType = ViolationType.LANE_CHANGE_WITHOUT_SIGNAL
    override val priority: Int = 10
    override var isEnabled: Boolean = true

    private val recentViolations = mutableMapOf<String, Long>() // vehicleId -> last violation time

    override suspend fun detect(context: DetectionContext): ViolationEvent? {
        if (!isEnabled) return null

        // 分析每个追踪的车辆
        for (trackedVehicle in context.trackedVehicles) {
            // 检查是否刚有违章记录（防止重复检测）
            val lastViolationTime = recentViolations[trackedVehicle.id]
            if (lastViolationTime != null && 
                context.timestampMs - lastViolationTime < LANE_CHANGE_TIME_WINDOW) {
                continue
            }

            // 检测变道
            val laneChange = detectLaneChange(trackedVehicle, context.lanes)
            if (laneChange != null) {
                // 检查转向灯状态
                val turnSignalState = trackedVehicle.turnSignalState
                
                // 如果转向灯是关闭的或有转向但角度太小，则为违章
                if (turnSignalState == TurnSignalState.OFF || 
                    turnSignalState == TurnSignalState.UNKNOWN) {
                    
                    val violation = ViolationEvent(
                        type = violationType,
                        vehicleId = trackedVehicle.id,
                        timestampMs = context.timestampMs,
                        confidence = calculateConfidence(laneChange, trackedVehicle),
                        laneChangeInfo = laneChange,
                        evidence = buildEvidence(laneChange, trackedVehicle)
                    )
                    
                    // 如果置信度足够，记录并返回
                    if (violation.confidence >= MIN_CONFIDENCE) {
                        recentViolations[trackedVehicle.id] = context.timestampMs
                        return violation
                    }
                }
            }
        }

        return null
    }

    /**
     * 检测变道
     */
    private fun detectLaneChange(
        vehicle: TrackedVehicleInfo,
        lanes: List<LaneInfo>
    ): LaneChangeDetails? {
        val history = vehicle.history
        
        // 需要足够的轨迹点
        if (history.size < MIN_TRAJECTORY_LENGTH) return null
        
        val firstPos = history.first()
        val lastPos = history.last()
        
        // 计算横向位移
        val displacementX = lastPos.x - firstPos.x
        
        // 横向位移太小，不认为是变道
        if (abs(displacementX) < MIN_LANE_CHANGE_DISPLACEMENT) return null
        
        // 确定变道方向
        val direction = if (displacementX > 0) "RIGHT" else "LEFT"
        
        // 检查是否是斜向行驶（可能是转弯而不是变道）
        val displacementY = lastPos.y - firstPos.y
        val slope = if (abs(displacementX) > 0) abs(displacementY / displacementX) else Float.MAX_VALUE
        
        // 斜率太大说明可能是转弯而不是变道
        if (slope > DIAGONAL_THRESHOLD) return null
        
        // 分析车道线穿越
        val sortedLanes = lanes.sortedBy { lane ->
            lane.points.map { it.first }.average().toFloat()
        }
        
        var fromLaneId = -1
        var toLaneId = -1
        
        for (lane in sortedLanes) {
            val laneX = lane.points.map { it.first }.average().toFloat()
            
            if (direction == "RIGHT") {
                // 向右变道
                if (firstPos.x < laneX && fromLaneId == -1) {
                    fromLaneId = lane.id
                }
                if (lastPos.x >= laneX) {
                    toLaneId = lane.id
                }
            } else {
                // 向左变道
                if (firstPos.x > laneX && fromLaneId == -1) {
                    fromLaneId = lane.id
                }
                if (lastPos.x <= laneX) {
                    toLaneId = lane.id
                }
            }
        }
        
        // 需要检测到车道变化
        if (fromLaneId == -1 || toLaneId == -1 || fromLaneId == toLaneId) {
            // 如果没有车道线信息，使用简单的轨迹判断
            return LaneChangeDetails(
                fromLaneId = 0,
                toLaneId = 1,
                direction = direction,
                displacementX = displacementX,
                trajectory = history.map { it.x to it.y }
            )
        }
        
        return LaneChangeDetails(
            fromLaneId = fromLaneId,
            toLaneId = toLaneId,
            direction = direction,
            displacementX = displacementX,
            trajectory = history.map { it.x to it.y }
        )
    }

    /**
     * 计算违章置信度
     */
    private fun calculateConfidence(
        laneChange: LaneChangeDetails,
        vehicle: TrackedVehicleInfo
    ): Float {
        var confidence = 0.5f
        
        // 横向位移越大越可信
        val displacementScore = (abs(laneChange.displacementX) / 200f).coerceIn(0f, 0.2f)
        confidence += displacementScore
        
        // 轨迹越平滑越可信
        val smoothnessScore = calculateTrajectorySmoothness(laneChange.trajectory)
        confidence += smoothnessScore * 0.2f
        
        // 速度一致性
        val speedConsistency = calculateSpeedConsistency(vehicle.history)
        confidence += speedConsistency * 0.1f
        
        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 计算轨迹平滑度
     */
    private fun calculateTrajectorySmoothness(trajectory: List<Pair<Float, Float>>): Float {
        if (trajectory.size < 3) return 0f
        
        var totalTurn = 0f
        for (i in 1 until trajectory.size - 1) {
            val prev = trajectory[i - 1]
            val curr = trajectory[i]
            val next = trajectory[i + 1]
            
            val angle1 = kotlin.math.atan2(curr.second - prev.second, curr.first - prev.first)
            val angle2 = kotlin.math.atan2(next.second - curr.second, next.first - curr.first)
            
            totalTurn += abs(angle2 - angle1)
        }
        
        // 平滑轨迹转向角较小
        val avgTurn = totalTurn / (trajectory.size - 2)
        return (1f - (avgTurn / kotlin.math.PI.toFloat())).coerceIn(0f, 1f)
    }

    /**
     * 计算速度一致性
     */
    private fun calculateSpeedConsistency(history: List<PositionSnapshot>): Float {
        if (history.size < 2) return 0f
        
        val speeds = mutableListOf<Float>()
        for (i in 1 until history.size) {
            val prev = history[i - 1]
            val curr = history[i]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dt = (curr.timestampMs - prev.timestampMs).toFloat()
            if (dt > 0) {
                speeds.add(kotlin.math.sqrt(dx * dx + dy * dy) / dt)
            }
        }
        
        if (speeds.isEmpty()) return 0f
        
        val avgSpeed = speeds.average().toFloat()
        val variance = speeds.map { (it - avgSpeed) * (it - avgSpeed) }.average().toFloat()
        
        // 方差越小一致性越高
        return (1f - (variance / (avgSpeed * avgSpeed + 1f))).coerceIn(0f, 1f)
    }

    /**
     * 构建证据
     */
    private fun buildEvidence(
        laneChange: LaneChangeDetails,
        vehicle: TrackedVehicleInfo
    ): Map<String, Any> {
        return mapOf(
            "from_lane" to laneChange.fromLaneId,
            "to_lane" to laneChange.toLaneId,
            "direction" to laneChange.direction,
            "displacement_x" to laneChange.displacementX,
            "trajectory_length" to vehicle.history.size,
            "time_span_ms" to (vehicle.history.last().timestampMs - vehicle.history.first().timestampMs)
        )
    }

    override fun reset() {
        recentViolations.clear()
    }
}
