package com.dva.app.presentation.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dva.app.domain.model.VideoFile
import com.dva.app.presentation.AppViewModel
import com.dva.app.presentation.GlobalVideoState

/**
 * 首页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appViewModel: AppViewModel? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    onVideoSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("dva_prefs", android.content.Context.MODE_PRIVATE)
    
    // 使用全局状态
    val selectedUri = GlobalVideoState.selectedFolderUri
    val videos = GlobalVideoState.videosList
    val isLoading = GlobalVideoState.isLoading
    
    // 首次启动引导状态
    var showFirstLaunchDialog by remember { mutableStateOf(false) }
    
    // 检查是否首次启动
    LaunchedEffect(Unit) {
        val hasSelectedWorkDir = sharedPrefs.getBoolean("has_selected_work_dir", false)
        if (!hasSelectedWorkDir && selectedUri == null) {
            showFirstLaunchDialog = true
        }
    }
    
    // 首次启动引导对话框
    if (showFirstLaunchDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("欢迎使用 DVA") },
            text = {
                Column {
                    Text("为了更好地访问您的视频文件，请选择一个工作目录。")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "推荐选择「Downloads/DVA」文件夹，这样您可以方便地管理要分析的视频。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showFirstLaunchDialog = false
                        folderPicker.launch(null)
                    }
                ) {
                    Text("选择工作目录")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFirstLaunchDialog = false }
                ) {
                    Text("稍后")
                }
            }
        )
    }
    
    // 选择文件夹后记住
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 授予持久化权限
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                           android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(selectedUri, takeFlags)
            } catch (e: SecurityException) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        selectedUri, 
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e2: SecurityException) {
                    // 权限获取失败
                }
            }
            
            // 记住选择
            sharedPrefs.edit().putBoolean("has_selected_work_dir", true).apply()
            
            // 保存到全局状态
            GlobalVideoState.setSelectedFolderUri(selectedUri)
            GlobalVideoState.updateLoading(true)
            
            // 触发扫描 - 使用 HomeViewModel
            viewModel.scanVideosFromUri(selectedUri)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 选择文件夹按钮
        Button(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("选择文件夹")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 视频数量
        if (selectedUri != null) {
            Text(
                text = "已选择: ${getFolderName(selectedUri)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Text(
            text = "${videos.size} 个视频",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 加载状态
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        // 视频列表
        if (videos.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videos) { video ->
                    VideoItem(
                        video = video,
                        onAnalyze = { onVideoSelected(video.path) }
                    )
                }
            }
        }
        
        // 空状态
        if (!isLoading && videos.isEmpty() && selectedUri != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "未找到视频",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "请确保文件夹中包含支持的视频格式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun getFolderName(uriString: String?): String {
    if (uriString == null) return ""
    return try {
        Uri.parse(uriString).lastPathSegment ?: uriString
    } catch (e: Exception) {
        uriString
    }
}

@Composable
fun VideoItem(
    video: VideoFile,
    onAnalyze: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VideoFile,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDuration(video.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${video.width}x${video.height} • ${formatFileSize(video.fileSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onAnalyze) {
                Icon(Icons.Default.PlayArrow, contentDescription = "分析")
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
