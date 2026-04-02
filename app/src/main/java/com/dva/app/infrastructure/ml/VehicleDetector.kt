package com.dva.app.infrastructure.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import java.nio.ByteOrder
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
        
        // COCO 类别中与车辆相关的类别 (YOLOv8 预训练模型)
        // 2=car, 3=motorcycle, 5=bus, 7=truck, 8=boat(可忽略)
        private val vehicleClasses = setOf(2, 3, 5, 7)
        
        // 置信度阈值
        private const val confidenceThreshold = 0.5f
        
        // NMS 阈值
        private const val nmsThreshold = 0.4f
        
        // 输入图片尺寸 (YOLOv8n 默认)
        private const val INPUT_SIZE = 640
        
        // YOLOv8 输出配置
        // YOLOv8 输出形状: [1, 84, 8400]
        // 84 = 4(bbox) + 80(class scores for COCO)
        // 8400 = 80*80 + 40*40 + 20*20 (multi-scale feature maps)
        private const val OUTPUT_COLUMNS = 84
        private const val NUM_BOXES = 8400
        private const val NUM_CLASSES = 80
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
            }
        }
        
        ModelLoadingDebug.log("=== YoloVehicleDetector init complete. Available: $_isModelLoaded ===")
        ModelLoadingDebug.close()
        
        Log.d(TAG, "YoloVehicleDetector init complete. Model available: $_isModelLoaded")
    }
    
    /**
     * 复制模型到缓存目录
     */
    private fun copyModelToCache(): File? {
        return try {
            val cacheDir = File(context.cacheDir, MODEL_CACHE_DIR)
            cacheDir.mkdirs()
            
            val destFile = File(cacheDir, MODEL_FILE_NAME)
            
            if (!destFile.exists()) {
                context.assets.open(ASSETS_MODEL_PATH).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            destFile
        } catch (e: Exception) {
            ModelLoadingDebug.logError("Failed to copy model", e)
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
            // 1. 解码图片
            val bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.size)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap for frame $frameIndex")
                return emptyList()
            }
            
            // 2. 预处理图片 (resize + 归一化)
            val inputBuffer = preprocessImage(bitmap)
            
            // 3. 创建输入 Tensor
            val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, inputBuffer, inputShape)
            
            // 4. 运行推理
            val inputs = mapOf("images" to inputTensor)
            val outputs = ortSession?.run(inputs)
            
            // 5. 解析输出
            val outputTensor = outputs?.get(0)?.value as? Array<Array<FloatArray>>
            val detections = if (outputTensor != null) {
                postProcessOutput(outputTensor, bitmap.width, bitmap.height, frameIndex)
            } else {
                Log.e(TAG, "Output tensor is null")
                emptyList()
            }
            
            // 清理
            inputTensor.close()
            outputs?.close()
            bitmap.recycle()
            
            return detections
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectFrame for frame $frameIndex", e)
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * 预处理图片
     * 将 RGB 图片 resize 到 640x640 并归一化
     */
    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        // Resize 到 640x640
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        
        // 创建 Buffer: NCHW format (1, 3, 640, 640)
        val buffer = ByteBuffer.allocateDirect(1 * 3 * INPUT_SIZE * INPUT_SIZE * 4)
        buffer.order(ByteOrder.nativeOrder())
        val floatBuffer = buffer.asFloatBuffer()
        
        // 归一化到 [0, 1] 并转换为 NCHW
        // YOLOv8 使用 RGB 格式
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = resized.getPixel(x, y)
                // R channel (normalize to 0-1)
                floatBuffer.put(((pixel shr 16) and 0xFF) / 255.0f)
            }
        }
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = resized.getPixel(x, y)
                // G channel
                floatBuffer.put(((pixel shr 8) and 0xFF) / 255.0f)
            }
        }
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = resized.getPixel(x, y)
                // B channel
                floatBuffer.put((pixel and 0xFF) / 255.0f)
            }
        }
        
        resized.recycle()
        floatBuffer.rewind()
        
        return floatBuffer
    }
    
    /**
     * 后处理 YOLOv8 输出
     * 
     * YOLOv8 输出形状: [1, 84, 8400]
     * 84 = 4 (x, y, w, h) + 80 (class scores)
     */
    private fun postProcessOutput(
        output: Array<Array<FloatArray>>,
        originalWidth: Int,
        originalHeight: Int,
        frameIndex: Int
    ): List<VehicleDetection> {
        val detections = mutableListOf<VehicleDetection>()
        
        // YOLOv8 输出: [batch, 84, 8400]
        val predictions = output[0] // shape: [84, 8400]
        
        // 遍历所有预测框
        for (i in 0 until NUM_BOXES) {
            // 获取该框的类别分数
            var maxScore = 0f
            var maxClassId = -1
            
            // 4-83 是 80 个类别的分数
            for (c in 4 until OUTPUT_COLUMNS) {
                val score = predictions[c][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClassId = c - 4 // 类别 ID 从 0 开始
                }
            }
            
            // 检查是否是车辆类别且置信度足够
            if (maxScore >= confidenceThreshold && maxClassId in vehicleClasses) {
                // 解析边界框
                val x = predictions[0][i]
                val y = predictions[1][i]
                val w = predictions[2][i]
                val h = predictions[3][i]
                
                // 转换为绝对坐标 (从 640x640 转换到原图尺寸)
                val left = ((x - w / 2) / INPUT_SIZE * originalWidth).toInt()
                val top = ((y - h / 2) / INPUT_SIZE * originalHeight).toInt()
                val right = ((x + w / 2) / INPUT_SIZE * originalWidth).toInt()
                val bottom = ((y + h / 2) / INPUT_SIZE * originalHeight).toInt()
                
                // 确保坐标在有效范围内
                val clampedLeft = left.toFloat().coerceIn(0f, originalWidth.toFloat())
                val clampedTop = top.toFloat().coerceIn(0f, originalHeight.toFloat())
                val clampedRight = right.toFloat().coerceIn(0f, originalWidth.toFloat())
                val clampedBottom = bottom.toFloat().coerceIn(0f, originalHeight.toFloat())
                
                detections.add(
                    VehicleDetection(
                        frameIndex = frameIndex,
                        trackId = 0, // 暂时不追踪
                        classId = maxClassId,
                        className = getClassName(maxClassId),
                        confidence = maxScore,
                        boundingBox = BoundingBox(
                            left = clampedLeft,
                            top = clampedTop,
                            right = clampedRight,
                            bottom = clampedBottom
                        ),
                        centerX = (clampedLeft + clampedRight) / 2,
                        centerY = (clampedTop + clampedBottom) / 2
                    )
                )
            }
        }
        
        // 应用 NMS (非极大值抑制)
        val nmsDetections = applyNMS(detections)
        
        Log.d(TAG, "Frame $frameIndex: ${nmsDetections.size} vehicles detected")
        
        return nmsDetections
    }
    
    /**
     * 非极大值抑制 (NMS)
     */
    private fun applyNMS(detections: List<VehicleDetection>): List<VehicleDetection> {
        if (detections.isEmpty()) return detections
        
        // 按置信度排序
        val sorted = detections.sortedByDescending { it.confidence }
        val keep = mutableListOf<VehicleDetection>()
        
        val used = BooleanArray(sorted.size) { false }
        
        for (i in sorted.indices) {
            if (used[i]) continue
            
            keep.add(sorted[i])
            
            for (j in (i + 1) until sorted.size) {
                if (used[j]) continue
                
                // 如果是同一类别且 IoU 超过阈值，跳过
                if (sorted[i].classId == sorted[j].classId) {
                    val iou = calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox)
                    if (iou > nmsThreshold) {
                        used[j] = true
                    }
                }
            }
        }
        
        return keep
    }
    
    /**
     * 计算两个边界框的 IoU (交并比)
     */
    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.left, box2.left)
        val y1 = maxOf(box1.top, box2.top)
        val x2 = minOf(box1.right, box2.right)
        val y2 = minOf(box1.bottom, box2.bottom)
        
        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val area1 = box1.width * box1.height
        val area2 = box2.width * box2.height
        val union = area1 + area2 - intersection
        
        return if (union > 0) intersection / union else 0f
    }
    
    private fun getClassName(classId: Int): String {
        return when (classId) {
            2 -> "car"
            3 -> "motorcycle"
            5 -> "bus"
            7 -> "truck"
            else -> "vehicle"
        }
    }
}
