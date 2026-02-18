package com.rover.ai.ui.face

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rover.ai.emotion.EmotionState
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders animated mouth that morphs between emotion-based shapes.
 * 
 * Mouth shapes adapt to express different emotions:
 * - HAPPY: Upward smile arc
 * - NEUTRAL: Straight line
 * - ALERT: Small O shape
 * - SCARED: Zigzag/wavy pattern
 * - SLEEPY: Small closed line
 * - LOVE: Gentle smile
 * - CONFUSED: Squiggly line
 * - THINKING: Horizontal dash
 * 
 * Transitions between mouth shapes are smooth and animated.
 * 
 * @param emotion Current emotion state determining mouth shape
 */
@Composable
fun DrawMouth(emotion: EmotionState) {
    // Animate mouth shape transitions
    val mouthCurvature by animateFloatAsState(
        targetValue = getMouthCurvature(emotion),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mouthCurvature"
    )
    
    val mouthWidth by animateFloatAsState(
        targetValue = getMouthWidth(emotion),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mouthWidth"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseSize = min(size.width, size.height) * 0.08f
        val mouthY = centerY + min(size.width, size.height) * 0.20f
        
        when (emotion) {
            EmotionState.HAPPY -> {
                drawSmileMouth(centerX, mouthY, baseSize * mouthWidth, mouthCurvature)
            }
            EmotionState.NEUTRAL -> {
                drawStraightMouth(centerX, mouthY, baseSize * mouthWidth)
            }
            EmotionState.ALERT -> {
                drawOMouth(centerX, mouthY, baseSize * mouthWidth)
            }
            EmotionState.SCARED -> {
                drawWavyMouth(centerX, mouthY, baseSize * mouthWidth)
            }
            EmotionState.SLEEPY -> {
                drawSleepyMouth(centerX, mouthY, baseSize * mouthWidth * 0.6f)
            }
            EmotionState.LOVE -> {
                drawSmileMouth(centerX, mouthY, baseSize * mouthWidth, mouthCurvature * 0.8f)
            }
            EmotionState.CONFUSED -> {
                drawSquigglyMouth(centerX, mouthY, baseSize * mouthWidth)
            }
            EmotionState.THINKING -> {
                drawThinkingMouth(centerX, mouthY, baseSize * mouthWidth * 0.7f)
            }
            EmotionState.LOW_BATTERY -> {
                drawStraightMouth(centerX, mouthY, baseSize * mouthWidth * 0.5f)
            }
        }
    }
}

/**
 * Returns target curvature value for mouth based on emotion
 */
private fun getMouthCurvature(emotion: EmotionState): Float {
    return when (emotion) {
        EmotionState.HAPPY -> 1.0f
        EmotionState.LOVE -> 0.8f
        EmotionState.NEUTRAL -> 0.0f
        EmotionState.ALERT -> 0.2f
        EmotionState.SCARED -> -0.5f
        EmotionState.SLEEPY -> -0.2f
        EmotionState.CONFUSED -> 0.0f
        EmotionState.THINKING -> 0.0f
        EmotionState.CURIOUS -> 0.3f
        EmotionState.LOW_BATTERY -> -0.3f
    }
}

/**
 * Returns target width multiplier for mouth based on emotion
 */
private fun getMouthWidth(emotion: EmotionState): Float {
    return when (emotion) {
        EmotionState.HAPPY -> 2.0f
        EmotionState.LOVE -> 1.5f
        EmotionState.NEUTRAL -> 1.5f
        EmotionState.ALERT -> 0.8f
        EmotionState.SCARED -> 1.2f
        EmotionState.SLEEPY -> 0.8f
        EmotionState.CONFUSED -> 1.3f
        EmotionState.THINKING -> 1.0f
        EmotionState.CURIOUS -> 1.2f
        EmotionState.LOW_BATTERY -> 1.0f
    }
}

/**
 * Draws a smile/frown mouth with adjustable curvature
 */
private fun DrawScope.drawSmileMouth(
    centerX: Float,
    centerY: Float,
    width: Float,
    curvature: Float
) {
    val mouthColor = Color.White
    val strokeWidth = 8f
    
    val path = Path().apply {
        moveTo(centerX - width, centerY)
        quadraticBezierTo(
            centerX, centerY + curvature * width * 0.5f,
            centerX + width, centerY
        )
    }
    
    drawPath(path, mouthColor, style = Stroke(width = strokeWidth))
}

/**
 * Draws a straight horizontal mouth line
 */
private fun DrawScope.drawStraightMouth(
    centerX: Float,
    centerY: Float,
    width: Float
) {
    val mouthColor = Color.White
    val strokeWidth = 6f
    
    drawLine(
        color = mouthColor,
        start = Offset(centerX - width, centerY),
        end = Offset(centerX + width, centerY),
        strokeWidth = strokeWidth
    )
}

/**
 * Draws an O-shaped mouth (alert/surprised)
 */
private fun DrawScope.drawOMouth(
    centerX: Float,
    centerY: Float,
    radius: Float
) {
    val mouthColor = Color.White
    val strokeWidth = 6f
    
    drawCircle(
        color = mouthColor,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Draws a wavy/zigzag mouth (scared)
 */
private fun DrawScope.drawWavyMouth(
    centerX: Float,
    centerY: Float,
    width: Float
) {
    val mouthColor = Color.White
    val strokeWidth = 6f
    val waveCount = 3
    
    val path = Path().apply {
        moveTo(centerX - width, centerY)
        
        for (i in 0..waveCount) {
            val x = centerX - width + (i.toFloat() / waveCount) * width * 2
            val y = if (i % 2 == 0) {
                centerY - width * 0.2f
            } else {
                centerY + width * 0.2f
            }
            lineTo(x, y)
        }
    }
    
    drawPath(path, mouthColor, style = Stroke(width = strokeWidth))
}

/**
 * Draws a small closed sleepy mouth
 */
private fun DrawScope.drawSleepyMouth(
    centerX: Float,
    centerY: Float,
    width: Float
) {
    val mouthColor = Color.White
    val strokeWidth = 5f
    
    val path = Path().apply {
        moveTo(centerX - width, centerY)
        quadraticBezierTo(
            centerX, centerY - width * 0.2f,
            centerX + width, centerY
        )
    }
    
    drawPath(path, mouthColor, style = Stroke(width = strokeWidth))
}

/**
 * Draws a squiggly confused mouth
 */
private fun DrawScope.drawSquigglyMouth(
    centerX: Float,
    centerY: Float,
    width: Float
) {
    val mouthColor = Color.White
    val strokeWidth = 6f
    val segments = 20
    
    val path = Path().apply {
        moveTo(centerX - width, centerY)
        
        for (i in 1..segments) {
            val progress = i.toFloat() / segments
            val x = centerX - width + progress * width * 2
            val y = centerY + sin(progress * 12) * width * 0.15f
            lineTo(x, y)
        }
    }
    
    drawPath(path, mouthColor, style = Stroke(width = strokeWidth))
}

/**
 * Draws a thinking mouth (short horizontal line)
 */
private fun DrawScope.drawThinkingMouth(
    centerX: Float,
    centerY: Float,
    width: Float
) {
    val mouthColor = Color.White
    val strokeWidth = 6f
    
    drawLine(
        color = mouthColor,
        start = Offset(centerX - width, centerY),
        end = Offset(centerX + width, centerY),
        strokeWidth = strokeWidth
    )
}
