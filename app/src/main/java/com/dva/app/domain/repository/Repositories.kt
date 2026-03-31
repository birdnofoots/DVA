package com.dva.app.domain.repository

import android.net.Uri
import com.dva.app.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 视频仓库接口
 */
interface VideoRepository {
    /**
     * 扫描目录获取所有视频文件（使用文件路径）
     */
    suspend fun scanDirectory(directoryPath: String): List<VideoFile>
    
    /**
     * 扫描 URI 获取所有视频文件（使用 SAF）
     */
    suspend fun scanUri(uri: Uri): List<VideoFile>
    
    /**
     * 获取视频信息
     */
    suspend fun getVideoInfo(videoPath: String): VideoFile?
    
    /**
     * 提取指定帧
     */
    suspend fun extractFrame(videoPath: String, frameIndex: Int): ByteArray?
    
    /**
     * 提取帧范围
     */
    suspend fun extractFrameRange(
        videoPath: String, 
        startFrame: Int, 
        endFrame: Int, 
        interval: Int = 1
    ): List<Pair<Int, ByteArray>>
    
    /**
     * 保存截图
     */
    suspend fun saveFrame(frameData: ByteArray, outputPath: String): String
    
    /**
     * 监听处理进度
     */
    fun observeProcessingState(videoPath: String): Flow<VideoProcessingState>
    
    /**
     * 更新处理状态
     */
    suspend fun updateProcessingState(state: VideoProcessingState)
}

/**
 * 违章记录仓库接口
 */
interface ViolationRepository {
    /**
     * 获取所有违章记录
     */
    fun getAllViolations(): Flow<List<ViolationRecord>>
    
    /**
     * 按日期获取违章记录
     */
    fun getViolationsByDate(date: Long): Flow<List<ViolationRecord>>
    
    /**
     * 获取单个违章记录
     */
    suspend fun getViolationById(id: Long): ViolationRecord?
    
    /**
     * 保存违章记录
     */
    suspend fun insertViolation(violation: ViolationRecord): Long
    
    /**
     * 删除违章记录
     */
    suspend fun deleteViolation(id: Long)
    
    /**
     * 清空所有记录
     */
    suspend fun deleteAll()
}
