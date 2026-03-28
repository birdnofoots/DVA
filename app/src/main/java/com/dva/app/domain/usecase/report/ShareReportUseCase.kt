package com.dva.app.domain.usecase.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dva.app.domain.model.AnalysisReport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分享报告用例
 */
@Singleton
class ShareReportUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 创建分享 Intent
     * @param report 报告
     * @param includeImages 是否包含截图
     */
    suspend fun createShareIntent(
        report: AnalysisReport,
        includeImages: Boolean = false
    ): Result<Intent> = withContext(Dispatchers.IO) {
        try {
            val shareText = buildShareText(report)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (includeImages) "*/*" else "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "DVA 违章分析报告")
                putExtra(Intent.EXTRA_TEXT, shareText)
                
                if (includeImages) {
                    // 收集截图文件
                    @Suppress("UNCHECKED_CAST")
                    val imageUris = mutableListOf<String>()
                    report.violations.forEach { violation ->
                        // TODO: 添加截图文件URI
                    }
                    @Suppress("UNCHECKED_CAST")
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        imageUris as java.util.ArrayList<android.os.Parcelable>
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            
            Result.success(Intent.createChooser(intent, "分享报告"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 构建分享文本
     */
    private fun buildShareText(report: AnalysisReport): String {
        return buildString {
            appendLine("DVA 违章分析报告")
            appendLine("=" .repeat(20))
            appendLine()
            appendLine("视频文件: ${report.videoFileName}")
            appendLine("分析时间: ${report.summary.formattedProcessingTime}")
            appendLine()
            appendLine("违章统计:")
            appendLine("- 总违章数: ${report.summary.totalViolations}")
            appendLine("- 已识别车牌: ${report.summary.platesIdentified}")
            appendLine("- 识别失败: ${report.summary.platesFailed}")
            appendLine()
            
            if (report.violations.isNotEmpty()) {
                appendLine("违章详情:")
                report.violations.forEachIndexed { index, violation ->
                    appendLine("${index + 1}. ${violation.type.displayName}")
                    appendLine("   时间: ${violation.timestamp}")
                    violation.licensePlate?.let {
                        appendLine("   车牌: $it")
                    }
                }
            }
            
            appendLine()
            appendLine("=" .repeat(20))
            appendLine("由 DVA (DashCam Violation Analyzer) 生成")
        }
    }
}
