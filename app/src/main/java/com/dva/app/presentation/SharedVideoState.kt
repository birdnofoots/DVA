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
        private set
    
    var videos: List<VideoFile> by mutableStateOf(emptyList())
        private set
    
    var isLoading: Boolean by mutableStateOf(false)
        private set
    
    var errorMessage: String? by mutableStateOf(null)
        private set
    
    fun setSelectedFolderUri(uri: Uri) {
        selectedFolderUri = uri.toString()
    }
    
    fun setSelectedFolderUriString(uriString: String) {
        selectedFolderUri = uriString
    }
    
    fun setVideos(videoList: List<VideoFile>) {
        videos = videoList
    }
    
    fun setLoading(loading: Boolean) {
        isLoading = loading
    }
    
    fun setError(message: String?) {
        errorMessage = message
    }
    
    fun clear() {
        selectedFolderUri = null
        videos = emptyList()
        isLoading = false
        errorMessage = null
    }
}
