package com.dva.app.data.local.db

import androidx.room.*
import com.dva.app.domain.model.ProcessingStatus
import com.dva.app.domain.model.ViolationRecord
import com.dva.app.domain.model.ViolationType
import kotlinx.coroutines.flow.Flow

/**
 * 违章记录 DAO
 */
@Dao
interface ViolationDao {
    @Query("SELECT * FROM violations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ViolationEntity>>
    
    @Query("SELECT * FROM violations WHERE createdAt >= :startOfDay AND createdAt < :endOfDay ORDER BY createdAt DESC")
    fun getByDate(startOfDay: Long, endOfDay: Long): Flow<List<ViolationEntity>>
    
    @Query("SELECT * FROM violations WHERE id = :id")
    suspend fun getById(id: Long): ViolationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(violation: ViolationEntity): Long
    
    @Delete
    suspend fun delete(violation: ViolationEntity)
    
    @Query("DELETE FROM violations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM violations")
    suspend fun deleteAll()
}

/**
 * 违章记录实体
 */
@Entity(tableName = "violations")
data class ViolationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoPath: String,
    val violationType: String,
    val plateNumber: String?,
    val plateConfidence: Float,
    val timestamp: Long,
    val frameIndex: Int,
    val beforeImagePath: String,
    val duringImagePath: String,
    val afterImagePath: String,
    val annotatedImagePath: String?,
    val confidence: Float,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ViolationRecord {
        return ViolationRecord(
            id = id,
            videoPath = videoPath,
            violationType = ViolationType.valueOf(violationType),
            plateNumber = plateNumber,
            plateConfidence = plateConfidence,
            timestamp = timestamp,
            frameIndex = frameIndex,
            beforeImagePath = beforeImagePath,
            duringImagePath = duringImagePath,
            afterImagePath = afterImagePath,
            annotatedImagePath = annotatedImagePath,
            confidence = confidence,
            createdAt = createdAt
        )
    }
    
    companion object {
        fun fromDomain(record: ViolationRecord): ViolationEntity {
            return ViolationEntity(
                id = record.id,
                videoPath = record.videoPath,
                violationType = record.violationType.name,
                plateNumber = record.plateNumber,
                plateConfidence = record.plateConfidence,
                timestamp = record.timestamp,
                frameIndex = record.frameIndex,
                beforeImagePath = record.beforeImagePath,
                duringImagePath = record.duringImagePath,
                afterImagePath = record.afterImagePath,
                annotatedImagePath = record.annotatedImagePath,
                confidence = record.confidence,
                createdAt = record.createdAt
            )
        }
    }
}

/**
 * 处理状态 DAO
 */
@Dao
interface ProcessingStateDao {
    @Query("SELECT * FROM processing WHERE videoPath = :videoPath")
    suspend fun getByVideoPath(videoPath: String): ProcessingStateEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: ProcessingStateEntity)
    
    @Query("DELETE FROM processing WHERE videoPath = :videoPath")
    suspend fun delete(videoPath: String)
}

/**
 * 处理状态实体
 */
@Entity(tableName = "processing")
data class ProcessingStateEntity(
    @PrimaryKey
    val videoPath: String,
    val status: String,
    val progress: Int,
    val currentFrame: Int,
    val totalFrames: Int,
    val violationCount: Int,
    val errorMessage: String?,
    val startedAt: Long?,
    val completedAt: Long?
)

/**
 * App 数据库
 */
@Database(
    entities = [ViolationEntity::class, ProcessingStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun violationDao(): ViolationDao
    abstract fun processingStateDao(): ProcessingStateDao
}
