package com.dva.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.data.local.storage.ModelDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ML 模型信息
 */
data class MLModel(
    val id: String,
    val name: String,
    val description: String,
    val purpose: String,
    val fileSize: Long,
    val downloadUrl: String,
    val localFileName: String,
    val localPath: String?,
    val isDownloaded: Boolean,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0
)

/**
 * 模型管理 UI 状态
 */
data class ModelsUiState(
    val models: List<MLModel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 模型管理 ViewModel
 */
@HiltViewModel
class ModelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()
    
    private val modelDownloader = ModelDownloader(context)
    
    init {
        loadModels()
    }
    
    /**
     * 加载所有 ML 模型状态
     */
    fun loadModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val models = listOf(
                MLModel(
                    id = "yolov8n-vehicle",
                    name = "YOLOv8n (车辆检测)",
                    description = "YOLOv8n 模型，用于检测车辆、行人等目标。是最轻量的 YOLOv8 模型，适合移动端运行。",
                    purpose = "车辆检测",
                    fileSize = 12_851_049, // 12.3 MB
                    downloadUrl = "https://github.com/birdnofoots/DVA/releases/download/models/yolov8n-vehicle.onnx",
                    localFileName = "yolov8n-vehicle.onnx",
                    localPath = getLocalModelPath("yolov8n-vehicle.onnx"),
                    isDownloaded = modelDownloader.isModelDownloaded("yolov8n-vehicle.onnx")
                ),
                MLModel(
                    id = "lanenet",
                    name = "LaneNet (车道线检测)",
                    description = "车道线检测模型，用于识别道路车道线。结合车辆检测可以判断车辆是否变道。",
                    purpose = "车道线检测",
                    fileSize = 50_000_000, // 估算 50MB
                    downloadUrl = "https://github.com/birdnofoots/DVA/releases/download/models/lanenet.onnx",
                    localFileName = "lanenet.onnx",
                    localPath = getLocalModelPath("lanenet.onnx"),
                    isDownloaded = modelDownloader.isModelDownloaded("lanenet.onnx")
                ),
                MLModel(
                    id = "lprnet",
                    name = "LPRNet (车牌识别)",
                    description = "中文车牌识别模型，识别中国大陆蓝色、绿色车牌。轻量级模型，适合移动端。",
                    purpose = "车牌识别",
                    fileSize = 30_000_000, // 估算 30MB
                    downloadUrl = "https://github.com/birdnofoots/DVA/releases/download/models/lprnet.onnx",
                    localFileName = "lprnet.onnx",
                    localPath = getLocalModelPath("lprnet.onnx"),
                    isDownloaded = modelDownloader.isModelDownloaded("lprnet.onnx")
                )
            )
            
            _uiState.value = _uiState.value.copy(
                models = models,
                isLoading = false
            )
        }
    }
    
    /**
     * 下载模型
     */
    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            val model = _uiState.value.models.find { it.id == modelId } ?: return@launch
            
            if (model.downloadUrl.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "此模型暂无下载链接"
                )
                return@launch
            }
            
            // 更新状态为下载中
            updateModelState(modelId, isDownloading = true, downloadProgress = 0)
            
            // 执行下载
            modelDownloader.download(
                url = model.downloadUrl,
                fileName = model.localFileName,
                onProgress = { progress ->
                    updateModelState(modelId, downloadProgress = progress)
                }
            ).onSuccess { file ->
                // 下载完成
                updateModelState(
                    modelId,
                    isDownloading = false,
                    isDownloaded = true,
                    downloadProgress = 100
                )
            }.onFailure { error ->
                // 下载失败
                updateModelState(
                    modelId,
                    isDownloading = false,
                    isDownloaded = false,
                    downloadProgress = 0
                )
                _uiState.value = _uiState.value.copy(
                    errorMessage = "下载失败: ${error.message}"
                )
            }
        }
    }
    
    /**
     * 删除模型
     */
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val model = _uiState.value.models.find { it.id == modelId } ?: return@launch
            
            val deleted = modelDownloader.deleteModel(model.localFileName)
            if (deleted) {
                updateModelState(modelId, isDownloaded = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "删除模型失败"
                )
            }
        }
    }
    
    /**
     * 更新模型状态
     */
    private fun updateModelState(
        modelId: String,
        isDownloading: Boolean? = null,
        isDownloaded: Boolean? = null,
        downloadProgress: Int? = null
    ) {
        val currentModels = _uiState.value.models.toMutableList()
        val index = currentModels.indexOfFirst { it.id == modelId }
        
        if (index >= 0) {
            val model = currentModels[index]
            currentModels[index] = model.copy(
                isDownloading = isDownloading ?: model.isDownloading,
                isDownloaded = isDownloaded ?: model.isDownloaded,
                downloadProgress = downloadProgress ?: model.downloadProgress
            )
            _uiState.value = _uiState.value.copy(models = currentModels)
        }
    }
    
    /**
     * 获取本地模型路径
     */
    private fun getLocalModelPath(fileName: String): String {
        return modelDownloader.getModelPath(fileName)
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * 获取已下载模型的可用状态
     */
    fun getAvailableModels(): List<MLModel> {
        return _uiState.value.models.filter { it.isDownloaded }
    }
    
    /**
     * 检查特定模型是否可用
     */
    fun isModelAvailable(modelId: String): Boolean {
        return _uiState.value.models.find { it.id == modelId }?.isDownloaded == true
    }
}
