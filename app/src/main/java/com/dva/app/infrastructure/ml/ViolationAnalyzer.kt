package com.dva.app.infrastructure.ml

import com.dva.app.domain.model.LaneDetection
import com.dva.app.domain.model.LaneLine
import com.dva.app.domain.model.VehicleDetection
import com.dva.app.domain.model.ViolationType

/**
 * 违章分析结果
 */
data class ViolationEvent(
    val type: ViolationType,
    val frameIndex: Int,
    val vehicleId: Int,
    val confidence: Float,
    val details: String
)

/**
 * 违章分析器接口
 */
interface ViolationAnalyzer {
    suspend fun analyze(
        videoPath: String,
        frameIndex: Int,
        vehicles: List<VehicleDetection>,
        lanes: List<LaneDetection>
    ): List<ViolationEvent>
    
    /**
     * 重置分析器状态（新视频开始时调用）
     */
    fun reset()
}

/**
 * 变道违章分析器
 * 
 * 检测逻辑：
 * 1. 追踪车辆在连续帧中的位置变化
 * 2. 当车辆跨越车道线时，判定为变道
 * 3. 同一车辆一旦报告变道违章，不再重复报告
 */
class LaneChangeViolationAnalyzer : ViolationAnalyzer {
    
    companion object {
        // 连续多少帧检测到跨越车道线才确认违章
        private const val MIN_CROSS_FRAMES = 3
        // 车辆跨越车道线的阈值（像素）
        private const val CROSS_THRESHOLD = 10f
    }
    
    // 车辆历史轨迹：positionKey -> list of (frameIndex, centerX)
    // 使用 centerX + classId 作为简单追踪 key
    private val vehicleTrajectories = mutableMapOf<String, MutableList<Pair<Int, Float>>>()
    
    // 已完成变道的车辆 key（不再重复报告）
    private val completedLaneChanges = mutableSetOf<String>()
    
    override suspend fun analyze(
        videoPath: String,
        frameIndex: Int,
        vehicles: List<VehicleDetection>,
        lanes: List<LaneDetection>
    ): List<ViolationEvent> {
        val violations = mutableListOf<ViolationEvent>()
        
        // 更新轨迹
        for (vehicle in vehicles) {
            // 创建车辆唯一标识：class + 大致位置（以 50 像素为单位）
            val positionKey = "${vehicle.classId}_${(vehicle.centerX / 50).toInt()}_${(vehicle.centerY / 50).toInt()}"
            
            val trajectory = vehicleTrajectories.getOrPut(positionKey) { mutableListOf() }
            trajectory.add(Pair(frameIndex, vehicle.centerX))
            
            // 保持最近30帧的轨迹
            if (trajectory.size > 30) {
                trajectory.removeAt(0)
            }
            
            // 检查该车是否已完成变道
            if (completedLaneChanges.contains(positionKey)) continue
            
            // 检测变道
            val laneChange = detectLaneChange(trajectory)
            if (laneChange != null) {
                violations.add(
                    ViolationEvent(
                        type = ViolationType.LANE_CHANGE_NO_SIGNAL,
                        frameIndex = frameIndex,
                        vehicleId = vehicle.trackId,
                        confidence = laneChange.confidence,
                        details = laneChange.details
                    )
                )
                // 标记该车已完成变道
                completedLaneChanges.add(positionKey)
            }
        }
        
        // 检测变道
        for (vehicle in vehicles) {
            val trajectory = vehicleTrajectories[vehicle.trackId] ?: continue
            
            // 需要至少5帧才能判断变道方向
            if (trajectory.size < 5) continue
            
            // 检查冷却期
            val lastFrame = lastViolationFrame[vehicle.trackId] ?: 0
            if (frameIndex - lastFrame < VIOLATION_COOLDOWN) continue
            
            // 分析变道
            val laneChange = detectLaneChange(trajectory)
            if (laneChange != null) {
                violations.add(
                    ViolationEvent(
                        type = ViolationType.LANE_CHANGE_NO_SIGNAL,
                        frameIndex = frameIndex,
                        vehicleId = vehicle.trackId,
                        confidence = laneChange.confidence,
                        details = laneChange.details
                    )
                )
                lastViolationFrame[vehicle.trackId] = frameIndex
            }
        }
        
        return violations
    }
    
    /**
     * 检测车辆是否在变道
     */
    private fun detectLaneChange(trajectory: List<Pair<Int, Float>>): LaneChangeResult? {
        if (trajectory.size < 5) return null
        
        // 取前1/3和后1/3的位置对比
        val firstThird = trajectory.subList(0, trajectory.size / 3)
        val lastThird = trajectory.subList(trajectory.size * 2 / 3, trajectory.size)
        
        val avgFirstX = firstThird.map { it.second }.average()
        val avgLastX = lastThird.map { it.second }.average()
        
        val deltaX = avgLastX - avgFirstX
        
        // 变道阈值：X方向移动超过50像素
        if (kotlin.math.abs(deltaX) < 50) return null
        
        val direction = if (deltaX > 0) "右" else "左"
        
        // 简化置信度计算
        val confidence = minOf(0.95f, (kotlin.math.abs(deltaX) / 100.0).toFloat())
        
        return LaneChangeResult(
            confidence = confidence,
            details = "车辆从车道${if (deltaX > 0) "左" else "右"}侧向${direction}侧变道，横向移动${kotlin.math.abs(deltaX).toInt()}像素"
        )
    }
    
    data class LaneChangeResult(
        val confidence: Float,
        val details: String
    )
    
    /**
     * 清除历史数据（处理新视频时调用）
     */
    override fun reset() {
        vehicleTrajectories.clear()
        completedLaneChanges.clear()
    }
}

/**
 * 闯红灯分析器（待实现）
 */
class RedLightViolationAnalyzer : ViolationAnalyzer {
    override suspend fun analyze(
        videoPath: String,
        frameIndex: Int,
        vehicles: List<VehicleDetection>,
        lanes: List<LaneDetection>
    ): List<ViolationEvent> {
        // TODO: 需要信号灯检测模型配合
        return emptyList()
    }
    
    override fun reset() {
        // 闯红灯分析器暂无状态
    }
}
