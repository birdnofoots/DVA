package com.dva.app.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.ViolationRecord
import com.dva.app.domain.model.VideoFile
import com.dva.app.domain.repository.VideoRepository
import com.dva.app.domain.usecase.AnalyzeVideoUseCase
import com.dva.app.domain.usecase.GetVideoInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val videoRepository: VideoRepository
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
            
            try {
                // 如果是 SAF URI，先复制到本地缓存
                var localVideoPath = videoPath
                if (videoPath.startsWith("content://")) {
                    addLog("检测到 SAF URI，正在复制到本地缓存...")
                    localVideoPath = videoRepository.copyToLocalCache(videoPath) ?: ""
                    if (localVideoPath.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            errorMessage = "无法复制视频到本地缓存，请确保存储空间充足后重试"
                        )
                        return@launch
                    }
                    addLog("已复制到: $localVideoPath")
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
                        "无法访问视频文件，请重新从文件夹选择该视频（SAF权限可能已失效）"
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
                
                // 开始分析（5分钟超时）
                addLog("开始帧提取和模型推理...")
                val result = withTimeoutOrNull(5 * 60 * 1000L) {
                    analyzeVideoUseCase(
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
                }
                
                if (result == null) {
                    // 超时
                    addLog("分析超时（5分钟）")
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = "分析超时（5分钟），视频可能过大或网络存储较慢"
                    )
                } else {
                    result.onSuccess { violations ->
                        addLog("分析完成，共检测到 ${violations.size} 个违章")
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            isComplete = true,
                            progress = 100,
                            currentFrame = totalFrames,
                            analyzedFrames = totalFrames,
                            violationCount = violations.size,
                            violations = violations
                        )
                    }.onFailure { error ->
                        addLog("分析失败: ${error.message}")
                        _uiState.value = _uiState.value.copy(
                            isAnalyzing = false,
                            errorMessage = error.message ?: "分析失败"
                        )
                    }
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
