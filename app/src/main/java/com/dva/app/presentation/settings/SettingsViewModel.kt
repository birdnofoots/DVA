package com.dva.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val cacheSize: Long = 0L,
    val isClearing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val videoRepository: VideoRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        refreshCacheSize()
    }
    
    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = videoRepository.getCacheSize()
            _uiState.value = _uiState.value.copy(cacheSize = size)
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClearing = true)
            videoRepository.clearCache()
            _uiState.value = _uiState.value.copy(
                cacheSize = 0L,
                isClearing = false
            )
        }
    }
    
    fun formatCacheSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
