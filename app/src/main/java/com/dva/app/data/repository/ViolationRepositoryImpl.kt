package com.dva.app.data.repository

import com.dva.app.data.local.db.ViolationDao
import com.dva.app.data.local.db.ViolationEntity
import com.dva.app.domain.model.ViolationRecord
import com.dva.app.domain.repository.ViolationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * 违章记录仓库实现
 */
class ViolationRepositoryImpl(
    private val violationDao: ViolationDao
) : ViolationRepository {
    
    override fun getAllViolations(): Flow<List<ViolationRecord>> {
        return violationDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getViolationsByDate(date: Long): Flow<List<ViolationRecord>> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis
        
        return violationDao.getByDate(startOfDay, endOfDay).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getViolationById(id: Long): ViolationRecord? {
        return violationDao.getById(id)?.toDomain()
    }
    
    override suspend fun insertViolation(violation: ViolationRecord): Long {
        return violationDao.insert(ViolationEntity.fromDomain(violation))
    }
    
    override suspend fun deleteViolation(id: Long) {
        violationDao.deleteById(id)
    }
    
    override suspend fun deleteAll() {
        violationDao.deleteAll()
    }
}
