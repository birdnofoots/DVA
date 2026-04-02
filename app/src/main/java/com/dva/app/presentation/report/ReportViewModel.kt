package com.dva.app.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.data.local.storage.StorageManager
import com.dva.app.domain.model.ViolationRecord
import com.dva.app.domain.model.ViolationType
import com.dva.app.domain.repository.ViolationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * 报告页面 UI 状态
 */
data class ReportUiState(
    val violations: List<ViolationRecord> = emptyList(),
    val isLoading: Boolean = false,
    val exportedPath: String? = null,
    val errorMessage: String? = null
)

/**
 * 报告生成器 ViewModel
 */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val violationRepository: ViolationRepository,
    private val storageManager: StorageManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * 加载所有违章记录
     */
    fun loadViolations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            violationRepository.getAllViolations().collect { violations ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    violations = violations
                )
            }
        }
    }
    
    /**
     * 生成文字报告
     */
    fun generateReport(violations: List<ViolationRecord>): String {
        val sb = StringBuilder()
        
        sb.appendLine("=" .repeat(50))
        sb.appendLine("交通违章分析报告")
        sb.appendLine("=" .repeat(50))
        sb.appendLine()
        sb.appendLine("生成时间: ${dateFormat.format(Date())}")
        sb.appendLine("违章总数: ${violations.size}")
        sb.appendLine()
        
        // 按日期分组
        val byDate = violations.groupBy { 
            dateOnlyFormat.format(Date(it.createdAt))
        }
        
        for ((date, dateViolations) in byDate) {
            sb.appendLine("-".repeat(50))
            sb.appendLine("日期: $date")
            sb.appendLine("-".repeat(50))
            
            for ((index, violation) in dateViolations.withIndex()) {
                sb.appendLine()
                sb.appendLine("${index + 1}. ${getViolationTypeName(violation.violationType)}")
                sb.appendLine("   时间: ${formatElapsedTime(violation.timestamp)}")
                sb.appendLine("   视频: ${violation.videoPath.substringAfterLast("/")}")
                sb.appendLine("   帧号: ${violation.frameIndex}")
                sb.appendLine("   置信度: ${(violation.confidence * 100).toInt()}%")
                
                if (violation.plateNumber != null) {
                    sb.appendLine("   车牌: ${violation.plateNumber}")
                    sb.appendLine("   车牌置信度: ${(violation.plateConfidence * 100).toInt()}%")
                }
            }
            sb.appendLine()
        }
        
        sb.appendLine("=" .repeat(50))
        sb.appendLine("报告结束")
        sb.appendLine("=" .repeat(50))
        
        return sb.toString()
    }
    
    /**
     * 导出报告
     */
    fun exportReport(violations: List<ViolationRecord>): String? {
        return try {
            val reportContent = generateReport(violations)
            val fileName = "violation_report_${System.currentTimeMillis()}.txt"
            val reportsDir = storageManager.getReportsDirectory()
            val file = java.io.File(reportsDir, fileName)
            file.writeText(reportContent)
            
            _uiState.value = _uiState.value.copy(exportedPath = file.absolutePath)
            file.absolutePath
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
            null
        }
    }
    
    /**
     * 删除违章记录
     */
    fun deleteViolation(id: Long) {
        viewModelScope.launch {
            violationRepository.deleteViolation(id)
        }
    }
    
    private fun getViolationTypeName(type: ViolationType): String {
        return when (type) {
            ViolationType.LANE_CHANGE_NO_SIGNAL -> "变道不打灯"
            ViolationType.RED_LIGHT -> "闯红灯"
            ViolationType.WRONG_LANE -> "不按规定车道行驶"
        }
    }
    
    /**
     * 格式化时间戳为相对时间 (HH:mm:ss)
     * @param timestampMs 时间戳（毫秒，从视频开始计算）
     */
    private fun formatElapsedTime(timestampMs: Long): String {
        val totalSeconds = timestampMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
