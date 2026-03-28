package com.dva.app.presentation.video

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.Video
import com.dva.app.domain.repository.VideoRepository
import com.dva.app.presentation.FolderPickerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoListUiState(
    val isLoading: Boolean = false,
    val videos: List<Video> = emptyList(),
    val currentFolder: String? = null,
    val selectedVideoId: String? = null,
    val error: String? = null
)

@HiltViewModel
class VideoListViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoListUiState())
    val uiState: StateFlow<VideoListUiState> = _uiState.asStateFlow()

    init {
        observeVideos()
        observeFolderPickerResult()
    }

    private fun observeVideos() {
        viewModelScope.launch {
            videoRepository.observeVideoList().collect { videos ->
                _uiState.update { it.copy(videos = videos) }
            }
        }
    }

    private fun observeFolderPickerResult() {
        viewModelScope.launch {
            FolderPickerManager.folderPickerResult.collect { uri ->
                uri?.let { onFolderSelected(it) }
            }
        }
    }

    fun scanVideos(folderPath: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = videoRepository.scanFolder(folderPath ?: "")

            result.fold(
                onSuccess = { videos ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            videos = videos,
                            currentFolder = folderPath
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "扫描失败"
                        )
                    }
                }
            )
        }
    }

    fun selectVideo(videoId: String) {
        _uiState.update { state ->
            state.copy(
                selectedVideoId = if (state.selectedVideoId == videoId) null else videoId
            )
        }
    }

    fun showFolderPicker() {
        FolderPickerManager.requestPick()
    }

    private fun onFolderSelected(uri: Uri) {
        val path = uri.toString() // SAF URI - persistable content URI
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = videoRepository.scanFolder(path)
            result.fold(
                onSuccess = { videos ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            videos = videos,
                            currentFolder = path
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "扫描失败"
                        )
                    }
                }
            )
        }
    }
}
