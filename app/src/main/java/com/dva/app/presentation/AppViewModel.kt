package com.dva.app.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用级共享状态
 * 用于跨页面共享数据（如文件夹选择）
 */
data class AppState(
    val selectedFolderUri: String? = null,
    val hasStoragePermission: Boolean = false,
    val appVersion: String = "1.3.0"
)

/**
 * 应用级 ViewModel
 * 使用 Hilt 单例，所有页面共享
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _appState = MutableStateFlow(AppState())
    val appState: StateFlow<AppState> = _appState.asStateFlow()
    
    init {
        // 从 DataStore 或 SharedPreferences 加载保存的状态
        loadSavedState()
    }
    
    /**
     * 设置选择的文件夹 URI
     */
    fun setSelectedFolderUri(uri: Uri) {
        _appState.value = _appState.value.copy(
            selectedFolderUri = uri.toString()
        )
        // TODO: 保存到 DataStore 持久化
    }
    
    /**
     * 获取选择的文件夹 URI
     */
    fun getSelectedFolderUri(): Uri? {
        return _appState.value.selectedFolderUri?.let { Uri.parse(it) }
    }
    
    /**
     * 设置存储权限状态
     */
    fun setStoragePermissionGranted(granted: Boolean) {
        _appState.value = _appState.value.copy(
            hasStoragePermission = granted
        )
    }
    
    /**
     * 清除权限提示
     */
    fun dismissPermissionHint() {
        if (_appState.value.hasStoragePermission) {
            // 权限已授予，不再显示提示
        }
    }
    
    /**
     * 加载保存的状态
     */
    private fun loadSavedState() {
        viewModelScope.launch {
            // TODO: 从 DataStore 加载保存的文件夹 URI
            // val savedUri = dataStore.getString("selected_folder_uri")
            // if (savedUri != null) {
            //     _appState.value = _appState.value.copy(selectedFolderUri = savedUri)
            // }
        }
    }
    
    /**
     * 保存状态
     */
    private fun saveState() {
        viewModelScope.launch {
            // TODO: 保存到 DataStore
        }
    }
}
