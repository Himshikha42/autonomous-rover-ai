package com.rover.ai.decision

import com.rover.ai.communication.RoverCommand
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Converts high-level behavior goals into concrete rover commands.
 * 
 * Takes behavior states from the behavior tree and generates appropriate
 * RoverCommands based on current sensor data. Implements simple reactive
 * logic for each behavior mode.
 * 
 * Behaviors:
 * - IDLE: Stay stopped
 * - EXPLORE: Random walk with obstacle avoidance
 * - FOLLOW_HUMAN: Track and follow detected person
 * - LINE_FOLLOW: Follow line using IR sensors
 * - PATROL: Systematic area coverage
 * - RETURN_HOME: Navigate back to starting position
 * 
 * @property stateManager Global state for sensor access
 */
@Singleton
class GoalCommandGenerator @Inject constructor(
    private val stateManager: StateManager
) {
    
    private var lastExploreDirection: String = RoverCommand.FORWARD
    private var lastExploreChangeTime: Long = 0L
    private var patrolPhase: Int = 0
    private var patrolStartTime: Long = 0L
    
    companion object {
        private const val EXPLORE_DIRECTION_CHANGE_MS = 3000L
        private const val PATROL_LEG_DURATION_MS = 5000L
        private const val SLOW_SPEED_FACTOR = 0.6f
    }
    
    /**
     * Generate rover command for a given behavior state.
     * 
     * @param behaviorState Current behavior from behavior tree
     * @param sensorData Current sensor readings
     * @return Appropriate rover command, or null if no action needed
     */
    fun generateCommand(
        behaviorState: BehaviorTree.BehaviorState,
        sensorData: StateManager.SensorData
    ): RoverCommand? {
        
        return when (behaviorState) {
            BehaviorTree.BehaviorState.IDLE -> idleCommand()
            BehaviorTree.BehaviorState.EXPLORE -> exploreCommand(sensorData)
            BehaviorTree.BehaviorState.FOLLOW_HUMAN -> followHumanCommand(sensorData)
            BehaviorTree.BehaviorState.LINE_FOLLOW -> lineFollowCommand(sensorData)
            BehaviorTree.BehaviorState.PATROL -> patrolCommand(sensorData)
            BehaviorTree.BehaviorState.RETURN_HOME -> returnHomeCommand(sensorData)
        }
    }
    
    /**
     * IDLE behavior: Stay stopped.
     * 
     * @return STOP command
     */
    private fun idleCommand(): RoverCommand {
        Logger.d(Constants.TAG_DECISION, "Goal: IDLE -> STOP")
        return RoverCommand(RoverCommand.STOP)
    }
    
    /**
     * EXPLORE behavior: Random walk with obstacle avoidance.
     * 
     * Strategy:
     * - Move forward when path is clear
     * - Turn randomly when obstacle detected
     * - Change direction periodically for better coverage
     * 
     * @param sensorData Current sensor readings
     * @return Exploration command
     */
    fun exploreCommand(sensorData: StateManager.SensorData): RoverCommand {
        val now = System.currentTimeMillis()
        
        // Check if we need to change direction
        val timeSinceChange = now - lastExploreChangeTime
        val shouldChangeDirection = timeSinceChange > EXPLORE_DIRECTION_CHANGE_MS
        
        // Obstacle avoidance logic
        return when {
            // Close obstacle - turn away
            sensorData.distanceCm < Constants.OBSTACLE_STOP_CM -> {
                val turnDirection = if (Random.nextBoolean()) RoverCommand.LEFT else RoverCommand.RIGHT
                lastExploreDirection = turnDirection
                lastExploreChangeTime = now
                Logger.d(Constants.TAG_DECISION, "Goal: EXPLORE -> Turn $turnDirection (obstacle)")
                RoverCommand(turnDirection, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            // Medium distance obstacle - turn or slow down
            sensorData.distanceCm < Constants.OBSTACLE_SLOW_CM -> {
                if (shouldChangeDirection) {
                    val turnDirection = if (Random.nextBoolean()) RoverCommand.LEFT else RoverCommand.RIGHT
                    lastExploreDirection = turnDirection
                    lastExploreChangeTime = now
                    Logger.d(Constants.TAG_DECISION, "Goal: EXPLORE -> Turn $turnDirection (caution)")
                    RoverCommand(turnDirection, Constants.DEFAULT_MOTOR_SPEED)
                } else {
                    // Slow forward
                    val slowSpeed = (Constants.DEFAULT_MOTOR_SPEED * SLOW_SPEED_FACTOR).toInt()
                    Logger.d(Constants.TAG_DECISION, "Goal: EXPLORE -> Slow forward")
                    RoverCommand(RoverCommand.FORWARD, slowSpeed)
                }
            }
            
            // IR sensor detection - turn away from obstacle
            sensorData.irLeft && !sensorData.irRight -> {
                lastExploreDirection = RoverCommand.RIGHT
                lastExploreChangeTime = now
                Logger.d(Constants.TAG_DECISION, "Goal: EXPLORE -> Turn right (IR left)")
                RoverCommand(RoverCommand.RIGHT, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            sensorData.irRight && !sensorData.irLeft -> {
                lastExploreDirection = RoverCommand.LEFT
                lastExploreChangeTime = now
                Logger.d(Constants.TAG_DECISION, "Goal: EXPLORE -> Turn left (IR right)")
                RoverCommand(RoverCommand.LEFT, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            // Periodically change direction for better coverage
            shouldChangeDirection -> {
                val directions = listOf(RoverCommand.FORWARD, RoverCommand.LEFT, RoverCommand.RIGHT)
                lastExploreDirection = directions.random()
                lastExploreChangeTime = now
                Logger.d(Constants.TAG_DECISION, "Goal: EXPLORE -> Random $lastExploreDirection")
                RoverCommand(lastExploreDirection, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            // Clear path - continue forward
            else -> {
                Logger.d(Constants.TAG_DECISION, "Goal: EXPLORE -> Forward")
                RoverCommand(RoverCommand.FORWARD, Constants.DEFAULT_MOTOR_SPEED)
            }
        }
    }
    
    /**
     * FOLLOW_HUMAN behavior: Track and follow detected person.
     * 
     * Currently uses simple distance-based following.
     * In production, this would use YOLO bounding box position.
     * 
     * @param sensorData Current sensor readings
     * @return Follow command
     */
    fun followHumanCommand(sensorData: StateManager.SensorData): RoverCommand {
        // Maintain distance from human
        return when {
            sensorData.distanceCm < Constants.HUMAN_FOLLOW_DISTANCE_CM * 0.8f -> {
                // Too close - back up
                Logger.d(Constants.TAG_DECISION, "Goal: FOLLOW_HUMAN -> Backup (too close)")
                RoverCommand(RoverCommand.BACKWARD, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            sensorData.distanceCm > Constants.HUMAN_FOLLOW_DISTANCE_CM * 1.2f -> {
                // Too far - move forward
                Logger.d(Constants.TAG_DECISION, "Goal: FOLLOW_HUMAN -> Forward (too far)")
                RoverCommand(RoverCommand.FORWARD, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            else -> {
                // Good distance - stay put
                Logger.d(Constants.TAG_DECISION, "Goal: FOLLOW_HUMAN -> Wait (good distance)")
                RoverCommand(RoverCommand.STOP)
            }
        }
    }
    
    /**
     * LINE_FOLLOW behavior: Follow line using IR sensors.
     * 
     * Uses three IR sensors for line tracking:
     * - Center sensor: Line directly ahead
     * - Left sensor: Line to the left
     * - Right sensor: Line to the right
     * 
     * @param sensorData Current sensor readings
     * @return Line following command
     */
    fun lineFollowCommand(sensorData: StateManager.SensorData): RoverCommand {
        return when {
            // Center sensor on line - go straight
            sensorData.irCenter -> {
                Logger.d(Constants.TAG_DECISION, "Goal: LINE_FOLLOW -> Forward (centered)")
                RoverCommand(RoverCommand.FORWARD, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            // Left sensor on line - turn left
            sensorData.irLeft -> {
                Logger.d(Constants.TAG_DECISION, "Goal: LINE_FOLLOW -> Left (line left)")
                RoverCommand(RoverCommand.LEFT, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            // Right sensor on line - turn right
            sensorData.irRight -> {
                Logger.d(Constants.TAG_DECISION, "Goal: LINE_FOLLOW -> Right (line right)")
                RoverCommand(RoverCommand.RIGHT, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            // No line detected - stop and search
            else -> {
                Logger.w(Constants.TAG_DECISION, "Goal: LINE_FOLLOW -> Stop (line lost)")
                RoverCommand(RoverCommand.STOP)
            }
        }
    }
    
    /**
     * PATROL behavior: Systematic area coverage.
     * 
     * Implements a simple square patrol pattern:
     * 1. Forward for duration
     * 2. Turn right
     * 3. Forward for duration
     * 4. Turn right
     * 5. Repeat
     * 
     * @param sensorData Current sensor readings
     * @return Patrol command
     */
    fun patrolCommand(sensorData: StateManager.SensorData): RoverCommand {
        val now = System.currentTimeMillis()
        
        // Initialize patrol if first time
        if (patrolStartTime == 0L) {
            patrolStartTime = now
            patrolPhase = 0
        }
        
        val phaseElapsed = now - patrolStartTime
        
        // Obstacle detected - override patrol pattern
        if (sensorData.distanceCm < Constants.OBSTACLE_SLOW_CM) {
            // Turn to avoid obstacle
            Logger.d(Constants.TAG_DECISION, "Goal: PATROL -> Turn (obstacle)")
            patrolStartTime = now
            patrolPhase = (patrolPhase + 1) % 4
            return RoverCommand(RoverCommand.RIGHT, Constants.DEFAULT_MOTOR_SPEED)
        }
        
        // Execute patrol pattern
        return when {
            phaseElapsed < PATROL_LEG_DURATION_MS -> {
                // Move forward
                Logger.d(Constants.TAG_DECISION, "Goal: PATROL -> Forward (phase $patrolPhase)")
                RoverCommand(RoverCommand.FORWARD, Constants.DEFAULT_MOTOR_SPEED)
            }
            
            else -> {
                // Turn right and start next leg
                patrolStartTime = now
                patrolPhase = (patrolPhase + 1) % 4
                Logger.d(Constants.TAG_DECISION, "Goal: PATROL -> Turn right (next leg)")
                RoverCommand(RoverCommand.RIGHT, Constants.DEFAULT_MOTOR_SPEED)
            }
        }
    }
    
    /**
     * RETURN_HOME behavior: Navigate back to starting position.
     * 
     * Currently implements simple backwards motion.
     * In production, this would use SLAM and path planning.
     * 
     * @param sensorData Current sensor readings
     * @return Return home command
     */
    fun returnHomeCommand(sensorData: StateManager.SensorData): RoverCommand {
        // Simple implementation: just back up slowly
        // TODO: Integrate with SpatialMap for proper navigation
        
        Logger.d(Constants.TAG_DECISION, "Goal: RETURN_HOME -> Backward (simple)")
        val slowSpeed = (Constants.DEFAULT_MOTOR_SPEED * SLOW_SPEED_FACTOR).toInt()
        return RoverCommand(RoverCommand.BACKWARD, slowSpeed)
    }
    
    /**
     * Reset generator state.
     * Clears exploration and patrol tracking.
     */
    fun reset() {
        lastExploreDirection = RoverCommand.FORWARD
        lastExploreChangeTime = 0L
        patrolPhase = 0
        patrolStartTime = 0L
        Logger.d(Constants.TAG_DECISION, "Goal command generator reset")
    }
}
