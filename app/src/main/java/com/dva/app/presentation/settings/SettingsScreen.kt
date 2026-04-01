package com.dva.app.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToModels: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showClearCacheDialog by remember { mutableStateOf(false) }
    
    // 截图保存路径选择器
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
            // TODO: 保存选择的 URI 到 DataStore
        }
    }
    
    // 清理缓存确认对话框
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存") },
            text = { Text("确定要清理所有缓存视频吗？这将删除所有临时保存的视频文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ML 模型管理
            item {
                SettingsItem(
                    icon = Icons.Default.ModelTraining,
                    title = "ML 模型管理",
                    subtitle = "下载和管理车辆检测、车道线识别等模型",
                    onClick = onNavigateToModels
                )
            }
            
            // 存储设置
            item {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = "截图保存路径",
                    subtitle = "选择保存截图的文件夹",
                    onClick = { folderPicker.launch(null) }
                )
            }
            
            // 清理缓存
            item {
                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "清理缓存",
                    subtitle = if (uiState.cacheSize > 0) {
                        "缓存大小: ${viewModel.formatCacheSize(uiState.cacheSize)}"
                    } else {
                        "无缓存视频"
                    },
                    onClick = { 
                        if (uiState.cacheSize > 0) {
                            showClearCacheDialog = true
                        }
                    }
                )
            }
            
            // 视频分析设置
            item {
                SettingsItem(
                    icon = Icons.Default.VideoSettings,
                    title = "视频分析",
                    subtitle = "帧率、检测灵敏度等设置",
                    onClick = { }
                )
            }
            
            // 权限管理
            item {
                SettingsItem(
                    icon = Icons.Default.Security,
                    title = "应用权限",
                    subtitle = "管理应用权限设置",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            
            // 关于
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于 DVA",
                    subtitle = "版本 1.4.0",
                    onClick = { }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "源代码",
                    subtitle = "GitHub: birdnofoots/DVA",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/birdnofoots/DVA"))
                        context.startActivity(intent)
                    }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = "问题反馈",
                    subtitle = "在 GitHub 上提交 Issue",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/birdnofoots/DVA/issues"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
