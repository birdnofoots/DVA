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
 * 新版首页 - 带项目介绍
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHomeScreen(
    appViewModel: AppViewModel? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    onVideoSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    
    // 使用全局状态
    val videos = GlobalVideoState.videosList
    val isLoading = GlobalVideoState.isLoading
    
    // MediaStore 视频选择器（不需要 SAF 权限）
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 复制到本地缓存
            viewModel.copyVideoToCache(selectedUri) { localPath, error ->
                if (localPath != null) {
                    // 使用本地路径
                    val videoFile = VideoFile(
                        path = localPath,
                        name = selectedUri.lastPathSegment ?: "video",
                        durationMs = 0,
                        width = 0,
                        height = 0,
                        fps = 0f,
                        frameCount = 0,
                        fileSize = 0
                    )
                    GlobalVideoState.updateVideos(listOf(videoFile))
                    // 直接开始分析
                    onVideoSelected(localPath)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DVA - 违章抓拍识别") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 项目介绍
            item {
                ProjectInfoCard()
            }
            
            // 快速开始
            item {
                QuickStartCard(
                    onSelectVideo = { videoPickerLauncher.launch("video/*") }
                )
            }
            
            // 使用说明
            item {
                UsageGuideCard()
            }
            
            // 注意事项
            item {
                NotesCard()
            }
            
            // 最近视频（如果有）
            if (videos.isNotEmpty()) {
                item {
                    Text(
                        text = "最近视频",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                items(videos.take(3)) { video ->
                    RecentVideoItem(
                        video = video,
                        onAnalyze = { onVideoSelected(video.path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "DVA - 车载违章抓拍识别",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "版本 1.5.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "基于 AI 的车载视频分析应用，支持车辆检测、车道线识别和车牌识别。自动检测变道不打灯等违章行为，生成分析报告。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun QuickStartCard(onSelectVideo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "快速开始",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onSelectVideo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.VideoFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择视频开始分析")
            }
        }
    }
}

@Composable
private fun UsageGuideCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "使用说明",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val steps = listOf(
                "1. 下载所需 AI 模型（首次使用）",
                "2. 点击「选择视频」选取车载录像",
                "3. 等待 AI 分析完成",
                "4. 查看分析结果和报告"
            )
            
            steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "注意事项",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val notes = listOf(
                "• 视频建议保存到手机「Downloads」或「Documents」文件夹",
                "• 支持 MP4、MOV、AVI 等常见视频格式",
                "• 分析时间取决于视频长度和手机性能",
                "• 模型文件存储在应用私有目录，不会泄露"
            )
            
            notes.forEach { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RecentVideoItem(
    video: VideoFile,
    onAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.VideoFile,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDuration(video.durationMs),
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
