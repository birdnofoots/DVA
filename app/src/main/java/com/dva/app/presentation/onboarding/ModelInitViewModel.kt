package com.dva.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.infrastructure.ml.modelmgr.ModelDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelInitViewModel @Inject constructor(
    private val modelDownloadService: ModelDownloadService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelInitUiState())
    val uiState: StateFlow<ModelInitUiState> = _uiState.asStateFlow()

    init {
        checkModels()
    }

    /**
     * 检查模型状态
     */
    fun checkModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val models = modelDownloadService.getModelsStatus()
                val allReady = models.all { it.ready }
                val totalSize = modelDownloadService.formatSize(
                    modelDownloadService.getTotalDownloadSize()
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        models = models.map { m ->
                            ModelUiInfo(
                                id = m.id,
                                name = m.name,
                                size = modelDownloadService.formatSize(m.size),
                                description = if (m.ready) "已就绪" else "需要下载",
                                isReady = m.ready,
                                isDownloading = false
                            )
                        },
                        allReady = allReady,
                        totalDownloadSize = totalSize,
                        canSkip = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "检查模型失败"
                    )
                }
            }
        }
    }

    /**
     * 下载所有缺失的模型
     */
    fun downloadAll() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0,
                    currentDownloading = "准备下载...",
                    error = null
                )
            }

            var completed = 0
            var total = _uiState.value.models.count { !it.isReady }


            modelDownloadService.downloadAllMissing().collect { state ->
                when (state) {
                    is ModelDownloadService.DownloadState.Progress -> {
                        _uiState.update {
                            it.copy(
                                downloadProgress = (completed * 100 / total.coerceAtLeast(1)),
                                currentDownloading = state.message
                            )
                        }
                    }

                    is ModelDownloadService.DownloadState.PartialCompleted -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = 100,
                                error = "部分模型下载失败: ${state.success}/${state.total}"
                            )
                        }
                    }

                    is ModelDownloadService.DownloadState.AllCompleted -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadProgress = 100,
                                allReady = true
                            )
                        }
                        // 刷新状态
                        checkModels()
                    }

                    is ModelDownloadService.DownloadState.Failed -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                error = state.error
                            )
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    /**
     * 下载单个模型
     */
    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            // 标记正在下载
            _uiState.update { state ->
                state.copy(
                    models = state.models.map { m ->
                        if (m.id == modelId) m.copy(isDownloading = true) else m
                    }
                )
            }

            modelDownloadService.downloadModel(modelId).collect { state ->
                when (state) {
                    is ModelDownloadService.DownloadState.Completed -> {
                        // 更新状态
                        _uiState.update { state ->
                            state.copy(
                                models = state.models.map { m ->
                                    if (m.id == modelId) {
                                        m.copy(
                                            isReady = true,
                                            isDownloading = false,
                                            description = "已就绪"
                                        )
                                    } else m
                                },
                                allReady = state.models.all { it.id == modelId || it.isReady }
                            )
                        }
                    }

                    is ModelDownloadService.DownloadState.Failed -> {
                        _uiState.update { state ->
                            state.copy(
                                models = state.models.map { m ->
                                    if (m.id == modelId) {
                                        m.copy(isDownloading = false, description = "下载失败")
                                    } else m
                                },
                                error = state.error
                            )
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    /**
     * 数据类
     */
    data class ModelUiInfo(
        val id: String,
        val name: String,
        val size: String,
        val description: String,
        val isReady: Boolean,
        val isDownloading: Boolean
    )

    data class ModelInitUiState(
        val isLoading: Boolean = false,
        val isDownloading: Boolean = false,
        val downloadProgress: Int = 0,
        val currentDownloading: String = "",
        val models: List<ModelUiInfo> = emptyList(),
        val allReady: Boolean = false,
        val totalDownloadSize: String = "0 MB",
        val canSkip: Boolean = false,
        val error: String? = null
    )
}
