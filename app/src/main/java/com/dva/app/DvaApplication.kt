package com.dva.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * DVA 应用入口
 */
@HiltAndroidApp
class DvaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化操作
    }
}
