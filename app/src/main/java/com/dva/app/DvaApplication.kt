package com.dva.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * DVA 应用入口
 */
@HiltAndroidApp
class DvaApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        // 启动时清理过期缓存
        applicationScope.launch {
            cleanupExpiredCache()
        }
    }
    
    /**
     * 清理过期的缓存视频（超过7天的）
     */
    private fun cleanupExpiredCache() {
        try {
            val cacheDir = File(cacheDir, "video_cache")
            if (!cacheDir.exists()) return
            
            val now = System.currentTimeMillis()
            val expireTime = 7 * 24 * 60 * 60 * 1000L // 7天
            
            cacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > expireTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // 忽略清理失败
        }
    }
}
