package com.dva.app.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.ViolationRecord
import com.dva.app.domain.model.VideoFile
import com.dva.app.domain.usecase.AnalyzeVideoUseCase
import com.dva.app.domain.usecase.GetVideoInfoUseCase
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
    val errorMessage: String? = null
)

/**
 * 视频分析 ViewModel
 */
@HiltViewModel
class VideoAnalysisViewModel @Inject constructor(
    private val analyzeVideoUseCase: AnalyzeVideoUseCase,
    private val getVideoInfoUseCase: GetVideoInfoUseCase
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
                errorMessage = null
            )
            
            try {
                // 获取视频信息
                val videoInfo = getVideoInfoUseCase(videoPath)
                val totalFrames = videoInfo?.frameCount ?: 0
                
                _uiState.value = _uiState.value.copy(
                    videoInfo = videoInfo,
                    totalFrames = totalFrames,
                    isAnalyzing = true
                )
                
                // 开始分析
                analyzeVideoUseCase(
                    videoPath = videoPath,
                    onProgress = { progress ->
                        val safeProgress = progress.coerceIn(0, 100)
                        val currentFrame = (safeProgress * totalFrames) / 100
                        _uiState.value = _uiState.value.copy(
                            progress = safeProgress,
                            currentFrame = currentFrame,
                            analyzedFrames = currentFrame
                        )
                    }
                ).onSuccess { violations ->
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
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = error.message ?: "分析失败"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = e.message ?: "获取视频信息失败"
                )
            }
        }
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
