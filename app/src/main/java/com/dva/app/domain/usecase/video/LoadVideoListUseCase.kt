package com.dva.app.domain.usecase.video

import com.dva.app.domain.model.Video
import com.dva.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加载视频列表用例
 */
@Singleton
class LoadVideoListUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    /**
     * 获取视频列表
     */
    suspend operator fun invoke(
        folderPath: String? = null,
        offset: Int = 0,
        limit: Int = 20
    ): Result<List<Video>> {
        return videoRepository.getVideoList(folderPath, offset, limit)
    }
    
    /**
     * 观察视频列表变化
     */
    fun observe(): Flow<List<Video>> {
        return videoRepository.observeVideoList()
    }
}
