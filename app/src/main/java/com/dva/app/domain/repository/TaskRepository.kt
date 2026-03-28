package com.dva.app.domain.repository

import com.dva.app.domain.model.AnalysisTask
import com.dva.app.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun createTask(videoPath: String): Result<AnalysisTask>
    suspend fun getTaskById(id: String): Result<AnalysisTask?>
    suspend fun getLatestTaskByVideo(videoPath: String): Result<AnalysisTask?>
    suspend fun updateTask(task: AnalysisTask): Result<Unit>
    suspend fun updateProgress(
        taskId: String,
        currentFrame: Long,
        violationsFound: Int
    ): Result<Unit>
    suspend fun updateStatus(taskId: String, status: TaskStatus): Result<Unit>
    suspend fun completeTask(taskId: String): Result<Unit>
    suspend fun failTask(taskId: String, error: String): Result<Unit>
    suspend fun getAllTasks(): Result<List<AnalysisTask>>
    fun observeTasks(): Flow<List<AnalysisTask>>
    fun observeTask(taskId: String): Flow<AnalysisTask?>
}
