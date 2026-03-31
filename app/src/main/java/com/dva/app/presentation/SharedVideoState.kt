package com.dva.app.presentation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dva.app.domain.model.VideoFile

/**
 * 全局共享视频状态
 * 使用单例模式，确保整个应用内共享同一份状态
 */
object GlobalVideoState {
    var selectedFolderUri: String? by mutableStateOf(null)
    
    private var _videos: List<VideoFile> by mutableStateOf(emptyList())
    var videos: List<VideoFile>
        get() = _videos
        set(value) { _videos = value }
    
    private var _isLoading: Boolean by mutableStateOf(false)
    var isLoading: Boolean
        get() = _isLoading
        set(value) { _isLoading = value }
    
    private var _errorMessage: String? by mutableStateOf(null)
    var errorMessage: String?
        get() = _errorMessage
        set(value) { _errorMessage = value }
    
    fun setSelectedFolderUri(uri: Uri) {
        selectedFolderUri = uri.toString()
    }
    
    fun setSelectedFolderUriString(uriString: String) {
        selectedFolderUri = uriString
    }
    
    fun setVideos(videoList: List<VideoFile>) {
        _videos = videoList
    }
    
    fun setLoading(loading: Boolean) {
        _isLoading = loading
    }
    
    fun setError(message: String?) {
        _errorMessage = message
    }
    
    fun clear() {
        selectedFolderUri = null
        _videos = emptyList()
        _isLoading = false
        _errorMessage = null
    }
}
