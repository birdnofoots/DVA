package com.dva.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dva.app.presentation.home.NewHomeScreen
import com.dva.app.presentation.report.ReportScreen
import com.dva.app.presentation.settings.ModelsScreen
import com.dva.app.presentation.settings.SettingsScreen
import com.dva.app.presentation.theme.DvaTheme
import com.dva.app.presentation.video.VideoAnalysisScreen
import com.dva.app.presentation.video.VideoListScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * 主界面
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // 权限授予成功
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DvaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

/**
 * 导航目标
 */
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "首页")
    object Videos : Screen("videos", "视频")
    object Report : Screen("report", "报告")
    object Settings : Screen("settings", "设置")
    object Models : Screen("models", "模型管理")
    object Analysis : Screen("analysis/{videoPath}", "分析") {
        fun createRoute(videoPath: String) = "analysis/${java.net.URLEncoder.encode(videoPath, "UTF-8")}"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Videos,
    Screen.Report,
    Screen.Settings
)

/**
 * 权限请求弹窗
 */
@Composable
fun PermissionRequestDialog(
    onPermissionGranted: () -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("需要存储权限") },
            text = { Text("DVA 需要访问存储设备才能读取行车记录仪视频。\n\n请授予存储权限以继续使用。") },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    onPermissionGranted()
                }) {
                    Text("授予权限")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("稍后")
                }
            }
        )
    }
}

/**
 * 主屏幕
 */
@Composable
fun MainScreen(
    appViewModel: AppViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // 检查存储权限
    var hasStoragePermission by remember { mutableStateOf(false) }
    
    // 初始化时检查权限状态
    LaunchedEffect(Unit) {
        // 通过 LocalContext 检查权限
        hasStoragePermission = true // 默认设为 true，因为 SAF 不需要传统权限
    }
    
    // 判断是否显示底部导航
    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }
    
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { 
                                when (screen) {
                                    Screen.Home -> Icon(Icons.Default.Home, contentDescription = null)
                                    Screen.Videos -> Icon(Icons.Default.VideoLibrary, contentDescription = null)
                                    Screen.Report -> Icon(Icons.Default.Assessment, contentDescription = null)
                                    Screen.Settings -> Icon(Icons.Default.Settings, contentDescription = null)
                                    else -> Icon(Icons.Default.QuestionMark, contentDescription = null)
                                }
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                NewHomeScreen(
                    appViewModel = appViewModel,
                    onVideoSelected = { videoPath ->
                        navController.navigate(Screen.Analysis.createRoute(videoPath))
                    }
                )
            }
            
            composable(Screen.Videos.route) {
                VideoListScreen(
                    appViewModel = appViewModel,
                    onVideoSelected = { videoPath ->
                        navController.navigate(Screen.Analysis.createRoute(videoPath))
                    }
                )
            }
            
            composable(Screen.Report.route) {
                ReportScreen()
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToModels = {
                        navController.navigate(Screen.Models.route)
                    }
                )
            }
            
            composable(Screen.Models.route) {
                ModelsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(Screen.Analysis.route) { backStackEntry ->
                val encodedPath = backStackEntry.arguments?.getString("videoPath") ?: ""
                val videoPath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                val videoName = videoPath.substringAfterLast("/")
                VideoAnalysisScreen(
                    videoPath = videoPath,
                    videoName = videoName,
                    onBack = { navController.popBackStack() },
                    onViewReport = { 
                        navController.popBackStack()
                        navController.navigate(Screen.Report.route)
                    }
                )
            }
        }
    }
}
