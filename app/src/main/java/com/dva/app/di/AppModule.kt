package com.dva.app.di

import android.content.Context
import com.dva.app.data.local.file.CacheManager
import com.dva.app.data.local.file.FileStorageManager
import com.dva.app.data.local.file.OutputManager
import com.dva.app.domain.repository.SettingsRepository
import com.dva.app.domain.repository.TaskRepository
import com.dva.app.domain.repository.VideoRepository
import com.dva.app.domain.repository.ViolationRepository
import com.dva.app.data.repository.SettingsRepositoryImpl
import com.dva.app.data.repository.TaskRepositoryImpl
import com.dva.app.data.repository.VideoRepositoryImpl
import com.dva.app.data.repository.ViolationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(
        impl: VideoRepositoryImpl
    ): VideoRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindViolationRepository(
        impl: ViolationRepositoryImpl
    ): ViolationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideFileStorageManager(
        @ApplicationContext context: Context
    ): FileStorageManager {
        return FileStorageManager(context)
    }

    @Provides
    @Singleton
    fun provideOutputManager(
        @ApplicationContext context: Context
    ): OutputManager {
        return OutputManager(context)
    }

    @Provides
    @Singleton
    fun provideCacheManager(
        @ApplicationContext context: Context
    ): CacheManager {
        return CacheManager(context)
    }
}
