package com.dva.app.infrastructure.ml

import android.content.Context
import com.dva.app.domain.model.VehicleDetection
import com.dva.app.domain.model.BoundingBox

/**
 * 车辆检测器接口
 */
interface VehicleDetector {
    suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<VehicleDetection>
}

/**
 * YOLOv8 车辆检测器实现
 * 
 * 注意：完整实现需要正确配置 ONNX Runtime
 * 当前版本为基础框架，实际推理需要模型文件
 */
class YoloVehicleDetector(
    private val context: Context,
    modelPath: String
) : VehicleDetector {
    
    // COCO 类别中与车辆相关的类别
    private val vehicleClasses = setOf(2, 3, 5, 7, 8) // car, motorcycle, bus, truck, train
    
    // 置信度阈值
    private val confidenceThreshold = 0.5f
    
    // 模型是否可用（需要 assets 中的 .onnx 文件）
    private var isModelLoaded = false
    
    init {
        try {
            // 尝试加载模型
            // val session = OrtEnvironment.getEnvironment().createSession(modelPath, OrtSession.SessionOptions())
            // isModelLoaded = true
            isModelLoaded = false // 暂时禁用，ONNX Runtime 需要正确配置
        } catch (e: Exception) {
            isModelLoaded = false
        }
    }
    
    override suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<VehicleDetection> {
        if (!isModelLoaded) {
            // 模型未加载，返回空结果
            // TODO: 后续接入真实的 YOLOv8 模型
            return emptyList()
        }
        
        val results = mutableListOf<VehicleDetection>()
        for ((frameIndex, frameData) in frames) {
            val detections = detectFrame(frameIndex, frameData)
            results.addAll(detections)
        }
        return results
    }
    
    private fun detectFrame(frameIndex: Int, frameData: ByteArray): List<VehicleDetection> {
        // TODO: 实现真实的 YOLOv8 推理
        // 1. 预处理：缩放到 640x640，归一化 RGB
        // 2. 推理：调用 ONNX Runtime
        // 3. 后处理：解析输出，NMS，非极大值抑制
        
        // 当前返回空，实际需要接入模型
        return emptyList()
    }
    
    /**
     * 预处理图像数据
     * 将原始帧数据转换为模型输入格式
     */
    private fun preprocessFrame(frameData: ByteArray, width: Int, height: Int): FloatArray {
        // 简化实现：实际需要 RGB 转换 + 归一化
        val inputSize = 640
        val floatInput = FloatArray(3 * inputSize * inputSize)
        
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
            
            // 检测格式: [x, y, w, h, obj_conf, class1_conf, class2_conf, ...]
            val objConf = detection[4]
            if (objConf < confidenceThreshold) continue
            
            // 找最高置信度的类别
            var maxClass = 0
            var maxConf = 0f
            for (j in 5 until detection.size) {
                if (detection[j] > maxConf) {
                    maxConf = detection[j]
                    maxClass = j - 5
                }
            }
            
            // 只保留车辆类别
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
}
