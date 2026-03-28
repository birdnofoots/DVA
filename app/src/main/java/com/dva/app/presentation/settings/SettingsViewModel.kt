package com.dva.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.repository.ModelQuality
import com.dva.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val sensitivity: Float = 0.5f,
    val modelQuality: ModelQuality = ModelQuality.BALANCED,
    val autoExport: Boolean = false,
    val outputDirectory: String = "/storage/emulated/0/DVA/output"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.detectionSensitivity.collect { sensitivity ->
                _uiState.update { it.copy(sensitivity = sensitivity) }
            }
        }
        viewModelScope.launch {
            settingsRepository.modelQuality.collect { quality ->
                _uiState.update { it.copy(modelQuality = quality) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoExportEnabled.collect { enabled ->
                _uiState.update { it.copy(autoExport = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.outputDirectory.collect { dir ->
                _uiState.update { it.copy(outputDirectory = dir) }
            }
        }
    }

    fun setSensitivity(value: Float) {
        viewModelScope.launch {
            settingsRepository.setDetectionSensitivity(value)
        }
    }

    fun setModelQuality(quality: ModelQuality) {
        viewModelScope.launch {
            settingsRepository.setModelQuality(quality)
        }
    }

    fun setAutoExport(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoExportEnabled(enabled)
        }
    }

    fun setOutputDirectory(path: String) {
        viewModelScope.launch {
            settingsRepository.setOutputDirectory(path)
        }
    }
}
