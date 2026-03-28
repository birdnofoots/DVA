package com.dva.app.domain.usecase.analysis

import com.dva.app.domain.model.TaskStatus
import com.dva.app.domain.repository.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 暂停分析用例
 */
@Singleton
class PauseAnalysisUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: String): Result<Unit> {
        return taskRepository.updateStatus(taskId, TaskStatus.PAUSED)
    }
}
