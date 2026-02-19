package com.rover.ai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.rover.ai.ai.model.ModelRegistry
import com.rover.ai.communication.ConnectionManager
import com.rover.ai.core.StateManager
import com.rover.ai.ui.navigation.RoverNavHost
import com.rover.ai.ui.theme.RoverTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var stateManager: StateManager
    
    @Inject
    lateinit var connectionManager: ConnectionManager
    
    @Inject
    lateinit var modelRegistry: ModelRegistry
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            RoverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RoverNavHost(
                        stateManager = stateManager,
                        connectionManager = connectionManager,
                        modelRegistry = modelRegistry
                    )
                }
            }
        }
    }
}
