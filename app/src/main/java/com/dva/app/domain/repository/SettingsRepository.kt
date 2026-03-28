package com.dva.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val detectionSensitivity: Flow<Float>
    val outputDirectory: Flow<String>
    val autoExportEnabled: Flow<Boolean>
    val modelQuality: Flow<ModelQuality>

    suspend fun setDetectionSensitivity(value: Float)
    suspend fun setOutputDirectory(path: String)
    suspend fun setAutoExportEnabled(enabled: Boolean)
    suspend fun setModelQuality(quality: ModelQuality)
}

enum class ModelQuality {
    HIGH,      // FP32
    BALANCED,  // FP16
    FAST       // INT8
}
