package com.dva.app.domain.model

/**
 * 违章实体
 */
data class Violation(
    val id: String,
    val taskId: String,
    val type: ViolationType,
    val timestampMs: Long,
    val vehicleId: String,
    val licensePlate: LicensePlate?,
    val vehicleSnapshot: Vehicle?,
    val screenshots: List<Screenshot>,
    val confidence: Float,
    val isConfirmed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
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
 * 违章类型枚举
 * @param code 违章代码
 * @param displayName 显示名称
 */
enum class ViolationType(
    val code: String,
    val displayName: String
) {
    // 当前支持
    LANE_CHANGE_WITHOUT_SIGNAL("LCWS", "变道不打灯"),
    
    // 预留扩展
    SPEEDING("SPD", "超速"),
    RUNNING_RED_LIGHT("RL", "闯红灯"),
    ILLEGAL_PARKING("IP", "违规停车"),
    UNKNOWN("UNK", "未知");
    
    companion object {
        fun fromCode(code: String): ViolationType {
            return entries.find { it.code == code } ?: UNKNOWN
        }
    }
}
