package com.dva.app.presentation.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.dva.app.domain.model.ScreenshotType
import com.dva.app.presentation.theme.ViolationLaneChange

@Suppress("UNUSED_VARIABLE")
@Composable
fun ViolationDetailScreen(
    violationId: String,
    onNavigateBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(violationId) {
        viewModel.loadViolation(violationId)
    }

    val violation = uiState.selectedViolation

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("违章详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading || violation == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Screenshots pager
                    if (violation.screenshots.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { violation.screenshots.size })

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            val screenshot = violation.screenshots[page]
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = screenshot.filePath,
                                    contentDescription = "Screenshot ${page + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                
                                // Page indicator
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(16.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = screenshot.type.displayName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // Tab row for page indicators
                        TabRow(
                            selectedTabIndex = pagerState.currentPage
                        ) {
                            violation.screenshots.forEachIndexed { index, screenshot ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { /* Pager handles this */ },
                                    text = { Text(screenshot.type.displayName) }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无截图")
                        }
                    }

                    // Info section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Violation type
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ViolationLaneChange
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = violation.type.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            // Time
                            InfoRow(
                                icon = Icons.Default.Schedule,
                                label = "违章时间",
                                value = violation.formattedTimestamp
                            )

                            Spacer(Modifier.height(8.dp))

                            // License plate
                            violation.licensePlate?.let { plate ->
                                InfoRow(
                                    icon = Icons.Default.DirectionsCar,
                                    label = "车牌号",
                                    value = plate.number
                                )
                                Spacer(Modifier.height(8.dp))
                                InfoRow(
                                    icon = Icons.Default.Verified,
                                    label = "识别置信度",
                                    value = "${(plate.confidence * 100).toInt()}%"
                                )
                            } ?: run {
                                InfoRow(
                                    icon = Icons.Default.Error,
                                    label = "车牌",
                                    value = "未能识别"
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            // Confidence
                            Text(
                                text = "检测置信度: ${(violation.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Confirmation status
                            if (violation.isConfirmed) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "已人工确认",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
