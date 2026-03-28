package com.dva.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "violations",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("task_id")]
)
data class ViolationEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "task_id")
    val taskId: String,
    
    @ColumnInfo(name = "violation_type")
    val violationType: String,
    
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    
    @ColumnInfo(name = "vehicle_id")
    val vehicleId: String,
    
    @ColumnInfo(name = "license_plate")
    val licensePlate: String?,
    
    @ColumnInfo(name = "plate_confidence")
    val plateConfidence: Float?,
    
    @ColumnInfo(name = "plate_province")
    val plateProvince: String?,
    
    @ColumnInfo(name = "plate_letter")
    val plateLetter: String?,
    
    @ColumnInfo(name = "plate_digits")
    val plateDigits: String?,
    
    @ColumnInfo(name = "plate_type")
    val plateType: String?,
    
    @ColumnInfo(name = "plate_color")
    val plateColor: String?,
    
    @ColumnInfo(name = "plate_bbox_x")
    val plateBboxX: Float?,
    
    @ColumnInfo(name = "plate_bbox_y")
    val plateBboxY: Float?,
    
    @ColumnInfo(name = "plate_bbox_w")
    val plateBboxW: Float?,
    
    @ColumnInfo(name = "plate_bbox_h")
    val plateBboxH: Float?,
    
    @ColumnInfo(name = "vehicle_color")
    val vehicleColor: String?,
    
    @ColumnInfo(name = "vehicle_type")
    val vehicleType: String?,
    
    @ColumnInfo(name = "confidence")
    val confidence: Float,
    
    @ColumnInfo(name = "is_confirmed")
    val isConfirmed: Boolean,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
