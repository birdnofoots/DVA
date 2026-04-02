package com.dva.app.infrastructure.ml

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.dva.app.domain.model.VehicleDetection
import com.dva.app.domain.model.BoundingBox
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
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
        try {
            // 获取模型文件
            val modelFile = getModelFile()
            if (modelFile != null && modelFile.exists()) {
                Log.d(TAG, "Loading model from: ${modelFile.absolutePath}")
                
                // 创建 ONNX Runtime session
                ortEnvironment = OrtEnvironment.getEnvironment()
                ortSession = ortEnvironment?.createSession(modelFile.absolutePath)
                
                _isModelLoaded = ortSession != null
                Log.d(TAG, "Model loaded successfully: $_isModelLoaded")
            } else {
                Log.e(TAG, "Model file not found at: $modelPath")
                _isModelLoaded = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _isModelLoaded = false
        }
    }
    
    /**
     * 获取模型文件
     * 优先从 filesDir 加载，其次从 assets 加载
     */
    private fun getModelFile(): File? {
        // 1. 先检查 filesDir 中的模型（下载的模型）
        val filesDirModel = File(context.filesDir, "$MODEL_CACHE_DIR/$MODEL_FILE_NAME")
        if (filesDirModel.exists()) {
            Log.d(TAG, "Model found in filesDir: ${filesDirModel.absolutePath}")
            return filesDirModel
        }
        
        // 2. 检查缓存目录（ModelDownloadManager 下载的位置）
        val cacheDirModel = File(context.cacheDir, "$MODEL_CACHE_DIR/$MODEL_FILE_NAME")
        if (cacheDirModel.exists()) {
            Log.d(TAG, "Model found in cacheDir: ${cacheDirModel.absolutePath}")
            return cacheDirModel
        }
        
        // 3. 从 assets 复制到缓存
        return try {
            val assetModel = copyAssetToCache()
            if (assetModel != null) {
                Log.d(TAG, "Model copied from assets to: ${assetModel.absolutePath}")
            }
            assetModel
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model from assets", e)
            null
        }
    }
    
    /**
     * 从 assets 复制模型到缓存目录
     */
    private fun copyAssetToCache(): File? {
        return try {
            val cacheDir = File(context.cacheDir, MODEL_CACHE_DIR)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val destFile = File(cacheDir, MODEL_FILE_NAME)
            if (destFile.exists()) {
                return destFile
            }
            
            context.assets.open(ASSETS_MODEL_PATH).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "Model copied from assets to: ${destFile.absolutePath}, size: ${destFile.length()}")
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model from assets", e)
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
            // 1. 预处理：将 frameData 转换为 Bitmap 然后缩放到 640x640
            // 2. 推理：调用 ortSession.run()
            // 3. 后处理：解析输出，NMS
            
            // 当前返回空，实际需要接入模型
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectFrame", e)
            return emptyList()
        }
    }
    
    /**
     * 预处理图像数据
     * 将原始帧数据转换为模型输入格式
     */
    private fun preprocessFrame(frameData: ByteArray, width: Int, height: Int): FloatArray {
        val floatInput = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        
        // 归一化到 [0, 1]
        for (i in frameData.indices) {
            if (i < floatInput.size) {
                floatInput[i] = (frameData[i].toInt() and 0xFF) / 255.0f
            }
        }
        
        return floatInput
    }
    
    /**
     * 后处理检测结果
     * 解析模型输出，应用置信度阈值和 NMS
     */
    private fun postprocess(
        output: Array<FloatArray>, 
        frameIndex: Int,
        imgWidth: Int,
        imgHeight: Int
    ): List<VehicleDetection> {
        val detections = mutableListOf<VehicleDetection>()
        
        for (i in output.indices) {
            val detection = output[i]
            if (detection.size < 6) continue
            
            val objConf = detection[4]
            if (objConf < confidenceThreshold) continue
            
            var maxClass = 0
            var maxConf = 0f
            for (j in 5 until detection.size) {
                if (detection[j] > maxConf) {
                    maxConf = detection[j]
                    maxClass = j - 5
                }
            }
            
            if (maxClass !in vehicleClasses) continue
            
            val x = detection[0]
            val y = detection[1]
            val w = detection[2]
            val h = detection[3]
            
            detections.add(
                VehicleDetection(
                    frameIndex = frameIndex,
                    trackId = i,
                    classId = maxClass,
                    className = getClassName(maxClass),
                    confidence = objConf * maxConf,
                    boundingBox = BoundingBox(
                        left = (x - w / 2).coerceAtLeast(0f),
                        top = (y - h / 2).coerceAtLeast(0f),
                        right = (x + w / 2).coerceAtMost(imgWidth.toFloat()),
                        bottom = (y + h / 2).coerceAtMost(imgHeight.toFloat())
                    ),
                    centerX = x,
                    centerY = y
                )
            )
        }
        
        return detections
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
    
    /**
     * 非极大值抑制 (NMS)
     */
    private fun applyNMS(detections: List<VehicleDetection>): List<VehicleDetection> {
        // 按置信度排序
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<VehicleDetection>()
        
        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            result.add(current)
            
            sorted.removeAll { other ->
                iou(current.boundingBox, other.boundingBox) > nmsThreshold
            }
        }
        
        return result
    }
    
    /**
     * 计算两个边界框的 IOU (Intersection over Union)
     */
    private fun iou(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.left, box2.left)
        val y1 = maxOf(box1.top, box2.top)
        val x2 = minOf(box1.right, box2.right)
        val y2 = minOf(box1.bottom, box2.bottom)
        
        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val area1 = (box1.right - box1.left) * (box1.bottom - box1.top)
        val area2 = (box2.right - box2.left) * (box2.bottom - box2.top)
        val union = area1 + area2 - intersection
        
        return if (union > 0) intersection / union else 0f
    }
}
