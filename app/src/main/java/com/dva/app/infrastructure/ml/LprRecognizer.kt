package com.dva.app.infrastructure.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.dva.app.domain.model.LicensePlate
import com.dva.app.domain.model.PlateColor
import com.dva.app.domain.model.PlateType
import com.dva.app.infrastructure.ml.models.LprConfig
import com.dva.app.infrastructure.ml.models.LprResult
import com.dva.app.infrastructure.ml.models.PlateColorResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 车牌识别器 (LPR - License Plate Recognition)
 * 使用轻量级模型识别中国大陆车牌
 */
@Singleton
class LprRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inferenceEngine: OnnxInferenceEngine
) {
    companion object {
        // 中国省份简称
        private val PROVINCES = listOf(
            "京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘",
            "皖", "鲁", "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋",
            "蒙", "陕", "吉", "闽", "贵", "粤", "青", "藏", "川", "宁", "琼"
        )
        
        // 车牌字符（第一位是省份简称）
        private val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        private val DIGITS = "0123456789"
        
        // 车牌颜色映射
        private val PLATE_COLOR_MAP = mapOf(
            "blue" to PlateColorResult.BLUE,
            "yellow" to PlateColorResult.YELLOW,
            "green" to PlateColorResult.GREEN,
            "white" to PlateColorResult.WHITE,
            "black" to PlateColorResult.BLACK
        )
        
        // 标准车牌长度（蓝牌/绿牌）
        private const val STANDARD_PLATE_LENGTH = 7
    }

    private var isWarmedUp = false
    private var modelPath: String? = null
    private val config = LprConfig()

    /**
     * 初始化识别器
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
        
        val dummyBitmap = Bitmap.createBitmap(94, 24, Bitmap.Config.ARGB_8888)
        try {
            recognize(dummyBitmap)
            isWarmedUp = true
        } catch (e: Exception) {
            // Ignore warmup errors
        } finally {
            dummyBitmap.recycle()
        }
    }

    /**
     * 识别车牌
     * @param plateImage 车牌区域图片（最好已经裁剪好）
     * @param boundingBox 车牌在原图中的位置（可选）
     */
    suspend fun recognize(
        plateImage: Bitmap,
        boundingBox: RectF? = null
    ): LprResult = withContext(Dispatchers.Default) {
        if (!inferenceEngine.isInitialized()) {
            return@withContext LprResult.empty()
        }

        try {
            // 预处理
            val preprocessed = preprocessPlateImage(plateImage)
            
            // 推理
            val inputBuffer = bitmapToByteBuffer(preprocessed)
            val inputShape = longArrayOf(1, 3, 94, 24) // LPRNet 输入尺寸
            
            val outputResult = inferenceEngine.run("input", inputBuffer, inputShape)
            
            preprocessed.recycle()
            
            if (outputResult.isFailure) {
                return@withContext LprResult.empty()
            }
            
            val outputs = outputResult.getOrNull() ?: return@withContext LprResult.empty()
            
            // 后处理：解码输出为车牌字符串
            val (plateNumber, confidence) = decodeOutput(outputs[0])
            
            // 解析车牌
            val parsed = parsePlateNumber(plateNumber)
            
            // 检测车牌颜色
            val plateColor = detectPlateColor(plateImage)
            
            LprResult(
                plateNumber = plateNumber,
                province = parsed.province,
                letter = parsed.letter,
                digits = parsed.digits,
                confidence = confidence,
                boundingBox = boundingBox,
                plateColor = plateColor
            )
        } catch (e: Exception) {
            LprResult.empty()
        }
    }

    /**
     * 批量识别
     */
    suspend fun recognizeBatch(
        images: List<Pair<Bitmap, RectF?>>
    ): List<LprResult> = withContext(Dispatchers.Default) {
        images.map { (bitmap, box) ->
            recognize(bitmap, box)
        }
    }

    /**
     * 预处理车牌图片
     */
    private fun preprocessPlateImage(bitmap: Bitmap): Bitmap {
        // 缩放到标准尺寸
        return Bitmap.createScaledBitmap(bitmap, 94, 24, true)
    }

    /**
     * Bitmap 转换为 ByteBuffer
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val buffer = ByteArray(width * height * 3)
        var index = 0
        
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // 归一化
            buffer[index++] = (r * 0.007843).toInt().toByte()
            buffer[index++] = (g * 0.007843).toInt().toByte()
            buffer[index++] = (b * 0.007843).toInt().toByte()
        }
        
        return buffer
    }

    /**
     * 解码模型输出
     * @return Pair(车牌号, 置信度)
     */
    private fun decodeOutput(
        output: ai.onnxruntime.OnnxTensor
    ): Pair<String, Float> {
        // LPRNet 输出处理
        // 输出形状通常是 [1, 68, 40] 或类似
        // 68 = 车牌字符类别数（包括省份、数字、字母）
        // 40 = 车牌最大长度
        
        try {
            val buffer = output.getFloatBuffer()
            val shape = output.info.shape
            
            if (shape.size < 3) {
                return "" to 0f
            }
            
            val numClasses = shape[1].toInt()
            val maxLen = shape[2].toInt()
            
            val chars = StringBuilder()
            var totalConf = 0f
            
            for (i in 0 until maxLen) {
                var maxProb = 0f
                var maxClass = 0
                
                // 查找每个位置概率最大的类别
                for (c in 0 until numClasses) {
                    val idx = c * maxLen + i
                    buffer.position(idx) // float32
                    val prob = buffer.get()
                    
                    if (prob > maxProb) {
                        maxProb = prob
                        maxClass = c
                    }
                }
                
                // 跳过空白字符（类别0通常是空白）
                if (maxClass > 0 && maxProb > 0.1f) {
                    val char = indexToChar(maxClass)
                    if (char.isNotEmpty()) {
                        chars.append(char)
                        totalConf += maxProb
                    }
                }
            }
            
            val confidence = if (chars.isNotEmpty()) {
                totalConf / chars.length
            } else 0f
            
            return chars.toString() to confidence
        } catch (e: Exception) {
            return "" to 0f
        }
    }

    /**
     * 类别索引转换为字符
     */
    private fun indexToChar(index: Int): String {
        // LPRNet 类别映射
        // 0: blank
        // 1-34: 省份简称
        // 35-60: 字母
        // 61-70: 数字
        
        return when {
            index == 0 -> ""
            index <= 34 -> PROVINCES.getOrNull(index - 1) ?: ""
            index <= 60 -> LETTERS.getOrNull(index - 35)?.toString() ?: ""
            index <= 70 -> DIGITS.getOrNull(index - 61)?.toString() ?: ""
            else -> ""
        }
    }

    /**
     * 解析车牌号
     */
    private fun parsePlateNumber(plateNumber: String): ParsedPlate {
        if (plateNumber.isEmpty()) {
            return ParsedPlate("", "", "")
        }
        
        val province = if (PROVINCES.contains(plateNumber.first().toString())) {
            plateNumber.first().toString()
        } else ""
        
        val remaining = if (province.isNotEmpty()) {
            plateNumber.drop(1)
        } else plateNumber
        
        // 分割字母和数字
        val letterEnd = remaining.indexOfFirst { it.isDigit() }
        val letter = if (letterEnd > 0) {
            remaining.substring(0, letterEnd)
        } else ""
        
        val digits = if (letterEnd > 0 && letterEnd < remaining.length) {
            remaining.substring(letterEnd)
        } else ""
        
        return ParsedPlate(province, letter, digits)
    }

    /**
     * 检测车牌颜色
     */
    private fun detectPlateColor(bitmap: Bitmap): PlateColorResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        
        // 采样中心区域
        val sampleWidth = width / 4
        val sampleHeight = height / 4
        val startX = sampleWidth
        val startY = sampleHeight
        val endX = width - sampleWidth
        val endY = height - sampleHeight
        
        var count = 0
        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = pixels[y * width + x]
                totalR += (pixel shr 16) and 0xFF
                totalG += (pixel shr 8) and 0xFF
                totalB += pixel and 0xFF
                count++
            }
        }
        
        if (count == 0) return PlateColorResult.UNKNOWN
        
        val avgR = (totalR / count).toInt()
        val avgG = (totalG / count).toInt()
        val avgB = (totalB / count).toInt()
        
        // 根据颜色判断车牌类型
        return when {
            // 蓝色车牌: R较低, G较低, B较高
            avgB > avgR + 30 && avgB > avgG + 30 -> PlateColorResult.BLUE
            // 绿色车牌: G最高
            avgG > avgR + 20 && avgG > avgB + 20 -> PlateColorResult.GREEN
            // 黄色车牌: R和G较高，B较低
            avgR > 150 && avgG > 150 && avgB < 100 -> PlateColorResult.YELLOW
            // 白色车牌: RGB都很高
            avgR > 200 && avgG > 200 && avgB > 200 -> PlateColorResult.WHITE
            // 黑色车牌: RGB都很低
            avgR < 50 && avgG < 50 && avgB < 50 -> PlateColorResult.BLACK
            else -> PlateColorResult.UNKNOWN
        }
    }

    /**
     * 转换为领域模型
     */
    fun toLicensePlate(result: LprResult): LicensePlate? {
        if (result.plateNumber.length < STANDARD_PLATE_LENGTH) {
            return null
        }
        
        if (result.confidence < config.confidenceThreshold) {
            return null
        }
        
        return LicensePlate(
            number = result.province + result.letter + result.digits,
            province = result.province,
            letter = result.letter,
            digits = result.digits,
            plateType = when (result.plateColor) {
                PlateColorResult.GREEN -> PlateType.GREEN
                PlateColorResult.YELLOW -> PlateType.YELLOW
                PlateColorResult.WHITE -> PlateType.WHITE
                PlateColorResult.BLACK -> PlateType.BLACK
                else -> PlateType.BLUE
            },
            confidence = result.confidence,
            boundingBox = result.boundingBox,
            color = when (result.plateColor) {
                PlateColorResult.BLUE -> PlateColor.BLUE
                PlateColorResult.YELLOW -> PlateColor.YELLOW
                PlateColorResult.WHITE -> PlateColor.WHITE
                PlateColorResult.BLACK -> PlateColor.BLACK
                PlateColorResult.GREEN -> PlateColor.GREEN
                PlateColorResult.UNKNOWN -> PlateColor.UNKNOWN
            }
        )
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

    /**
     * 解析结果
     */
    private data class ParsedPlate(
        val province: String,
        val letter: String,
        val digits: String
    )
}
