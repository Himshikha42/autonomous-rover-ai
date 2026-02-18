package com.rover.ai.ui.face

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.rover.ai.emotion.EmotionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Renders floating background particles that respond to emotion state.
 * 
 * Particles change their:
 * - Color based on emotion (warm for happy, cool for sleepy, etc.)
 * - Speed based on emotion (fast for happy/alert, slow for sleepy)
 * - Size and opacity based on emotion intensity
 * 
 * Particles continuously move and wrap around screen edges for endless motion.
 * 
 * @param emotion Current emotion state affecting particle behavior
 * @param particleCount Number of particles to render (default 20)
 */
@Composable
fun ParticleBackground(
    emotion: EmotionState,
    particleCount: Int = 20
) {
    // Particle state
    var particles by remember {
        mutableStateOf(List(particleCount) { createRandomParticle() })
    }
    
    // Get emotion-based particle configuration
    val particleConfig = getParticleConfig(emotion)
    
    // Animate particles
    LaunchedEffect(emotion) {
        while (isActive) {
            particles = particles.map { particle ->
                updateParticle(particle, particleConfig)
            }
            delay(16L) // ~60 FPS
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            drawCircle(
                color = particle.color.copy(alpha = particle.alpha),
                radius = particle.size,
                center = Offset(
                    particle.x * size.width,
                    particle.y * size.height
                )
            )
        }
    }
}

/**
 * Particle data class
 */
private data class Particle(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val size: Float,
    val color: Color,
    val alpha: Float
)

/**
 * Particle configuration based on emotion
 */
private data class ParticleConfig(
    val baseColor: Color,
    val speedMultiplier: Float,
    val sizeRange: ClosedFloatingPointRange<Float>,
    val alphaRange: ClosedFloatingPointRange<Float>
)

/**
 * Creates a random particle with initial properties
 */
private fun createRandomParticle(): Particle {
    return Particle(
        x = Random.nextFloat(),
        y = Random.nextFloat(),
        velocityX = (Random.nextFloat() - 0.5f) * 0.002f,
        velocityY = (Random.nextFloat() - 0.5f) * 0.002f,
        size = Random.nextFloat() * 4f + 2f,
        color = Color.White,
        alpha = Random.nextFloat() * 0.3f + 0.1f
    )
}

/**
 * Updates particle position and wraps around edges
 */
private fun updateParticle(
    particle: Particle,
    config: ParticleConfig
): Particle {
    var newX = particle.x + particle.velocityX * config.speedMultiplier
    var newY = particle.y + particle.velocityY * config.speedMultiplier
    
    // Wrap around edges
    if (newX < 0f) newX += 1f
    if (newX > 1f) newX -= 1f
    if (newY < 0f) newY += 1f
    if (newY > 1f) newY -= 1f
    
    return particle.copy(
        x = newX,
        y = newY,
        color = config.baseColor,
        alpha = particle.alpha.coerceIn(config.alphaRange)
    )
}

/**
 * Returns particle configuration based on emotion
 */
private fun getParticleConfig(emotion: EmotionState): ParticleConfig {
    return when (emotion) {
        EmotionState.HAPPY -> ParticleConfig(
            baseColor = Color(0xFFFFD700), // Gold
            speedMultiplier = 2.0f,
            sizeRange = 3f..6f,
            alphaRange = 0.3f..0.6f
        )
        EmotionState.NEUTRAL -> ParticleConfig(
            baseColor = Color(0xFF6B8E99), // Cool grey-blue
            speedMultiplier = 1.0f,
            sizeRange = 2f..4f,
            alphaRange = 0.2f..0.4f
        )
        EmotionState.CURIOUS -> ParticleConfig(
            baseColor = Color(0xFF00CED1), // Turquoise
            speedMultiplier = 1.5f,
            sizeRange = 2f..5f,
            alphaRange = 0.3f..0.5f
        )
        EmotionState.ALERT -> ParticleConfig(
            baseColor = Color(0xFFFF6B00), // Orange
            speedMultiplier = 2.5f,
            sizeRange = 3f..5f,
            alphaRange = 0.4f..0.7f
        )
        EmotionState.SCARED -> ParticleConfig(
            baseColor = Color(0xFF9370DB), // Purple
            speedMultiplier = 3.0f,
            sizeRange = 2f..4f,
            alphaRange = 0.2f..0.5f
        )
        EmotionState.SLEEPY -> ParticleConfig(
            baseColor = Color(0xFF4A5F7F), // Dark blue
            speedMultiplier = 0.3f,
            sizeRange = 2f..3f,
            alphaRange = 0.1f..0.3f
        )
        EmotionState.LOVE -> ParticleConfig(
            baseColor = Color(0xFFFF69B4), // Hot pink
            speedMultiplier = 1.2f,
            sizeRange = 3f..6f,
            alphaRange = 0.3f..0.6f
        )
        EmotionState.CONFUSED -> ParticleConfig(
            baseColor = Color(0xFFA9A9A9), // Dark grey
            speedMultiplier = 0.8f,
            sizeRange = 2f..4f,
            alphaRange = 0.2f..0.4f
        )
        EmotionState.THINKING -> ParticleConfig(
            baseColor = Color(0xFF87CEEB), // Sky blue
            speedMultiplier = 0.6f,
            sizeRange = 2f..4f,
            alphaRange = 0.3f..0.5f
        )
        EmotionState.LOW_BATTERY -> ParticleConfig(
            baseColor = Color(0xFFFF4444), // Red
            speedMultiplier = 1.5f,
            sizeRange = 3f..5f,
            alphaRange = 0.4f..0.7f
        )
    }
}
