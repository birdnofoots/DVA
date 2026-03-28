package com.dva.app.infrastructure.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * 视频解码器 - 使用 MediaCodec 进行硬件加速解码
 */
@Singleton
class VideoDecoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TIMEOUT_US = 10000L
        private const val DEFAULT_FRAME_RATE = 30
    }

    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private var videoTrackIndex = -1
    private var isConfigured = false
    private var inputSurface: Surface? = null

    private var videoWidth = 0
    private var videoHeight = 0
    private var durationUs = 0L
    private var frameRate: Float = DEFAULT_FRAME_RATE.toFloat()

    /**
     * 视频元数据
     */
    data class VideoInfo(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val frameRate: Float,
        val totalFrames: Long,
        val mimeType: String
    )

    /**
     * 解码状态
     */
    sealed class DecoderState {
        object Idle : DecoderState()
        object Configured : DecoderState()
        object Running : DecoderState()
        object EndOfStream : DecoderState()
        data class Error(val message: String) : DecoderState()
    }

    /**
     * 打开视频文件
     */
    suspend fun open(filePath: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            release()
            
            extractor = MediaExtractor().apply {
                setDataSource(filePath)
            }
            
            val ext = extractor!!
            
            // 查找视频轨道
            for (i in 0 until ext.trackCount) {
                val format = ext.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    ext.selectTrack(i)
                    
                    videoWidth = format.getInteger(MediaFormat.KEY_WIDTH)
                    videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
                    durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    
                    frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    } else {
                        DEFAULT_FRAME_RATE.toFloat()
                    }
                    
                    val mimeType = format.getString(MediaFormat.KEY_MIME) ?: "video/*"
                    
                    isConfigured = true
                    
                    val info = VideoInfo(
                        width = videoWidth,
                        height = videoHeight,
                        durationMs = durationUs / 1000,
                        frameRate = frameRate,
                        totalFrames = ((durationUs / 1_000_000.0) * frameRate).toLong(),
                        mimeType = mimeType
                    )
                    
                    return@withContext Result.success(info)
                }
            }
            
            Result.failure(Exception("No video track found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 配置解码器（输出到 Surface）
     */
    fun configureDecoder(surface: Surface): Result<Unit> {
        if (!isConfigured || extractor == null) {
            return Result.failure(Exception("Video not opened"))
        }
        
        return try {
            val format = extractor!!.getTrackFormat(videoTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, surface, null, 0)
            }
            
            inputSurface = surface
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 配置解码器（输出到回调）
     */
    fun configureDecoder(callback: FrameCallback): Result<Unit> {
        if (!isConfigured || extractor == null) {
            return Result.failure(Exception("Video not opened"))
        }
        
        return try {
            val format = extractor!!.getTrackFormat(videoTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        val buffer = codec.getInputBuffer(index)
                        if (buffer != null) {
                            val sampleSize = extractor?.readSampleData(buffer, 0) ?: -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                val presentationTimeUs = extractor?.sampleTime ?: 0
                                codec.queueInputBuffer(index, 0, sampleSize, presentationTimeUs, 0)
                                extractor?.advance()
                            }
                        }
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            callback.onEndOfStream()
                        } else {
                            val buffer = codec.getOutputBuffer(index)
                            if (buffer != null) {
                                callback.onFrameAvailable(buffer, info.presentationTimeUs)
                            }
                        }
                        codec.releaseOutputBuffer(index, info.presentationTimeUs * 1000)
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        callback.onError(e.message ?: "Codec error")
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        callback.onFormatChanged(format)
                    }
                })
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 开始解码
     */
    suspend fun start(): Result<Unit> = withContext(Dispatchers.Default) {
        return@withContext try {
            decoder?.start()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解码到指定时间
     */
    suspend fun seekTo(timeMs: Long) = withContext(Dispatchers.IO) {
        extractor?.seekTo(timeMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    }

    /**
     * 解码一帧
     */
    suspend fun decodeFrame(): Result<Boolean> = withContext(Dispatchers.Default) {
        val dec = decoder ?: return@withContext Result.failure(Exception("Decoder not initialized"))
        val ext = extractor ?: return@withContext Result.failure(Exception("Extractor not initialized"))
        
        try {
            // 输入
            val inputIndex = dec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    val sampleSize = ext.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        dec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        return@withContext Result.success(false) // End of stream
                    } else {
                        val presentationTimeUs = ext.sampleTime
                        dec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                        ext.advance()
                    }
                }
            }
            
            // 输出
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = dec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 格式变化，忽略
                }
                outputIndex >= 0 -> {
                    dec.releaseOutputBuffer(outputIndex, bufferInfo.presentationTimeUs * 1000)
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return@withContext Result.success(false)
                    }
                }
            }
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取当前播放时间（微秒）
     */
    fun getCurrentPositionUs(): Long {
        return extractor?.sampleTime ?: 0
    }

    /**
     * 获取视频信息
     */
    fun getVideoInfo(): VideoInfo? {
        if (!isConfigured) return null
        return VideoInfo(
            width = videoWidth,
            height = videoHeight,
            durationMs = durationUs / 1000,
            frameRate = frameRate,
            totalFrames = ((durationUs / 1_000_000.0) * frameRate).toLong(),
            mimeType = ""
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
        } catch (e: Exception) {
            // Ignore
        }
        
        try {
            extractor?.release()
            extractor = null
        } catch (e: Exception) {
            // Ignore
        }
        
        isConfigured = false
        videoTrackIndex = -1
        inputSurface = null
    }

    /**
     * 帧回调接口
     */
    interface FrameCallback {
        fun onFrameAvailable(buffer: ByteBuffer, presentationTimeUs: Long)
        fun onFormatChanged(format: MediaFormat)
        fun onEndOfStream()
        fun onError(message: String)
    }
}
