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
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型管理器
 * 负责模型的下载、更新和版本管理
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ModelManager"

        // 模型配置
        val MODELS = listOf(
            ModelInfo(
                id = "vehicle_detector",
                name = "车辆检测",
                fileName = "yolov8n-vehicle.onnx",
                defaultVersion = "1.0.0",
                sizeBytes = 6_000_000L,
                description = "YOLOv8n 车辆检测"
            ),
            ModelInfo(
                id = "lane_detector",
                name = "车道线检测",
                fileName = "lanenet.onnx",
                defaultVersion = "1.0.0",
                sizeBytes = 15_000_000L,
                description = "车道线分割"
            ),
            ModelInfo(
                id = "lpr_recognizer",
                name = "车牌识别",
                fileName = "lprnet_chinese.onnx",
                defaultVersion = "1.0.0",
                sizeBytes = 10_000_000L,
                description = "LPRNet 中文车牌"
            )
        )

        private const val MODEL_DIR = "models"
    }

    private val modelDir: File by lazy {
        File(context.filesDir, MODEL_DIR).also { it.mkdirs() }
    }

    /**
     * 获取模型目录
     */
    fun getModelDirectory(): File = modelDir

    /**
     * 获取模型文件路径
     */
    fun getModelPath(modelId: String): String? {
        val modelInfo = MODELS.find { it.id == modelId } ?: return null
        val file = File(modelDir, modelInfo.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * 检查模型是否已安装
     */
    fun isModelInstalled(modelId: String): Boolean {
        return getModelPath(modelId) != null
    }

    /**
     * 获取已安装模型列表
     */
    fun getInstalledModels(): List<InstalledModel> {
        return MODELS.map { model ->
            val path = getModelPath(model.id)
            InstalledModel(
                info = model,
                installed = path != null,
                localPath = path,
                installedVersion = if (path != null) model.defaultVersion else null
            )
        }
    }

    /**
     * 获取模型信息
     */
    fun getModelInfo(modelId: String): ModelInfo? {
        return MODELS.find { it.id == modelId }
    }

    /**
     * 删除模型
     */
    fun deleteModel(modelId: String): Boolean {
        val modelInfo = MODELS.find { it.id == modelId } ?: return false
        val file = File(modelDir, modelInfo.fileName)
        return file.delete()
    }

    /**
     * 删除所有模型
     */
    fun deleteAllModels(): Boolean {
        return modelDir.listFiles()?.all { it.delete() } ?: true
    }

    /**
     * 获取已安装模型总大小
     */
    fun getTotalModelsSize(): Long {
        return MODELS.sumOf { model ->
            val file = File(modelDir, model.fileName)
            if (file.exists()) file.length() else 0L
        }
    }

    /**
     * 检查应用内是否有内置模型
     */
    fun hasBuiltInModel(modelId: String): Boolean {
        val modelInfo = MODELS.find { it.id == modelId } ?: return false
        val assetPath = "models/${modelInfo.fileName}"
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从 assets 复制内置模型
     */
    suspend fun extractBuiltInModel(modelId: String): Result<String> = withContext(Dispatchers.IO) {
        val modelInfo = MODELS.find { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown model: $modelId"))

        val assetPath = "models/${modelInfo.fileName}"

        try {
            val outputFile = File(modelDir, modelInfo.fileName)

            context.assets.open(assetPath).use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 模型信息
     */
    data class ModelInfo(
        val id: String,
        val name: String,
        val fileName: String,
        val defaultVersion: String,
        val sizeBytes: Long,
        val description: String
    )

    /**
     * 已安装模型
     */
    data class InstalledModel(
        val info: ModelInfo,
        val installed: Boolean,
        val localPath: String?,
        val installedVersion: String?
    )
}
