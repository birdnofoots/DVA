package com.dva.app.presentation.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.data.local.storage.DownloadState
import com.dva.app.data.local.storage.ModelDownloadManager
import com.dva.app.data.local.storage.ModelInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsUiState(
    val models: List<ModelInfo> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val modelStatuses: Map<String, Boolean> = emptyMap(),
    val totalSize: Long = 0L
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    
    private val modelDownloadManager = ModelDownloadManager(application)
    
    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()
    
    init {
        refresh()
        
        // 观察下载状态变化
        viewModelScope.launch {
            modelDownloadManager.downloadStates.collect { states ->
                _uiState.value = _uiState.value.copy(downloadStates = states)
            }
        }
        
        viewModelScope.launch {
            modelDownloadManager.modelStatuses.collect { statuses ->
                _uiState.value = _uiState.value.copy(
                    modelStatuses = statuses,
                    models = ModelDownloadManager.AVAILABLE_MODELS.map { model ->
                        model.copy(isDownloaded = statuses[model.id] == true)
                    }
                )
            }
        }
    }
    
    fun refresh() {
        modelDownloadManager.refreshModelStatuses()
        _uiState.value = _uiState.value.copy(
            models = ModelDownloadManager.AVAILABLE_MODELS.map { model ->
                model.copy(isDownloaded = modelDownloadManager.isModelDownloaded(model.id))
            },
            downloadStates = modelDownloadManager.downloadStates.value,
            modelStatuses = _uiState.value.modelStatuses,
            totalSize = modelDownloadManager.getCacheSize()
        )
    }
    
    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            modelDownloadManager.downloadModel(modelId)
        }
    }
    
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelDownloadManager.deleteModel(modelId)
        }
    }
    
    fun deleteAllModels() {
        viewModelScope.launch {
            modelDownloadManager.deleteAllModels()
        }
    }
}
