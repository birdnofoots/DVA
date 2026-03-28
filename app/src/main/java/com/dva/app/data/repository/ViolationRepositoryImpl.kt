package com.dva.app.data.repository

import android.graphics.RectF
import com.dva.app.data.local.db.dao.ScreenshotDao
import com.dva.app.data.local.db.dao.ViolationDao
import com.dva.app.data.local.db.entity.ScreenshotEntity
import com.dva.app.data.local.db.entity.ViolationEntity
import com.dva.app.domain.model.LicensePlate
import com.dva.app.domain.model.PlateColor
import com.dva.app.domain.model.PlateType
import com.dva.app.domain.model.Screenshot
import com.dva.app.domain.model.Violation
import com.dva.app.domain.model.ViolationType
import com.dva.app.domain.model.Vehicle
import com.dva.app.domain.model.VehicleCategory
import com.dva.app.domain.model.VehicleColor
import com.dva.app.domain.repository.ViolationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViolationRepositoryImpl @Inject constructor(
    private val violationDao: ViolationDao,
    private val screenshotDao: ScreenshotDao
) : ViolationRepository {

    override suspend fun saveViolation(violation: Violation): Result<String> {
        return try {
            violationDao.insert(violation.toEntity())
            if (violation.screenshots.isNotEmpty()) {
                screenshotDao.insertAll(violation.screenshots.map { it.toEntity() })
            }
            Result.success(violation.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveViolations(violations: List<Violation>): Result<Unit> {
        return try {
            violations.forEach { violation ->
                violationDao.insert(violation.toEntity())
                if (violation.screenshots.isNotEmpty()) {
                    screenshotDao.insertAll(violation.screenshots.map { it.toEntity() })
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getViolationsByTaskId(taskId: String): Result<List<Violation>> {
        return try {
            val entities = violationDao.getByTaskId(taskId)
            val violations = entities.map { entity ->
                val screenshots = screenshotDao.getByViolationId(entity.id)
                entity.toDomain(screenshots.map { it.toDomain() })
            }
            Result.success(violations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getViolationById(id: String): Result<Violation?> {
        return try {
            val entity = violationDao.getById(id)
            if (entity != null) {
                val screenshots = screenshotDao.getByViolationId(id)
                Result.success(entity.toDomain(screenshots.map { it.toDomain() }))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateConfirmation(id: String, isConfirmed: Boolean): Result<Unit> {
        return try {
            violationDao.updateConfirmation(id, isConfirmed)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteViolation(id: String): Result<Unit> {
        return try {
            screenshotDao.deleteByViolationId(id)
            violationDao.delete(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteViolationsByTaskId(taskId: String): Result<Unit> {
        return try {
            violationDao.deleteByTaskId(taskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeViolations(taskId: String): Flow<List<Violation>> {
        return violationDao.observeByTaskId(taskId).map { entities ->
            entities.map { it.toDomain(emptyList()) }
        }
    }

    private fun Violation.toEntity() = ViolationEntity(
        id = id,
        taskId = taskId,
        violationType = type.code,
        timestampMs = timestampMs,
        vehicleId = vehicleId,
        licensePlate = licensePlate?.number,
        plateConfidence = licensePlate?.confidence,
        plateProvince = licensePlate?.province,
        plateLetter = licensePlate?.letter,
        plateDigits = licensePlate?.digits,
        plateType = licensePlate?.plateType?.name,
        plateColor = licensePlate?.color?.name,
        plateBboxX = licensePlate?.boundingBox?.left,
        plateBboxY = licensePlate?.boundingBox?.top,
        plateBboxW = licensePlate?.boundingBox?.width(),
        plateBboxH = licensePlate?.boundingBox?.height(),
        vehicleColor = vehicleSnapshot?.color?.name,
        vehicleType = vehicleSnapshot?.category?.name,
        confidence = confidence,
        isConfirmed = isConfirmed,
        createdAt = createdAt
    )

    private fun ViolationEntity.toDomain(screenshots: List<Screenshot>) = Violation(
        id = id,
        taskId = taskId,
        type = ViolationType.fromCode(violationType),
        timestampMs = timestampMs,
        vehicleId = vehicleId,
        licensePlate = if (licensePlate != null) {
            LicensePlate(
                number = licensePlate,
                province = plateProvince ?: "",
                letter = plateLetter ?: "",
                digits = plateDigits ?: "",
                plateType = plateType?.let { PlateType.valueOf(it) } ?: PlateType.UNKNOWN,
                confidence = plateConfidence ?: 0f,
                boundingBox = if (plateBboxX != null && plateBboxW != null) {
                    RectF(plateBboxX, plateBboxY ?: 0f, plateBboxX + plateBboxW, (plateBboxY ?: 0f) + (plateBboxH ?: 0f))
                } else null,
                color = plateColor?.let { PlateColor.valueOf(it) } ?: PlateColor.UNKNOWN
            )
        } else null,
        vehicleSnapshot = if (vehicleColor != null) {
            Vehicle(
                id = vehicleId,
                boundingBox = RectF(0f, 0f, 0f, 0f),
                category = vehicleType?.let { VehicleCategory.valueOf(it) } ?: VehicleCategory.UNKNOWN,
                color = VehicleColor.valueOf(vehicleColor),
                confidence = confidence,
                timestampMs = timestampMs,
                centerX = 0f,
                centerY = 0f
            )
        } else null,
        screenshots = screenshots,
        confidence = confidence,
        isConfirmed = isConfirmed,
        createdAt = createdAt
    )

    private fun Screenshot.toEntity() = ScreenshotEntity(
        id = id,
        violationId = violationId,
        type = type.name,
        filePath = filePath,
        timestampMs = timestampMs,
        width = width,
        height = height,
        fileSize = fileSize
    )

    private fun ScreenshotEntity.toDomain() = Screenshot(
        id = id,
        violationId = violationId,
        type = com.dva.app.domain.model.ScreenshotType.values()
            .find { it.name == type } ?: com.dva.app.domain.model.ScreenshotType.MOMENT,
        filePath = filePath,
        timestampMs = timestampMs,
        width = width,
        height = height,
        fileSize = fileSize
    )
}
