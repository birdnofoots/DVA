package com.dva.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dva.app.domain.repository.ModelQuality
import com.dva.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    companion object {
        private val KEY_SENSITIVITY = floatPreferencesKey("detection_sensitivity")
        private val KEY_OUTPUT_DIR = stringPreferencesKey("output_directory")
        private val KEY_AUTO_EXPORT = booleanPreferencesKey("auto_export")
        private val KEY_MODEL_QUALITY = stringPreferencesKey("model_quality")
        
        private const val DEFAULT_SENSITIVITY = 0.5f
        private const val DEFAULT_OUTPUT_DIR = "/storage/emulated/0/DVA/output"
    }

    override val detectionSensitivity: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SENSITIVITY] ?: DEFAULT_SENSITIVITY
        }

    override val outputDirectory: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_OUTPUT_DIR] ?: DEFAULT_OUTPUT_DIR
        }

    override val autoExportEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_AUTO_EXPORT] ?: false
        }

    override val modelQuality: Flow<ModelQuality> = context.dataStore.data
        .map { preferences ->
            val value = preferences[KEY_MODEL_QUALITY] ?: ModelQuality.BALANCED.name
            try {
                ModelQuality.valueOf(value)
            } catch (e: Exception) {
                ModelQuality.BALANCED
            }
        }

    override suspend fun setDetectionSensitivity(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SENSITIVITY] = value.coerceIn(0f, 1f)
        }
    }

    override suspend fun setOutputDirectory(path: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_OUTPUT_DIR] = path
        }
    }

    override suspend fun setAutoExportEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_EXPORT] = enabled
        }
    }

    override suspend fun setModelQuality(quality: ModelQuality) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MODEL_QUALITY] = quality.name
        }
    }
}
