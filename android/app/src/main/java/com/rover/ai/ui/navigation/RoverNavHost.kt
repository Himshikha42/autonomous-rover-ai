package com.rover.ai.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Model
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rover.ai.ai.model.ModelRegistry
import com.rover.ai.communication.ConnectionManager
import com.rover.ai.core.StateManager
import com.rover.ai.ui.dashboard.DebugDashboardScreen
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
    
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
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
            icon = { Icon(Icons.Default.Model, contentDescription = "Models") },
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
