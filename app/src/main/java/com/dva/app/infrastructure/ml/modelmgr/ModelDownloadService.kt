package com.dva.app.infrastructure.ml.modelmgr

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型下载服务
 * 负责从远程服务器下载模型到应用目录
 */
@Singleton
class ModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
) {
    companion object {
        private const val TAG = "ModelDownloadService"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 300000

        // 模型下载服务器地址（需要替换为实际服务器）
        private const val MODEL_BASE_URL = "https://your-model-server.com/models"

        // 各模型的下载地址
        private val MODEL_DOWNLOAD_URLS = mapOf(
            "vehicle_detector" to listOf(
                "$MODEL_BASE_URL/yolov8n-vehicle.onnx",
                "https://github.com/ultralytics/ultralytics/releases/download/v8.2.0/yolov8n.onnx"
            ),
            "lane_detector" to listOf(
                "$MODEL_BASE_URL/lanenet.onnx",
                "https://github.com/harryhanYu/LaneNet_Deep_Learning_Studio/releases/download/v1.0/lanenet.onnx"
            ),
            "lpr_recognizer" to listOf(
                "$MODEL_BASE_URL/lprnet_chinese.onnx",
                "https://raw.githubusercontent.com/myyrRO/le-cheng-shu/main/lprnet.onnx"
            )
        )
    }

    /**
     * 下载单个模型
     */
    fun downloadModel(modelId: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0, 0, 0))

        val modelInfo = modelManager.getModelInfo(modelId)
        if (modelInfo == null) {
            emit(DownloadState.Failed("未知模型: $modelId"))
            return@flow
        }

        val urls = MODEL_DOWNLOAD_URLS[modelId] ?: emptyList()
        if (urls.isEmpty()) {
            emit(DownloadState.Failed("无下载链接: $modelId"))
            return@flow
        }

        var lastError: String? = null

        for (url in urls) {
            try {
                Log.d(TAG, "尝试下载: $url")
                emit(DownloadState.Downloading(0, 0, 0, "正在连接..."))

                val result = downloadFromUrl(url, modelInfo.fileName)
                if (result != null) {
                    emit(DownloadState.Completed(result, modelInfo.fileName))
                    return@flow
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "下载失败: $url, ${e.message}")
            }
        }

        emit(DownloadState.Failed(lastError ?: "下载失败"))
    }

    /**
     * 下载所有缺失的模型
     */
    fun downloadAllMissing(): Flow<DownloadState> = flow {
        val missingModels = ModelManager.MODELS.filter { !modelManager.isModelInstalled(it.id) }

        if (missingModels.isEmpty()) {
            emit(DownloadState.AllCompleted)
            return@flow
        }

        emit(DownloadState.Progress(0, missingModels.size, "准备下载..."))

        var completed = 0
        var failed = 0

        for (model in missingModels) {
            emit(DownloadState.Progress(completed, missingModels.size, "下载 ${model.name}..."))

            var success = false
            val urls = MODEL_DOWNLOAD_URLS[model.id] ?: emptyList()

            for (url in urls) {
                try {
                    val result = downloadFromUrl(url, model.fileName)
                    if (result != null) {
                        completed++
                        success = true
                        emit(DownloadState.Progress(completed, missingModels.size, "完成 ${model.name}"))
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "下载失败: ${model.id}, ${e.message}")
                }
            }


            if (!success) {
                failed++
            }
        }

        if (failed > 0) {
            emit(DownloadState.PartialCompleted(completed, missingModels.size))
        } else {
            emit(DownloadState.AllCompleted)
        }
    }

    /**
     * 从 URL 下载文件
     */
    private suspend fun downloadFromUrl(urlString: String, fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("User-Agent", "DVA-Android/1.0")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP $responseCode: $urlString")
                return@withContext null
            }

            val contentLength = connection.contentLength.toLong()
            val modelDir = modelManager.getModelDirectory()
            val outputFile = File(modelDir, fileName)

            Log.d(TAG, "开始下载: $fileName, 大小: $contentLength")

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            // 这里可以通过 Flow 发送进度，但为了简化省略
                        }
                    }
                }
            }

            connection.disconnect()

            // 验证下载的文件
            if (outputFile.exists() && outputFile.length() > 1000000) {
                Log.d(TAG, "下载完成: ${outputFile.absolutePath}, 大小: ${outputFile.length()}")
                return@withContext outputFile.absolutePath
            } else {
                outputFile.delete()
                Log.w(TAG, "文件太小或不存在: ${outputFile.absolutePath}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载异常: ${e.message}")
            return@withContext null
        }
    }

    /**
     * 获取模型安装状态
     */
    fun getModelsStatus(): List<ModelStatus> {
        return ModelManager.MODELS.map { model ->
            val installed = modelManager.isModelInstalled(model.id)
            val localPath = modelManager.getModelPath(model.id)
            val hasBuiltIn = modelManager.hasBuiltInModel(model.id)

            ModelStatus(
                id = model.id,
                name = model.name,
                fileName = model.fileName,
                size = model.sizeBytes,
                installed = installed,
                builtIn = hasBuiltIn,
                localPath = localPath,
                ready = installed || hasBuiltIn
            )
        }
    }

    /**
     * 检查是否所有模型都可用
     */
    fun areAllModelsReady(): Boolean {
        return getModelsStatus().all { it.ready }
    }

    /**
     * 删除模型
     */
    fun deleteModel(modelId: String): Boolean {
        return modelManager.deleteModel(modelId)
    }

    /**
     * 获取模型目录
     */
    fun getModelDirectory(): File = modelManager.getModelDirectory()

    /**
     * 获取模型文件大小
     */
    fun getModelSize(modelId: String): Long {
        return modelManager.getModelInfo(modelId)?.sizeBytes ?: 0
    }

    /**
     * 获取总下载大小
     */
    fun getTotalDownloadSize(): Long {
        return getModelsStatus()
            .filter { !it.ready }
            .sumOf { it.size }
    }

    /**
     * 格式化文件大小
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * 下载状态
     */
    sealed class DownloadState {
        data class Downloading(
            val progress: Int,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val message: String = ""
        ) : DownloadState()

        data class Completed(val filePath: String, val fileName: String) : DownloadState()
        data class Failed(val error: String) : DownloadState()
        data class Progress(val completed: Int, val total: Int, val message: String) : DownloadState()
        data class PartialCompleted(val success: Int, val total: Int) : DownloadState()
        object AllCompleted : DownloadState()
    }

    /**
     * 模型状态
     */
    data class ModelStatus(
        val id: String,
        val name: String,
        val fileName: String,
        val size: Long,
        val installed: Boolean,
        val builtIn: Boolean,
        val localPath: String?,
        val ready: Boolean
    )
}
