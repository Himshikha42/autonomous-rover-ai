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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.rover.ai.core.Constants
import com.rover.ai.emotion.EmotionState
import kotlin.math.min

/**
 * Renders animated eyes with emotion-based shapes and pupil tracking.
 * 
 * Eyes adapt their shape, size, and behavior based on the current emotion:
 * - HAPPY: Arched upward (smiling eyes)
 * - ALERT: Angled sharp look
 * - SCARED: Wide open
 * - SLEEPY: Half-closed droopy
 * - LOVE: Heart-shaped
 * - CURIOUS: One eye bigger than the other
 * 
 * Pupils track the look direction smoothly and blink animations are supported.
 * 
 * @param emotion Current emotion state determining eye appearance
 * @param lookDirection Direction pupils should look (normalized -1 to 1)
 * @param isBlinking Whether eyes are currently blinking
 */
@Composable
fun DrawEyes(
    emotion: EmotionState,
    lookDirection: Offset,
    isBlinking: Boolean
) {
    // Animate eye openness for smooth blinking
    val eyeOpenness by animateFloatAsState(
        targetValue = if (isBlinking) 0.0f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "eyeOpenness"
    )
    
    // Animate pupil position for smooth tracking
    val pupilX by animateFloatAsState(
        targetValue = lookDirection.x,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pupilX"
    )
    
    val pupilY by animateFloatAsState(
        targetValue = lookDirection.y,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pupilY"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseRadius = min(size.width, size.height) * 0.12f
        val spacing = size.width * Constants.EYE_SPACING
        
        // Left eye center
        val leftEyeCenter = Offset(centerX - spacing, centerY)
        // Right eye center
        val rightEyeCenter = Offset(centerX + spacing, centerY)
        
        when (emotion) {
            EmotionState.HAPPY -> {
                drawHappyEye(leftEyeCenter, baseRadius, eyeOpenness, Offset(pupilX, pupilY))
                drawHappyEye(rightEyeCenter, baseRadius, eyeOpenness, Offset(pupilX, pupilY))
            }
            EmotionState.ALERT -> {
                drawAlertEye(leftEyeCenter, baseRadius, eyeOpenness, Offset(pupilX, pupilY))
                drawAlertEye(rightEyeCenter, baseRadius, eyeOpenness, Offset(pupilX, pupilY))
            }
            EmotionState.SCARED -> {
                drawScaredEye(leftEyeCenter, baseRadius * 1.3f, eyeOpenness, Offset(pupilX, pupilY))
                drawScaredEye(rightEyeCenter, baseRadius * 1.3f, eyeOpenness, Offset(pupilX, pupilY))
            }
            EmotionState.SLEEPY -> {
                val sleepyOpenness = eyeOpenness * 0.4f
                drawSleepyEye(leftEyeCenter, baseRadius, sleepyOpenness, Offset(pupilX, pupilY))
                drawSleepyEye(rightEyeCenter, baseRadius, sleepyOpenness, Offset(pupilX, pupilY))
            }
            EmotionState.LOVE -> {
                drawHeartEye(leftEyeCenter, baseRadius, eyeOpenness)
                drawHeartEye(rightEyeCenter, baseRadius, eyeOpenness)
            }
            EmotionState.CURIOUS -> {
                drawCircleEye(leftEyeCenter, baseRadius * 1.2f, eyeOpenness, Offset(pupilX, pupilY))
                drawCircleEye(rightEyeCenter, baseRadius * 0.9f, eyeOpenness, Offset(pupilX, pupilY))
            }
            else -> {
                // Default circular eyes for NEUTRAL, CONFUSED, THINKING, LOW_BATTERY
                drawCircleEye(leftEyeCenter, baseRadius, eyeOpenness, Offset(pupilX, pupilY))
                drawCircleEye(rightEyeCenter, baseRadius, eyeOpenness, Offset(pupilX, pupilY))
            }
        }
    }
}

/**
 * Draws a circular eye with pupil tracking
 */
private fun DrawScope.drawCircleEye(
    center: Offset,
    radius: Float,
    openness: Float,
    pupilOffset: Offset
) {
    val eyeColor = Color.White
    val pupilColor = Color.Black
    
    // Draw white of eye
    drawCircle(
        color = eyeColor,
        radius = radius * openness,
        center = center
    )
    
    // Draw pupil with tracking
    if (openness > 0.2f) {
        val pupilRadius = radius * Constants.EYE_PUPIL_RADIUS
        val maxPupilMove = radius * 0.3f
        val pupilCenter = Offset(
            center.x + pupilOffset.x * maxPupilMove,
            center.y + pupilOffset.y * maxPupilMove
        )
        
        drawCircle(
            color = pupilColor,
            radius = pupilRadius * openness,
            center = pupilCenter
        )
    }
}

/**
 * Draws happy eyes with upward arch
 */
private fun DrawScope.drawHappyEye(
    center: Offset,
    radius: Float,
    openness: Float,
    pupilOffset: Offset
) {
    val eyeColor = Color.White
    val pupilColor = Color.Black
    
    // Draw oval eye with upward curve
    val eyeHeight = radius * 2 * openness
    val eyeWidth = radius * 2
    
    drawOval(
        color = eyeColor,
        topLeft = Offset(center.x - radius, center.y - radius * openness),
        size = Size(eyeWidth, eyeHeight)
    )
    
    // Draw curved top (happy arch)
    val archPath = Path().apply {
        moveTo(center.x - radius, center.y - radius * 0.3f * openness)
        quadraticBezierTo(
            center.x, center.y - radius * 0.8f * openness,
            center.x + radius, center.y - radius * 0.3f * openness
        )
    }
    drawPath(archPath, eyeColor, style = Stroke(width = radius * 0.3f))
    
    // Draw pupil
    if (openness > 0.2f) {
        val pupilRadius = radius * Constants.EYE_PUPIL_RADIUS
        val maxPupilMove = radius * 0.3f
        val pupilCenter = Offset(
            center.x + pupilOffset.x * maxPupilMove,
            center.y + pupilOffset.y * maxPupilMove * 0.5f
        )
        
        drawCircle(
            color = pupilColor,
            radius = pupilRadius * openness,
            center = pupilCenter
        )
    }
}

/**
 * Draws alert eyes with angled sharp look
 */
private fun DrawScope.drawAlertEye(
    center: Offset,
    radius: Float,
    openness: Float,
    pupilOffset: Offset
) {
    val eyeColor = Color(0xFFFFFFFF)
    val pupilColor = Color(0xFFFF6B00) // Orange pupil for alert
    
    // Draw angled eye
    val eyePath = Path().apply {
        val angle = radius * 0.3f * openness
        moveTo(center.x - radius, center.y + angle)
        lineTo(center.x - radius * 0.3f, center.y - radius * openness)
        lineTo(center.x + radius * 0.3f, center.y - radius * openness)
        lineTo(center.x + radius, center.y + angle)
        lineTo(center.x, center.y + radius * openness)
        close()
    }
    drawPath(eyePath, eyeColor, style = Fill)
    
    // Draw focused pupil
    if (openness > 0.2f) {
        val pupilRadius = radius * Constants.EYE_PUPIL_RADIUS * 0.8f
        val maxPupilMove = radius * 0.2f
        val pupilCenter = Offset(
            center.x + pupilOffset.x * maxPupilMove,
            center.y + pupilOffset.y * maxPupilMove
        )
        
        drawCircle(
            color = pupilColor,
            radius = pupilRadius * openness,
            center = pupilCenter
        )
    }
}

/**
 * Draws scared eyes (wide open)
 */
private fun DrawScope.drawScaredEye(
    center: Offset,
    radius: Float,
    openness: Float,
    pupilOffset: Offset
) {
    val eyeColor = Color.White
    val pupilColor = Color.Black
    
    // Draw large circular eye
    drawCircle(
        color = eyeColor,
        radius = radius * openness,
        center = center
    )
    
    // Draw small shaking pupil
    if (openness > 0.2f) {
        val pupilRadius = radius * Constants.EYE_PUPIL_RADIUS * 0.7f
        val maxPupilMove = radius * 0.4f
        val pupilCenter = Offset(
            center.x + pupilOffset.x * maxPupilMove,
            center.y + pupilOffset.y * maxPupilMove
        )
        
        drawCircle(
            color = pupilColor,
            radius = pupilRadius * openness,
            center = pupilCenter
        )
    }
}

/**
 * Draws sleepy eyes (half-closed droopy)
 */
private fun DrawScope.drawSleepyEye(
    center: Offset,
    radius: Float,
    openness: Float,
    pupilOffset: Offset
) {
    val eyeColor = Color.White
    val pupilColor = Color.Black
    
    // Draw droopy half-circle
    val rect = Rect(
        left = center.x - radius,
        top = center.y - radius * openness,
        right = center.x + radius,
        bottom = center.y + radius
    )
    
    drawArc(
        color = eyeColor,
        startAngle = 0f,
        sweepAngle = 180f * openness,
        useCenter = true,
        topLeft = rect.topLeft,
        size = rect.size
    )
    
    // Small sleepy pupil
    if (openness > 0.1f) {
        val pupilRadius = radius * Constants.EYE_PUPIL_RADIUS * 0.6f
        drawCircle(
            color = pupilColor,
            radius = pupilRadius * openness,
            center = center
        )
    }
}

/**
 * Draws heart-shaped eyes
 */
private fun DrawScope.drawHeartEye(
    center: Offset,
    radius: Float,
    openness: Float
) {
    val heartColor = Color(0xFFFF69B4) // Pink hearts
    
    val heartPath = Path().apply {
        val scale = radius * openness
        moveTo(center.x, center.y + scale * 0.5f)
        
        // Left side of heart
        cubicTo(
            center.x - scale * 0.5f, center.y + scale * 0.3f,
            center.x - scale, center.y - scale * 0.3f,
            center.x, center.y - scale
        )
        
        // Right side of heart
        cubicTo(
            center.x + scale, center.y - scale * 0.3f,
            center.x + scale * 0.5f, center.y + scale * 0.3f,
            center.x, center.y + scale * 0.5f
        )
        close()
    }
    
    drawPath(heartPath, heartColor, style = Fill)
}
