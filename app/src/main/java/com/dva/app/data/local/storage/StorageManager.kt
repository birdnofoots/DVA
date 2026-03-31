package com.dva.app.data.local.storage

import android.content.Context
import android.os.Environment
import com.dva.app.domain.model.ViolationRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件存储管理器
 * 负责截图和报告的存储
 */
class StorageManager(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
    
    /**
     * 获取截图存储根目录
     */
    fun getScreenshotsRoot(): File {
        val root = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            File(context.getExternalFilesDir(null), "violations")
        } else {
            File(context.filesDir, "violations")
        }
        root.mkdirs()
        return root
    }
    
    /**
     * 为单个违章创建目录
     */
    fun createViolationDirectory(violation: ViolationRecord): File {
        val dateStr = dateFormat.format(Date(violation.createdAt))
        val timeStr = timeFormat.format(Date(violation.timestamp))
        val dirName = "violation_${violation.id}_${dateStr}_$timeStr"
        
        val dir = File(getScreenshotsRoot(), "$dateStr/$dirName")
        dir.mkdirs()
        return dir
    }
    
    /**
     * 保存截图
     */
    fun saveScreenshot(
        violation: ViolationRecord,
        type: ScreenshotType,
        imageData: ByteArray
    ): String {
        val dir = createViolationDirectory(violation)
        val fileName = "${type.name.lowercase()}.jpg"
        val file = File(dir, fileName)
        
        file.writeBytes(imageData)
        return file.absolutePath
    }
    
    /**
     * 保存标注后的截图
     */
    fun saveAnnotatedScreenshot(
        violation: ViolationRecord,
        imageData: ByteArray
    ): String {
        val dir = createViolationDirectory(violation)
        val annotatedDir = File(dir, "annotated")
        annotatedDir.mkdirs()
        
        val file = File(annotatedDir, "annotated_${System.currentTimeMillis()}.jpg")
        file.writeBytes(imageData)
        return file.absolutePath
    }
    
    /**
     * 获取报告目录
     */
    fun getReportsDirectory(): File {
        val dir = File(getScreenshotsRoot(), "reports")
        dir.mkdirs()
        return dir
    }
    
    /**
     * 保存报告
     */
    fun saveReport(violation: ViolationRecord, reportContent: String): String {
        val dir = createViolationDirectory(violation)
        val file = File(dir, "report.txt")
        file.writeText(reportContent)
        return file.absolutePath
    }
    
    /**
     * 删除违章相关文件
     */
    fun deleteViolationFiles(violation: ViolationRecord) {
        val dir = File(getScreenshotsRoot(), 
            "${dateFormat.format(Date(violation.createdAt))}/violation_${violation.id}_*")
        dir.deleteRecursively()
    }
    
    /**
     * 获取存储使用情况
     */
    fun getStorageUsage(): StorageUsage {
        val root = getScreenshotsRoot()
        var totalSize = 0L
        var fileCount = 0
        
        root.walkTopDown().forEach {
            if (it.isFile) {
                totalSize += it.length()
                fileCount++
            }
        }
        
        return StorageUsage(totalSize, fileCount)
    }
    
    data class StorageUsage(
        val totalBytes: Long,
        val fileCount: Int
    ) {
        val totalMB: Double get() = totalBytes / (1024.0 * 1024.0)
        val totalGB: Double get() = totalBytes / (1024.0 * 1024.0 * 1024.0)
    }
    
    enum class ScreenshotType {
        BEFORE,   // 违章前
        DURING,   // 违章中
        AFTER     // 违章后
    }
}
