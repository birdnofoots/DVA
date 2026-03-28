package com.dva.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dva.app.data.local.db.entity.ViolationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ViolationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(violation: ViolationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(violations: List<ViolationEntity>)

    @Query("SELECT * FROM violations WHERE task_id = :taskId ORDER BY timestamp_ms ASC")
    suspend fun getByTaskId(taskId: String): List<ViolationEntity>

    @Query("SELECT * FROM violations WHERE id = :id")
    suspend fun getById(id: String): ViolationEntity?

    @Query("UPDATE violations SET is_confirmed = :isConfirmed WHERE id = :id")
    suspend fun updateConfirmation(id: String, isConfirmed: Boolean)

    @Query("DELETE FROM violations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM violations WHERE task_id = :taskId")
    suspend fun deleteByTaskId(taskId: String)

    @Query("SELECT * FROM violations WHERE task_id = :taskId")
    fun observeByTaskId(taskId: String): Flow<List<ViolationEntity>>

    @Query("SELECT COUNT(*) FROM violations WHERE task_id = :taskId")
    suspend fun countByTaskId(taskId: String): Int
}
