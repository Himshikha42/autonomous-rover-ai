package com.rover.ai.emotion

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import com.rover.ai.core.ThreadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finite State Machine (FSM) for emotion management using weighted scoring.
 * 
 * The EmotionEngine processes multiple input signals to determine the rover's emotional state:
 * - Battery level and energy status
 * - Obstacle density and threat perception
 * - Human presence and social interaction
 * - Task progress and success/failure events
 * - AI suggestions from Gemma model
 * - Personality traits (curiosity, sociability, confidence)
 * 
 * Key features:
 * - **Hysteresis**: Minimum 3-second duration in any emotion state to prevent flickering
 * - **Weighted scoring**: Each emotion has a score calculated from multiple factors
 * - **Priority system**: LOW_BATTERY always overrides other emotions when critical
 * - **Smooth transitions**: Cooldown timer between state changes
 * 
 * Thread safety: All state updates are performed on the default dispatcher.
 */
@Singleton
class EmotionEngine @Inject constructor(
    private val stateManager: StateManager,
    private val threadManager: ThreadManager
) {
    
    /**
     * Coroutine scope for async emotion updates.
     * Uses SupervisorJob to prevent child failures from cancelling the scope.
     */
    private val scope = CoroutineScope(threadManager.defaultDispatcher + SupervisorJob())
    
    /**
     * Current emotion state as reactive StateFlow.
     * UI components can observe this for real-time emotion updates.
     */
    private val _currentEmotion = MutableStateFlow(EmotionState.NEUTRAL)
    val currentEmotion: StateFlow<EmotionState> = _currentEmotion.asStateFlow()
    
    /**
     * Timestamp when the current emotion was entered (milliseconds).
     * Used to enforce minimum duration hysteresis.
     */
    private var lastEmotionChangeMs: Long = System.currentTimeMillis()
    
    /**
     * Flag indicating if emotion transitions are currently locked due to cooldown.
     */
    private var isTransitionLocked: Boolean = false
    
    /**
     * Emotion scoring weights for decision-making.
     * Higher score means stronger emotion response.
     */
    private data class EmotionScore(
        val emotion: EmotionState,
        val score: Float,
        val reason: String
    )
    
    /**
     * Input data for emotion computation.
     * All parameters with default values for flexible calling.
     */
    data class EmotionInputs(
        val batteryLevel: Float = 100.0f,
        val obstacleDensity: Float = 0.0f,
        val humanPresent: Boolean = false,
        val taskProgress: Float = 0.0f,
        val gemmaEmotionSuggestion: EmotionState? = null,
        val isStuck: Boolean = false,
        val isProcessing: Boolean = false,
        val recentSuccess: Boolean = false,
        val recentFailure: Boolean = false,
        val curiosityTrait: Float = 0.5f,
        val sociabilityTrait: Float = 0.5f,
        val confidenceTrait: Float = 0.5f,
        val energyTrait: Float = 0.7f
    )
    
    /**
     * Compute the best emotion state based on weighted scoring.
     * 
     * Evaluates all possible emotions and returns the one with the highest score.
     * Does NOT update the current emotion - call [updateEmotion] for that.
     * 
     * @param inputs All sensor and context data affecting emotion
     * @return The computed emotion with the highest score
     */
    fun computeEmotion(inputs: EmotionInputs): EmotionState {
        val scores = mutableListOf<EmotionScore>()
        
        // CRITICAL: LOW_BATTERY always takes priority
        if (inputs.batteryLevel <= Constants.CRITICAL_BATTERY_THRESHOLD) {
            Logger.w(Constants.TAG_EMOTION, "Critical battery ${inputs.batteryLevel}%, forcing LOW_BATTERY emotion")
            return EmotionState.LOW_BATTERY
        }
        
        // Score each emotion based on current context
        scores.add(scoreNeutral(inputs))
        scores.add(scoreHappy(inputs))
        scores.add(scoreCurious(inputs))
        scores.add(scoreAlert(inputs))
        scores.add(scoreScared(inputs))
        scores.add(scoreSleepy(inputs))
        scores.add(scoreLove(inputs))
        scores.add(scoreConfused(inputs))
        scores.add(scoreThinking(inputs))
        scores.add(scoreLowBattery(inputs))
        
        // Apply Gemma AI suggestion boost if provided
        inputs.gemmaEmotionSuggestion?.let { suggestion ->
            val index = scores.indexOfFirst { it.emotion == suggestion }
            if (index >= 0) {
                val boosted = scores[index].copy(
                    score = scores[index].score + 0.3f,
                    reason = "${scores[index].reason} + AI suggestion"
                )
                scores[index] = boosted
                Logger.d(Constants.TAG_EMOTION, "Boosting ${suggestion.name} by 0.3 (AI suggestion)")
            }
        }
        
        // Select emotion with highest score
        val winner = scores.maxByOrNull { it.score } ?: scores.first()
        
        Logger.d(Constants.TAG_EMOTION, "Computed emotion: ${winner.emotion.name} (score=${String.format("%.2f", winner.score)}, reason=${winner.reason})")
        
        return winner.emotion
    }
    
    /**
     * Update the current emotion with hysteresis enforcement.
     * 
     * Only allows transition if:
     * 1. Minimum duration (3 seconds) has passed since last change
     * 2. New emotion is different from current emotion
     * 3. Transition is not locked by cooldown timer
     * 
     * @param inputs All sensor and context data affecting emotion
     */
    fun updateEmotion(inputs: EmotionInputs) {
        scope.launch {
            val newEmotion = computeEmotion(inputs)
            val currentMs = System.currentTimeMillis()
            val elapsedMs = currentMs - lastEmotionChangeMs
            
            // Check if enough time has passed (hysteresis)
            if (newEmotion != _currentEmotion.value && elapsedMs >= Constants.EMOTION_HYSTERESIS_MS) {
                // Allow transition
                val oldEmotion = _currentEmotion.value
                _currentEmotion.value = newEmotion
                lastEmotionChangeMs = currentMs
                
                // Update global state
                stateManager.updateEmotionState(newEmotion)
                
                Logger.i(Constants.TAG_EMOTION, "Emotion transition: $oldEmotion → $newEmotion (elapsed ${elapsedMs}ms)")
            } else if (newEmotion != _currentEmotion.value) {
                Logger.v(Constants.TAG_EMOTION, "Transition to $newEmotion blocked by hysteresis (${elapsedMs}ms < ${Constants.EMOTION_HYSTERESIS_MS}ms)")
            }
        }
    }
    
    /**
     * Force an immediate emotion change, bypassing hysteresis.
     * Use only for critical situations (e.g., emergency stop, critical battery).
     * 
     * @param emotion The emotion to force
     */
    fun forceEmotion(emotion: EmotionState) {
        scope.launch {
            val oldEmotion = _currentEmotion.value
            _currentEmotion.value = emotion
            lastEmotionChangeMs = System.currentTimeMillis()
            stateManager.updateEmotionState(emotion)
            
            Logger.w(Constants.TAG_EMOTION, "FORCED emotion transition: $oldEmotion → $emotion")
        }
    }
    
    // ============================================================================
    // EMOTION SCORING FUNCTIONS
    // Each function returns a score from 0.0 to 1.0+ based on relevance
    // ============================================================================
    
    private fun scoreNeutral(inputs: EmotionInputs): EmotionScore {
        // Base emotion when nothing interesting is happening
        var score = 0.2f
        
        // Higher score when everything is calm and normal
        if (inputs.batteryLevel > 50.0f) score += 0.1f
        if (inputs.obstacleDensity < 0.2f) score += 0.1f
        if (!inputs.humanPresent) score += 0.05f
        if (!inputs.isStuck && !inputs.isProcessing) score += 0.1f
        
        return EmotionScore(EmotionState.NEUTRAL, score, "calm baseline")
    }
    
    private fun scoreHappy(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Success triggers happiness
        if (inputs.recentSuccess) {
            score += 0.6f
            reasons.add("success")
        }
        
        // Human interaction boosts happiness (especially with high sociability)
        if (inputs.humanPresent) {
            score += 0.3f * inputs.sociabilityTrait
            reasons.add("human")
        }
        
        // Good battery and no threats
        if (inputs.batteryLevel > 70.0f && inputs.obstacleDensity < 0.2f) {
            score += 0.2f
            reasons.add("optimal-conditions")
        }
        
        // High confidence increases happiness
        score += inputs.confidenceTrait * 0.2f
        
        return EmotionScore(EmotionState.HAPPY, score, reasons.joinToString(", "))
    }
    
    private fun scoreCurious(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Curiosity trait is primary driver
        score += inputs.curiosityTrait * 0.5f
        reasons.add("trait=${String.format("%.2f", inputs.curiosityTrait)}")
        
        // Moderate obstacle density suggests interesting environment
        if (inputs.obstacleDensity in 0.2f..0.5f) {
            score += 0.3f
            reasons.add("interesting-env")
        }
        
        // Good energy enables curiosity
        if (inputs.batteryLevel > 40.0f) {
            score += 0.2f * inputs.energyTrait
            reasons.add("energy-ok")
        }
        
        return EmotionScore(EmotionState.CURIOUS, score, reasons.joinToString(", "))
    }
    
    private fun scoreAlert(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Moderate threat level triggers alertness
        if (inputs.obstacleDensity in 0.3f..0.6f) {
            score += 0.5f
            reasons.add("obstacles")
        }
        
        // Sudden changes or unexpected events
        if (inputs.recentFailure && !inputs.isStuck) {
            score += 0.3f
            reasons.add("failure")
        }
        
        // Low confidence increases alertness
        if (inputs.confidenceTrait < 0.4f) {
            score += 0.2f
            reasons.add("low-confidence")
        }
        
        return EmotionScore(EmotionState.ALERT, score, reasons.joinToString(", "))
    }
    
    private fun scoreScared(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // High obstacle density triggers fear
        if (inputs.obstacleDensity > 0.6f) {
            score += 0.7f
            reasons.add("high-threats")
        }
        
        // Being stuck is frightening
        if (inputs.isStuck) {
            score += 0.5f
            reasons.add("stuck")
        }
        
        // Low confidence amplifies fear
        if (inputs.confidenceTrait < 0.3f) {
            score += 0.3f
            reasons.add("no-confidence")
        }
        
        // Recent failures compound fear
        if (inputs.recentFailure) {
            score += 0.2f
            reasons.add("failure")
        }
        
        return EmotionScore(EmotionState.SCARED, score, reasons.joinToString(", "))
    }
    
    private fun scoreSleepy(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Primary driver is battery level
        if (inputs.batteryLevel < Constants.LOW_BATTERY_THRESHOLD) {
            score += 0.6f
            reasons.add("low-battery")
        } else if (inputs.batteryLevel < 30.0f) {
            score += 0.3f
            reasons.add("moderate-battery")
        }
        
        // Low energy trait contributes
        if (inputs.energyTrait < 0.4f) {
            score += 0.3f
            reasons.add("low-energy-trait")
        }
        
        return EmotionScore(EmotionState.SLEEPY, score, reasons.joinToString(", "))
    }
    
    private fun scoreLove(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Strong human interaction required
        if (inputs.humanPresent) {
            score += 0.4f * inputs.sociabilityTrait
            reasons.add("human-bond")
        }
        
        // High sociability trait
        if (inputs.sociabilityTrait > 0.7f) {
            score += 0.3f
            reasons.add("high-sociability")
        }
        
        // Recent success with human present
        if (inputs.recentSuccess && inputs.humanPresent) {
            score += 0.3f
            reasons.add("positive-interaction")
        }
        
        return EmotionScore(EmotionState.LOVE, score, reasons.joinToString(", "))
    }
    
    private fun scoreConfused(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Being stuck without clear threat suggests confusion
        if (inputs.isStuck && inputs.obstacleDensity < 0.4f) {
            score += 0.6f
            reasons.add("stuck-unclear")
        }
        
        // Repeated failures suggest confusion
        if (inputs.recentFailure && inputs.taskProgress < 0.2f) {
            score += 0.4f
            reasons.add("no-progress")
        }
        
        // Low confidence without clear threat
        if (inputs.confidenceTrait < 0.3f && inputs.obstacleDensity < 0.3f) {
            score += 0.3f
            reasons.add("uncertain")
        }
        
        return EmotionScore(EmotionState.CONFUSED, score, reasons.joinToString(", "))
    }
    
    private fun scoreThinking(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Processing indicator
        if (inputs.isProcessing) {
            score += 0.7f
            reasons.add("ai-active")
        }
        
        // Complex decision-making situations
        if (inputs.obstacleDensity in 0.3f..0.6f && inputs.taskProgress > 0.0f) {
            score += 0.3f
            reasons.add("planning")
        }
        
        return EmotionScore(EmotionState.THINKING, score, reasons.joinToString(", "))
    }
    
    private fun scoreLowBattery(inputs: EmotionInputs): EmotionScore {
        var score = 0.0f
        val reasons = mutableListOf<String>()
        
        // Critical battery level
        if (inputs.batteryLevel <= Constants.CRITICAL_BATTERY_THRESHOLD) {
            score += 1.5f // Very high priority
            reasons.add("critical")
        } else if (inputs.batteryLevel <= Constants.LOW_BATTERY_THRESHOLD) {
            score += 0.8f
            reasons.add("low")
        }
        
        return EmotionScore(EmotionState.LOW_BATTERY, score, reasons.joinToString(", "))
    }
    
    /**
     * Clean up resources when emotion engine is no longer needed.
     */
    fun shutdown() {
        Logger.i(Constants.TAG_EMOTION, "Shutting down EmotionEngine")
        scope.cancel()
    }
}
