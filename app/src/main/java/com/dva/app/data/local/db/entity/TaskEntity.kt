package com.dva.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "analysis_tasks",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["video_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("video_id")]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "video_id")
    val videoId: String,
    
    @ColumnInfo(name = "video_path")
    val videoPath: String,
    
    @ColumnInfo(name = "status")
    val status: String,
    
    @ColumnInfo(name = "progress")
    val progress: Float,
    
    @ColumnInfo(name = "current_frame")
    val currentFrame: Long,
    
    @ColumnInfo(name = "total_frames")
    val totalFrames: Long,
    
    @ColumnInfo(name = "violations_found")
    val violationsFound: Int,
    
    @ColumnInfo(name = "started_at")
    val startedAt: Long?,
    
    @ColumnInfo(name = "completed_at")
    val completedAt: Long?,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
    
    @ColumnInfo(name = "last_resume_position")
    val lastResumePosition: Long?
)
