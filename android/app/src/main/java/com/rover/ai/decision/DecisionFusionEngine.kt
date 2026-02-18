package com.rover.ai.decision

import com.rover.ai.communication.RoverCommand
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decision fusion engine that merges multiple AI and sensor inputs.
 * 
 * Combines three decision paths:
 * 1. Fast path: YOLO object detection (reactive, 12 FPS)
 * 2. Slow path: Gemma VLM reasoning (strategic, every 5 seconds)
 * 3. ESP32 sensors: Distance, IR, cliff (immediate, 10 Hz)
 * 
 * Priority System (highest to lowest):
 * 1. Safety Override: Emergency stops for cliff/obstacle
 * 2. Reactive: YOLO detections requiring immediate response
 * 3. Behavior Tree: High-level autonomous behavior
 * 4. LLM Suggestion: Gemma reasoning for complex scenarios
 * 
 * All commands pass through SafetyValidator before execution.
 * 
 * @property safetyValidator Validates all outgoing commands
 * @property behaviorTree High-level behavior decision system
 * @property stateManager Global state for sensor access
 * @property goalCommandGenerator Converts behaviors to commands
 */
@Singleton
class DecisionFusionEngine @Inject constructor(
    private val safetyValidator: SafetyValidator,
    private val behaviorTree: BehaviorTree,
    private val stateManager: StateManager,
    private val goalCommandGenerator: GoalCommandGenerator
) {
    
    /**
     * YOLO detection result from fast path.
     * 
     * @property detectedClasses List of detected object class names
     * @property highestConfidence Highest confidence score (0.0 to 1.0)
     * @property boundingBoxes List of detection bounding boxes
     * @property timestamp When detection was performed
     */
    data class YoloOutput(
        val detectedClasses: List<String> = emptyList(),
        val highestConfidence: Float = 0.0f,
        val boundingBoxes: List<BoundingBox> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Bounding box for detected object.
     * 
     * @property x X coordinate (0.0 to 1.0, normalized)
     * @property y Y coordinate (0.0 to 1.0, normalized)
     * @property width Box width (0.0 to 1.0, normalized)
     * @property height Box height (0.0 to 1.0, normalized)
     * @property confidence Detection confidence (0.0 to 1.0)
     * @property className Detected object class
     */
    data class BoundingBox(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val confidence: Float,
        val className: String
    )
    
    /**
     * Gemma VLM reasoning output from slow path.
     * 
     * @property suggestion Suggested action or reasoning text
     * @property confidence Model confidence in suggestion (0.0 to 1.0)
     * @property reasoning Explanation of the decision
     * @property timestamp When inference was performed
     */
    data class GemmaOutput(
        val suggestion: String = "",
        val confidence: Float = 0.0f,
        val reasoning: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private var lastYoloOutput: YoloOutput? = null
    private var lastGemmaOutput: GemmaOutput? = null
    private var lastDecisionTime: Long = 0L
    
    /**
     * Main decision function that fuses all inputs and returns final command.
     * 
     * Decision flow:
     * 1. Check safety validators (cliff, obstacle, battery)
     * 2. Evaluate behavior tree for high-level behavior
     * 3. Consider YOLO detections for reactive responses
     * 4. Consider Gemma suggestions for strategic planning
     * 5. Generate command based on fused decision
     * 6. Validate command through safety layer
     * 
     * @param yoloOutput Latest YOLO detection results (null if not available)
     * @param gemmaOutput Latest Gemma reasoning (null if not available)
     * @return Final rover command, or null if no safe command available
     */
    fun decide(
        yoloOutput: YoloOutput? = null,
        gemmaOutput: GemmaOutput? = null
    ): RoverCommand? {
        
        val now = System.currentTimeMillis()
        lastDecisionTime = now
        
        // Store latest outputs
        if (yoloOutput != null) {
            lastYoloOutput = yoloOutput
        }
        if (gemmaOutput != null) {
            lastGemmaOutput = gemmaOutput
        }
        
        val state = stateManager.currentState
        val sensorData = state.sensorData
        
        Logger.d(
            Constants.TAG_DECISION,
            "Deciding: dist=${sensorData.distanceCm}cm, cliff=${sensorData.cliffDetected}, " +
            "yolo=${yoloOutput?.detectedClasses?.size ?: 0} objects"
        )
        
        // PRIORITY 1: Safety Override
        // Emergency stop for immediate dangers
        val safetyCommand = evaluateSafetyOverride(sensorData)
        if (safetyCommand != null) {
            Logger.w(Constants.TAG_DECISION, "Safety override: ${safetyCommand.cmd}")
            return safetyCommand
        }
        
        // PRIORITY 2: Reactive Response
        // Respond to YOLO detections (e.g., avoid obstacles, follow person)
        val reactiveCommand = evaluateReactiveResponse(yoloOutput, sensorData)
        if (reactiveCommand != null) {
            val (allowed, reason) = safetyValidator.validateCommand(reactiveCommand, sensorData)
            if (allowed) {
                Logger.i(Constants.TAG_DECISION, "Reactive command: ${reactiveCommand.cmd}")
                return reactiveCommand
            } else {
                Logger.w(Constants.TAG_DECISION, "Reactive command blocked: $reason")
            }
        }
        
        // PRIORITY 3: Behavior Tree
        // Execute high-level autonomous behavior
        val behaviorState = behaviorTree.tick()
        val behaviorCommand = goalCommandGenerator.generateCommand(behaviorState, sensorData)
        
        if (behaviorCommand != null) {
            val (allowed, reason) = safetyValidator.validateCommand(behaviorCommand, sensorData)
            if (allowed) {
                Logger.d(Constants.TAG_DECISION, "Behavior command: ${behaviorCommand.cmd} ($behaviorState)")
                return behaviorCommand
            } else {
                Logger.w(Constants.TAG_DECISION, "Behavior command blocked: $reason - falling back to STOP")
                return RoverCommand(RoverCommand.STOP)
            }
        }
        
        // PRIORITY 4: LLM Suggestion
        // Consider Gemma's strategic reasoning
        val llmCommand = evaluateLLMSuggestion(gemmaOutput, sensorData)
        if (llmCommand != null) {
            val (allowed, reason) = safetyValidator.validateCommand(llmCommand, sensorData)
            if (allowed) {
                Logger.i(Constants.TAG_DECISION, "LLM command: ${llmCommand.cmd}")
                return llmCommand
            } else {
                Logger.w(Constants.TAG_DECISION, "LLM command blocked: $reason")
            }
        }
        
        // Fallback: STOP if no decision made
        Logger.d(Constants.TAG_DECISION, "No decision made - defaulting to STOP")
        return RoverCommand(RoverCommand.STOP)
    }
    
    /**
     * Evaluate if emergency safety override is needed.
     * 
     * @param sensorData Current sensor readings
     * @return STOP command if emergency detected, null otherwise
     */
    private fun evaluateSafetyOverride(sensorData: StateManager.SensorData): RoverCommand? {
        // Cliff detection - immediate stop
        if (sensorData.cliffDetected) {
            Logger.e(Constants.TAG_DECISION, "EMERGENCY: Cliff detected!")
            return RoverCommand(RoverCommand.STOP)
        }
        
        // Critical obstacle - immediate stop
        if (sensorData.distanceCm < Constants.OBSTACLE_STOP_CM) {
            Logger.w(Constants.TAG_DECISION, "Emergency stop: obstacle at ${sensorData.distanceCm}cm")
            return RoverCommand(RoverCommand.STOP)
        }
        
        // Battery critical - stop movement
        val battery = stateManager.currentState.batteryLevel
        if (battery <= Constants.CRITICAL_BATTERY_THRESHOLD) {
            Logger.e(Constants.TAG_DECISION, "EMERGENCY: Critical battery ${battery}%")
            return RoverCommand(RoverCommand.STOP)
        }
        
        return null
    }
    
    /**
     * Evaluate reactive response to YOLO detections.
     * 
     * @param yoloOutput YOLO detection results
     * @param sensorData Current sensor readings
     * @return Reactive command if applicable, null otherwise
     */
    private fun evaluateReactiveResponse(
        yoloOutput: YoloOutput?,
        sensorData: StateManager.SensorData
    ): RoverCommand? {
        
        if (yoloOutput == null || yoloOutput.detectedClasses.isEmpty()) {
            return null
        }
        
        // Check for person detection (follow human behavior)
        if (yoloOutput.detectedClasses.contains("person")) {
            // Find person bounding box
            val personBox = yoloOutput.boundingBoxes.firstOrNull { it.className == "person" }
            if (personBox != null) {
                // Simple reactive logic: turn toward person if not centered
                val centerX = personBox.x + personBox.width / 2.0f
                return when {
                    centerX < 0.4f -> RoverCommand(RoverCommand.LEFT, Constants.DEFAULT_MOTOR_SPEED)
                    centerX > 0.6f -> RoverCommand(RoverCommand.RIGHT, Constants.DEFAULT_MOTOR_SPEED)
                    sensorData.distanceCm > Constants.HUMAN_FOLLOW_DISTANCE_CM -> 
                        RoverCommand(RoverCommand.FORWARD, Constants.DEFAULT_MOTOR_SPEED)
                    else -> RoverCommand(RoverCommand.STOP)
                }
            }
        }
        
        return null
    }
    
    /**
     * Evaluate LLM suggestion from Gemma.
     * 
     * Parses Gemma's text output and converts to rover command.
     * Currently supports simple keyword matching.
     * 
     * @param gemmaOutput Gemma reasoning output
     * @param sensorData Current sensor readings
     * @return Command based on LLM suggestion, null if not applicable
     */
    private fun evaluateLLMSuggestion(
        gemmaOutput: GemmaOutput?,
        sensorData: StateManager.SensorData
    ): RoverCommand? {
        
        if (gemmaOutput == null || gemmaOutput.suggestion.isBlank()) {
            return null
        }
        
        // Simple keyword matching (can be improved with NLP)
        val suggestion = gemmaOutput.suggestion.lowercase()
        
        return when {
            "forward" in suggestion || "ahead" in suggestion -> 
                RoverCommand(RoverCommand.FORWARD, Constants.DEFAULT_MOTOR_SPEED)
            "back" in suggestion || "backward" in suggestion || "reverse" in suggestion -> 
                RoverCommand(RoverCommand.BACKWARD, Constants.DEFAULT_MOTOR_SPEED)
            "left" in suggestion || "turn left" in suggestion -> 
                RoverCommand(RoverCommand.LEFT, Constants.DEFAULT_MOTOR_SPEED)
            "right" in suggestion || "turn right" in suggestion -> 
                RoverCommand(RoverCommand.RIGHT, Constants.DEFAULT_MOTOR_SPEED)
            "stop" in suggestion || "wait" in suggestion -> 
                RoverCommand(RoverCommand.STOP)
            else -> {
                Logger.d(Constants.TAG_DECISION, "LLM suggestion not actionable: ${gemmaOutput.suggestion}")
                null
            }
        }
    }
    
    /**
     * Get the last YOLO output received.
     * 
     * @return Last YOLO output or null if none received
     */
    fun getLastYoloOutput(): YoloOutput? = lastYoloOutput
    
    /**
     * Get the last Gemma output received.
     * 
     * @return Last Gemma output or null if none received
     */
    fun getLastGemmaOutput(): GemmaOutput? = lastGemmaOutput
    
    /**
     * Get time since last decision was made.
     * 
     * @return Milliseconds since last decide() call
     */
    fun getTimeSinceLastDecision(): Long {
        return System.currentTimeMillis() - lastDecisionTime
    }
    
    /**
     * Reset decision engine state.
     * Clears cached outputs and resets behavior tree.
     */
    fun reset() {
        lastYoloOutput = null
        lastGemmaOutput = null
        lastDecisionTime = 0L
        behaviorTree.reset()
        safetyValidator.resetRateLimiting()
        Logger.i(Constants.TAG_DECISION, "Decision fusion engine reset")
    }
}
