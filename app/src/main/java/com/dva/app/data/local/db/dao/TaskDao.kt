package com.dva.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dva.app.data.local.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM analysis_tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM analysis_tasks WHERE video_path = :videoPath ORDER BY started_at DESC LIMIT 1")
    suspend fun getLatestByVideo(videoPath: String): TaskEntity?

    @Query("SELECT * FROM analysis_tasks ORDER BY started_at DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM analysis_tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("UPDATE analysis_tasks SET status = :status, current_frame = :frame, progress = :progress, violations_found = :violations WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, frame: Long, progress: Float, violations: Int)

    @Query("UPDATE analysis_tasks SET status = :status, completed_at = :completedAt, progress = 1.0 WHERE id = :id")
    suspend fun complete(id: String, status: String, completedAt: Long)

    @Query("UPDATE analysis_tasks SET status = :status, error_message = :error WHERE id = :id")
    suspend fun fail(id: String, status: String, error: String)

    @Query("DELETE FROM analysis_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
