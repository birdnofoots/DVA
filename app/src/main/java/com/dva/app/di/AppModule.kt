package com.dva.app.di

import android.content.Context
import androidx.room.Room
import com.dva.app.data.local.db.AppDatabase
import com.dva.app.data.local.db.ProcessingStateDao
import com.dva.app.data.local.db.ViolationDao
import com.dva.app.data.local.storage.StorageManager
import com.dva.app.data.repository.ViolationRepositoryImpl
import com.dva.app.domain.repository.ViolationRepository
import com.dva.app.domain.repository.VideoRepository
import com.dva.app.domain.usecase.*
import com.dva.app.infrastructure.ml.*
import com.dva.app.infrastructure.video.VideoRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 应用模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // ==================== 数据库 ====================
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "dva_database"
        ).build()
    }
    
    @Provides
    fun provideViolationDao(database: AppDatabase): ViolationDao {
        return database.violationDao()
    }
    
    @Provides
    fun provideProcessingStateDao(database: AppDatabase): ProcessingStateDao {
        return database.processingStateDao()
    }
    
    // ==================== 存储 ====================
    
    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager {
        return StorageManager(context)
    }
    
    // ==================== 仓库 ====================
    
    @Provides
    @Singleton
    fun provideVideoRepository(@ApplicationContext context: Context): VideoRepository {
        return VideoRepositoryImpl(context)
    }
    
    @Provides
    @Singleton
    fun provideViolationRepository(violationDao: ViolationDao): ViolationRepository {
        return ViolationRepositoryImpl(violationDao)
    }
    
    // ==================== ML 模型 ====================
    
    @Provides
    @Singleton
    fun provideVehicleDetector(
        @ApplicationContext context: Context
    ): VehicleDetector {
        // 模型路径：assets/models/yolov8n-vehicle.onnx
        val modelPath = "file:///android_asset/models/yolov8n-vehicle.onnx"
        return YoloVehicleDetector(context, modelPath)
    }
    
    @Provides
    @Singleton
    fun provideLaneDetector(): LaneDetector {
        return SimpleLaneDetector()
    }
    
    @Provides
    @Singleton
    fun providePlateRecognizer(
        @ApplicationContext context: Context
    ): PlateRecognizer {
        return OCRPlateRecognizer(context)
    }
    
    @Provides
    @Singleton
    fun provideViolationAnalyzer(): ViolationAnalyzer {
        return LaneChangeViolationAnalyzer()
    }
    
    // ==================== 用例 ====================
    
    @Provides
    fun provideScanVideosUseCase(videoRepository: VideoRepository): ScanVideosUseCase {
        return ScanVideosUseCase(videoRepository)
    }
    
    @Provides
    fun provideAnalyzeVideoUseCase(
        videoRepository: VideoRepository,
        violationRepository: ViolationRepository,
        vehicleDetector: VehicleDetector,
        laneDetector: LaneDetector,
        plateRecognizer: PlateRecognizer,
        violationAnalyzer: ViolationAnalyzer
    ): AnalyzeVideoUseCase {
        return AnalyzeVideoUseCase(
            videoRepository,
            violationRepository,
            vehicleDetector,
            laneDetector,
            plateRecognizer,
            violationAnalyzer
        )
    }
    
    @Provides
    fun provideGetViolationsUseCase(violationRepository: ViolationRepository): GetViolationsUseCase {
        return GetViolationsUseCase(violationRepository)
    }
    
    @Provides
    fun provideGetProcessingProgressUseCase(videoRepository: VideoRepository): GetProcessingProgressUseCase {
        return GetProcessingProgressUseCase(videoRepository)
    }
    
    @Provides
    fun provideGetVideoInfoUseCase(videoRepository: VideoRepository): GetVideoInfoUseCase {
        return GetVideoInfoUseCase(videoRepository)
    }
}
