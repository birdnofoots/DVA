package com.dva.app.data.local.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.dva.app.domain.model.VideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 MediaStore 的视频选择器
 * 不需要 SAF 权限，可以访问所有媒体库视频
 */
class MediaStoreVideoPicker(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaStoreVideoPicker"
    }
    
    /**
     * 查询所有视频
     */
    suspend fun queryAllVideos(): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "unknown"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    
                    // 构建 content:// URI
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    // 只添加 mp4, mov, avi 格式
                    val extension = name.substringAfterLast(".").lowercase()
                    if (extension in listOf("mp4", "mov", "avi", "mkv", "3gp", "webm")) {
                        videos.add(
                            VideoFile(
                                path = contentUri.toString(),
                                name = name,
                                durationMs = duration,
                                width = width,
                                height = height,
                                fps = 25f,
                                frameCount = ((duration / 1000.0) * 25).toInt(),
                                fileSize = size
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询视频失败", e)
        }
        
        videos
    }
    
    /**
     * 按文件夹分组查询视频
     */
    suspend fun queryVideosByFolder(folderPath: String?): List<VideoFile> = withContext(Dispatchers.IO) {
        val allVideos = queryAllVideos()
        
        if (folderPath.isNullOrBlank()) {
            return@withContext allVideos
        }
        
        allVideos.filter { video ->
            video.path?.startsWith(folderPath) == true
        }
    }
    
    /**
     * 获取 Downloads/DVA 文件夹下的视频
     */
    suspend fun queryDvaFolderVideos(): List<VideoFile> = withContext(Dispatchers.IO) {
        val allVideos = queryAllVideos()
        
        allVideos.filter { video ->
            val path = video.path ?: ""
            path.contains("Download", ignoreCase = true) && 
            (path.contains("DVA", ignoreCase = true) || !path.contains("/DVA/"))
        }
    }
    
    /**
     * 通过 content URI 获取输入流
     */
    fun getInputStream(uri: Uri) = context.contentResolver.openInputStream(uri)
    
    /**
     * 检查 URI 是否有效
     */
    fun isUriValid(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
