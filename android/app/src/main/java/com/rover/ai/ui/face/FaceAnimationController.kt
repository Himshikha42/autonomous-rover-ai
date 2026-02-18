package com.rover.ai.ui.face

import androidx.compose.ui.geometry.Offset
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import com.rover.ai.emotion.EmotionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Orchestrates face animations including eye tracking, blinking, and emotion transitions.
 * 
 * This controller manages:
 * - Eye tracking direction (toward detected objects/humans)
 * - Blink timing and double-blink on surprise
 * - Squint effect when Gemma is inferring ("thinking")
 * - Drowsy effect when battery < 20%
 * - Smooth emotion transitions (morphing, not instant swap)
 * - Wake-up animation on app start
 * - Sleep animation after idle period
 * 
 * All animations are exposed as StateFlows for reactive Compose observation.
 * Uses coroutines for timed animations and autonomous behaviors.
 */
@Singleton
class FaceAnimationController @Inject constructor(
    private val stateManager: StateManager
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // ============================================================================
    // ANIMATION STATE FLOWS
    // ============================================================================
    
    private val _lookDirection = MutableStateFlow(Offset.Zero)
    val lookDirection: StateFlow<Offset> = _lookDirection.asStateFlow()
    
    private val _isBlinking = MutableStateFlow(false)
    val isBlinking: StateFlow<Boolean> = _isBlinking.asStateFlow()
    
    private val _eyeSquintAmount = MutableStateFlow(0.0f)
    val eyeSquintAmount: StateFlow<Float> = _eyeSquintAmount.asStateFlow()
    
    private val _isAwake = MutableStateFlow(true)
    val isAwake: StateFlow<Boolean> = _isAwake.asStateFlow()
    
    // ============================================================================
    // INTERNAL STATE
    // ============================================================================
    
    private var blinkJob: Job? = null
    private var trackingJob: Job? = null
    private var idleJob: Job? = null
    private var lastActivityTime = System.currentTimeMillis()
    
    private var previousEmotion: EmotionState = EmotionState.NEUTRAL
    
    init {
        Logger.i(Constants.TAG_UI, "FaceAnimationController initialized")
        
        // Start animation loops
        startBlinkingLoop()
        startIdleMonitor()
        observeStateChanges()
        
        // Perform wake-up animation
        performWakeUpAnimation()
    }
    
    // ============================================================================
    // PUBLIC METHODS
    // ============================================================================
    
    /**
     * Updates eye tracking to look at specific coordinates.
     * Coordinates are normalized -1.0 to 1.0 (center is 0,0)
     */
    fun lookAt(x: Float, y: Float) {
        val normalizedX = x.coerceIn(-1.0f, 1.0f)
        val normalizedY = y.coerceIn(-1.0f, 1.0f)
        
        scope.launch {
            // Smooth transition to new look direction
            val currentDirection = _lookDirection.value
            val steps = 10
            
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val newX = currentDirection.x + (normalizedX - currentDirection.x) * progress
                val newY = currentDirection.y + (normalizedY - currentDirection.y) * progress
                
                _lookDirection.value = Offset(newX, newY)
                delay(30L)
            }
        }
        
        resetIdleTimer()
    }
    
    /**
     * Resets look direction to center
     */
    fun lookCenter() {
        lookAt(0.0f, 0.0f)
    }
    
    /**
     * Triggers a single blink animation
     */
    fun blink() {
        scope.launch {
            _isBlinking.value = true
            delay(Constants.BLINK_DURATION_MS)
            _isBlinking.value = false
        }
        
        resetIdleTimer()
    }
    
    /**
     * Triggers a double blink (surprise reaction)
     */
    fun doubleBlink() {
        scope.launch {
            blink()
            delay(200L)
            blink()
        }
        
        resetIdleTimer()
    }
    
    /**
     * Sets eye squint amount (0.0 = normal, 1.0 = fully squinted)
     * Used during AI inference to show "thinking"
     */
    fun setSquint(amount: Float) {
        _eyeSquintAmount.value = amount.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Triggers random eye movement (wandering gaze)
     */
    fun randomLook() {
        val randomX = Random.nextFloat() * 0.6f - 0.3f // -0.3 to 0.3
        val randomY = Random.nextFloat() * 0.4f - 0.2f // -0.2 to 0.2
        lookAt(randomX, randomY)
    }
    
    /**
     * Resets idle timer (call when user interacts)
     */
    fun resetIdleTimer() {
        lastActivityTime = System.currentTimeMillis()
        if (!_isAwake.value) {
            performWakeUpAnimation()
        }
    }
    
    // ============================================================================
    // PRIVATE ANIMATION METHODS
    // ============================================================================
    
    /**
     * Starts automatic blinking loop
     */
    private fun startBlinkingLoop() {
        blinkJob?.cancel()
        blinkJob = scope.launch {
            while (isActive) {
                // Random blink interval between min and max
                val interval = Random.nextLong(
                    Constants.BLINK_INTERVAL_MIN_MS,
                    Constants.BLINK_INTERVAL_MAX_MS
                )
                delay(interval)
                
                // Only blink if awake
                if (_isAwake.value) {
                    blink()
                }
            }
        }
    }
    
    /**
     * Monitors idle time and triggers sleep animation
     */
    private fun startIdleMonitor() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                delay(10000L) // Check every 10 seconds
                
                val idleTime = System.currentTimeMillis() - lastActivityTime
                val idleThresholdMs = 120000L // 2 minutes
                
                if (idleTime > idleThresholdMs && _isAwake.value) {
                    performSleepAnimation()
                }
            }
        }
    }
    
    /**
     * Observes state changes to react to emotion and AI status
     */
    private fun observeStateChanges() {
        scope.launch {
            stateManager.state.collect { state ->
                // React to emotion changes
                if (state.emotionState != previousEmotion) {
                    onEmotionChanged(previousEmotion, state.emotionState)
                    previousEmotion = state.emotionState
                }
                
                // React to Gemma AI inference
                when (state.gemmaStatus) {
                    StateManager.AIStatus.INFERRING -> {
                        setSquint(0.3f)
                    }
                    else -> {
                        setSquint(0.0f)
                    }
                }
                
                // React to low battery
                if (state.batteryLevel < 20.0f) {
                    // Trigger drowsy behavior
                    triggerDrowsyBehavior()
                }
            }
        }
    }
    
    /**
     * Handles emotion transition animations
     */
    private fun onEmotionChanged(from: EmotionState, to: EmotionState) {
        Logger.d(Constants.TAG_UI, "Emotion transition: $from -> $to")
        
        // Trigger specific reactions based on emotion
        when (to) {
            EmotionState.HAPPY -> {
                doubleBlink()
                lookCenter()
            }
            EmotionState.ALERT -> {
                lookAt(Random.nextFloat() - 0.5f, -0.3f) // Look up/around
            }
            EmotionState.SCARED -> {
                // Wide eyes, rapid look movements
                scope.launch {
                    repeat(3) {
                        randomLook()
                        delay(300L)
                    }
                    lookCenter()
                }
            }
            EmotionState.LOVE -> {
                // Gentle blinking
                scope.launch {
                    repeat(2) {
                        blink()
                        delay(800L)
                    }
                }
            }
            EmotionState.CURIOUS -> {
                // Look around curiously
                scope.launch {
                    lookAt(0.4f, -0.2f)
                    delay(500L)
                    lookAt(-0.4f, -0.2f)
                    delay(500L)
                    lookCenter()
                }
            }
            EmotionState.CONFUSED -> {
                // Asymmetric look
                lookAt(0.3f, 0.2f)
            }
            EmotionState.SLEEPY -> {
                // Slow blink
                scope.launch {
                    _isBlinking.value = true
                    delay(400L)
                    _isBlinking.value = false
                }
            }
            else -> {
                lookCenter()
            }
        }
        
        resetIdleTimer()
    }
    
    /**
     * Wake-up animation sequence
     */
    private fun performWakeUpAnimation() {
        scope.launch {
            _isAwake.value = false
            _isBlinking.value = true // Eyes closed
            
            delay(500L)
            
            // Slowly open eyes
            _isBlinking.value = false
            _isAwake.value = true
            
            delay(300L)
            
            // Look around
            lookAt(0.3f, 0.0f)
            delay(400L)
            lookAt(-0.3f, 0.0f)
            delay(400L)
            lookCenter()
            
            Logger.i(Constants.TAG_UI, "Wake-up animation complete")
        }
    }
    
    /**
     * Sleep animation sequence
     */
    private fun performSleepAnimation() {
        scope.launch {
            Logger.i(Constants.TAG_UI, "Entering sleep mode")
            
            // Slow blink
            _isBlinking.value = true
            delay(300L)
            _isBlinking.value = false
            delay(500L)
            
            // Another slow blink
            _isBlinking.value = true
            delay(300L)
            _isBlinking.value = false
            delay(400L)
            
            // Close eyes
            _isBlinking.value = true
            _isAwake.value = false
        }
    }
    
    /**
     * Triggers drowsy behavior for low battery
     */
    private fun triggerDrowsyBehavior() {
        scope.launch {
            // Slow, heavy blink
            _isBlinking.value = true
            delay(400L)
            _isBlinking.value = false
        }
    }
    
    /**
     * Cleanup resources
     */
    fun dispose() {
        blinkJob?.cancel()
        trackingJob?.cancel()
        idleJob?.cancel()
        Logger.i(Constants.TAG_UI, "FaceAnimationController disposed")
    }
}
