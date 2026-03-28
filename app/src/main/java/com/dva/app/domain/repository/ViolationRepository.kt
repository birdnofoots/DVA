package com.dva.app.domain.repository

import com.dva.app.domain.model.Violation
import kotlinx.coroutines.flow.Flow

interface ViolationRepository {
    suspend fun saveViolation(violation: Violation): Result<String>
    suspend fun saveViolations(violations: List<Violation>): Result<Unit>
    suspend fun getViolationsByTaskId(taskId: String): Result<List<Violation>>
    suspend fun getViolationById(id: String): Result<Violation?>
    suspend fun updateConfirmation(id: String, isConfirmed: Boolean): Result<Unit>
    suspend fun deleteViolation(id: String): Result<Unit>
    suspend fun deleteViolationsByTaskId(taskId: String): Result<Unit>
    fun observeViolations(taskId: String): Flow<List<Violation>>
}
