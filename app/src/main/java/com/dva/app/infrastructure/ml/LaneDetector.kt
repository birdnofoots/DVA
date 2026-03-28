package com.dva.app.infrastructure.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import com.dva.app.domain.model.Lane
import com.dva.app.domain.model.LaneType
import com.dva.app.infrastructure.ml.models.DetectorConfig
import com.dva.app.infrastructure.ml.models.LaneDetectionResult
import ai.onnxruntime.OnnxTensor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 车道线检测器
 * 使用轻量级模型或传统算法检测车道线
 */
@Singleton
class LaneDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inferenceEngine: OnnxInferenceEngine
) {
    companion object {
        private const val MODEL_INPUT_WIDTH = 512
        private const val MODEL_INPUT_HEIGHT = 256
        
        // 传统算法参数
        private const val CANNY_LOW_THRESHOLD = 50
        private const val CANNY_HIGH_THRESHOLD = 150
        private const val HOUGH_THRESHOLD = 50
        private const val HOUGH_MIN_LINE_LENGTH = 50
        private const val HOUGH_MAX_LINE_GAP = 100
        
        // 透视变换参数
        private const val VANISHING_POINT_Y = 0.4f
        
        // 车道线角度阈值
        private const val HORIZONTAL_ANGLE_THRESHOLD = 30 // 度
    }

    private var useOnnxModel = false
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
            useOnnxModel = true
        }
        return result
    }

    /**
     * 预热模型
     */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        if (!inferenceEngine.isInitialized()) return@withContext
        
        val dummyBitmap = Bitmap.createBitmap(MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            detect(dummyBitmap)
            isWarmedUp = true
        } catch (e: Exception) {
            // Ignore warmup errors
        } finally {
            dummyBitmap.recycle()
        }
    }

    /**
     * 检测车道线
     * @param image 输入图片
     * @param originalWidth 原图宽度
     * @param originalHeight 原图高度
     */
    suspend fun detect(
        image: Bitmap,
        originalWidth: Int = image.width,
        originalHeight: Int = image.height
    ): List<LaneDetectionResult> = withContext(Dispatchers.Default) {
        return@withContext try {
            if (useOnnxModel && inferenceEngine.isInitialized()) {
                detectWithModel(image, originalWidth, originalHeight)
            } else {
                detectWithTraditional(image, originalWidth, originalHeight)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 使用 ONNX 模型检测
     */
    private suspend fun detectWithModel(
        image: Bitmap,
        originalWidth: Int,
        originalHeight: Int
    ): List<LaneDetectionResult> {
        // 预处理
        val inputBuffer = preprocessImage(image)
        
        // 推理
        val inputShape = longArrayOf(1, 3, MODEL_INPUT_HEIGHT.toLong(), MODEL_INPUT_WIDTH.toLong())
        val outputResult = inferenceEngine.run("input", inputBuffer, inputShape)
        
        if (outputResult.isFailure) {
            return emptyList()
        }
        
        val outputs = outputResult.getOrNull() ?: return emptyList()
        
        // 后处理
        return postProcessLaneNet(outputs[0], originalWidth, originalHeight)
    }

    /**
     * 使用传统算法检测
     */
    private fun detectWithTraditional(
        image: Bitmap,
        originalWidth: Int,
        originalHeight: Int
    ): List<LaneDetectionResult> {
        // 简化版：使用颜色分割和边缘检测
        // 在实际应用中，这里应该使用完整的 OpenCV 算法
        
        val lanes = mutableListOf<LaneDetectionResult>()
        
        // 缩放到处理尺寸
        val width = MODEL_INPUT_WIDTH
        val height = MODEL_INPUT_HEIGHT
        val scaled = Bitmap.createScaledBitmap(image, width, height, true)
        
        // 简单颜色分割：检测白色/黄色车道线
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 扫描下半部分图像（车道线通常在下半部分）
        val scanStartY = (height * 0.5).toInt()
        
        // 左车道线
        var leftLanePoints = mutableListOf<PointF>()
        var rightLanePoints = mutableListOf<PointF>()
        
        // 从下往上扫描
        for (y in (height - 1) downTo scanStartY step 5) {
            for (x in 0 until width step 2) {
                val pixel = pixels[y * width + x]
                val brightness = brightness(pixel)
                
                // 检测亮色像素（可能是车道线）
                if (brightness > 200) {
                    val relativeX = x.toFloat() / width
                    
                    // 左半部分
                    if (relativeX < 0.5) {
                        // 找到最左边的亮像素
                        if (leftLanePoints.isEmpty() || x < (leftLanePoints.last().x * width).toInt()) {
                            leftLanePoints.add(PointF(x.toFloat(), y.toFloat()))
                        }
                    } else {
                        // 右半部分
                        if (rightLanePoints.isEmpty() || x > (rightLanePoints.last().x * width).toInt()) {
                            rightLanePoints.add(PointF(x.toFloat(), y.toFloat()))
                        }
                    }
                }
            }
        }
        
        // filter and fit
        if (leftLanePoints.size > 10) {
            val filteredPoints = filterPoints(leftLanePoints)
            lanes.add(
                LaneDetectionResult(
                    points = filteredPoints,
                    laneType = LaneType.DASHED,
                    laneId = 0,
                    confidence = calculateConfidence(filteredPoints)
                )
            )
        }
        
        if (rightLanePoints.size > 10) {
            val filteredPoints = filterPoints(rightLanePoints)
            lanes.add(
                LaneDetectionResult(
                    points = filteredPoints,
                    laneType = LaneType.DASHED,
                    laneId = 1,
                    confidence = calculateConfidence(filteredPoints)
                )
            )
        }
        
        if (scaled != image) {
            scaled.recycle()
        }
        
        return lanes
    }

    /**
     * 预处理图片
     */
    private fun preprocessImage(bitmap: Bitmap): ByteArray {
        val scaled = Bitmap.createScaledBitmap(
            bitmap, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, true
        )
        
        val pixels = IntArray(MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT)
        scaled.getPixels(pixels, 0, MODEL_INPUT_WIDTH, 0, 0, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT)
        
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(
            MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT * 3
        ).order(java.nio.ByteOrder.nativeOrder())
        
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // 归一化
            byteBuffer.put((r * 0.003921).toInt().toByte())
            byteBuffer.put((g * 0.003921).toInt().toByte())
            byteBuffer.put((b * 0.003921).toInt().toByte())
        }
        
        if (scaled != bitmap) {
            scaled.recycle()
        }
        
        return byteBuffer.array()
    }

    /**
     * LaneNet 后处理
     */
    private fun postProcessLaneNet(
        output: ai.onnxruntime.OnnxTensor,
        originalWidth: Int,
        originalHeight: Int
    ): List<LaneDetectionResult> {
        // LaneNet 输出处理
        // 输出通常是分割掩码或车道线点
        
        val lanes = mutableListOf<LaneDetectionResult>()
        
        try {
            val buffer = output.getFloatBuffer()
            val shape = output.info.shape
            
            if (shape.size < 3) return lanes
            
            // 输出形状: [1, H, W] 或 [1, num_lanes, H, W]
            val outHeight = shape[1].toInt()
            val outWidth = shape[2].toInt()
            
            // 缩放比例
            val scaleX = originalWidth.toFloat() / outWidth
            val scaleY = originalHeight.toFloat() / outHeight
            
            // 简化处理：提取车道线中心点
            // 实际应用中应该使用更复杂的分割和拟合算法
            
            for (laneId in 0 until minOf(shape[1].toInt(), 4)) {
                val lanePoints = mutableListOf<PointF>()
                
                for (y in 0 until outHeight step 4) {
                    for (x in 0 until outWidth step 2) {
                        val idx = (laneId * outHeight + y) * outWidth + x
                        buffer.position(idx)
                        val prob = buffer.get()
                        
                        if (prob > 0.5f) {
                            lanePoints.add(PointF(x * scaleX, y * scaleY))
                        }
                    }
                }
                
                if (lanePoints.size > 20) {
                    val filteredPoints = filterPoints(lanePoints)
                    lanes.add(
                        LaneDetectionResult(
                            points = filteredPoints,
                            laneType = classifyLaneType(filteredPoints),
                            laneId = laneId,
                            confidence = calculateConfidence(filteredPoints)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore processing errors
        }
        
        return lanes
    }

    /**
     * 过滤异常点
     */
    private fun filterPoints(points: List<PointF>): List<PointF> {
        if (points.size < 3) return points
        
        // 计算点到拟合直线的平均距离
        val filtered = mutableListOf<PointF>()
        
        // 取上下限外的点作为边界
        val sortedByY = points.sortedBy { it.y }
        val topPoints = sortedByY.take(points.size / 4)
        val bottomPoints = sortedByY.takeLast(points.size / 4)
        
        // 计算平均X
        val avgTopX = topPoints.map { it.x }.average().toFloat()
        val avgBottomX = bottomPoints.map { it.x }.average().toFloat()
        
        // 过滤偏离较大的点
        for (point in points) {
            val expectedX = if (abs(avgBottomX - avgTopX) > 10) {
                // 斜线
                val t = (point.y - sortedByY.first().y) / (sortedByY.last().y - sortedByY.first().y)
                avgTopX + (avgBottomX - avgTopX) * t
            } else {
                // 直线
                avgTopX
            }
            
            if (abs(point.x - expectedX) < 50) {
                filtered.add(point)
            }
        }
        
        return filtered
    }

    /**
     * 计算置信度
     */
    private fun calculateConfidence(points: List<PointF>): Float {
        if (points.size < 3) return 0f
        
        var confidence = 0.5f
        
        // 点数越多置信度越高
        val pointScore = (points.size / 100f).coerceAtMost(0.3f)
        confidence += pointScore
        
        // 连续性：相邻点的距离
        var continuity = 0f
        for (i in 1 until points.size) {
            val dist = sqrt(
                (points[i].x - points[i-1].x).pow(2) +
                (points[i].y - points[i-1].y).pow(2)
            )
            if (dist < 20) continuity += 1
        }
        continuity /= points.size
        confidence += continuity * 0.2f
        
        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 判断车道线类型
     */
    private fun classifyLaneType(points: List<PointF>): LaneType {
        if (points.size < 2) return LaneType.UNKNOWN
        
        // 计算斜率变化
        val sortedByY = points.sortedBy { it.y }
        
        // 上半部分和下半部分的平均X
        val midIndex = points.size / 2
        val topAvgX = sortedByY.take(midIndex).map { it.x }.average()
        val bottomAvgX = sortedByY.takeLast(midIndex).map { it.x }.average()
        
        // 虚线：上下部分有明显间隔
        val gap = abs(bottomAvgX - topAvgX)
        
        return when {
            gap > 20 -> LaneType.DASHED
            gap < 5 -> LaneType.SOLID
            else -> LaneType.UNKNOWN
        }
    }

    /**
     * 转换为领域模型
     */
    fun toLane(result: LaneDetectionResult): Lane {
        return Lane(
            id = result.laneId,
            points = result.points,
            laneType = result.laneType,
            curvature = calculateCurvature(result.points),
            vanishingPoint = estimateVanishingPoint(result.points)
        )
    }

    /**
     * 计算曲率
     */
    private fun calculateCurvature(points: List<PointF>): Float {
        if (points.size < 3) return 0f
        
        val sortedByY = points.sortedBy { it.y }
        val mid = sortedByY[sortedByY.size / 2]
        val top = sortedByY.first()
        val bottom = sortedByY.last()
        
        val deltaX = bottom.x - top.x
        val deltaY = bottom.y - top.y
        
        return if (abs(deltaY) > 0) {
            abs(deltaX / deltaY) * 0.1f
        } else 0f
    }

    /**
     * 估算消失点
     */
    private fun estimateVanishingPoint(points: List<PointF>): PointF? {
        if (points.size < 3) return null
        
        // 简化：使用最高点的延伸线交点
        val sortedByY = points.sortedBy { it.y }
        val top = sortedByY.first()
        
        return PointF(top.x, top.y * VANISHING_POINT_Y)
    }

    /**
     * 计算像素亮度
     */
    private fun brightness(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    /**
     * 释放资源
     */
    fun release() {
        inferenceEngine.release()
        isWarmedUp = false
        modelPath = null
        useOnnxModel = false
    }

    /**
     * 是否已初始化
     */
    fun isInitialized(): Boolean = inferenceEngine.isInitialized()
}
