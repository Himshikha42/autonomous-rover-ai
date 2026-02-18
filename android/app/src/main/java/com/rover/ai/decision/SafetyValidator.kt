package com.rover.ai.decision

import com.rover.ai.communication.RoverCommand
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safety validator for all rover commands.
 * 
 * Acts as a safety layer between decision systems and the rover hardware.
 * All commands must pass validation before being sent to the ESP32.
 * 
 * Validation Rules:
 * 1. Block FORWARD when obstacle < 15cm
 * 2. Block ALL movement when cliff detected
 * 3. Allow STOP always (safety override)
 * 4. Allow BACKWARD even when forward obstacle detected
 * 5. Rate limit commands to prevent spam (max 10/second)
 * 6. Log all blocked commands with reason
 * 
 * @property stateManager Global state for sensor data access
 */
@Singleton
class SafetyValidator @Inject constructor(
    private val stateManager: StateManager
) {
    
    private var lastCommandTime: Long = 0L
    private var commandCount: Int = 0
    private var rateLimitWindowStart: Long = 0L
    
    companion object {
        private const val MAX_COMMANDS_PER_SECOND = 10
        private const val RATE_LIMIT_WINDOW_MS = 1000L
    }
    
    /**
     * Validate a rover command against current sensor data.
     * 
     * Checks all safety constraints and returns whether the command
     * is safe to execute along with a reason if blocked.
     * 
     * @param command The command to validate
     * @param sensorData Current sensor readings from ESP32
     * @return Pair of (allowed: Boolean, reason: String?) where reason is non-null if blocked
     */
    fun validateCommand(
        command: RoverCommand,
        sensorData: StateManager.SensorData
    ): Pair<Boolean, String?> {
        
        val now = System.currentTimeMillis()
        
        // Check rate limiting
        if (now - rateLimitWindowStart > RATE_LIMIT_WINDOW_MS) {
            // Reset rate limit window
            rateLimitWindowStart = now
            commandCount = 0
        }
        
        commandCount++
        if (commandCount > MAX_COMMANDS_PER_SECOND) {
            val reason = "Rate limit exceeded: $commandCount commands in ${now - rateLimitWindowStart}ms"
            Logger.w(Constants.TAG_DECISION, "BLOCKED: ${command.cmd} - $reason")
            return Pair(false, reason)
        }
        
        // STOP is always allowed (emergency override)
        if (command.cmd == RoverCommand.STOP) {
            Logger.d(Constants.TAG_DECISION, "Command allowed: STOP (always safe)")
            lastCommandTime = now
            return Pair(true, null)
        }
        
        // Check cliff detection - blocks ALL movement
        if (sensorData.cliffDetected) {
            val reason = "Cliff detected - blocking all movement"
            Logger.w(Constants.TAG_DECISION, "BLOCKED: ${command.cmd} - $reason")
            return Pair(false, reason)
        }
        
        // Check edge detection - blocks forward movement
        if (sensorData.edgeDetected && command.cmd == RoverCommand.FORWARD) {
            val reason = "Edge detected - blocking forward movement"
            Logger.w(Constants.TAG_DECISION, "BLOCKED: ${command.cmd} - $reason")
            return Pair(false, reason)
        }
        
        // Check obstacle distance for forward movement
        if (command.cmd == RoverCommand.FORWARD) {
            if (sensorData.distanceCm < Constants.OBSTACLE_STOP_CM) {
                val reason = "Obstacle too close: ${sensorData.distanceCm}cm < ${Constants.OBSTACLE_STOP_CM}cm"
                Logger.w(Constants.TAG_DECISION, "BLOCKED: ${command.cmd} - $reason")
                return Pair(false, reason)
            }
        }
        
        // BACKWARD is allowed even with forward obstacle
        // LEFT and RIGHT are allowed unless cliff detected (already checked above)
        
        // Validate motor speed is within bounds
        if (command.speed < Constants.MIN_MOTOR_SPEED || command.speed > Constants.MAX_MOTOR_SPEED) {
            val reason = "Invalid speed: ${command.speed} (must be ${Constants.MIN_MOTOR_SPEED}-${Constants.MAX_MOTOR_SPEED})"
            Logger.w(Constants.TAG_DECISION, "BLOCKED: ${command.cmd} - $reason")
            return Pair(false, reason)
        }
        
        // Command passed all validation checks
        Logger.d(
            Constants.TAG_DECISION,
            "Command validated: ${command.cmd} speed=${command.speed} " +
            "(dist=${sensorData.distanceCm}cm, cliff=${sensorData.cliffDetected})"
        )
        lastCommandTime = now
        return Pair(true, null)
    }
    
    /**
     * Check if enough time has passed since last command.
     * Used to enforce minimum delay between commands.
     * 
     * @return true if rate limit allows another command
     */
    fun canSendCommand(): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastCommandTime
        return elapsed >= Constants.COMMAND_RATE_LIMIT_MS
    }
    
    /**
     * Get time until next command can be sent (in milliseconds).
     * 
     * @return Milliseconds to wait, or 0 if command can be sent now
     */
    fun getCommandCooldown(): Long {
        val now = System.currentTimeMillis()
        val elapsed = now - lastCommandTime
        val remaining = Constants.COMMAND_RATE_LIMIT_MS - elapsed
        return remaining.coerceAtLeast(0L)
    }
    
    /**
     * Check if a command is safe without updating internal state.
     * Useful for preview/testing without affecting rate limits.
     * 
     * @param command Command to check
     * @param sensorData Current sensor data
     * @return true if command would be allowed
     */
    fun isSafe(
        command: RoverCommand,
        sensorData: StateManager.SensorData
    ): Boolean {
        // Always allow STOP
        if (command.cmd == RoverCommand.STOP) {
            return true
        }
        
        // Check cliff
        if (sensorData.cliffDetected) {
            return false
        }
        
        // Check edge for forward
        if (sensorData.edgeDetected && command.cmd == RoverCommand.FORWARD) {
            return false
        }
        
        // Check obstacle for forward
        if (command.cmd == RoverCommand.FORWARD) {
            if (sensorData.distanceCm < Constants.OBSTACLE_STOP_CM) {
                return false
            }
        }
        
        // Check speed bounds
        if (command.speed < Constants.MIN_MOTOR_SPEED || command.speed > Constants.MAX_MOTOR_SPEED) {
            return false
        }
        
        return true
    }
    
    /**
     * Reset rate limiting state.
     * Useful for testing or after emergency stop.
     */
    fun resetRateLimiting() {
        commandCount = 0
        rateLimitWindowStart = System.currentTimeMillis()
        Logger.d(Constants.TAG_DECISION, "Rate limiting reset")
    }
    
    /**
     * Get statistics about command validation.
     * Useful for debugging and monitoring.
     * 
     * @return Map of validation statistics
     */
    fun getStatistics(): Map<String, Any> {
        val now = System.currentTimeMillis()
        return mapOf(
            "lastCommandTime" to lastCommandTime,
            "timeSinceLastCommand" to (now - lastCommandTime),
            "commandsInWindow" to commandCount,
            "rateLimitWindowAge" to (now - rateLimitWindowStart),
            "canSendNow" to canSendCommand()
        )
    }
}
