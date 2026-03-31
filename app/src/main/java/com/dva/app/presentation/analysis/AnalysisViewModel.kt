package com.dva.app.presentation.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.VideoFile
import com.dva.app.domain.model.ViolationRecord
import com.dva.app.domain.model.VideoProcessingState
import com.dva.app.domain.model.ProcessingStatus
import com.dva.app.domain.usecase.AnalyzeVideoUseCase
import com.dva.app.domain.usecase.GetViolationsUseCase
import com.dva.app.domain.usecase.GetProcessingProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分析页面 UI 状态
 */
data class AnalysisUiState(
    val currentVideo: VideoFile? = null,
    val isAnalyzing: Boolean = false,
    val progress: Int = 0,
    val violations: List<ViolationRecord> = emptyList(),
    val errorMessage: String? = null,
    val isComplete: Boolean = false
)

/**
 * 分析页面 ViewModel
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val analyzeVideoUseCase: AnalyzeVideoUseCase,
    private val getViolationsUseCase: GetViolationsUseCase,
    private val getProcessingProgressUseCase: GetProcessingProgressUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    
    /**
     * 开始分析视频
     */
    fun startAnalysis(video: VideoFile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentVideo = video,
                isAnalyzing = true,
                progress = 0,
                violations = emptyList(),
                errorMessage = null,
                isComplete = false
            )
            
            analyzeVideoUseCase(video.path) { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }
                .onSuccess { violations ->
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        violations = violations,
                        isComplete = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = error.message ?: "分析失败"
                    )
                }
        }
    }
    
    /**
     * 加载已有违章记录
     */
    fun loadViolations() {
        viewModelScope.launch {
            getViolationsUseCase().collect { violations ->
                _uiState.value = _uiState.value.copy(violations = violations)
            }
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        _uiState.value = AnalysisUiState()
    }
}
