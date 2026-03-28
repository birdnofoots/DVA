package com.dva.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dva.app.domain.repository.ModelQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Detection Settings
            SettingsSection(title = "检测设置") {
                // Sensitivity
                SettingsItem(
                    icon = Icons.Default.Tune,
                    title = "检测灵敏度",
                    subtitle = "调整违章检测的敏感程度"
                ) {
                    Column(modifier = Modifier.width(200.dp)) {
                        Slider(
                            value = uiState.sensitivity,
                            onValueChange = { viewModel.setSensitivity(it) },
                            valueRange = 0.1f..0.9f,
                            steps = 7
                        )
                        Text(
                            text = when {
                                uiState.sensitivity < 0.3f -> "低（减少误报）"
                                uiState.sensitivity > 0.7f -> "高（可能增加误报）"
                                else -> "中"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Model Quality
                SettingsItem(
                    icon = Icons.Default.Memory,
                    title = "模型质量",
                    subtitle = "影响识别精度和处理速度"
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(
                                text = when (uiState.modelQuality) {
                                    ModelQuality.HIGH -> "高精度"
                                    ModelQuality.BALANCED -> "平衡"
                                    ModelQuality.FAST -> "快速"
                                }
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("高精度 (FP32)") },
                                onClick = {
                                    viewModel.setModelQuality(ModelQuality.HIGH)
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("平衡 (FP16)") },
                                onClick = {
                                    viewModel.setModelQuality(ModelQuality.BALANCED)
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("快速 (INT8)") },
                                onClick = {
                                    viewModel.setModelQuality(ModelQuality.FAST)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Divider()

            // Output Settings
            SettingsSection(title = "输出设置") {
                // Auto Export
                SettingsItem(
                    icon = Icons.Default.Save,
                    title = "分析完成后自动导出",
                    subtitle = "将报告自动保存到指定目录"
                ) {
                    Switch(
                        checked = uiState.autoExport,
                        onCheckedChange = { viewModel.setAutoExport(it) }
                    )
                }

                // Output Directory
                SettingsItem(
                    icon = Icons.Default.Folder,
                    title = "输出目录",
                    subtitle = uiState.outputDirectory
                ) {
                    TextButton(onClick = { /* Open folder picker */ }) {
                        Text("更改")
                    }
                }
            }

            Divider()

            // Model Management
            SettingsSection(title = "模型管理") {
                SettingsItem(
                    icon = Icons.Default.CloudDownload,
                    title = "下载/更新模型",
                    subtitle = "当前版本: v1.0.0"
                ) {
                    TextButton(onClick = { /* Check for updates */ }) {
                        Text("检查更新")
                    }
                }
            }

            Divider()

            // About
            SettingsSection(title = "关于") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "DVA",
                    subtitle = "版本 1.0.0"
                ) {}
                
                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "开源许可",
                    subtitle = "查看使用的开源库"
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
