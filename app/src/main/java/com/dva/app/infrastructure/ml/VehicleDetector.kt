package com.dva.app.infrastructure.ml

import android.content.Context
import com.dva.app.domain.model.VehicleDetection
import com.dva.app.domain.model.BoundingBox
import org.onnxruntime.OnnxRuntime
import org.onnxruntime.OrtEnvironment
import org.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * 车辆检测器接口
 */
interface VehicleDetector {
    suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<VehicleDetection>
}

/**
 * YOLOv8 车辆检测器实现
 */
class YoloVehicleDetector(
    private val context: Context,
    modelPath: String
) : VehicleDetector {
    
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    
    // COCO 类别中与车辆相关的类别
    private val vehicleClasses = setOf(2, 3, 5, 7, 8) // car, motorcycle, bus, truck, train
    
    init {
        val sessionOptions = OrtSession.SessionOptions()
        session = environment.createSession(modelPath, sessionOptions)
    }
    
    override suspend fun detect(frames: List<Pair<Int, ByteArray>>): List<VehicleDetection> {
        val results = mutableListOf<VehicleDetection>()
        
        for ((frameIndex, frameData) in frames) {
            val detections = detectFrame(frameIndex, frameData)
            results.addAll(detections)
        }
        
        return results
    }
    
    private fun detectFrame(frameIndex: Int, frameData: ByteArray): List<VehicleDetection> {
        // 预处理：缩放到 640x640，归一化
        val inputTensor = preprocessFrame(frameData, 640, 640)
        
        // 运行推理
        val inputName = session.inputNames.first()
        val outputName = session.outputNames.first()
        
        val inputs = mapOf(inputName to inputTensor)
        val outputs = session.run(inputs)
        val outputTensor = outputs.first().value as Array<Array<FloatArray>>
        
        // 后处理：解析检测结果
        return postprocess(outputTensor, frameIndex)
    }
    
    private fun preprocessFrame(frameData: ByteArray, width: Int, height: Int): org.onnxruntime.OnnxTensor {
        // 注意：实际实现需要正确的图像预处理（RGB转换、归一化等）
        // 这里简化处理
        val buffer = ByteBuffer.allocateDirect(1 * 3 * width * height * 4)
        buffer.order(ByteOrder.nativeOrder())
        
        val floatBuffer = buffer.asFloatBuffer()
        for (i in 0 until 3 * width * height) {
            floatBuffer.put(i, 0f)
        }
        
        return org.onnxruntime.OnnxTensor.createTensor(environment, buffer, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }
    
    private fun postprocess(output: Array<Array<FloatArray>>, frameIndex: Int): List<VehicleDetection> {
        val detections = mutableListOf<VehicleDetection>()
        val numDetections = output.size
        
        // 简化处理：实际应该解析 YOLO 输出格式
        // YOLOv8 输出格式：[num_predictions, 5+num_classes]
        // 每行: [x, y, w, h, obj_conf, class1_conf, class2_conf, ...]
        
        for (i in 0 until minOf(numDetections, 100)) { // 限制检测数量
            val detection = output[i]
            if (detection.size < 6) continue
            
            val objConf = detection[4]
            if (objConf < 0.5f) continue // 置信度阈值
            
            // 找到最高置信度的类别
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
                        left = x - w / 2,
                        top = y - h / 2,
                        right = x + w / 2,
                        bottom = y + h / 2
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
    
    fun close() {
        session.close()
        environment.close()
    }
}
