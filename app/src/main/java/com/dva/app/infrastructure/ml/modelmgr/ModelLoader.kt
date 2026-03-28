package com.dva.app.infrastructure.ml.modelmgr

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型加载器
 * 负责从应用目录或下载路径加载模型文件
 * 
 * 加载优先级：
 * 1. 应用内部存储 (filesDir/models/) - 下载的模型
 * 2. assets/models/ - 内置模型（首次使用时复制）
 */
@Singleton
class ModelLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
) {
    companion object {
        private const val TAG = "ModelLoader"
    }

    /**
     * 加载模型
     * @param modelId 模型ID
     * @return 模型文件路径
     */
    suspend fun loadModel(modelId: String): Result<String> = withContext(Dispatchers.IO) {
        // 1. 检查已安装的模型（应用目录）
        val installedPath = modelManager.getModelPath(modelId)
        if (installedPath != null) {
            Log.d(TAG, "使用已安装模型: $installedPath")
            return@withContext Result.success(installedPath)
        }

        // 2. 检查内置模型（assets）
        val builtInPath = loadFromAssets(modelId)
        if (builtInPath != null) {
            Log.d(TAG, "从assets加载并复制: $builtInPath")
            return@withContext Result.success(builtInPath)
        }

        // 3. 无可用模型
        Result.failure(Exception("模型不可用: $modelId"))
    }

    /**
     * 从 assets 加载模型到内部存储
     */
    private fun loadFromAssets(modelId: String): String? {
        val modelInfo = modelManager.getModelInfo(modelId) ?: return null
        val assetFileName = "models/${modelInfo.fileName}"

        return try {
            context.assets.open(assetFileName).use { input ->
                val outputFile = File(modelManager.getModelDirectory(), modelInfo.fileName)

                // 如果已存在，直接返回
                if (outputFile.exists() && outputFile.length() > 1000000) {
                    return outputFile.absolutePath
                }

                // 复制到内部存储
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }

                Log.d(TAG, "从assets复制模型: ${outputFile.absolutePath}")
                outputFile.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "assets中无模型: ${e.message}")
            null
        }
    }

    /**
     * 检查模型是否可用
     */
    fun isModelReady(modelId: String): Boolean {
        // 检查已安装
        if (modelManager.isModelInstalled(modelId)) {
            return true
        }

        // 检查内置
        return hasBuiltInModel(modelId)
    }

    /**
     * 检查是否有内置模型
     */
    private fun hasBuiltInModel(modelId: String): Boolean {
        val modelInfo = modelManager.getModelInfo(modelId) ?: return false
        val assetPath = "models/${modelInfo.fileName}"

        return try {
            context.assets.open(assetPath).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取所有模型状态
     */
    fun getAllModelsStatus(): List<ModelStatus> {
        return ModelManager.MODELS.map { model ->
            val installed = modelManager.isModelInstalled(model.id)
            val builtIn = hasBuiltInModel(model.id)

            ModelStatus(
                id = model.id,
                name = model.name,
                fileName = model.fileName,
                size = model.sizeBytes,
                installed = installed,
                builtIn = builtIn,
                localPath = if (installed) modelManager.getModelPath(model.id) else null,
                ready = installed || builtIn
            )
        }
    }

    /**
     * 获取模型目录
     */
    fun getModelDirectory(): File = modelManager.getModelDirectory()

    /**
     * 删除模型
     */
    fun deleteModel(modelId: String): Boolean = modelManager.deleteModel(modelId)

    /**
     * 获取缓存的模型总大小
     */
    fun getCachedModelsSize(): Long = modelManager.getTotalModelsSize()

    /**
     * 格式化大小
     */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
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
