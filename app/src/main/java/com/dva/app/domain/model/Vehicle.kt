package com.dva.app.domain.model

import android.graphics.RectF

/**
 * 车辆实体
 */
data class Vehicle(
    val id: String,
    val boundingBox: RectF,
    val category: VehicleCategory,
    val color: VehicleColor?,
    val confidence: Float,
    val timestampMs: Long,
    val centerX: Float,
    val centerY: Float
) {
    val width: Float get() = boundingBox.width()
    val height: Float get() = boundingBox.height()
}

/**
 * 车辆类别
 */
enum class VehicleCategory(val displayName: String) {
    CAR("轿车"),
    TRUCK("卡车"),
    BUS("公交车"),
    MOTORCYCLE("摩托车"),
    UNKNOWN("未知")
}

/**
 * 车辆颜色
 */
enum class VehicleColor(val displayName: String) {
    WHITE("白色"),
    BLACK("黑色"),
    SILVER("银色"),
    RED("红色"),
    BLUE("蓝色"),
    GREEN("绿色"),
    YELLOW("黄色"),
    BROWN("棕色"),
    UNKNOWN("未知")
}
