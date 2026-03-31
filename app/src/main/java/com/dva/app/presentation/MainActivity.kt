package com.dva.app.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dva.app.presentation.home.HomeScreen
import com.dva.app.presentation.theme.DvaTheme
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
 * 主屏幕
 */
@Composable
fun MainScreen() {
    var currentRoute by remember { mutableStateOf("home") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DVA - 违章分析") },
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
            // 导航栏
            NavigationBar()
            
            // 页面内容
            when (currentRoute) {
                "home" -> HomeScreen()
                "analysis" -> AnalysisScreen()
                "report" -> ReportScreen()
                "settings" -> SettingsScreen()
            }
        }
    }
}

@Composable
fun NavigationBar() {
    NavigationBar {
        NavigationBarItem(
            icon = { Text("🏠") },
            label = { Text("首页") },
            selected = true,
            onClick = {}
        )
        NavigationBarItem(
            icon = { Text("🎬") },
            label = { Text("视频") },
            selected = false,
            onClick = {}
        )
        NavigationBarItem(
            icon = { Text("📊") },
            label = { Text("报告") },
            selected = false,
            onClick = {}
        )
        NavigationBarItem(
            icon = { Text("⚙️") },
            label = { Text("设置") },
            selected = false,
            onClick = {}
        )
    }
}

@Composable
fun AnalysisScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("视频分析页面 - 待实现")
    }
}

@Composable
fun ReportScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("报告页面 - 待实现")
    }
}

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("设置页面 - 待实现")
    }
}
