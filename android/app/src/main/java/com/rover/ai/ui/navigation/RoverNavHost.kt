package com.rover.ai.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rover.ai.ai.model.ModelRegistry
import com.rover.ai.communication.ConnectionManager
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import com.rover.ai.ui.dashboard.DebugDashboardScreen
import com.rover.ai.ui.face.EmotionFaceScreen
import com.rover.ai.ui.models.ModelStatusScreen

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object Models : Screen("models", "Model Status")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoverNavHost(
    modifier: Modifier = Modifier,
    stateManager: StateManager,
    connectionManager: ConnectionManager,
    modelRegistry: ModelRegistry
) {
    val navController = rememberNavController()
    var isImmersive by remember { mutableStateOf(false) }

    val view = LocalView.current
    LaunchedEffect(isImmersive) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            Logger.w(Constants.TAG_UI, "Cannot toggle immersive mode: view not attached to an Activity")
            return@LaunchedEffect
        }
        val controller = WindowCompat.getInsetsController(window, view)
        if (isImmersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (isImmersive) {
        EmotionFaceScreen(
            stateManager = stateManager,
            onExitImmersive = { isImmersive = false }
        )
    } else {
        Scaffold(
            bottomBar = {
                BottomNavigationBar(navController)
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { isImmersive = true }) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Enter Immersive / Face Display Mode"
                    )
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Dashboard.route,
                modifier = modifier.padding(paddingValues)
            ) {
                composable(Screen.Dashboard.route) {
                    DebugDashboardScreen(
                        stateManager = stateManager,
                        connectionManager = connectionManager
                    )
                }

                composable(Screen.Models.route) {
                    ModelStatusScreen(
                        modelRegistry = modelRegistry
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            selected = currentRoute == Screen.Dashboard.route,
            onClick = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
        
        NavigationBarItem(
            icon = { Icon(Icons.Default.Storage, contentDescription = "Models") },
            label = { Text("Models") },
            selected = currentRoute == Screen.Models.route,
            onClick = {
                navController.navigate(Screen.Models.route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}
