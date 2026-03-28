package com.dva.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screenshots",
    foreignKeys = [
        ForeignKey(
            entity = ViolationEntity::class,
            parentColumns = ["id"],
            childColumns = ["violation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("violation_id")]
)
data class ScreenshotEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "violation_id")
    val violationId: String,
    
    @ColumnInfo(name = "type")
    val type: String,
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    
    @ColumnInfo(name = "width")
    val width: Int,
    
    @ColumnInfo(name = "height")
    val height: Int,
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long
)
