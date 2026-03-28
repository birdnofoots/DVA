package com.dva.app.infrastructure.ml

import android.graphics.RectF
import com.dva.app.domain.model.VehicleCategory
import com.dva.app.infrastructure.ml.models.LaneChangeEvent
import com.dva.app.infrastructure.ml.models.LaneChangeDirection
import com.dva.app.infrastructure.ml.models.TrackedVehicle
import com.dva.app.infrastructure.ml.models.VehicleDetectionResult
import com.dva.app.infrastructure.ml.models.VehiclePosition
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 车辆追踪器
 * 使用 IOU 和卡尔曼滤波追踪跨帧车辆
 */
@Singleton
class VehicleTracker @Inject constructor() {
    companion object {
        private const val MAX_DISAPPEARED_FRAMES = 5
        private const val MAX_DISTANCE = 150f
        private const val MIN_HISTORY_SIZE = 3
        private const val IOU_THRESHOLD = 0.3f
    }

    private val trackedVehicles = mutableMapOf<String, TrackedVehicle>()
    private var nextVehicleId = 0
    private var currentFrameIndex = 0L
    private var currentTimestampMs = 0L

    /**
     * 更新追踪状态
     * @param detections 当前帧检测结果
     * @param frameIndex 当前帧索引
     * @param timestampMs 当前时间戳
     */
    fun update(
        detections: List<VehicleDetectionResult>,
        frameIndex: Long,
        timestampMs: Long
    ): List<TrackedVehicle> {
        currentFrameIndex = frameIndex
        currentTimestampMs = timestampMs
        
        if (detections.isEmpty()) {
            // 没有检测到车辆，标记所有追踪为消失
            markAllAsDisappeared()
            return emptyList()
        }
        
        // 计算 IOU 匹配
        val matches = computeMatches(detections)
        
        // 更新已匹配的追踪
        matches.matchedDetections.forEach { (vehicleId, detection) ->
            val vehicle = trackedVehicles[vehicleId]!!
            val newPosition = VehiclePosition(
                centerX = detection.boundingBox.centerX(),
                centerY = detection.boundingBox.centerY(),
                timestampMs = timestampMs,
                frameIndex = frameIndex
            )
            
            trackedVehicles[vehicleId] = vehicle.copy(
                currentBox = detection.boundingBox,
                history = (vehicle.history + newPosition).takeLast(30),
                disappearedFrames = 0
            )
        }
        
        // 处理新检测到的车辆
        matches.unmatchedDetections.forEach { detection ->
            val newId = "vehicle_${nextVehicleId++}"
            val position = VehiclePosition(
                centerX = detection.boundingBox.centerX(),
                centerY = detection.boundingBox.centerY(),
                timestampMs = timestampMs,
                frameIndex = frameIndex
            )
            
            trackedVehicles[newId] = TrackedVehicle(
                id = newId,
                currentBox = detection.boundingBox,
                history = listOf(position),
                category = detection.category,
                color = null,
                firstSeenFrame = frameIndex,
                lastSeenFrame = frameIndex
            )
        }
        
        // 处理消失的追踪
        matches.unmatchedTracks.forEach { vehicleId ->
            val vehicle = trackedVehicles[vehicleId]!!
            trackedVehicles[vehicleId] = vehicle.copy(
                disappearedFrames = vehicle.disappearedFrames + 1
            )
        }
        
        // 移除长期消失的追踪
        removeDisappearedTracks()
        
        return trackedVehicles.values.toList()
    }

    /**
     * 计算 IOU 匹配
     */
    private fun computeMatches(detections: List<VehicleDetectionResult>): MatchResult {
        val matchedPairs = mutableListOf<Pair<String, VehicleDetectionResult>>()
        val unmatchedDetections = detections.toMutableList()
        val unmatchedTracks = mutableListOf<String>()
        
        // 对每个追踪找最佳匹配
        trackedVehicles.values
            .filter { it.disappearedFrames == 0 }
            .forEach { vehicle ->
                var bestMatch: VehicleDetectionResult? = null
                var bestIou = 0f
                var bestIndex = -1
                
                unmatchedDetections.forEachIndexed { index, detection ->
                    val iou = calculateIoU(vehicle.currentBox, detection.boundingBox)
                    val distance = calculateDistance(vehicle, detection.boundingBox)
                    
                    if (iou > IOU_THRESHOLD && iou > bestIou && distance < MAX_DISTANCE) {
                        bestMatch = detection
                        bestIou = iou
                        bestIndex = index
                    }
                }
                
                if (bestMatch != null && bestIndex >= 0) {
                    matchedPairs.add(vehicle.id to bestMatch!!)
                    unmatchedDetections.removeAt(bestIndex)
                } else {
                    unmatchedTracks.add(vehicle.id)
                }
            }
        
        return MatchResult(matchedPairs, unmatchedDetections, unmatchedTracks)
    }

    /**
     * 计算 IOU（交并比）
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val left = max(box1.left, box2.left)
        val top = max(box1.top, box2.top)
        val right = min(box1.right, box2.right)
        val bottom = min(box1.bottom, box2.bottom)
        
        if (left >= right || top >= bottom) return 0f
        
        val intersection = (right - left) * (bottom - top)
        val area1 = box1.width() * box1.height()
        val area2 = box2.width() * box2.height()
        val union = area1 + area2 - intersection
        
        return if (union > 0) intersection / union else 0f
    }

    /**
     * 计算距离（中心点欧氏距离）
     */
    private fun calculateDistance(vehicle: TrackedVehicle, box: RectF): Float {
        val latestPos = vehicle.latestPosition ?: return Float.MAX_VALUE
        val dx = latestPos.centerX - box.centerX()
        val dy = latestPos.centerY - box.centerY()
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * 标记所有追踪为消失
     */
    private fun markAllAsDisappeared() {
        trackedVehicles.forEach { (id, vehicle) ->
            trackedVehicles[id] = vehicle.copy(disappearedFrames = vehicle.disappearedFrames + 1)
        }
    }

    /**
     * 移除长期消失的追踪
     */
    private fun removeDisappearedTracks() {
        val toRemove = trackedVehicles.filter { (_, v) -> 
            v.disappearedFrames > MAX_DISAPPEARED_FRAMES 
        }.keys
        toRemove.forEach { trackedVehicles.remove(it) }
    }

    /**
     * 检测变道
     * @param vehicleId 车辆ID
     * @param lanePositions 车道线位置
     * @return 变道事件
     */
    fun detectLaneChange(
        vehicleId: String,
        lanePositions: List<Pair<Int, Float>> // (laneId, x_position)
    ): LaneChangeEvent? {
        val vehicle = trackedVehicles[vehicleId] ?: return null
        if (vehicle.history.size < MIN_HISTORY_SIZE) return null
        
        val history = vehicle.history
        val firstPos = history.first()
        val lastPos = history.last()
        
        // 计算横向位移
        val deltaX = lastPos.centerX - firstPos.centerX
        
        // 如果位移太小，不认为是变道
        if (abs(deltaX) < 50) return null
        
        // 确定变道方向
        val direction = if (deltaX > 0) LaneChangeDirection.RIGHT else LaneChangeDirection.LEFT
        
        // 检查是否跨越了车道线
        val sortedLanes = lanePositions.sortedBy { it.second }
        var fromLaneId = -1
        var toLaneId = -1
        
        for (i in sortedLanes.indices) {
            val (laneId, xPos) = sortedLanes[i]
            
            if (direction == LaneChangeDirection.RIGHT) {
                // 向右变道：从左车道移到右车道
                if (firstPos.centerX < xPos && fromLaneId == -1) {
                    fromLaneId = laneId
                }
                if (lastPos.centerX >= xPos) {
                    toLaneId = laneId
                }
            } else {
                // 向左变道：从右车道移到左车道
                if (firstPos.centerX > xPos && fromLaneId == -1) {
                    fromLaneId = laneId
                }
                if (lastPos.centerX <= xPos) {
                    toLaneId = laneId
                }
            }
        }
        
        if (fromLaneId == -1 || toLaneId == -1 || fromLaneId == toLaneId) {
            return null
        }
        
        return LaneChangeEvent(
            vehicleId = vehicleId,
            fromLaneId = fromLaneId,
            toLaneId = toLaneId,
            direction = direction,
            startTimeMs = firstPos.timestampMs,
            endTimeMs = lastPos.timestampMs,
            trajectory = history,
            confidence = calculateLaneChangeConfidence(vehicle, direction)
        )
    }

    /**
     * 计算变道置信度
     */
    private fun calculateLaneChangeConfidence(
        vehicle: TrackedVehicle,
        direction: LaneChangeDirection
    ): Float {
        if (vehicle.history.size < MIN_HISTORY_SIZE) return 0f
        
        var confidence = 0.5f
        
        // 轨迹平滑度
        var totalDeviation = 0f
        for (i in 1 until vehicle.history.size) {
            val prev = vehicle.history[i - 1]
            val curr = vehicle.history[i]
            totalDeviation += abs(curr.centerX - prev.centerX - 
                (if (direction == LaneChangeDirection.RIGHT) 1f else 0f) * 5f - 2.5f)
        }
        
        // 轨迹越平滑置信度越高
        if (totalDeviation < 100) confidence += 0.3f
        
        // 横向位移越大置信度越高
        val totalDisplacement = abs(
            vehicle.history.last().centerX - vehicle.history.first().centerX
        )
        if (totalDisplacement > 100) confidence += 0.2f
        
        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 重置追踪器
     */
    fun reset() {
        trackedVehicles.clear()
        nextVehicleId = 0
        currentFrameIndex = 0
        currentTimestampMs = 0
    }

    /**
     * 获取当前追踪的车辆
     */
    fun getTrackedVehicles(): List<TrackedVehicle> = trackedVehicles.values.toList()

    /**
     * 获取指定车辆
     */
    fun getVehicle(vehicleId: String): TrackedVehicle? = trackedVehicles[vehicleId]

    /**
     * 匹配结果
     */
    private data class MatchResult(
        val matchedDetections: List<Pair<String, VehicleDetectionResult>>,
        val unmatchedDetections: List<VehicleDetectionResult>,
        val unmatchedTracks: List<String>
    )
}
