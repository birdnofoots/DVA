package com.dva.app.domain.model

import android.graphics.PointF

/**
 * 车道线实体
 */
data class Lane(
    val id: Int,
    val points: List<PointF>,
    val laneType: LaneType,
    val curvature: Float = 0f,
    val vanishingPoint: PointF? = null
)

/**
 * 车道线类型
 */
enum class LaneType(val displayName: String) {
    SOLID("实线"),
    DASHED("虚线"),
    DOUBLE_SOLID("双实线"),
    CURB("路缘"),
    UNKNOWN("未知")
}

/**
 * 变道方向
 */
enum class LaneChangeDirection(val displayName: String) {
    LEFT("向左变道"),
    RIGHT("向右变道")
}

/**
 * 变道信息
 */
data class LaneChangeInfo(
    val fromLaneId: Int,
    val toLaneId: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val direction: LaneChangeDirection
)
