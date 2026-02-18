package com.rover.ai.decision

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Behavior Tree engine for autonomous rover decision making.
 * 
 * Implements a hierarchical decision system using selector and sequence nodes.
 * The tree is evaluated each tick to determine the current behavior state.
 * 
 * Tree Structure:
 * - Root Selector
 *   - Sequence: Check battery low -> Return Home
 *   - Sequence: Check cliff/obstacle -> Emergency Stop
 *   - Sequence: Check human detected -> Follow Human
 *   - Sequence: Check line detected -> Line Follow
 *   - Sequence: Check idle -> Explore
 *   - Default: Patrol
 * 
 * @property stateManager Global state manager for reading sensor data
 */
@Singleton
class BehaviorTree @Inject constructor(
    private val stateManager: StateManager
) {
    
    /**
     * Behavior states the rover can be in.
     * Each state corresponds to a high-level goal.
     */
    enum class BehaviorState {
        IDLE,
        EXPLORE,
        FOLLOW_HUMAN,
        LINE_FOLLOW,
        PATROL,
        RETURN_HOME
    }
    
    /**
     * Result of a node evaluation.
     * SUCCESS: node succeeded, FAILURE: node failed, RUNNING: node still executing
     */
    private enum class NodeResult {
        SUCCESS,
        FAILURE,
        RUNNING
    }
    
    private var currentBehavior: BehaviorState = BehaviorState.IDLE
    private var lastTickTime: Long = 0L
    private var explorationStartTime: Long = 0L
    
    /**
     * Evaluate the behavior tree and return the current behavior state.
     * 
     * Should be called periodically (e.g., every 200ms) to update decisions.
     * The tree is evaluated top-to-bottom using selector and sequence logic.
     * 
     * @return Current behavior state after tree evaluation
     */
    fun tick(): BehaviorState {
        val now = System.currentTimeMillis()
        lastTickTime = now
        
        val state = stateManager.currentState
        
        // Root selector: try each behavior in priority order
        val newBehavior = when {
            // Highest priority: Safety behaviors
            isBatteryLow() -> {
                Logger.w(Constants.TAG_DECISION, "Battery low: ${state.batteryLevel}% - returning home")
                BehaviorState.RETURN_HOME
            }
            
            isCliffDetected() -> {
                Logger.w(Constants.TAG_DECISION, "Cliff detected - staying idle")
                BehaviorState.IDLE
            }
            
            // High priority: Reactive behaviors
            isHumanDetected() -> {
                Logger.i(Constants.TAG_DECISION, "Human detected - following")
                BehaviorState.FOLLOW_HUMAN
            }
            
            isLineDetected() -> {
                Logger.i(Constants.TAG_DECISION, "Line detected - following line")
                BehaviorState.LINE_FOLLOW
            }
            
            // Medium priority: Autonomous behaviors
            shouldExplore() -> {
                if (currentBehavior != BehaviorState.EXPLORE) {
                    explorationStartTime = now
                }
                Logger.d(Constants.TAG_DECISION, "Exploring environment")
                BehaviorState.EXPLORE
            }
            
            // Low priority: Default behavior
            else -> {
                Logger.d(Constants.TAG_DECISION, "Patrolling")
                BehaviorState.PATROL
            }
        }
        
        // Log state transitions
        if (newBehavior != currentBehavior) {
            Logger.i(
                Constants.TAG_DECISION,
                "Behavior transition: $currentBehavior -> $newBehavior"
            )
            stateManager.updateBehaviorMode(mapToBehaviorMode(newBehavior))
        }
        
        currentBehavior = newBehavior
        return currentBehavior
    }
    
    /**
     * Get the current behavior state without evaluating the tree.
     * 
     * @return Current behavior state
     */
    fun getCurrentBehavior(): BehaviorState = currentBehavior
    
    // ============================================================================
    // CONDITION NODES
    // ============================================================================
    
    /**
     * Check if battery level is below the return home threshold.
     * 
     * @return true if battery is low and rover should return home
     */
    private fun isBatteryLow(): Boolean {
        val battery = stateManager.currentState.batteryLevel
        return battery <= Constants.RETURN_HOME_BATTERY_THRESHOLD
    }
    
    /**
     * Check if cliff sensor has detected a drop-off.
     * 
     * @return true if cliff is detected ahead
     */
    private fun isCliffDetected(): Boolean {
        return stateManager.currentState.sensorData.cliffDetected
    }
    
    /**
     * Check if obstacle is critically close (< 15cm).
     * 
     * @return true if immediate obstacle detected
     */
    private fun isObstacleClose(): Boolean {
        val distance = stateManager.currentState.sensorData.distanceCm
        return distance < Constants.OBSTACLE_STOP_CM
    }
    
    /**
     * Check if human has been detected by vision system.
     * 
     * Currently checks YOLO status as proxy for human detection.
     * In production, this would check actual YOLO detections for person class.
     * 
     * @return true if human is detected
     */
    private fun isHumanDetected(): Boolean {
        // TODO: Integrate with actual YOLO person detection results
        val yoloStatus = stateManager.currentState.yoloStatus
        return yoloStatus == StateManager.AIStatus.READY && 
               stateManager.currentState.behaviorMode == StateManager.BehaviorMode.FOLLOW_HUMAN
    }
    
    /**
     * Check if line is detected by IR sensors.
     * 
     * @return true if center IR sensor detects a line
     */
    private fun isLineDetected(): Boolean {
        val sensorData = stateManager.currentState.sensorData
        return sensorData.lineFollowMode || sensorData.irCenter
    }
    
    /**
     * Determine if rover should enter exploration mode.
     * 
     * Exploration is triggered when:
     * - Rover is idle
     * - No other high-priority behaviors are active
     * - Exploration timeout hasn't been reached
     * 
     * @return true if rover should explore
     */
    private fun shouldExplore(): Boolean {
        val state = stateManager.currentState
        val isIdle = !state.isMoving && currentBehavior == BehaviorState.IDLE
        
        // Check exploration timeout
        if (currentBehavior == BehaviorState.EXPLORE) {
            val elapsed = System.currentTimeMillis() - explorationStartTime
            if (elapsed > Constants.EXPLORATION_TIMEOUT_MS) {
                Logger.i(Constants.TAG_DECISION, "Exploration timeout reached")
                return false
            }
        }
        
        return isIdle || currentBehavior == BehaviorState.EXPLORE
    }
    
    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    /**
     * Map BehaviorState to StateManager.BehaviorMode.
     * 
     * @param state Behavior tree state
     * @return Corresponding StateManager behavior mode
     */
    private fun mapToBehaviorMode(state: BehaviorState): StateManager.BehaviorMode {
        return when (state) {
            BehaviorState.IDLE -> StateManager.BehaviorMode.IDLE
            BehaviorState.EXPLORE -> StateManager.BehaviorMode.EXPLORE
            BehaviorState.FOLLOW_HUMAN -> StateManager.BehaviorMode.FOLLOW_HUMAN
            BehaviorState.LINE_FOLLOW -> StateManager.BehaviorMode.LINE_FOLLOW
            BehaviorState.PATROL -> StateManager.BehaviorMode.PATROL
            BehaviorState.RETURN_HOME -> StateManager.BehaviorMode.RETURN_HOME
        }
    }
    
    /**
     * Reset behavior tree to initial state.
     * Useful for testing or manual override.
     */
    fun reset() {
        currentBehavior = BehaviorState.IDLE
        explorationStartTime = 0L
        Logger.i(Constants.TAG_DECISION, "Behavior tree reset")
    }
}
