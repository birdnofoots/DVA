package com.dva.app.infrastructure.ml

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.dva.app.domain.model.VehicleDetection
import com.dva.app.domain.model.BoundingBox
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtUtil
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer

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
        try {
            Log.d(TAG, "Initializing YoloVehicleDetector...")
            
            // 方法1: 尝试从 assets 直接加载模型（作为 ByteArray）
            val modelBytes = loadModelFromAssets()
            if (modelBytes != null) {
                Log.d(TAG, "Model loaded from assets, size: ${modelBytes.size}")
                try {
                    ortEnvironment = OrtEnvironment.getEnvironment()
                    ortSession = ortEnvironment?.createSession(modelBytes)
                    _isModelLoaded = ortSession != null
                    Log.d(TAG, "Model created from ByteArray, success: $_isModelLoaded")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create session from ByteArray", e)
                    _isModelLoaded = false
                }
            } else {
                Log.e(TAG, "Failed to load model from assets as ByteArray")
                
                // 方法2: 复制到缓存后加载
                val modelFile = copyModelToCache()
                if (modelFile != null && modelFile.exists()) {
                    Log.d(TAG, "Trying to load from file: ${modelFile.absolutePath}")
                    try {
                        ortEnvironment = OrtEnvironment.getEnvironment()
                        ortSession = ortEnvironment?.createSession(modelFile.absolutePath)
                        _isModelLoaded = ortSession != null
                        Log.d(TAG, "Model loaded from file, success: $_isModelLoaded")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create session from file", e)
                        _isModelLoaded = false
                    }
                } else {
                    Log.e(TAG, "Model file not found in cache either")
                    _isModelLoaded = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoloVehicleDetector", e)
            _isModelLoaded = false
        }
        
        Log.d(TAG, "YoloVehicleDetector initialization complete. Model available: $_isModelLoaded")
    }
    
    /**
     * 从 assets 加载模型作为 ByteArray
     */
    private fun loadModelFromAssets(): ByteArray? {
        return try {
            context.assets.open(ASSETS_MODEL_PATH).use { input ->
                input.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model from assets", e)
            null
        }
    }
    
    /**
     * 复制模型到缓存目录
     */
    private fun copyModelToCache(): File? {
        return try {
            val cacheDir = File(context.cacheDir, MODEL_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val destFile = File(cacheDir, MODEL_FILE_NAME)
            if (destFile.exists()) {
                Log.d(TAG, "Model already exists at: ${destFile.absolutePath}")
                return destFile
            }
            
            context.assets.open(ASSETS_MODEL_PATH).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Model copied to: ${destFile.absolutePath}, size: ${destFile.length()}")
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model to cache", e)
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
            // 目前返回空，等修复后启用
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
