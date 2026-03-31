package com.dva.app.presentation.video

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.VideoFile
import com.dva.app.domain.usecase.ScanVideosUseCase
import com.dva.app.presentation.GlobalVideoState
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
    val selectedDirectory: String? = null,
    val selectedFolderUri: String? = null,
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
     * 保存选择的文件夹 URI
     */
    fun setSelectedFolderUri(uriString: String) {
        _uiState.value = _uiState.value.copy(selectedFolderUri = uriString)
        GlobalVideoState.setSelectedFolderUriString(uriString)
    }
    
    /**
     * 从 URI 扫描视频
     */
    fun scanVideosFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedDirectory = uri.toString(),
                errorMessage = null
            )
            GlobalVideoState.setLoading(true)
            GlobalVideoState.setError(null)
            
            scanVideosUseCase.invoke(uri)
                .onSuccess { videos ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = videos,
                        errorMessage = null
                    )
                    GlobalVideoState.setVideos(videos)
                    GlobalVideoState.setLoading(false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "扫描失败"
                    )
                    GlobalVideoState.setError(error.message)
                    GlobalVideoState.setLoading(false)
                }
        }
    }
    
    /**
     * 加载视频列表
     */
    fun loadVideos(directoryPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            GlobalVideoState.setLoading(true)
            
            scanVideosUseCase(directoryPath)
                .onSuccess { videos ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = videos
                    )
                    GlobalVideoState.setVideos(videos)
                    GlobalVideoState.setLoading(false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                    )
                    GlobalVideoState.setError(error.message)
                    GlobalVideoState.setLoading(false)
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
