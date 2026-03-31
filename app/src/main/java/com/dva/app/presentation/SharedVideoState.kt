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
    
    private var _videosList by mutableStateOf<List<VideoFile>>(emptyList())
    val videosList: List<VideoFile>
        get() = _videosList
    
    private var _loading by mutableStateOf(false)
    val isLoading: Boolean
        get() = _loading
    
    private var _error by mutableStateOf<String?>(null)
    val errorMessage: String?
        get() = _error
    
    fun setSelectedFolderUri(uri: Uri) {
        selectedFolderUri = uri.toString()
    }
    
    fun setSelectedFolderUriString(uriString: String) {
        selectedFolderUri = uriString
    }
    
    fun updateVideos(list: List<VideoFile>) {
        _videosList = list
    }
    
    fun updateLoading(loading: Boolean) {
        _loading = loading
    }
    
    fun updateError(message: String?) {
        _error = message
    }
    
    fun clear() {
        selectedFolderUri = null
        _videosList = emptyList()
        _loading = false
        _error = null
    }
}
