package com.dva.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DvaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any global configurations here
    }
}
