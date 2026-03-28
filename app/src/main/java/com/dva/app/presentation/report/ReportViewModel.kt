package com.dva.app.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dva.app.domain.model.Violation
import com.dva.app.domain.repository.ViolationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportUiState(
    val isLoading: Boolean = false,
    val violations: List<Violation> = emptyList(),
    val selectedViolation: Violation? = null,
    val isExporting: Boolean = false,
    val exportPath: String? = null,
    val error: String? = null
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val violationRepository: ViolationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private var currentTaskId: String? = null

    fun loadReport(taskId: String) {
        currentTaskId = taskId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = violationRepository.getViolationsByTaskId(taskId)
            
            result.fold(
                onSuccess = { violations ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            violations = violations
                        ) 
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "加载失败"
                        ) 
                    }
                }
            )
        }
    }

    fun loadViolation(violationId: String) {
        viewModelScope.launch {
            val result = violationRepository.getViolationById(violationId)
            result.fold(
                onSuccess = { violation ->
                    _uiState.update { it.copy(selectedViolation = violation) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
            )
        }
    }

    fun exportReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            // TODO: Implement export logic
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun updateConfirmation(violationId: String, isConfirmed: Boolean) {
        viewModelScope.launch {
            violationRepository.updateConfirmation(violationId, isConfirmed)
            // Refresh the list
            currentTaskId?.let { loadReport(it) }
        }
    }
}
