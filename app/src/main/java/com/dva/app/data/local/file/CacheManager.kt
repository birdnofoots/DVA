package com.dva.app.data.local.file

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_CACHE_SIZE = 30 // 缓存帧数
        private const val MAX_AGE_MS = 60000L // 缓存有效期：1分钟
    }

    private val frameCache = FrameLruCache(MAX_CACHE_SIZE)
    private val cacheDir = File(context.cacheDir, "frames").also { it.mkdirs() }

    fun getCachedFrame(timestampMs: Long): Bitmap? {
        return frameCache.get(timestampMs)
    }

    fun cacheFrame(timestampMs: Long, bitmap: Bitmap) {
        frameCache.put(timestampMs, bitmap)
    }

    suspend fun loadFrameFromDisk(timestampMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "frame_$timestampMs.png")
        if (file.exists()) {
            try {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun saveFrameToDisk(timestampMs: Long, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "frame_$timestampMs.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clear() {
        frameCache.clear()
    }

    suspend fun clearDiskCache() = withContext(Dispatchers.IO) {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    private inner class FrameLruCache(private val maxSize: Int) {
        private val cache = LinkedHashMap<Long, Bitmap>(maxSize, 0.75f, true)
        private val timestamps = mutableMapOf<Long, Long>()

        fun get(timestampMs: Long): Bitmap? {
            val bitmap = cache[timestampMs]
            if (bitmap != null && isValid(timestampMs)) {
                // 移到最新位置
                cache.remove(timestampMs)
                cache[timestampMs] = bitmap
                return bitmap
            }
            cache.remove(timestampMs)
            timestamps.remove(timestampMs)
            return null
        }

        fun put(timestampMs: Long, bitmap: Bitmap) {
            if (cache.size >= maxSize) {
                val oldest = cache.keys.iterator().next()
                cache.remove(oldest)?.recycle()
                timestamps.remove(oldest)
            }
            cache[timestampMs] = bitmap
            timestamps[timestampMs] = System.currentTimeMillis()
        }

        fun clear() {
            cache.values.forEach { it.recycle() }
            cache.clear()
            timestamps.clear()
        }

        private fun isValid(timestampMs: Long): Boolean {
            val age = System.currentTimeMillis() - (timestamps[timestampMs] ?: 0)
            return age < MAX_AGE_MS
        }
    }
}
