package com.dva.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToModels: () -> Unit = {}
) {
    val uiState by remember { mutableStateOf(SettingsUiState()) }
    
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
                    title = "存储设置",
                    subtitle = "管理截图和报告存储位置",
                    onClick = { }
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
            
            // 关于
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于 DVA",
                    subtitle = "版本 1.0.1",
                    onClick = { }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "源代码",
                    subtitle = "GitHub: birdnofoots/DVA",
                    onClick = { }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = "问题反馈",
                    subtitle = "在 GitHub 上提交 Issue",
                    onClick = { }
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

data class SettingsUiState(
    val version: String = "1.0.1"
)
