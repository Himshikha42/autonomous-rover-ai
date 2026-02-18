package com.rover.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun RoverNavHost(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            // Placeholder home screen
            // TODO: Implement home screen
        }
    }
}
