package com.rover.ai.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rover.ai.core.Logger
import com.rover.ai.core.Constants
import com.rover.ai.core.StateManager
import com.rover.ai.emotion.EmotionState

/**
 * Full-screen Composable that renders the animated rover face.
 * 
 * This is the main UI screen showing the rover's emotional state through:
 * - Animated eyes with emotion-based shapes and pupil tracking
 * - Animated mouth that morphs between emotion states
 * - Background particles that change color and speed with emotion
 * - Dynamic background color that shifts based on current emotion
 * 
 * The screen automatically enters immersive mode to hide system bars
 * and provide a full-screen face experience.
 * 
 * @param stateManager Global state manager for observing emotion state
 * @param faceAnimationController Controller that orchestrates face animations
 */
@Composable
fun RoverFaceScreen(
    stateManager: StateManager,
    faceAnimationController: FaceAnimationController
) {
    val roverState by stateManager.state.collectAsState()
    val lookDirection by faceAnimationController.lookDirection.collectAsState()
    val isBlinking by faceAnimationController.isBlinking.collectAsState()
    val currentEmotion = roverState.emotionState
    
    val view = LocalView.current
    
    // Enable immersive mode (hide system bars)
    LaunchedEffect(Unit) {
        val window = view.context.let { context ->
            (context as? android.app.Activity)?.window
        }
        
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            val controller = WindowInsetsControllerCompat(it, view)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        Logger.i(Constants.TAG_UI, "RoverFaceScreen entered immersive mode")
    }
    
    // Get background color based on emotion
    val backgroundColor = getEmotionBackgroundColor(currentEmotion)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Particle system in background
        ParticleBackground(
            emotion = currentEmotion,
            particleCount = Constants.PARTICLE_COUNT
        )
        
        // Face elements (eyes and mouth)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Draw eyes
            DrawEyes(
                emotion = currentEmotion,
                lookDirection = lookDirection,
                isBlinking = isBlinking
            )
            
            // Draw mouth
            DrawMouth(
                emotion = currentEmotion
            )
        }
    }
}

/**
 * Maps emotion states to background colors.
 * Colors are carefully chosen to enhance the emotional expression.
 */
private fun getEmotionBackgroundColor(emotion: EmotionState): Color {
    return when (emotion) {
        EmotionState.NEUTRAL -> Color(0xFF1A1A2E)      // Dark blue-grey
        EmotionState.HAPPY -> Color(0xFF2A4858)        // Warm dark teal
        EmotionState.CURIOUS -> Color(0xFF1F2937)      // Dark grey-blue
        EmotionState.ALERT -> Color(0xFF3A2618)        // Dark orange-brown
        EmotionState.SCARED -> Color(0xFF2D1B2E)       // Dark purple
        EmotionState.SLEEPY -> Color(0xFF1C1C2E)       // Very dark blue
        EmotionState.LOVE -> Color(0xFF3A1F2E)         // Dark pink-purple
        EmotionState.CONFUSED -> Color(0xFF2A2A3A)     // Medium dark grey
        EmotionState.THINKING -> Color(0xFF1E2A3A)     // Dark blue-grey
        EmotionState.LOW_BATTERY -> Color(0xFF3A1818)  // Dark red
    }
}
