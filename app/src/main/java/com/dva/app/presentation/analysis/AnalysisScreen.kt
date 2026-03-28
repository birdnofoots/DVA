package com.dva.app.presentation.analysis

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    videoId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReport: (String) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    LaunchedEffect(uiState.task?.status) {
        if (uiState.task?.isCompleted == true && uiState.task?.id != null) {
            onNavigateToReport(uiState.task!!.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频分析") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.cancelAnalysis() }) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                uiState.error != null -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.error ?: "分析失败",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onNavigateBack) {
                        Text("返回")
                    }
                }
                uiState.task == null -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("正在加载...")
                }
                else -> {
                    AnalysisContent(
                        uiState = uiState,
                        onPause = { viewModel.pauseAnalysis() },
                        onResume = { viewModel.resumeAnalysis() },
                        onCancel = { 
                            viewModel.cancelAnalysis()
                            onNavigateBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisContent(
    uiState: AnalysisUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val task = uiState.task ?: return
    val progress = uiState.progress

    // Animated progress indicator
    val infiniteTransition = rememberInfiniteTransition(label = "analysis")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Status icon
    Icon(
        imageVector = when {
            task.isPaused -> Icons.Default.Pause
            task.isCompleted -> Icons.Default.CheckCircle
            task.isFailed -> Icons.Default.Error
            else -> Icons.Default.Analytics
        },
        contentDescription = null,
        modifier = Modifier
            .size(80.dp)
            .then(if (!task.isCompleted && !task.isFailed) Modifier.rotate(rotation) else Modifier),
        tint = when {
            task.isCompleted -> MaterialTheme.colorScheme.primary
            task.isFailed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
    )

    Spacer(Modifier.height(24.dp))

    // Status text
    Text(
        text = when {
            task.isPaused -> "已暂停"
            task.isCompleted -> "分析完成"
            task.isFailed -> "分析失败"
            else -> "分析中..."
        },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(16.dp))

    // Progress bar
    if (!task.isCompleted && !task.isFailed) {
        LinearProgressIndicator(
            progress = task.progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${task.progressPercent}%",
            style = MaterialTheme.typography.bodyLarge
        )
    }

    Spacer(Modifier.height(16.dp))

    // Progress details
    if (progress != null) {
        Text(
            text = "时间: ${progress.formattedTime} / ${uiState.totalTimeFormatted ?: "--:--:--"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "帧: ${progress.currentFrame} / ${progress.totalFrames}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "处理速度: ${String.format("%.1f", progress.fps)} fps",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))

    // Violations found
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "发现 ${task.violationsFound} 个违章",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    Spacer(Modifier.height(32.dp))

    // Action buttons
    if (!task.isCompleted && !task.isFailed) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (task.isPaused) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("继续")
                }
            } else {
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("暂停")
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("取消")
            }
        }
    }
}
