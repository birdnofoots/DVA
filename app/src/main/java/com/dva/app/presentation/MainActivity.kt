package com.dva.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dva.app.presentation.home.HomeScreen
import com.dva.app.presentation.home.HomeViewModel
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
        if (!allGranted) {
            Toast.makeText(this, "需要存储权限才能使用", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查权限
        checkAndRequestPermissions()
        
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
    
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        val needsPermission = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needsPermission) {
            permissionLauncher.launch(permissions)
        }
    }
}

/**
 * 导航目标
 */
sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Home : Screen("home", "首页", { Icon(Icons.Default.Home, contentDescription = null) })
    object Videos : Screen("videos", "视频", { Icon(Icons.Default.VideoLibrary, contentDescription = null) })
    object Report : Screen("report", "报告", { Icon(Icons.Default.Assessment, contentDescription = null) })
    object Settings : Screen("settings", "设置", { Icon(Icons.Default.Settings, contentDescription = null) })
    object Models : Screen("models", "模型管理", { Icon(Icons.Default.ModelTraining, contentDescription = null) })
    object Analysis : Screen("analysis/{videoPath}", "分析", { Icon(Icons.Default.PlayArrow, contentDescription = null) }) {
        fun createRoute(videoPath: String) = "analysis/$videoPath"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Videos,
    Screen.Report,
    Screen.Settings
)

/**
 * 主屏幕
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
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
                            icon = screen.icon,
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
                HomeScreen(
                    onVideoSelected = { videoPath ->
                        navController.navigate(Screen.Analysis.createRoute(videoPath))
                    }
                )
            }
            
            composable(Screen.Videos.route) {
                VideoListScreen(
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
                val videoPath = backStackEntry.arguments?.getString("videoPath") ?: ""
                val videoName = videoPath.substringAfterLast("/")
                VideoAnalysisScreen(
                    videoPath = videoPath,
                    videoName = videoName,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
