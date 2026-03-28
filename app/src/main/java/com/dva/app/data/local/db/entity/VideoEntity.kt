package com.dva.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    @ColumnInfo(name = "file_name")
    val fileName: String,
    
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    
    @ColumnInfo(name = "width")
    val width: Int,
    
    @ColumnInfo(name = "height")
    val height: Int,
    
    @ColumnInfo(name = "frame_rate")
    val frameRate: Float,
    
    @ColumnInfo(name = "total_frames")
    val totalFrames: Long,
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "last_modified")
    val lastModified: Long,
    
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?
)
