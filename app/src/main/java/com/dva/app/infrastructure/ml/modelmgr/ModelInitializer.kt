package com.dva.app.infrastructure.ml.modelmgr

import android.content.Context
import android.util.Log
import com.dva.app.infrastructure.ml.LaneDetector
import com.dva.app.infrastructure.ml.LprRecognizer
import com.dva.app.infrastructure.ml.VehicleDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型初始化器
 * 应用启动时自动检查并加载模型
 */
@Singleton
class ModelInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLoader: ModelLoader,
    private val vehicleDetector: VehicleDetector,
    private val laneDetector: LaneDetector,
    private val lprRecognizer: LprRecognizer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val logger = Logger("ModelInitializer")

    /**
     * 模型初始化状态
     */
    data class InitState(
        val vehicleDetectorReady: Boolean = false,
        val laneDetectorReady: Boolean = false,
        val lprRecognizerReady: Boolean = false
    ) {
        val isReady: Boolean
            get() = vehicleDetectorReady && laneDetectorReady && lprRecognizerReady

        val progress: Float
            get() {
                var loaded = 0
                if (vehicleDetectorReady) loaded++
                if (laneDetectorReady) loaded++
                if (lprRecognizerReady) loaded++
                return loaded / 3f
            }
    }

    /**
     * 初始化所有模型
     */
    fun initializeAll(onProgress: ((InitState) -> Unit)? = null) {
        scope.launch {
            logger.d("Starting model initialization...")
            
            val state = InitState()
            
            // 初始化车辆检测模型
            launch {
                try {
                    val path = modelLoader.loadModel("vehicle_detector")
                    if (path.isSuccess) {
                        vehicleDetector.initialize(path.getOrThrow())
                        vehicleDetector.warmUp()
                        logger.d("Vehicle detector ready")
                        onProgress?.invoke(state.copy(vehicleDetectorReady = true))
                    }
                } catch (e: Exception) {
                    logger.e("Failed to load vehicle detector: ${e.message}")
                }
            }
            
            // 初始化车道线检测模型
            launch {
                try {
                    val path = modelLoader.loadModel("lane_detector")
                    if (path.isSuccess) {
                        laneDetector.initialize(path.getOrThrow())
                        laneDetector.warmUp()
                        logger.d("Lane detector ready")
                        onProgress?.invoke(state.copy(laneDetectorReady = true))
                    }
                } catch (e: Exception) {
                    logger.e("Failed to load lane detector: ${e.message}")
                }
            }
            
            // 初始化车牌识别模型
            launch {
                try {
                    val path = modelLoader.loadModel("lpr_recognizer")
                    if (path.isSuccess) {
                        lprRecognizer.initialize(path.getOrThrow())
                        lprRecognizer.warmUp()
                        logger.d("LPR recognizer ready")
                        onProgress?.invoke(state.copy(lprRecognizerReady = true))
                    }
                } catch (e: Exception) {
                    logger.e("Failed to load LPR recognizer: ${e.message}")
                }
            }
        }
    }

    /**
     * 异步初始化
     */
    suspend fun initializeAsync(): InitState {
        var state = InitState()
        
        // 车辆检测
        try {
            val path = modelLoader.loadModel("vehicle_detector")
            if (path.isSuccess) {
                vehicleDetector.initialize(path.getOrThrow())
                vehicleDetector.warmUp()
                state = state.copy(vehicleDetectorReady = true)
            }
        } catch (e: Exception) {
            logger.e("Vehicle detector: ${e.message}")
        }
        
        // 车道线检测
        try {
            val path = modelLoader.loadModel("lane_detector")
            if (path.isSuccess) {
                laneDetector.initialize(path.getOrThrow())
                laneDetector.warmUp()
                state = state.copy(laneDetectorReady = true)
            }
        } catch (e: Exception) {
            logger.e("Lane detector: ${e.message}")
        }
        
        // 车牌识别
        try {
            val path = modelLoader.loadModel("lpr_recognizer")
            if (path.isSuccess) {
                lprRecognizer.initialize(path.getOrThrow())
                lprRecognizer.warmUp()
                state = state.copy(lprRecognizerReady = true)
            }
        } catch (e: Exception) {
            logger.e("LPR recognizer: ${e.message}")
        }
        
        logger.d("Initialization complete: $state")
        return state
    }

    /**
     * 检查模型是否就绪
     */
    fun isReady(): Boolean {
        return vehicleDetector.isInitialized() &&
               laneDetector.isInitialized() &&
               lprRecognizer.isInitialized()
    }

    /**
     * 获取模型状态
     */
    fun getModelsStatus(): List<ModelLoader.ModelStatus> {
        return modelLoader.getAllModelsStatus()
    }

    private class Logger(private val tag: String) {
        fun d(msg: String) = Log.d(tag, msg)
        fun e(msg: String) = Log.e(tag, msg)
    }
}
