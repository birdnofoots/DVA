package com.dva.app.infrastructure.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.dva.app.domain.model.VehicleCategory
import com.dva.app.domain.model.VehicleColor
import com.dva.app.infrastructure.ml.models.DetectorConfig
import com.dva.app.infrastructure.ml.models.VehicleDetectionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 车辆检测器
 * 使用 YOLOv8 模型检测车辆
 */
@Singleton
class VehicleDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inferenceEngine: OnnxInferenceEngine
) {
    companion object {
        private const val MODEL_INPUT_SIZE = 640
        private val VEHICLE_CLASSES = listOf("car", "truck", "bus", "motorcycle")
        
        // 车辆类别映射
        private val CLASS_TO_CATEGORY = mapOf(
            0 to VehicleCategory.CAR,
            1 to VehicleCategory.TRUCK,
            2 to VehicleCategory.BUS,
            3 to VehicleCategory.MOTORCYCLE
        )
    }

    private var isWarmedUp = false
    private var modelPath: String? = null
    private val config = DetectorConfig()

    /**
     * 初始化检测器
     */
    fun initialize(path: String): Result<Unit> {
        val result = inferenceEngine.initialize(path)
        if (result.isSuccess) {
            modelPath = path
        }
        return result
    }

    /**
     * 预热模型
     */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        if (!inferenceEngine.isInitialized()) return@withContext
        
        val dummyBitmap = Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888)
        try {
            detect(dummyBitmap, 0.1f)
            isWarmedUp = true
        } catch (e: Exception) {
            // Ignore warmup errors
        } finally {
            dummyBitmap.recycle()
        }
    }

    /**
     * 检测车辆
     * @param bitmap 输入图片
     * @param threshold 置信度阈值
     * @param originalWidth 原图宽度
     * @param originalHeight 原图高度
     */
    suspend fun detect(
        bitmap: Bitmap,
        threshold: Float = config.confidenceThreshold,
        originalWidth: Int = bitmap.width,
        originalHeight: Int = bitmap.height
    ): List<VehicleDetectionResult> = withContext(Dispatchers.Default) {
        if (!inferenceEngine.isInitialized()) {
            return@withContext emptyList()
        }

        try {
            // 预处理
            val inputBuffer = preprocessImage(bitmap)
            
            // 推理
            val inputShape = longArrayOf(1, 3, MODEL_INPUT_SIZE.toLong(), MODEL_INPUT_SIZE.toLong())
            val outputResult = inferenceEngine.run("images", inputBuffer, inputShape)
            
            if (outputResult.isFailure) {
                return@withContext emptyList()
            }
            
            val outputs = outputResult.getOrNull() ?: return@withContext emptyList()
            
            // 后处理
            val detections = postProcessYolov8(outputs[0], threshold)
            
            // 转换坐标到原图尺寸
            detections.mapNotNull { det ->
                if (det.confidence < threshold) return@mapNotNull null
                
                // 将归一化坐标转换为原图坐标
                val left = det.boundingBox.left * originalWidth
                val top = det.boundingBox.top * originalHeight
                val right = det.boundingBox.right * originalWidth
                val bottom = det.boundingBox.bottom * originalHeight
                
                VehicleDetectionResult(
                    boundingBox = android.graphics.RectF(
                        max(0f, left),
                        max(0f, top),
                        min(originalWidth.toFloat(), right),
                        min(originalHeight.toFloat(), bottom)
                    ),
                    category = CLASS_TO_CATEGORY[det.classId] ?: VehicleCategory.UNKNOWN,
                    confidence = det.confidence,
                    classId = det.classId
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 预处理图片
     */
    private fun preprocessImage(bitmap: Bitmap): ByteArray {
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true
        )
        
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        scaledBitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        
        // 转换为 RGB 字节（YOLOv8 使用 BGR 顺序）
        val byteBuffer = ByteBuffer.allocateDirect(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // YOLOv8 使用 BGR 顺序
            byteBuffer.put(b.toByte())
            byteBuffer.put(g.toByte())
            byteBuffer.put(r.toByte())
        }
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return byteBuffer.array()
    }

    /**
     * YOLOv8 后处理
     */
    private fun postProcessYolov8(
        output: ai.onnxruntime.OnnxTensor,
        threshold: Float
    ): List<VehicleDetectionResult> {
        // YOLOv8 输出处理
        // 输出形状: [1, 84, 8400] (84 = 4(box) + 80(classes))
        // 或 [1, 5, N] (5 = 4(box) + 1(confidence))
        
        val results = mutableListOf<VehicleDetectionResult>()
        
        try {
            val floatBuffer = output.getFloatBuffer()
            
            // 获取输出形状
            val shape = output.info.shape
            
            if (shape.size >= 3) {
                // [batch, 84, 8400] 格式
                val numClasses = shape[1] - 4 // 4 = x, y, w, h
                val numDetections = shape[2]
                
                // 仅处理车辆相关类别
                for (i in 0 until min(numDetections, 100).toInt()) {
                    // 读取置信度和类别
                    var maxConf = 0f
                    var maxClassId = 0
                    
                    for (c in 0 until min(numClasses, VEHICLE_CLASSES.size.toLong()).toInt()) {
                        val confIndex = ((c + 4) * numDetections + i).toInt()
                        if (confIndex < floatBuffer.capacity()) {
                            floatBuffer.position(confIndex)
                            val conf = floatBuffer.get()
                            if (conf > maxConf) {
                                maxConf = conf
                                maxClassId = c
                            }
                        }
                    }
                    
                    if (maxConf >= threshold) {
                        // 读取边界框
                        val boxOffset = i
                        floatBuffer.position(boxOffset)
                        val cx = floatBuffer.get()
                        val cy = floatBuffer.get()
                        val w = floatBuffer.get()
                        val h = floatBuffer.get()
                        
                        // 转换为 xywh -> xyxy
                        val left = (cx - w / 2) / MODEL_INPUT_SIZE
                        val top = (cy - h / 2) / MODEL_INPUT_SIZE
                        val right = (cx + w / 2) / MODEL_INPUT_SIZE
                        val bottom = (cy + h / 2) / MODEL_INPUT_SIZE
                        
                        results.add(
                            VehicleDetectionResult(
                                boundingBox = RectF(left, top, right, bottom),
                                category = CLASS_TO_CATEGORY[maxClassId] ?: VehicleCategory.UNKNOWN,
                                confidence = maxConf,
                                classId = maxClassId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // 处理解析错误
        }
        
        return results
    }

    /**
     * 释放资源
     */
    fun release() {
        inferenceEngine.release()
        isWarmedUp = false
        modelPath = null
    }

    /**
     * 是否已初始化
     */
    fun isInitialized(): Boolean = inferenceEngine.isInitialized()
}
