package com.dva.app.domain.usecase.report

import android.content.Context
import com.dva.app.data.local.file.OutputManager
import com.dva.app.domain.model.*
import com.dva.app.domain.repository.TaskRepository
import com.dva.app.domain.repository.ViolationRepository
import com.dva.app.domain.repository.VideoRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生成分析报告用例
 */
@Singleton
class GenerateReportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val violationRepository: ViolationRepository,
    private val videoRepository: VideoRepository,
    private val outputManager: OutputManager
) {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    /**
     * 生成报告
     * @param taskId 任务ID
     */
    suspend operator fun invoke(taskId: String): Result<AnalysisReport> = withContext(Dispatchers.IO) {
        try {
            // 获取任务信息
            val taskResult = taskRepository.getTaskById(taskId)
            if (taskResult.isFailure) {
                return@withContext Result.failure(
                    taskResult.exceptionOrNull() ?: Exception("Task not found")
                )
            }
            
            val task = taskResult.getOrNull()
                ?: return@withContext Result.failure(Exception("Task not found"))
            
            // 获取视频信息
            val video = videoRepository.getVideoById(task.videoPath).getOrNull()
            
            // 获取违章列表
            val violationsResult = violationRepository.getViolationsByTaskId(taskId)
            val violations = if (violationsResult.isSuccess) {
                violationsResult.getOrNull() ?: emptyList()
            } else {
                emptyList()
            }
            
            // 生成报告
            val report = AnalysisReport(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                videoFileName = video?.fileName ?: File(task.videoPath).name,
                videoPath = task.videoPath,
                analyzeTime = System.currentTimeMillis(),
                videoDuration = video?.durationMs ?: 0L,
                totalFrames = video?.totalFrames ?: 0L,
                violations = violations.map { violation ->
                    ViolationSummary(
                        id = violation.id,
                        type = violation.type,
                        timestamp = violation.formattedTimestamp,
                        licensePlate = violation.licensePlate?.number,
                        thumbnailPath = violation.screenshots
                            .find { it.type == ScreenshotType.MOMENT }
                            ?.filePath ?: ""
                    )
                },
                summary = ReportSummary(
                    totalViolations = violations.size,
                    violationsByType = violations.groupBy { it.type }
                        .mapValues { it.value.size },
                    platesIdentified = violations.count { it.licensePlate != null },
                    platesFailed = violations.count { it.licensePlate == null },
                    processingTimeMs = (task.completedAt ?: 0L) - (task.startedAt ?: 0L)
                )
            )
            
            // 保存报告到文件
            val reportPath = outputManager.buildReportPath(report.videoFileName)
            saveReportToFile(report, reportPath)
            
            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 保存报告到JSON文件
     */
    private suspend fun saveReportToFile(report: AnalysisReport, path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(report))
        } catch (e: Exception) {
            // 忽略保存错误
        }
    }

    /**
     * 加载报告
     */
    suspend fun loadReport(reportPath: String): Result<AnalysisReport> = withContext(Dispatchers.IO) {
        try {
            val file = File(reportPath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Report file not found"))
            }
            
            val json = file.readText()
            val report = gson.fromJson(json, AnalysisReport::class.java)
            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 导出报告
     */
    suspend fun exportReport(report: AnalysisReport, exportDir: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "DVA_Report_${report.videoFileName}_${System.currentTimeMillis()}.json"
            val exportPath = File(exportDir, fileName).absolutePath
            saveReportToFile(report, exportPath)
            Result.success(exportPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
