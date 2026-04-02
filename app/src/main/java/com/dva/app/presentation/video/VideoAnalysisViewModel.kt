package com.dva.app.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.ViolationRecord
import com.dva.app.domain.model.VideoFile
import com.dva.app.domain.repository.VideoRepository
import com.dva.app.domain.usecase.AnalyzeVideoUseCase
import com.dva.app.domain.usecase.GetVideoInfoUseCase
import com.dva.app.infrastructure.ml.VehicleDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视频分析 UI 状态
 */
data class VideoAnalysisUiState(
    val videoInfo: VideoFile? = null,
    val isAnalyzing: Boolean = false,
    val isComplete: Boolean = false,
    val progress: Int = 0,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val violationCount: Int = 0,
    val vehicleCount: Int = 0,
    val analyzedFrames: Int = 0,
    val violations: List<ViolationRecord> = emptyList(),
    val errorMessage: String? = null,
    val logMessages: List<String> = emptyList()
)

/**
 * 视频分析 ViewModel
 */
@HiltViewModel
class VideoAnalysisViewModel @Inject constructor(
    private val analyzeVideoUseCase: AnalyzeVideoUseCase,
    private val getVideoInfoUseCase: GetVideoInfoUseCase,
    private val videoRepository: VideoRepository,
    private val vehicleDetector: VehicleDetector
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(VideoAnalysisUiState())
    val uiState: StateFlow<VideoAnalysisUiState> = _uiState.asStateFlow()
    
    private var analysisJob: Job? = null
    
    /**
     * 获取视频信息并准备分析
     */
    fun startAnalysis(videoPath: String) {
        if (videoPath.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "视频路径无效"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                errorMessage = null,
                logMessages = listOf("开始分析视频...")
            )
            
            // 检查模型加载状态
            if (!vehicleDetector.isModelAvailable()) {
                val errorMsg = "⚠️ AI 模型未加载！车辆检测功能不可用。"
                addLog(errorMsg)
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = "AI 模型未加载，请检查 assets/models/ 目录"
                )
                return@launch
            }
            addLog("✅ AI 模型已加载")
            
            try {
                // 如果是 SAF URI，尝试直接分析或复制到本地缓存
                var localVideoPath = videoPath
                if (videoPath.startsWith("content://")) {
                    addLog("检测到 SAF URI，尝试复制到本地缓存...")
                    val cachePath = videoRepository.copyToLocalCache(videoPath)
                    if (!cachePath.isNullOrBlank()) {
                        localVideoPath = cachePath
                        addLog("已复制到本地缓存: $localVideoPath")
                    } else {
                        // 复制失败，尝试直接用 URI 分析
                        addLog("复制失败，尝试直接分析...")
                        localVideoPath = videoPath
                    }
                }
                
                // 获取视频信息
                addLog("正在获取视频信息...")
                val videoInfo = try {
                    getVideoInfoUseCase(localVideoPath)
                } catch (e: Exception) {
                    addLog("获取视频信息异常: ${e::class.simpleName}: ${e.message}")
                    null
                }
                
                if (videoInfo == null) {
                    val errorMsg = if (videoPath.startsWith("content://")) {
                        addLog("提示: SAF URI 访问失败，请尝试将视频保存到手机本地文件夹后再分析")
                        "无法访问视频文件（SAF权限受限），请将视频保存到「文件」/「Downloads」文件夹后重试"
                    } else {
                        "无法读取视频信息，请检查视频文件是否有效"
                    }
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = errorMsg
                    )
                    return@launch
                }
                
                val totalFrames = videoInfo.frameCount
                addLog("视频信息: ${videoInfo.width}x${videoInfo.height}, ${totalFrames}帧")
                
                _uiState.value = _uiState.value.copy(
                    videoInfo = videoInfo,
                    totalFrames = totalFrames,
                    isAnalyzing = true
                )
                
                // 开始帧提取和模型推理
                addLog("开始帧提取和模型推理...")
                val result = analyzeVideoUseCase(
                    videoPath = localVideoPath,
                    onProgress = { progress ->
                        val safeProgress = progress.coerceIn(0, 100)
                        val currentFrame = (safeProgress * totalFrames) / 100
                        _uiState.value = _uiState.value.copy(
                            progress = safeProgress,
                            currentFrame = currentFrame,
                            analyzedFrames = currentFrame
                        )
                        if (safeProgress % 10 == 0) {
                            addLog("分析进度: ${safeProgress}%")
                        }
                    }
                )
                
                result.onSuccess { violations ->
                    // 统计涉及车辆数（按 frameIndex 和类型去重，简单估算）
                    val uniqueVehicles = violations.map { "${it.frameIndex}_${it.violationType}" }.distinct()
                    val vehicleCount = uniqueVehicles.size.coerceAtLeast(1)
                    
                    addLog("分析完成，共检测到 ${violations.size} 个违章，涉及 $vehicleCount 辆车")
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        isComplete = true,
                        progress = 100,
                        currentFrame = totalFrames,
                        analyzedFrames = totalFrames,
                        violationCount = violations.size,
                        vehicleCount = vehicleCount,
                        violations = violations
                    )
                }.onFailure { error ->
                    addLog("分析失败: ${error.message}")
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = error.message ?: "分析失败"
                    )
                }
            } catch (e: Exception) {
                addLog("错误: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = e.message ?: "分析过程出错"
                )
            }
        }
    }
    
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val newLog = "[$timestamp] $message"
        _uiState.value = _uiState.value.copy(
            logMessages = _uiState.value.logMessages + newLog
        )
    }
    
    /**
     * 取消分析
     */
    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isAnalyzing = false,
            isComplete = false
        )
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        analysisJob?.cancel()
        _uiState.value = VideoAnalysisUiState()
    }
}
