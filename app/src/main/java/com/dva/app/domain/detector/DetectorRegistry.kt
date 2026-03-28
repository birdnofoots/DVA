package com.dva.app.domain.detector

import com.dva.app.domain.detector.impl.LaneChangeDetector
import com.dva.app.domain.model.ViolationType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 检测器注册表
 * 管理所有违章检测器，支持插件化扩展
 */
@Singleton
class DetectorRegistry @Inject constructor(
    private val laneChangeDetector: LaneChangeDetector
) {
    private val detectors = mutableMapOf<ViolationType, ViolationDetector>()
    private val enabledTypes = mutableSetOf<ViolationType>()

    init {
        // 注册默认检测器
        register(laneChangeDetector)
        // 默认启用变道检测
        enable(ViolationType.LANE_CHANGE_WITHOUT_SIGNAL)
    }

    /**
     * 注册检测器
     */
    fun register(detector: ViolationDetector) {
        detectors[detector.violationType] = detector
    }

    /**
     * 注销检测器
     */
    fun unregister(type: ViolationType) {
        detectors.remove(type)
        enabledTypes.remove(type)
    }

    /**
     * 获取检测器
     */
    fun getDetector(type: ViolationType): ViolationDetector? {
        return detectors[type]
    }

    /**
     * 启用检测器
     */
    fun enable(type: ViolationType) {
        enabledTypes.add(type)
        detectors[type]?.isEnabled = true
    }

    /**
     * 禁用检测器
     */
    fun disable(type: ViolationType) {
        enabledTypes.remove(type)
        detectors[type]?.isEnabled = false
    }

    /**
     * 检查是否启用
     */
    fun isEnabled(type: ViolationType): Boolean {
        return enabledTypes.contains(type)
    }

    /**
     * 获取所有启用的检测器
     */
    fun getAllEnabled(): List<ViolationDetector> {
        return detectors.filter { enabledTypes.contains(it.key) }
            .values
            .sortedBy { it.priority }
    }

    /**
     * 获取所有检测器
     */
    fun getAllDetectors(): List<ViolationDetector> {
        return detectors.values.sortedBy { it.priority }
    }

    /**
     * 执行所有启用的检测器
     */
    suspend fun detectAll(context: DetectionContext): List<ViolationEvent> {
        val events = mutableListOf<ViolationEvent>()
        
        for (detector in getAllEnabled()) {
            val event = detector.detect(context)
            if (event != null) {
                events.add(event)
            }
        }
        
        return events
    }

    /**
     * 重置所有检测器
     */
    fun resetAll() {
        detectors.values.forEach { it.reset() }
    }

    /**
     * 获取启用的违章类型列表
     */
    fun getEnabledTypes(): List<ViolationType> {
        return enabledTypes.toList()
    }
}
