package com.dva.app.di

import android.content.Context
import androidx.room.Room
import com.dva.app.data.local.db.DvaDatabase
import com.dva.app.data.local.db.dao.ScreenshotDao
import com.dva.app.data.local.db.dao.TaskDao
import com.dva.app.data.local.db.dao.VideoDao
import com.dva.app.data.local.db.dao.ViolationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): DvaDatabase {
        return Room.databaseBuilder(
            context,
            DvaDatabase::class.java,
            DvaDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideVideoDao(database: DvaDatabase): VideoDao {
        return database.videoDao()
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: DvaDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideViolationDao(database: DvaDatabase): ViolationDao {
        return database.violationDao()
    }

    @Provides
    @Singleton
    fun provideScreenshotDao(database: DvaDatabase): ScreenshotDao {
        return database.screenshotDao()
    }
}
