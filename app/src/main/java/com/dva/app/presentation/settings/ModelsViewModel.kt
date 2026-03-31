package com.dva.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    
    // assets/models 目录
    private val modelsDir = File(context.filesDir, "models")
    
    init {
        modelsDir.mkdirs()
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
                    description = "YOLOv8n 模型，用于检测车辆、行人等目标",
                    purpose = "车辆检测",
                    fileSize = 12_851_049, // 12.3 MB
                    downloadUrl = "",
                    localPath = getLocalModelPath("yolov8n-vehicle.onnx"),
                    isDownloaded = checkModelExists("yolov8n-vehicle.onnx")
                ),
                MLModel(
                    id = "lanenet",
                    name = "LaneNet (车道线检测)",
                    description = "车道线检测模型，用于识别道路车道线",
                    purpose = "变道检测",
                    fileSize = 50_000_000, // 估算 50MB
                    downloadUrl = "https://github.com/birdnofoots/DVA/releases/download/models/lanenet.onnx",
                    localPath = getLocalModelPath("lanenet.onnx"),
                    isDownloaded = checkModelExists("lanenet.onnx")
                ),
                MLModel(
                    id = "lprnet",
                    name = "LPRNet (车牌识别)",
                    description = "中文车牌识别模型，识别中国大陆车牌",
                    purpose = "车牌识别",
                    fileSize = 30_000_000, // 估算 30MB
                    downloadUrl = "https://github.com/birdnofoots/DVA/releases/download/models/lprnet.onnx",
                    localPath = getLocalModelPath("lprnet.onnx"),
                    isDownloaded = checkModelExists("lprnet.onnx")
                ),
                MLModel(
                    id = "paddleocr",
                    name = "PaddleOCR (文字识别)",
                    description = "通用文字识别，可作为车牌识别的备选方案",
                    purpose = "车牌识别（备选）",
                    fileSize = 100_000_000, // 估算 100MB
                    downloadUrl = "https://github.com/PaddlePaddle/PaddleOCR/blob/release/2.7/models/ch_PP-OCRv4_det_infer.tar",
                    localPath = getLocalModelPath("paddleocr"),
                    isDownloaded = checkPaddleOCRExists()
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
            
            // 更新状态为下载中
            updateModelState(modelId, isDownloading = true, downloadProgress = 0)
            
            // 模拟下载进度（实际应该用真实的下载库）
            // TODO: 实现真实的模型下载
            simulateDownload(modelId)
        }
    }
    
    /**
     * 模拟下载进度
     */
    private suspend fun simulateDownload(modelId: String) {
        for (progress in 0..100 step 10) {
            kotlinx.coroutines.delay(500)
            updateModelState(modelId, downloadProgress = progress)
        }
        
        // 下载完成
        updateModelState(
            modelId, 
            isDownloading = false, 
            isDownloaded = true, 
            downloadProgress = 100
        )
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
        return File(modelsDir, fileName).absolutePath
    }
    
    /**
     * 检查模型是否存在
     */
    private fun checkModelExists(fileName: String): Boolean {
        return File(modelsDir, fileName).exists()
    }
    
    /**
     * 检查 PaddleOCR 是否存在
     */
    private fun checkPaddleOCRExists(): Boolean {
        return File(modelsDir, "paddleocr").isDirectory
    }
    
    /**
     * 删除模型
     */
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val model = _uiState.value.models.find { it.id == modelId } ?: return@launch
            
            model.localPath?.let { path ->
                if (File(path).exists()) {
                    File(path).delete()
                }
            }
            
            updateModelState(modelId, isDownloaded = false)
        }
    }
}
