package com.rover.ai.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rover.ai.core.StateManager
import com.rover.ai.emotion.EmotionState

/**
 * Full-screen composable that displays the current emotion state as a large
 * expressive emoji and label on a dark background.
 *
 * Intended for use when the phone is mounted on the rover facing outward.
 * Tap anywhere to show an exit button that returns to normal mode.
 *
 * @param stateManager Global state manager for observing emotion state
 * @param onExitImmersive Callback invoked when the user requests to leave immersive mode
 */
@Composable
fun EmotionFaceScreen(
    stateManager: StateManager,
    onExitImmersive: () -> Unit
) {
    val roverState by stateManager.state.collectAsState()
    val emotion = roverState.emotionState
    var showExitButton by remember { mutableStateOf(false) }

    val (emoji, label) = getEmotionDisplay(emotion)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showExitButton = !showExitButton },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 120.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = label,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Exit overlay — shown when the user taps the screen
        if (showExitButton) {
            FloatingActionButton(
                onClick = onExitImmersive,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = Color.White.copy(alpha = 0.4f)
            ) {
                Icon(
                    imageVector = Icons.Default.FullscreenExit,
                    contentDescription = "Exit Immersive Mode",
                    tint = Color.White
                )
            }
        }
    }
}

private fun getEmotionDisplay(emotion: EmotionState): Pair<String, String> = when (emotion) {
    EmotionState.NEUTRAL -> "😐" to "NEUTRAL"
    EmotionState.HAPPY -> "😄" to "HAPPY"
    EmotionState.CURIOUS -> "🤔" to "CURIOUS"
    EmotionState.ALERT -> "⚠️" to "ALERT"
    EmotionState.SCARED -> "😨" to "SCARED"
    EmotionState.SLEEPY -> "😴" to "SLEEPY"
    EmotionState.LOVE -> "😍" to "LOVE"
    EmotionState.CONFUSED -> "😵" to "CONFUSED"
    EmotionState.THINKING -> "🧠" to "THINKING"
    EmotionState.LOW_BATTERY -> "🔋" to "LOW BATTERY"
}
