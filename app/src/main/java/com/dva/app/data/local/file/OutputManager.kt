package com.dva.app.data.local.file

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val APP_FOLDER = "DVA"
        private const val OUTPUT_FOLDER = "output"
        private const val CACHE_FOLDER = "cache"
        private const val THUMBNAILS_FOLDER = "thumbnails"
        private const val VIOLATIONS_FOLDER = "violations"
    }

    private val baseOutputDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$APP_FOLDER/$OUTPUT_FOLDER"
        )

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_FOLDER)

    fun getOutputDir(videoFileName: String): File {
        val sanitizedName = videoFileName.substringBeforeLast(".")
        return File(baseOutputDir, sanitizedName).also { it.mkdirs() }
    }

    fun getViolationsDir(videoFileName: String): File {
        return File(getOutputDir(videoFileName), VIOLATIONS_FOLDER).also { it.mkdirs() }
    }

    fun getViolationScreenshotsDir(videoFileName: String, violationId: String): File {
        return File(getViolationsDir(videoFileName), violationId).also { it.mkdirs() }
    }

    fun getThumbnailDir(videoFileName: String): File {
        return File(getOutputDir(videoFileName), THUMBNAILS_FOLDER).also { it.mkdirs() }
    }

    suspend fun saveScreenshot(
        bitmap: Bitmap,
        outputPath: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { out ->
                bitmap.compress(format, quality, out)
            }
            
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildScreenshotPath(
        videoFileName: String,
        violationId: String,
        plateNumber: String?,
        screenshotType: String,
        timestamp: Long
    ): String {
        val dir = getViolationScreenshotsDir(videoFileName, violationId)
        val prefix = plateNumber?.replace(" ", "_") ?: "unknown"
        return File(dir, "${prefix}_${screenshotType}_$timestamp.png").absolutePath
    }

    fun buildReportPath(videoFileName: String): String {
        val dir = getOutputDir(videoFileName)
        return File(dir, "report.json").absolutePath
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        calculateDirSize(cacheDir)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
