package com.rover.ai.emotion

/**
 * Enumeration of all possible emotion states for the autonomous rover.
 * 
 * Each emotion represents a distinct behavioral and visual state that affects:
 * - Face animation rendering (eye shapes, colors, particles)
 * - Motor control behavior (speed, exploration patterns)
 * - Decision-making priorities (curiosity vs caution)
 * - Sound effects and feedback patterns
 * 
 * Emotions are selected by [EmotionEngine] based on sensor inputs, battery level,
 * AI suggestions, and personality traits.
 */
enum class EmotionState(val description: String) {
    
    /**
     * Default calm state.
     * Eyes are normal circles, no special behavior.
     * Used when no strong stimuli are present.
     */
    NEUTRAL("Calm and ready, awaiting input"),
    
    /**
     * Positive, joyful state.
     * Eyes become curved upward (smiling), warm colors.
     * Triggered by successful task completion, human interaction, or positive discoveries.
     */
    HAPPY("Joyful and content, tail wagging metaphorically"),
    
    /**
     * Inquisitive exploration state.
     * Eyes wide open, pupils dilated, scanning particles.
     * Triggered by new objects detected, unexplored areas, or high curiosity trait.
     */
    CURIOUS("Interested in surroundings, exploring actively"),
    
    /**
     * Heightened awareness state.
     * Sharp focused eyes, red/orange colors, ready to react.
     * Triggered by sudden movements, unknown objects, or moderate obstacle density.
     */
    ALERT("Vigilant and attentive, scanning for threats"),
    
    /**
     * Fear response state.
     * Eyes wide with fear indicators, shaking pupils, retreat behavior.
     * Triggered by high obstacle density, cliff detection, or overwhelming situations.
     */
    SCARED("Frightened and defensive, seeking safety"),
    
    /**
     * Low energy state.
     * Half-closed droopy eyes, slow blinking, reduced motion.
     * Triggered by low battery level or extended operation time.
     */
    SLEEPY("Tired and low energy, conserving power"),
    
    /**
     * Affectionate bonding state.
     * Heart particles, pink/warm colors, following behavior.
     * Triggered by prolonged positive human interaction and high sociability trait.
     */
    LOVE("Affectionate and bonded, seeking companionship"),
    
    /**
     * Uncertainty state.
     * Asymmetric eyes (one raised), question mark particles.
     * Triggered by conflicting sensor data, stuck situations, or ambiguous AI outputs.
     */
    CONFUSED("Uncertain about situation, processing information"),
    
    /**
     * Processing/computation state.
     * Gear/cog particles, eyes with loading indicators.
     * Triggered during complex AI inference, path planning, or decision-making.
     */
    THINKING("Analyzing data, planning next action"),
    
    /**
     * Critical battery warning state.
     * Flashing red eyes, battery icon particles, urgent behavior.
     * Triggered by battery level below critical threshold, overrides other emotions.
     */
    LOW_BATTERY("Critical power level, returning to charge station")
}
