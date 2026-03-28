package com.dva.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dva.app.data.local.db.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getById(id: String): VideoEntity?

    @Query("SELECT * FROM videos WHERE file_path = :path")
    suspend fun getByPath(path: String): VideoEntity?

    @Query("SELECT * FROM videos ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getList(limit: Int, offset: Int): List<VideoEntity>

    @Query("SELECT * FROM videos ORDER BY created_at DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM videos")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun count(): Int
}
