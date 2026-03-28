package com.dva.app.domain.repository

import com.dva.app.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    suspend fun scanFolder(folderPath: String): Result<List<Video>>
    suspend fun getVideoList(
        folderPath: String? = null,
        offset: Int = 0,
        limit: Int = 20
    ): Result<List<Video>>
    suspend fun getVideoById(id: String): Result<Video?>
    suspend fun getThumbnail(videoPath: String): Result<String>
    suspend fun deleteVideo(id: String): Result<Unit>
    fun observeVideoList(): Flow<List<Video>>
}
