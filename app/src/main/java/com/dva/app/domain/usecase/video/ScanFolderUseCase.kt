package com.dva.app.domain.usecase.video

import com.dva.app.domain.model.Video
import com.dva.app.domain.repository.VideoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫描文件夹用例
 */
@Singleton
class ScanFolderUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    /**
     * 扫描指定文件夹
     * @param folderPath 文件夹路径，null 表示扫描默认位置
     */
    suspend operator fun invoke(folderPath: String? = null): Result<List<Video>> {
        return videoRepository.scanFolder(folderPath ?: "")
    }
}
