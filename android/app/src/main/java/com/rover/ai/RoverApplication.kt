package com.rover.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RoverApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
