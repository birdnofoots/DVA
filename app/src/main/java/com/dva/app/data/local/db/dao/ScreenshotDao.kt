package com.dva.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dva.app.data.local.db.entity.ScreenshotEntity

@Dao
interface ScreenshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(screenshot: ScreenshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(screenshots: List<ScreenshotEntity>)

    @Query("SELECT * FROM screenshots WHERE violation_id = :violationId ORDER BY timestamp_ms ASC")
    suspend fun getByViolationId(violationId: String): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getById(id: String): ScreenshotEntity?

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM screenshots WHERE violation_id = :violationId")
    suspend fun deleteByViolationId(violationId: String)
}
