package com.dva.app.domain.model

import android.graphics.RectF

/**
 * 车牌实体
 */
data class LicensePlate(
    val number: String,
    val province: String,
    val letter: String,
    val digits: String,
    val plateType: PlateType,
    val confidence: Float,
    val boundingBox: RectF?,
    val color: PlateColor,
    val croppedImagePath: String? = null
) {
    val isValid: Boolean
        get() = confidence >= MIN_CONFIDENCE && number.length >= 7
    
    companion object {
        const val MIN_CONFIDENCE = 0.7f
        
        val PROVINCES = listOf(
            "京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘",
            "皖", "鲁", "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋",
            "蒙", "陕", "吉", "闽", "贵", "粤", "青", "藏", "川", "宁", "琼"
        )
    }
}

/**
 * 车牌类型
 */
enum class PlateType(val displayName: String) {
    BLUE("蓝牌"),
    GREEN("绿牌(新能源)"),
    YELLOW("黄牌"),
    WHITE("白牌"),
    BLACK("黑牌"),
    UNKNOWN("未知")
}

/**
 * 车牌颜色
 */
enum class PlateColor(val displayName: String) {
    BLUE("蓝色"),
    YELLOW("黄色"),
    WHITE("白色"),
    BLACK("黑色"),
    GREEN("绿色"),
    UNKNOWN("未知")
}
