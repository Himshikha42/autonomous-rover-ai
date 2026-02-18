package com.rover.ai.emotion

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages evolving personality traits for the autonomous rover.
 * 
 * Personality traits are continuous values (0.0 to 1.0) that slowly change based on
 * experiences and interactions. These traits influence emotion scoring and behavioral decisions.
 * 
 * Traits:
 * - **Curiosity**: Desire to explore new areas and investigate objects
 * - **Sociability**: Preference for human interaction and bonding
 * - **Confidence**: Self-assurance in navigation and decision-making
 * - **Energy**: Activity level and enthusiasm
 * - **Playfulness**: Tendency for spontaneous, non-goal-directed behavior
 * 
 * Personality evolves gradually through reinforcement learning principles:
 * - Positive experiences increase relevant traits
 * - Negative experiences decrease relevant traits
 * - Changes are small (PERSONALITY_CHANGE_RATE) to prevent abrupt shifts
 * - Traits are clamped to [0.0, 1.0] range
 * 
 * Future enhancement: Persist traits to Room database for continuity across app restarts.
 */
@Singleton
class PersonalityEngine @Inject constructor() {
    
    /**
     * Immutable personality trait values.
     * All properties are Float values ranging from 0.0 (minimum) to 1.0 (maximum).
     */
    data class PersonalityTraits(
        val curiosity: Float = Constants.INITIAL_CURIOSITY,
        val sociability: Float = Constants.INITIAL_SOCIABILITY,
        val confidence: Float = Constants.INITIAL_CONFIDENCE,
        val energy: Float = Constants.INITIAL_ENERGY,
        val playfulness: Float = Constants.INITIAL_PLAYFULNESS
    ) {
        /**
         * Validate and clamp all traits to valid range [0.0, 1.0]
         */
        fun clamp(): PersonalityTraits {
            return PersonalityTraits(
                curiosity = curiosity.coerceIn(Constants.PERSONALITY_MIN, Constants.PERSONALITY_MAX),
                sociability = sociability.coerceIn(Constants.PERSONALITY_MIN, Constants.PERSONALITY_MAX),
                confidence = confidence.coerceIn(Constants.PERSONALITY_MIN, Constants.PERSONALITY_MAX),
                energy = energy.coerceIn(Constants.PERSONALITY_MIN, Constants.PERSONALITY_MAX),
                playfulness = playfulness.coerceIn(Constants.PERSONALITY_MIN, Constants.PERSONALITY_MAX)
            )
        }
        
        /**
         * Get human-readable summary of personality
         */
        override fun toString(): String {
            return "Personality(curiosity=${String.format("%.2f", curiosity)}, " +
                    "sociability=${String.format("%.2f", sociability)}, " +
                    "confidence=${String.format("%.2f", confidence)}, " +
                    "energy=${String.format("%.2f", energy)}, " +
                    "playfulness=${String.format("%.2f", playfulness)})"
        }
    }
    
    /**
     * Current personality traits as reactive StateFlow.
     * UI and decision systems can observe this for real-time trait updates.
     */
    private val _traits = MutableStateFlow(PersonalityTraits())
    val traits: StateFlow<PersonalityTraits> = _traits.asStateFlow()
    
    /**
     * Current traits value (synchronous access)
     */
    val currentTraits: PersonalityTraits
        get() = _traits.value
    
    // ============================================================================
    // EXPERIENCE-BASED TRAIT EVOLUTION
    // ============================================================================
    
    /**
     * Handle positive human interaction event.
     * 
     * Increases sociability and confidence traits.
     * Examples: Human gives praise, successful follow behavior, positive gesture recognition.
     * 
     * @param intensity Strength of interaction (0.0 to 1.0), defaults to moderate
     */
    fun onHumanInteraction(intensity: Float = 0.5f) {
        val change = Constants.PERSONALITY_CHANGE_RATE * intensity.coerceIn(0.0f, 1.0f)
        
        val newTraits = _traits.value.copy(
            sociability = _traits.value.sociability + change * 2.0f, // Double weight for sociability
            confidence = _traits.value.confidence + change
        ).clamp()
        
        _traits.value = newTraits
        
        Logger.d(Constants.TAG_EMOTION, "Human interaction: sociability +${String.format("%.3f", change * 2.0f)}, confidence +${String.format("%.3f", change)}")
        logTraitChange()
    }
    
    /**
     * Handle obstacle encounter event.
     * 
     * Slightly decreases curiosity (learns caution) and confidence.
     * Examples: Collision avoidance, cliff detection, getting stuck.
     * 
     * @param severity How severe the obstacle was (0.0 to 1.0)
     */
    fun onObstacleEncounter(severity: Float = 0.5f) {
        val change = Constants.PERSONALITY_CHANGE_RATE * severity.coerceIn(0.0f, 1.0f)
        
        val newTraits = _traits.value.copy(
            curiosity = _traits.value.curiosity - change * 0.5f, // Smaller decrease
            confidence = _traits.value.confidence - change
        ).clamp()
        
        _traits.value = newTraits
        
        Logger.d(Constants.TAG_EMOTION, "Obstacle encounter: curiosity -${String.format("%.3f", change * 0.5f)}, confidence -${String.format("%.3f", change)}")
        logTraitChange()
    }
    
    /**
     * Handle new object discovery event.
     * 
     * Increases curiosity and playfulness traits.
     * Examples: YOLO detects new object class, enters unexplored room, finds novel shape.
     * 
     * @param novelty How novel/interesting the discovery is (0.0 to 1.0)
     */
    fun onNewObjectDiscovered(novelty: Float = 0.5f) {
        val change = Constants.PERSONALITY_CHANGE_RATE * novelty.coerceIn(0.0f, 1.0f)
        
        val newTraits = _traits.value.copy(
            curiosity = _traits.value.curiosity + change * 1.5f, // Higher weight for curiosity
            playfulness = _traits.value.playfulness + change
        ).clamp()
        
        _traits.value = newTraits
        
        Logger.d(Constants.TAG_EMOTION, "New object discovered: curiosity +${String.format("%.3f", change * 1.5f)}, playfulness +${String.format("%.3f", change)}")
        logTraitChange()
    }
    
    /**
     * Handle successful navigation completion event.
     * 
     * Increases confidence, energy, and playfulness traits.
     * Examples: Reached waypoint, completed autonomous exploration, successful object retrieval.
     * 
     * @param complexity How difficult the navigation was (0.0 to 1.0)
     */
    fun onSuccessfulNavigation(complexity: Float = 0.5f) {
        val change = Constants.PERSONALITY_CHANGE_RATE * complexity.coerceIn(0.0f, 1.0f)
        
        val newTraits = _traits.value.copy(
            confidence = _traits.value.confidence + change * 2.0f, // Biggest confidence boost
            energy = _traits.value.energy + change * 0.5f,
            playfulness = _traits.value.playfulness + change * 0.5f
        ).clamp()
        
        _traits.value = newTraits
        
        Logger.d(Constants.TAG_EMOTION, "Successful navigation: confidence +${String.format("%.3f", change * 2.0f)}, energy +${String.format("%.3f", change * 0.5f)}")
        logTraitChange()
    }
    
    /**
     * Handle failed task or navigation event.
     * 
     * Decreases confidence and energy traits.
     * Examples: Got stuck, failed to reach goal, timeout on task.
     * 
     * @param severity How severe the failure was (0.0 to 1.0)
     */
    fun onNavigationFailure(severity: Float = 0.5f) {
        val change = Constants.PERSONALITY_CHANGE_RATE * severity.coerceIn(0.0f, 1.0f)
        
        val newTraits = _traits.value.copy(
            confidence = _traits.value.confidence - change * 1.5f,
            energy = _traits.value.energy - change * 0.5f
        ).clamp()
        
        _traits.value = newTraits
        
        Logger.d(Constants.TAG_EMOTION, "Navigation failure: confidence -${String.format("%.3f", change * 1.5f)}, energy -${String.format("%.3f", change * 0.5f)}")
        logTraitChange()
    }
    
    /**
     * Handle low battery situation.
     * 
     * Decreases energy trait temporarily.
     * 
     * @param batteryLevel Current battery percentage (0-100)
     */
    fun onLowBattery(batteryLevel: Float) {
        val change = Constants.PERSONALITY_CHANGE_RATE
        
        val newTraits = _traits.value.copy(
            energy = _traits.value.energy - change
        ).clamp()
        
        _traits.value = newTraits
        
        Logger.d(Constants.TAG_EMOTION, "Low battery ($batteryLevel%): energy -${String.format("%.3f", change)}")
        logTraitChange()
    }
    
    /**
     * Handle battery recharge event.
     * 
     * Increases energy trait back toward normal.
     * 
     * @param batteryLevel Current battery percentage (0-100)
     */
    fun onBatteryRecharged(batteryLevel: Float) {
        val change = Constants.PERSONALITY_CHANGE_RATE * 2.0f // Faster recovery
        
        val newTraits = _traits.value.copy(
            energy = _traits.value.energy + change
        ).clamp()
        
        _traits.value = newTraits
        
        Logger.d(Constants.TAG_EMOTION, "Battery recharged ($batteryLevel%): energy +${String.format("%.3f", change)}")
        logTraitChange()
    }
    
    // ============================================================================
    // BEHAVIORAL INFLUENCE METHODS
    // ============================================================================
    
    /**
     * Get exploration bias factor based on curiosity and energy traits.
     * 
     * Used by behavior tree to adjust exploration vs exploitation balance.
     * High value (> 0.5) means rover prefers exploring new areas.
     * Low value (< 0.5) means rover prefers known safe areas.
     * 
     * @return Float in range [0.0, 1.0]
     */
    fun getExplorationBias(): Float {
        val current = _traits.value
        return ((current.curiosity * 0.6f) + (current.energy * 0.3f) + (current.playfulness * 0.1f))
            .coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Get social behavior preference based on sociability trait.
     * 
     * Used to determine how strongly the rover seeks or follows humans.
     * High value (> 0.6) means rover actively seeks human interaction.
     * Low value (< 0.4) means rover is more independent.
     * 
     * @return Float in range [0.0, 1.0]
     */
    fun getSocialBehavior(): Float {
        return _traits.value.sociability
    }
    
    /**
     * Get risk-taking propensity based on confidence and curiosity.
     * 
     * Used to adjust obstacle avoidance thresholds and exploration depth.
     * High value means rover takes more risks in navigation.
     * Low value means rover is more cautious.
     * 
     * @return Float in range [0.0, 1.0]
     */
    fun getRiskTolerance(): Float {
        val current = _traits.value
        return ((current.confidence * 0.7f) + (current.curiosity * 0.3f))
            .coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Get activity level based on energy and playfulness.
     * 
     * Used to adjust motor speeds and movement frequency.
     * High value means more energetic movement patterns.
     * Low value means slower, more deliberate movements.
     * 
     * @return Float in range [0.0, 1.0]
     */
    fun getActivityLevel(): Float {
        val current = _traits.value
        return ((current.energy * 0.7f) + (current.playfulness * 0.3f))
            .coerceIn(0.0f, 1.0f)
    }
    
    // ============================================================================
    // PERSISTENCE (Future implementation with Room DB)
    // ============================================================================
    
    /**
     * Load personality traits from persistent storage.
     * 
     * TODO: Implement Room database integration
     * For now, returns default traits.
     * 
     * @return Loaded traits or defaults if not found
     */
    fun loadTraits(): PersonalityTraits {
        // TODO: Load from Room database
        // val entity = database.personalityDao().getLatest()
        // return entity?.toPersonalityTraits() ?: PersonalityTraits()
        
        Logger.i(Constants.TAG_EMOTION, "Loading default personality traits (DB not implemented)")
        return PersonalityTraits()
    }
    
    /**
     * Save current personality traits to persistent storage.
     * 
     * TODO: Implement Room database integration
     * For now, this is a no-op.
     */
    fun saveTraits() {
        // TODO: Save to Room database
        // val entity = currentTraits.toEntity()
        // database.personalityDao().insert(entity)
        
        Logger.d(Constants.TAG_EMOTION, "Personality traits saved (DB not implemented): ${_traits.value}")
    }
    
    /**
     * Reset personality traits to default values.
     * 
     * Useful for testing or when user wants to reset rover personality.
     */
    fun resetToDefaults() {
        _traits.value = PersonalityTraits()
        Logger.i(Constants.TAG_EMOTION, "Personality traits reset to defaults")
        logTraitChange()
    }
    
    /**
     * Manually set specific trait values.
     * 
     * Used for testing or manual personality configuration.
     * Values are automatically clamped to valid range.
     * 
     * @param newTraits The traits to set
     */
    fun setTraits(newTraits: PersonalityTraits) {
        _traits.value = newTraits.clamp()
        Logger.i(Constants.TAG_EMOTION, "Personality traits manually set")
        logTraitChange()
    }
    
    /**
     * Log current trait values for debugging.
     */
    private fun logTraitChange() {
        Logger.v(Constants.TAG_EMOTION, "Current traits: ${_traits.value}")
    }
}
