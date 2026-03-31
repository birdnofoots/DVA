package com.dva.app.presentation.home

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
 * 首页 UI 状态
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedDirectory: String? = null,
    val selectedFolderUri: String? = null,
    val videos: List<VideoFile> = emptyList(),
    val errorMessage: String? = null
)

/**
 * 首页 ViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanVideosUseCase: ScanVideosUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    /**
     * 保存选择的文件夹 URI
     */
    fun setSelectedFolderUri(uriString: String) {
        _uiState.value = _uiState.value.copy(selectedFolderUri = uriString)
        GlobalVideoState.setSelectedFolderUriString(uriString)
    }
    
    /**
     * 从 URI 扫描视频（使用 SAF）
     */
    fun scanVideosFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedDirectory = uri.toString(),
                errorMessage = null
            )
            GlobalVideoState.updateLoading(true)
            GlobalVideoState.updateError(null)
            
            // 使用 content resolver 扫描
            scanVideosUseCase.invoke(uri)
                .onSuccess { videos ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = videos,
                        errorMessage = null
                    )
                    // 同时更新全局状态
                    GlobalVideoState.updateVideos(videos)
                    GlobalVideoState.updateLoading(false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "扫描失败"
                    )
                    GlobalVideoState.updateError(error.message)
                    GlobalVideoState.updateLoading(false)
                }
        }
    }
    
    /**
     * 扫描视频目录（传统方式，使用文件路径）
     */
    fun scanVideos(directoryPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                selectedDirectory = directoryPath,
                errorMessage = null
            )
            GlobalVideoState.updateLoading(true)
            
            scanVideosUseCase(directoryPath)
                .onSuccess { videos ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        videos = videos,
                        errorMessage = null
                    )
                    GlobalVideoState.updateVideos(videos)
                    GlobalVideoState.updateLoading(false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "扫描失败"
                    )
                    GlobalVideoState.updateError(error.message)
                    GlobalVideoState.updateLoading(false)
                }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        GlobalVideoState.updateError(null)
    }
}
