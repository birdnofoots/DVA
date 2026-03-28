package com.dva.app.presentation

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 单例管理器：用于 ViewModel 触发 MainActivity 的 SAF 文件夹选择器
 * ViewModel 通过 requestFolderPicker() 请求选择，选择结果通过 folderPickerResult 收集
 */
object FolderPickerManager {
    private val _folderPickerResult = MutableSharedFlow<Uri?>(replay = 0, extraBufferCapacity = 1)
    val folderPickerResult: SharedFlow<Uri?> = _folderPickerResult.asSharedFlow()

    private val _pickRequest = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val pickRequest: SharedFlow<Unit> = _pickRequest.asSharedFlow()

    fun requestPick() {
        _pickRequest.tryEmit(Unit)
    }

    fun onFolderPicked(uri: Uri?) {
        _folderPickerResult.tryEmit(uri)
    }
}
