package com.rover.ai.core

import com.rover.ai.emotion.EmotionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global rover state management using MVI (Model-View-Intent) pattern.
 * 
 * Provides a single source of truth for the entire rover's state.
 * All UI components and decision systems observe this state reactively.
 * 
 * State updates are atomic and thread-safe through StateFlow.
 */
@Singleton
class StateManager @Inject constructor() {
    
    /**
     * Complete rover state data class.
     * All properties are immutable (val). Use copy() for updates.
     */
    data class RoverState(
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
        val sensorData: SensorData = SensorData(),
        val emotionState: EmotionState = EmotionState.NEUTRAL,
        val behaviorMode: BehaviorMode = BehaviorMode.IDLE,
        val gemmaStatus: AIStatus = AIStatus.IDLE,
        val yoloStatus: AIStatus = AIStatus.IDLE,
        val personalityTraits: PersonalityTraits = PersonalityTraits(),
        val batteryLevel: Float = 100.0f,
        val isMoving: Boolean = false,
        val currentCommand: String = "STOP",
        val lastError: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * WebSocket connection state
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ERROR
    }
    
    /**
     * Sensor data from ESP32
     */
    data class SensorData(
        val distanceCm: Float = 400.0f,
        val irLeft: Boolean = false,
        val irCenter: Boolean = false,
        val irRight: Boolean = false,
        val cliffDetected: Boolean = false,
        val edgeDetected: Boolean = false,
        val motorSpeedLeft: Int = 0,
        val motorSpeedRight: Int = 0,
        val lineFollowMode: Boolean = false
    )
    
    /**
     * Behavior tree execution modes
     */
    enum class BehaviorMode {
        IDLE,
        EXPLORE,
        FOLLOW_HUMAN,
        LINE_FOLLOW,
        PATROL,
        RETURN_HOME,
        MANUAL
    }
    
    /**
     * AI system status
     */
    enum class AIStatus {
        IDLE,
        LOADING,
        READY,
        INFERRING,
        ERROR
    }
    
    /**
     * Personality traits (0.0 to 1.0)
     */
    data class PersonalityTraits(
        val curiosity: Float = Constants.INITIAL_CURIOSITY,
        val sociability: Float = Constants.INITIAL_SOCIABILITY,
        val confidence: Float = Constants.INITIAL_CONFIDENCE,
        val energy: Float = Constants.INITIAL_ENERGY,
        val playfulness: Float = Constants.INITIAL_PLAYFULNESS
    )
    
    // ============================================================================
    // STATE FLOW
    // ============================================================================
    
    private val _state = MutableStateFlow(RoverState())
    val state: StateFlow<RoverState> = _state.asStateFlow()
    
    /**
     * Current state value (synchronous access)
     */
    val currentState: RoverState
        get() = _state.value
    
    // ============================================================================
    // STATE UPDATE METHODS
    // ============================================================================
    
    /**
     * Update connection state
     */
    fun updateConnectionState(newState: ConnectionState) {
        _state.value = _state.value.copy(
            connectionState = newState,
            timestamp = System.currentTimeMillis()
        )
        Logger.d(Constants.TAG_CORE, "Connection state: $newState")
    }
    
    /**
     * Update sensor data from ESP32
     */
    fun updateSensorData(sensorData: SensorData) {
        _state.value = _state.value.copy(
            sensorData = sensorData,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Update emotion state
     */
    fun updateEmotionState(emotion: EmotionState) {
        _state.value = _state.value.copy(
            emotionState = emotion,
            timestamp = System.currentTimeMillis()
        )
        Logger.i(Constants.TAG_EMOTION, "Emotion: $emotion")
    }
    
    /**
     * Update behavior mode
     */
    fun updateBehaviorMode(mode: BehaviorMode) {
        _state.value = _state.value.copy(
            behaviorMode = mode,
            timestamp = System.currentTimeMillis()
        )
        Logger.i(Constants.TAG_DECISION, "Behavior mode: $mode")
    }
    
    /**
     * Update Gemma AI status
     */
    fun updateGemmaStatus(status: AIStatus) {
        _state.value = _state.value.copy(
            gemmaStatus = status,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Update YOLO detector status
     */
    fun updateYoloStatus(status: AIStatus) {
        _state.value = _state.value.copy(
            yoloStatus = status,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Update personality traits
     */
    fun updatePersonalityTraits(traits: PersonalityTraits) {
        _state.value = _state.value.copy(
            personalityTraits = traits,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Update battery level
     */
    fun updateBatteryLevel(level: Float) {
        _state.value = _state.value.copy(
            batteryLevel = level.coerceIn(0.0f, 100.0f),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Update movement state
     */
    fun updateMovementState(isMoving: Boolean, command: String) {
        _state.value = _state.value.copy(
            isMoving = isMoving,
            currentCommand = command,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Set error message
     */
    fun setError(error: String) {
        _state.value = _state.value.copy(
            lastError = error,
            timestamp = System.currentTimeMillis()
        )
        Logger.e(Constants.TAG_CORE, "Error: $error")
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(
            lastError = null,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Reset state to defaults (for testing or app restart)
     */
    fun reset() {
        _state.value = RoverState()
        Logger.i(Constants.TAG_CORE, "State reset to defaults")
    }
}
