package com.rover.ai

import android.app.Application
import com.rover.ai.ai.model.ModelRegistry
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RoverApplication : Application() {
    
    @Inject
    lateinit var modelRegistry: ModelRegistry
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // Scan for models on startup
        applicationScope.launch {
            modelRegistry.scanModels()
        }
    }
}
