package com.dva.app.data.repository

import com.dva.app.data.local.db.dao.TaskDao
import com.dva.app.data.local.db.dao.VideoDao
import com.dva.app.data.local.db.entity.TaskEntity
import com.dva.app.domain.model.AnalysisTask
import com.dva.app.domain.model.TaskStatus
import com.dva.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val videoDao: VideoDao
) : TaskRepository {

    override suspend fun createTask(videoPath: String): Result<AnalysisTask> {
        return try {
            val video = videoDao.getByPath(videoPath)
                ?: return Result.failure(IllegalArgumentException("Video not found"))
            
            val task = AnalysisTask(
                id = UUID.randomUUID().toString(),
                videoPath = videoPath,
                status = TaskStatus.PENDING,
                totalFrames = video.totalFrames
            )
            
            taskDao.insert(task.toEntity())
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTaskById(id: String): Result<AnalysisTask?> {
        return try {
            val entity = taskDao.getById(id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLatestTaskByVideo(videoPath: String): Result<AnalysisTask?> {
        return try {
            val entity = taskDao.getLatestByVideo(videoPath)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateTask(task: AnalysisTask): Result<Unit> {
        return try {
            taskDao.update(task.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProgress(
        taskId: String,
        currentFrame: Long,
        violationsFound: Int
    ): Result<Unit> {
        return try {
            val task = taskDao.getById(taskId) ?: return Result.failure(Exception("Task not found"))
            val progress = if (task.totalFrames > 0) {
                currentFrame.toFloat() / task.totalFrames
            } else 0f
            
            taskDao.updateProgress(
                id = taskId,
                status = TaskStatus.RUNNING.name,
                frame = currentFrame,
                progress = progress,
                violations = violationsFound
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateStatus(taskId: String, status: TaskStatus): Result<Unit> {
        return try {
            val entity = taskDao.getById(taskId) ?: return Result.failure(Exception("Task not found"))
            val updatedEntity = entity.copy(
                status = status.name,
                startedAt = if (status == TaskStatus.RUNNING && entity.startedAt == null) {
                    System.currentTimeMillis()
                } else entity.startedAt
            )
            taskDao.update(updatedEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeTask(taskId: String): Result<Unit> {
        return try {
            taskDao.complete(taskId, TaskStatus.COMPLETED.name, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun failTask(taskId: String, error: String): Result<Unit> {
        return try {
            taskDao.fail(taskId, TaskStatus.FAILED.name, error)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllTasks(): Result<List<AnalysisTask>> {
        return try {
            // Note: Need to implement observeAll().first() or similar
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeTasks(): Flow<List<AnalysisTask>> {
        return taskDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTask(taskId: String): Flow<AnalysisTask?> {
        return taskDao.observe(taskId).map { it?.toDomain() }
    }

    private fun AnalysisTask.toEntity() = TaskEntity(
        id = id,
        videoId = "",
        videoPath = videoPath,
        status = status.name,
        progress = progress,
        currentFrame = currentFrame,
        totalFrames = totalFrames,
        violationsFound = violationsFound,
        startedAt = startedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        lastResumePosition = lastResumePosition
    )

    private fun TaskEntity.toDomain() = AnalysisTask(
        id = id,
        videoPath = videoPath,
        status = TaskStatus.valueOf(status),
        progress = progress,
        currentFrame = currentFrame,
        totalFrames = totalFrames,
        violationsFound = violationsFound,
        startedAt = startedAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        lastResumePosition = lastResumePosition
    )
}
