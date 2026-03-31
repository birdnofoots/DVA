package com.dva.app.data.local.storage

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 模型下载状态
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * 模型下载器
 * 从 URL 下载文件并保存到本地
 */
class ModelDownloader(
    private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * 下载文件
     * @param url 下载地址
     * @param fileName 保存的文件名
     * @param onProgress 进度回调 (0-100)
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 确保目录存在
            val modelsDir = File(context.filesDir, "models")
            modelsDir.mkdirs()
            
            val outputFile = File(modelsDir, fileName)
            
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            
            val fileSize = connection.contentLength
            var downloadedSize = 0
            
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        // 计算进度
                        val progress = if (fileSize > 0) {
                            (downloadedSize * 100 / fileSize).coerceIn(0, 100)
                        } else {
                            -1 // 未知大小
                        }
                        
                        // 回调进度
                        mainHandler.post { onProgress(progress) }
                    }
                }
            }
            
            connection.disconnect()
            
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 检查本地模型是否存在
     */
    fun isModelDownloaded(fileName: String): Boolean {
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, fileName)
        return file.exists() && file.length() > 0
    }
    
    /**
     * 删除本地模型
     */
    fun deleteModel(fileName: String): Boolean {
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, fileName)
        return file.delete()
    }
    
    /**
     * 获取本地模型路径
     */
    fun getModelPath(fileName: String): String {
        val modelsDir = File(context.filesDir, "models")
        return File(modelsDir, fileName).absolutePath
    }
    
    /**
     * 获取已下载模型的大小
     */
    fun getModelSize(fileName: String): Long {
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, fileName)
        return if (file.exists()) file.length() else 0
    }
}
