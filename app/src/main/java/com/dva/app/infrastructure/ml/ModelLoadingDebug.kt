package com.dva.app.infrastructure.ml

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模型加载调试日志
 * 写入文件到 cache/models/ 目录
 */
object ModelLoadingDebug {
    
    private const val TAG = "ModelLoadingDebug"
    private const val LOG_FILE_NAME = "model_loading_debug.log"
    
    private var logFile: File? = null
    private var writer: PrintWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    /**
     * 初始化日志文件
     */
    fun init(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "models")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            logFile = File(cacheDir, LOG_FILE_NAME)
            
            // 清空旧日志
            PrintWriter(logFile).close()
            
            writer = PrintWriter(FileWriter(logFile, true))
            
            log("=== Model Loading Debug Started ===")
            log("App Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
            log("Cache Dir: ${cacheDir.absolutePath}")
            log("")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init debug log", e)
        }
    }
    
    /**
     * 写入日志
     */
    fun log(message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val logLine = "[$timestamp] $message"
            
            Log.d(TAG, logLine)
            
            writer?.let {
                it.println(logLine)
                it.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }
    
    /**
     * 写入错误日志
     */
    fun logError(message: String, throwable: Throwable? = null) {
        try {
            val timestamp = dateFormat.format(Date())
            val logLine = "[$timestamp] ERROR: $message"
            
            Log.e(TAG, logLine, throwable)
            
            writer?.let {
                it.println(logLine)
                throwable?.printStackTrace(it)
                it.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write error log", e)
        }
    }
    
    /**
     * 关闭日志文件
     */
    fun close() {
        try {
            writer?.let {
                it.println("")
                it.println("=== Model Loading Debug Ended ===")
                it.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close debug log", e)
        }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
}
