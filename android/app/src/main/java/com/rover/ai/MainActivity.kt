package com.rover.ai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
                    SetupSystemUI()
                    RoverNavHost(
                        stateManager = stateManager,
                        connectionManager = connectionManager,
                        modelRegistry = modelRegistry
                    )
                }
            }
        }
    }
    
    @Composable
    private fun SetupSystemUI() {
        DisposableEffect(Unit) {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            windowInsetsController.apply {
                // Hide system bars for immersive experience
                hide(WindowInsetsCompat.Type.systemBars())
                
                // Configure behavior when swiping from edges
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            
            onDispose {
                // Restore system bars when activity is disposed
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
