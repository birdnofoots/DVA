package com.dva.app.infrastructure.ml

import android.content.Context
import android.util.Log
import com.dva.app.domain.model.VehicleDetection
import com.dva.app.domain.model.BoundingBox
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream

/**
 * 车辆检测器接口
 */
interface VehicleDetector {
    suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<VehicleDetection>
    fun isModelAvailable(): Boolean
}

/**
 * YOLOv8 车辆检测器实现
 * 
 * 使用 ONNX Runtime 进行模型推理
 */
class YoloVehicleDetector(
    private val context: Context,
    private val modelPath: String
) : VehicleDetector {
    
    companion object {
        private const val TAG = "YoloVehicleDetector"
        
        // 模型文件名
        private const val MODEL_FILE_NAME = "yolov8n-vehicle.onnx"
        
        // assets 中模型的路径
        private const val ASSETS_MODEL_PATH = "models/$MODEL_FILE_NAME"
        
        // 模型缓存目录
        private const val MODEL_CACHE_DIR = "models"
        
        // COCO 类别中与车辆相关的类别
        private val vehicleClasses = setOf(2, 3, 5, 7, 8) // car, motorcycle, bus, truck, train
        
        // 置信度阈值
        private val confidenceThreshold = 0.5f
        
        // NMS 阈值
        private val nmsThreshold = 0.4f
        
        // 输入图片尺寸 (YOLOv8n 默认)
        private const val INPUT_SIZE = 640
    }
    
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    
    // 模型是否可用
    private var _isModelLoaded = false
    override fun isModelAvailable() = _isModelLoaded
    
    init {
        // 初始化调试日志
        ModelLoadingDebug.init(context)
        ModelLoadingDebug.log("=== YoloVehicleDetector initializing ===")
        
        // 打印系统信息
        ModelLoadingDebug.log("Android SDK: ${android.os.Build.VERSION.SDK_INT}")
        ModelLoadingDebug.log("ONNX Runtime info: ai.onnxruntime")
        
        // 方法1: 从 assets 直接加载 ByteArray
        try {
            ModelLoadingDebug.log("Method 1: Loading from assets as ByteArray...")
            val modelBytes = context.assets.open(ASSETS_MODEL_PATH).readBytes()
            ModelLoadingDebug.log("  Read ${modelBytes.size} bytes from assets")
            
            ModelLoadingDebug.log("  Creating OrtEnvironment...")
            ortEnvironment = OrtEnvironment.getEnvironment()
            ModelLoadingDebug.log("  OrtEnvironment created")
            
            ModelLoadingDebug.log("  Creating OrtSession from ByteArray...")
            ortSession = ortEnvironment?.createSession(modelBytes)
            ModelLoadingDebug.log("  OrtSession created")
            
            _isModelLoaded = ortSession != null
            ModelLoadingDebug.log("  Model loaded: $_isModelLoaded")
            
        } catch (e: Exception) {
            ModelLoadingDebug.logError("Method 1 failed", e)
            e.printStackTrace()
            
            // 方法2: 复制到缓存后从文件加载
            try {
                ModelLoadingDebug.log("Method 2: Copying to cache and loading from file...")
                val modelFile = copyModelToCache()
                if (modelFile != null && modelFile.exists()) {
                    ModelLoadingDebug.log("  Model file exists: ${modelFile.absolutePath}, size: ${modelFile.length()}")
                    
                    ModelLoadingDebug.log("  Creating OrtEnvironment...")
                    ortEnvironment = OrtEnvironment.getEnvironment()
                    ModelLoadingDebug.log("  OrtEnvironment created")
                    
                    ModelLoadingDebug.log("  Creating OrtSession from file: ${modelFile.absolutePath}...")
                    ortSession = ortEnvironment?.createSession(modelFile.absolutePath)
                    
                    _isModelLoaded = ortSession != null
                    ModelLoadingDebug.log("  Model loaded from file: $_isModelLoaded")
                } else {
                    ModelLoadingDebug.log("  Model file not found after copy")
                }
            } catch (e2: Exception) {
                ModelLoadingDebug.logError("Method 2 also failed", e2)
                e2.printStackTrace()
            }
        }
        
        ModelLoadingDebug.log("=== YoloVehicleDetector init complete. Available: $_isModelLoaded ===")
        ModelLoadingDebug.log("Log file: ${ModelLoadingDebug.getLogFilePath()}")
        ModelLoadingDebug.close()
        
        Log.d(TAG, "YoloVehicleDetector init complete. Model available: $_isModelLoaded")
    }
    
    /**
     * 复制模型到缓存目录
     */
    private fun copyModelToCache(): File? {
        return try {
            val cacheDir = File(context.cacheDir, MODEL_CACHE_DIR)
            ModelLoadingDebug.log("  Creating cache dir: ${cacheDir.absolutePath}")
            cacheDir.mkdirs()
            
            val destFile = File(cacheDir, MODEL_FILE_NAME)
            
            if (!destFile.exists()) {
                ModelLoadingDebug.log("  Copying model from assets to: ${destFile.absolutePath}")
                context.assets.open(ASSETS_MODEL_PATH).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                ModelLoadingDebug.log("  Copy complete, size: ${destFile.length()}")
            } else {
                ModelLoadingDebug.log("  Model already exists in cache")
            }
            
            destFile
        } catch (e: Exception) {
            ModelLoadingDebug.logError("  Failed to copy model", e)
            e.printStackTrace()
            null
        }
    }
    
    override suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<VehicleDetection> {
        if (!_isModelLoaded || ortSession == null) {
            Log.w(TAG, "Model not loaded, returning empty results")
            return emptyList()
        }
        
        val results = mutableListOf<VehicleDetection>()
        for ((frameIndex, frameData) in frames) {
            try {
                val detections = detectFrame(frameIndex, frameData)
                results.addAll(detections)
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting frame $frameIndex", e)
            }
        }
        return results
    }
    
    /**
     * 检测单帧中的车辆
     */
    private fun detectFrame(frameIndex: Int, frameData: ByteArray): List<VehicleDetection> {
        if (ortSession == null) return emptyList()
        
        try {
            // TODO: 实现真实的 YOLOv8 推理
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectFrame", e)
            return emptyList()
        }
    }
    
    private fun getClassName(classId: Int): String {
        return when (classId) {
            2 -> "car"
            3 -> "motorcycle"
            4 -> "bicycle"
            5 -> "bus"
            7 -> "truck"
            else -> "vehicle"
        }
    }
}
