package com.dva.app.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.VideoFile
import com.dva.app.domain.usecase.ScanVideosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 视频列表 UI 状态
 */
data class VideoListUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoFile> = emptyList(),
    val selectedVideo: VideoFile? = null,
    val errorMessage: String? = null
)

/**
 * 视频列表 ViewModel
 */
@HiltViewModel
class VideoListViewModel @Inject constructor(
    private val scanVideosUseCase: ScanVideosUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()
    
    /**
     * 加载视频列表
     */
    fun loadVideos(directoryPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            scanVideosUseCase(directoryPath)
                .onSuccess { videos ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = videos
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                    )
                }
        }
    }
    
    /**
     * 选择视频
     */
    fun selectVideo(video: VideoFile) {
        _uiState.value = _uiState.value.copy(selectedVideo = video)
    }
    
    /**
     * 清除选择
     */
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedVideo = null)
    }
}
