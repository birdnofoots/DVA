package com.dva.app.presentation.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.AnalysisProgress
import com.dva.app.domain.model.AnalysisTask
import com.dva.app.domain.model.TaskStatus
import com.dva.app.domain.model.Violation
import com.dva.app.domain.repository.TaskRepository
import com.dva.app.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalysisUiState(
    val task: AnalysisTask? = null,
    val videoName: String = "",
    val progress: AnalysisProgress? = null,
    val recentViolations: List<Violation> = emptyList(),
    val isPaused: Boolean = false,
    val error: String? = null,
    val totalTimeFormatted: String? = null
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null
    private var currentVideoId: String? = null

    fun loadVideo(videoId: String) {
        currentVideoId = videoId
        viewModelScope.launch {
            val result = videoRepository.getVideoById(videoId)
            result.fold(
                onSuccess = { video ->
                    if (video != null) {
                        _uiState.update { 
                            it.copy(
                                videoName = video.fileName,
                                totalTimeFormatted = video.formattedDuration
                            )
                        }
                        // Check for existing task
                        val existingTask = taskRepository.getLatestTaskByVideo(video.filePath).getOrNull()
                        if (existingTask != null && existingTask.status == TaskStatus.PAUSED) {
                            _uiState.update { it.copy(task = existingTask, isPaused = true) }
                            resumeAnalysis()
                        } else {
                            startNewAnalysis(video.filePath)
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
            )
        }
    }

    private fun startNewAnalysis(videoPath: String) {
        viewModelScope.launch {
            val taskResult = taskRepository.createTask(videoPath)
            taskResult.fold(
                onSuccess = { task ->
                    _uiState.update { it.copy(task = task) }
                    runAnalysis(task)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
            )
        }
    }

    private fun runAnalysis(task: AnalysisTask) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                taskRepository.updateStatus(task.id, TaskStatus.RUNNING)
                
                var currentFrame = task.currentFrame
                val totalFrames = task.totalFrames
                var violationsFound = task.violationsFound
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = startTime

                // Simulate analysis (replace with actual analysis logic)
                while (currentFrame < totalFrames && _uiState.value.task?.status == TaskStatus.RUNNING) {
                    // Simulate processing
                    kotlinx.coroutines.delay(50)
                    
                    currentFrame += 100
                    val elapsed = System.currentTimeMillis() - startTime
                    val fps = if (elapsed > 0) (currentFrame * 1000f / elapsed) else 0f
                    
                    val progress = AnalysisProgress(
                        taskId = task.id,
                        currentFrame = currentFrame,
                        totalFrames = totalFrames,
                        progressPercent = currentFrame.toFloat() / totalFrames,
                        currentTimeMs = (currentFrame * 1000 / 30), // Assuming 30fps
                        fps = fps,
                        violationsFound = violationsFound
                    )
                    
                    _uiState.update {
                        it.copy(
                            task = it.task?.copy(
                                currentFrame = currentFrame,
                                progress = currentFrame.toFloat() / totalFrames,
                                violationsFound = violationsFound
                            ),
                            progress = progress
                        )
                    }
                    
                    // Update database every second
                    if (System.currentTimeMillis() - lastUpdateTime > 1000) {
                        taskRepository.updateProgress(task.id, currentFrame, violationsFound)
                        lastUpdateTime = System.currentTimeMillis()
                    }
                }

                if (_uiState.value.task?.status == TaskStatus.RUNNING) {
                    taskRepository.completeTask(task.id)
                    _uiState.update {
                        it.copy(
                            task = it.task?.copy(status = TaskStatus.COMPLETED),
                            progress = null
                        )
                    }
                }
            } catch (e: Exception) {
                taskRepository.failTask(task.id, e.message ?: "Unknown error")
                _uiState.update {
                    it.copy(
                        task = it.task?.copy(status = TaskStatus.FAILED),
                        error = e.message
                    )
                }
            }
        }
    }

    fun pauseAnalysis() {
        analysisJob?.cancel()
        viewModelScope.launch {
            _uiState.value.task?.let { task ->
                taskRepository.updateStatus(task.id, TaskStatus.PAUSED)
                _uiState.update {
                    it.copy(
                        task = it.task?.copy(status = TaskStatus.PAUSED),
                        isPaused = true
                    )
                }
            }
        }
    }

    fun resumeAnalysis() {
        viewModelScope.launch {
            _uiState.value.task?.let { task ->
                taskRepository.updateStatus(task.id, TaskStatus.RUNNING)
                _uiState.update {
                    it.copy(
                        task = it.task?.copy(status = TaskStatus.RUNNING),
                        isPaused = false
                    )
                }
                runAnalysis(task.copy(status = TaskStatus.RUNNING))
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        viewModelScope.launch {
            _uiState.value.task?.let { task ->
                taskRepository.updateStatus(task.id, TaskStatus.CANCELLED)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
    }
}
