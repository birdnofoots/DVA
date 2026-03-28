package com.dva.app.domain.model

/**
 * 分析报告实体
 */
data class AnalysisReport(
    val id: String,
    val taskId: String,
    val videoFileName: String,
    val videoPath: String,
    val analyzeTime: Long,
    val videoDuration: Long,
    val totalFrames: Long,
    val violations: List<ViolationSummary>,
    val summary: ReportSummary,
    val exportedAt: Long? = null
) {
    val formattedAnalyzeTime: String
        get() {
            val seconds = analyzeTime / 1000
            val minutes = seconds / 60
            return String.format("%d分%d秒", minutes, seconds % 60)
        }
}

/**
 * 违章摘要
 */
data class ViolationSummary(
    val id: String,
    val type: ViolationType,
    val timestamp: String,
    val licensePlate: String?,
    val thumbnailPath: String
)

/**
 * 报告摘要
 */
data class ReportSummary(
    val totalViolations: Int,
    val violationsByType: Map<ViolationType, Int>,
    val platesIdentified: Int,
    val platesFailed: Int,
    val processingTimeMs: Long
) {
    val formattedProcessingTime: String
        get() {
            val seconds = processingTimeMs / 1000
            val minutes = seconds / 60
            return if (minutes > 0) {
                String.format("%d分%d秒", minutes, seconds % 60)
            } else {
                String.format("%d秒", seconds)
            }
        }
}
