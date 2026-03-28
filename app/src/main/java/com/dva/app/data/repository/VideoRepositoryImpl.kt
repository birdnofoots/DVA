package com.dva.app.data.repository

import com.dva.app.data.local.db.dao.VideoDao
import com.dva.app.data.local.db.entity.VideoEntity
import com.dva.app.data.local.file.FileStorageManager
import com.dva.app.domain.model.Video
import com.dva.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val videoDao: VideoDao,
    private val fileStorageManager: FileStorageManager
) : VideoRepository {

    override suspend fun scanFolder(folderPath: String): Result<List<Video>> {
        return try {
            val files = fileStorageManager.scanVideoFolder(folderPath)
            val videos = files.mapNotNull { file ->
                createVideoFromFile(file)
            }
            
            // 保存到数据库
            videoDao.insertAll(videos.map { it.toEntity() })
            
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVideoList(
        folderPath: String?,
        offset: Int,
        limit: Int
    ): Result<List<Video>> {
        return try {
            val entities = videoDao.getList(limit, offset)
            Result.success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVideoById(id: String): Result<Video?> {
        return try {
            val entity = videoDao.getById(id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getThumbnail(videoPath: String): Result<String> {
        return Result.success("") // 缩略图暂未实现
    }

    override suspend fun deleteVideo(id: String): Result<Unit> {
        return try {
            videoDao.delete(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeVideoList(): Flow<List<Video>> {
        return videoDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private suspend fun createVideoFromFile(file: File): Video? {
        val metadata = fileStorageManager.getVideoMetadata(file.absolutePath) ?: return null
        
        return Video(
            id = UUID.randomUUID().toString(),
            filePath = file.absolutePath,
            fileName = file.name,
            durationMs = metadata.durationMs,
            width = metadata.width,
            height = metadata.height,
            frameRate = metadata.frameRate,
            totalFrames = metadata.totalFrames,
            fileSize = file.length(),
            createdAt = file.lastModified(),
            lastModified = file.lastModified()
        )
    }

    private fun Video.toEntity() = VideoEntity(
        id = id,
        filePath = filePath,
        fileName = fileName,
        durationMs = durationMs,
        width = width,
        height = height,
        frameRate = frameRate,
        totalFrames = totalFrames,
        fileSize = fileSize,
        createdAt = createdAt,
        lastModified = lastModified,
        thumbnailPath = thumbnailPath
    )

    private fun VideoEntity.toDomain() = Video(
        id = id,
        filePath = filePath,
        fileName = fileName,
        durationMs = durationMs,
        width = width,
        height = height,
        frameRate = frameRate,
        totalFrames = totalFrames,
        fileSize = fileSize,
        createdAt = createdAt,
        lastModified = lastModified,
        thumbnailPath = thumbnailPath
    )
}
