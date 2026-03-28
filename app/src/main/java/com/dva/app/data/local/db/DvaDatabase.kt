package com.dva.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dva.app.data.local.db.dao.ScreenshotDao
import com.dva.app.data.local.db.dao.TaskDao
import com.dva.app.data.local.db.dao.VideoDao
import com.dva.app.data.local.db.dao.ViolationDao
import com.dva.app.data.local.db.entity.ScreenshotEntity
import com.dva.app.data.local.db.entity.TaskEntity
import com.dva.app.data.local.db.entity.VideoEntity
import com.dva.app.data.local.db.entity.ViolationEntity

@Database(
    entities = [
        VideoEntity::class,
        TaskEntity::class,
        ViolationEntity::class,
        ScreenshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DvaDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun taskDao(): TaskDao
    abstract fun violationDao(): ViolationDao
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        const val DATABASE_NAME = "dva_database"
    }
}
