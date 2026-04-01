package com.dva.app.presentation.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dva.app.data.local.storage.DownloadState
import com.dva.app.data.local.storage.ModelDownloadManager

/**
 * 模型管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    onBack: () -> Unit = {},
    viewModel: ModelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    
    // 删除全部确认对话框
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("删除全部模型") },
            text = { Text("确定要删除所有已下载的模型吗？删除后需要重新下载。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllModels()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 说明文字
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "模型说明",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "AI 模型用于分析视频中的车辆、车道线和车牌。模型文件存储在应用私有目录，不会泄露到外部。首次使用前请下载所需模型。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // 模型列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.models) { model ->
                    ModelItem(
                        model = model,
                        downloadState = uiState.downloadStates[model.id] ?: DownloadState.Idle,
                        onDownload = { viewModel.downloadModel(model.id) },
                        onDelete = { viewModel.deleteModel(model.id) }
                    )
                }
            }
            
            // 底部操作
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已用空间: ${formatSize(uiState.totalSize)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.totalSize > 0
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("清理解载全部模型")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: com.dva.app.data.local.storage.ModelInfo,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 状态图标
                when (downloadState) {
                    is DownloadState.Idle -> {
                        if (model.isDownloaded) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已下载",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "未下载",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is DownloadState.Downloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    is DownloadState.Completed -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "完成",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    is DownloadState.Error -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "失败",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        // Handle other states: Idle, Downloading
                        Icon(
                            Icons.Default.Help,
                            contentDescription = "未知状态",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "大小: ${formatSize(model.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row {
                    when (downloadState) {
                        is DownloadState.Idle -> {
                            if (model.isDownloaded) {
                                TextButton(onClick = onDelete) {
                                    Text("删除")
                                }
                            } else {
                                Button(onClick = onDownload) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("下载")
                                }
                            }
                        }
                        is DownloadState.Downloading -> {
                            Text(
                                text = "${downloadState.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is DownloadState.Completed -> {
                            TextButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("删除")
                            }
                        }
                        is DownloadState.Error -> {
                            Button(onClick = onDownload) {
                                Text("重试")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = onDelete) {
                                Text("删除")
                            }
                        }
                        else -> {
                            // Handle Idle state
                            if (model.isDownloaded) {
                                TextButton(onClick = onDelete) {
                                    Text("删除")
                                }
                            } else {
                                Button(onClick = onDownload) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("下载")
                                }
                            }
                        }
                    }
                }
            }
            
            // 错误信息
            if (downloadState is DownloadState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
