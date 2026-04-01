package com.dva.app.data.local.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String? = null,
    val isDownloaded: Boolean = false
)

/**
 * 模型下载管理器
 * 从 GitHub Releases 下载 ML 模型文件
 */
class ModelDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ModelDownloadManager"
        
        // GitHub Releases URL
        private const val GITHUB_REPO = "birdnofoots/DVA"
        
        // 模型文件目录
        private const val MODELS_DIR = "models"
        
        // 可用模型列表
        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "yolov8n-vehicle",
                name = "YOLOv8n 车辆检测",
                description = "用于检测视频中的车辆目标，轻量级模型",
                fileName = "yolov8n-vehicle.onnx",
                sizeBytes = 6_500_000,
                downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/v1.5.0/yolov8n-vehicle.onnx",
                sha256 = null
            ),
            ModelInfo(
                id = "lane-detection",
                name = "车道线检测",
                description = "基于边缘检测的车道线识别模型",
                fileName = "lane-detection.onnx",
                sizeBytes = 5_200_000,
                downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/v1.5.0/lane-detection.onnx",
                sha256 = null
            ),
            ModelInfo(
                id = "paddle-ocr-lite",
                name = "PaddleOCR 车牌识别",
                description = "轻量级 OCR 模型，用于识别车牌号码",
                fileName = "paddle-ocr-lite.onnx",
                sizeBytes = 10_500_000,
                downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/v1.5.0/paddle-ocr-lite.onnx",
                sha256 = null
            )
        )
    }
    
    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    }
    
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(
        AVAILABLE_MODELS.associate { it.id to DownloadState.Idle }
    )
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()
    
    private val _modelStatuses = MutableStateFlow<Map<String, Boolean>>(
        emptyMap()
    )
    val modelStatuses: StateFlow<Map<String, Boolean>> = _modelStatuses.asStateFlow()
    
    init {
        refreshModelStatuses()
    }
    
    /**
     * 检查模型是否已下载
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        return File(modelsDir, model.fileName).exists()
    }
    
    /**
     * 刷新所有模型状态
     */
    fun refreshModelStatuses() {
        _modelStatuses.value = AVAILABLE_MODELS.associate { model ->
            model.id to File(modelsDir, model.fileName).exists()
        }
    }
    
    /**
     * 获取已下载模型的路径
     */
    fun getModelPath(modelId: String): String? {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists()) file.absolutePath else null
    }
    
    /**
     * 获取模型文件
     */
    fun getModelFile(modelId: String): File? {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val file = File(modelsDir, model.fileName)
        return if (file.exists()) file else null
    }
    
    /**
     * 下载模型
     */
    suspend fun downloadModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: run {
            updateState(modelId, DownloadState.Error("未知模型: $modelId"))
            return@withContext
        }
        
        updateState(modelId, DownloadState.Downloading(0))
        
        try {
            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()
            
            val fileSize = connection.contentLength
            val outputFile = File(modelsDir, model.fileName)
            
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val progress = if (fileSize > 0) {
                            ((totalBytesRead * 100) / fileSize).toInt()
                        } else {
                            -1
                        }
                        updateState(modelId, DownloadState.Downloading(progress))
                    }
                }
            }
            
            // 验证 SHA256（如果有）
            if (model.sha256 != null) {
                val fileSha256 = calculateSHA256(outputFile)
                if (fileSha256 != model.sha256) {
                    outputFile.delete()
                    updateState(modelId, DownloadState.Error("SHA256 校验失败"))
                    return@withContext
                }
            }
            
            updateState(modelId, DownloadState.Completed)
            refreshModelStatuses()
            Log.d(TAG, "模型下载完成: ${model.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "模型下载失败: ${model.name}", e)
            updateState(modelId, DownloadState.Error(e.message ?: "下载失败"))
        }
    }
    
    /**
     * 删除模型
     */
    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return@withContext
        val file = File(modelsDir, model.fileName)
        if (file.exists()) {
            file.delete()
        }
        refreshModelStatuses()
        updateState(modelId, DownloadState.Idle)
    }
    
    /**
     * 删除所有模型
     */
    suspend fun deleteAllModels() = withContext(Dispatchers.IO) {
        modelsDir.listFiles()?.forEach { it.delete() }
        refreshModelStatuses()
        AVAILABLE_MODELS.forEach { updateState(it.id, DownloadState.Idle) }
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    /**
     * 复制视频到本地缓存目录
     */
    suspend fun copyVideoToLocalCache(videoUri: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!videoUri.startsWith("content://")) {
                return@withContext videoUri
            }
            
            val uri = Uri.parse(videoUri)
            val fileName = getFileNameFromUri(uri) ?: "video_${System.currentTimeMillis()}.mp4"
            val cacheDir = File(context.cacheDir, "video_cache")
            cacheDir.mkdirs()
            
            // 清理旧文件
            cacheDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                    file.delete()
                }
            }
            
            val outputFile = File(cacheDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "复制视频到缓存失败", e)
            null
        }
    }
    
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
    
    private fun updateState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }
    
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
