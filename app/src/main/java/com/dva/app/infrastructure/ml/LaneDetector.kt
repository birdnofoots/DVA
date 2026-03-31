package com.dva.app.infrastructure.ml

import android.graphics.Bitmap
import android.graphics.Color
import com.dva.app.domain.model.LaneDetection
import com.dva.app.domain.model.LaneLine
import com.dva.app.domain.model.PointF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 车道线检测器接口
 */
interface LaneDetector {
    suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<LaneDetection>
}

/**
 * 基于图像处理的车道线检测器（简化实现）
 * 
 * 使用纯 Kotlin 实现的简化算法：
 * 1. 灰度转换
 * 2. Sobel 边缘检测
 * 3. 感兴趣区域（ROI）裁剪
 * 4. 霍夫变换检测直线
 * 5. 分类左右车道线
 * 
 * 注意：这是简化版，适合车道线明显的场景（如高速公路）
 * 复杂场景需要深度学习模型（LaneNet）
 */
class SimpleLaneDetector : LaneDetector {
    
    companion object {
        // 图像缩放比例（用于加速处理）
        private const val SCALE = 4
        // 边缘检测阈值
        private const val EDGE_THRESHOLD = 30
        // 直线检测最小点数
        private const val MIN_LINE_POINTS = 10
        // 车道线角度范围（度）
        private const val MAX_ANGLE = 45f
        // ROI 起始行（图像下半部分）
        private const val ROI_TOP_RATIO = 0.5f
    }
    
    override suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<LaneDetection> {
        return frames.map { (frameIndex, frameData) ->
            detectFrame(frameIndex, frameData)
        }
    }
    
    private fun detectFrame(frameIndex: Int, frameData: ByteArray): LaneDetection {
        // 简化实现：返回模拟结果
        // 实际需要 Bitmap 数据，这里暂时返回空
        // 后续需要与视频帧提取器配合使用
        return LaneDetection(
            frameIndex = frameIndex,
            lanes = emptyList()
        )
    }
    
    /**
     * 检测车道线（需要 Bitmap 输入）
     */
    fun detectFromBitmap(bitmap: Bitmap): LaneDetection {
        // 1. 缩放图像加速处理
        val scaledWidth = bitmap.width / SCALE
        val scaledHeight = bitmap.height / SCALE
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // 2. 灰度转换
        val gray = toGrayscale(scaled)
        
        // 3. Sobel 边缘检测
        val edges = sobelEdgeDetection(gray, scaledWidth, scaledHeight)
        
        // 4. 感兴趣区域（ROI）- 只看下半部分
        val roiEdges = applyROI(edges, scaledWidth, scaledHeight)
        
        // 5. 霍夫变换检测直线
        val lines = probabilisticHoughLine(roiEdges, scaledWidth, scaledHeight)
        
        // 6. 分类左右车道线
        val (leftLines, rightLines) = classifyLines(lines, scaledWidth)
        
        // 7. 合并车道线
        val lanes = mutableListOf<LaneLine>()
        
        if (leftLines.isNotEmpty()) {
            lanes.add(LaneLine(
                points = leftLines,
                isSolid = true,
                isLeft = true
            ))
        }
        
        if (rightLines.isNotEmpty()) {
            lanes.add(LaneLine(
                points = rightLines,
                isSolid = true,
                isLeft = false
            ))
        }
        
        scaled.recycle()
        
        return LaneDetection(
            frameIndex = 0,
            lanes = lanes
        )
    }
    
    /**
     * 灰度转换
     */
    private fun toGrayscale(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            // 使用 luminance 公式
            gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        return gray
    }
    
    /**
     * Sobel 边缘检测
     */
    private fun sobelEdgeDetection(gray: IntArray, width: Int, height: Int): IntArray {
        val edges = IntArray(width * height)
        
        // Sobel 算子
        val sobelX = intArrayOf(-1, 0, 1, -2, 0, 2, -1, 0, 1)
        val sobelY = intArrayOf(-1, -2, -1, 0, 0, 0, 1, 2, 1)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val weightIdx = (ky + 1) * 3 + (kx + 1)
                        gx += gray[idx] * sobelX[weightIdx]
                        gy += gray[idx] * sobelY[weightIdx]
                    }
                }
                
                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()
                edges[y * width + x] = if (magnitude > EDGE_THRESHOLD) 255 else 0
            }
        }
        
        return edges
    }
    
    /**
     * 应用感兴趣区域（ROI）
     * 只保留下半部分的边缘
     */
    private fun applyROI(edges: IntArray, width: Int, height: Int): IntArray {
        val roi = IntArray(width * height)
        val roiTop = (height * ROI_TOP_RATIO).toInt()
        
        for (y in roiTop until height) {
            for (x in 0 until width) {
                roi[y * width + x] = edges[y * width + x]
            }
        }
        
        return roi
    }
    
    /**
     * 简化的霍夫变换直线检测
     */
    private fun probabilisticHoughLine(
        edges: IntArray,
        width: Int,
        height: Int
    ): List<Pair<PointF, PointF>> {
        val lines = mutableListOf<Pair<PointF, PointF>>()
        
        // 简化实现：扫描每一行和每一列，找连续的边缘点
        // 水平线检测
        for (y in height / 2 until height step 10) {
            var lineStart: Int? = null
            for (x in 0..width) {
                val isEdge = if (x < width) edges[y * width + x] > 0 else false
                
                if (isEdge && lineStart == null) {
                    lineStart = x
                } else if (!isEdge && lineStart != null) {
                    val lineEnd = x - 1
                    if (lineEnd - lineStart > MIN_LINE_POINTS) {
                        lines.add(Pair(
                            PointF(lineStart.toFloat(), y.toFloat()),
                            PointF(lineEnd.toFloat(), y.toFloat())
                        ))
                    }
                    lineStart = null
                }
            }
        }
        
        // 垂直线检测（使用简化方法）
        for (x in width / 4 until width * 3 / 4 step 10) {
            var lineStart: Int? = null
            for (y in height / 2 until height) {
                val isEdge = edges[y * width + x] > 0
                
                if (isEdge && lineStart == null) {
                    lineStart = y
                } else if (!isEdge && lineStart != null) {
                    val lineEnd = y - 1
                    if (lineEnd - lineStart > MIN_LINE_POINTS) {
                        // 计算斜率，过滤掉近似水平的线
                        val angle = atan2((lineEnd - lineStart).toDouble(), 1.0) * 180 / Math.PI
                        if (abs(angle) > MAX_ANGLE) {
                            lines.add(Pair(
                                PointF(x.toFloat(), lineStart.toFloat()),
                                PointF(x.toFloat(), lineEnd.toFloat())
                            ))
                        }
                    }
                    lineStart = null
                }
            }
        }
        
        return lines
    }
    
    /**
     * 分类左右车道线
     */
    private fun classifyLines(
        lines: List<Pair<PointF, PointF>>,
        imageWidth: Int
    ): Pair<List<PointF>, List<PointF>> {
        val centerX = imageWidth / 2f
        val leftPoints = mutableListOf<PointF>()
        val rightPoints = mutableListOf<PointF>()
        
        for ((p1, p2) in lines) {
            val midX = (p1.x + p2.x) / 2
            if (midX < centerX) {
                leftPoints.add(p1)
                leftPoints.add(p2)
            } else {
                rightPoints.add(p1)
                rightPoints.add(p2)
            }
        }
        
        return Pair(leftPoints, rightPoints)
    }
}

/**
 * 基于颜色的车道线检测（备选方案）
 * 用于检测黄色和白色车道线
 */
class ColorBasedLaneDetector : LaneDetector {
    
    companion object {
        // 黄色阈值 (R, G, B)
        private val YELLOW_LOWER = intArrayOf(100, 100, 0)
        private val YELLOW_UPPER = intArrayOf(255, 255, 150)
        // 白色阈值
        private val WHITE_LOWER = intArrayOf(200, 200, 200)
        private val WHITE_UPPER = intArrayOf(255, 255, 255)
    }
    
    override suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<LaneDetection> {
        return frames.map { (frameIndex, frameData) ->
            LaneDetection(frameIndex = frameIndex, lanes = emptyList())
        }
    }
    
    /**
     * 检测黄色车道线
     */
    fun detectYellowLane(bitmap: Bitmap): List<PointF> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val yellowPoints = mutableListOf<PointF>()
        
        for (y in height / 2 until height step 5) {
            for (x in 0 until width step 5) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // 检测黄色（RGB 黄色的范围）
                if (r > 150 && g > 150 && b < 100) {
                    yellowPoints.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }
        
        return yellowPoints
    }
}
