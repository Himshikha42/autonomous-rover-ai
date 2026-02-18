package com.rover.ai.ai.prompt

import android.graphics.Bitmap
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Build compact, optimized prompts for Gemma 3n model
 * 
 * Responsibilities:
 * - Inject sensor data into prompt templates
 * - Keep prompts under token budget (128 tokens)
 * - Escape special characters for JSON safety
 * - Format multimodal inputs (text + image)
 * 
 * Thread-safe singleton managed by Hilt.
 */
@Singleton
class RoverPromptBuilder @Inject constructor() {
    
    private val tag = Constants.TAG_AI
    
    /**
     * Build scene analysis prompt with image
     * 
     * Uses minimal template for fast inference.
     * 
     * @param image Camera frame to analyze
     * @param sensorData Current sensor readings
     * @return Formatted prompt string
     */
    fun buildSceneAnalysis(
        image: Bitmap,
        sensorData: StateManager.SensorData
    ): String {
        Logger.d(tag, "Building scene analysis prompt")
        
        return try {
            // Use basic template without sensors for speed
            val prompt = PromptTemplates.SCENE_ANALYSIS
            
            // Log image metadata
            Logger.d(tag, "Image: ${image.width}x${image.height}")
            Logger.d(tag, "Distance: ${sensorData.distanceCm}cm")
            
            PromptTemplates.withSystemPrompt(prompt)
        } catch (e: Exception) {
            Logger.e(tag, "Error building scene analysis prompt", e)
            PromptTemplates.SCENE_ANALYSIS
        }
    }
    
    /**
     * Build scene analysis WITH sensor data context
     * 
     * Includes distance, battery, and state for better decision making.
     * 
     * @param image Camera frame
     * @param sensorData Sensor readings
     * @param battery Battery level percentage
     * @return Formatted prompt with sensor data
     */
    fun buildWithSensors(
        image: Bitmap,
        sensorData: StateManager.SensorData,
        battery: Float
    ): String {
        Logger.d(tag, "Building prompt with sensors")
        
        return try {
            // Determine current state based on sensors
            val state = when {
                sensorData.distanceCm < Constants.OBSTACLE_STOP_CM -> "BLOCKED"
                sensorData.cliffDetected -> "CLIFF"
                sensorData.lineFollowMode -> "LINE_FOLLOW"
                battery < Constants.LOW_BATTERY_THRESHOLD -> "LOW_BAT"
                else -> "NORMAL"
            }
            
            val substitutions = mapOf(
                "DISTANCE" to String.format("%.0f", sensorData.distanceCm),
                "BATTERY" to String.format("%.0f", battery),
                "STATE" to state
            )
            
            val prompt = PromptTemplates.substitute(
                PromptTemplates.WITH_SENSORS,
                substitutions
            )
            
            Logger.d(tag, "Sensor context: $state, ${sensorData.distanceCm}cm, ${battery}%")
            
            PromptTemplates.withSystemPrompt(prompt)
        } catch (e: Exception) {
            Logger.e(tag, "Error building sensor prompt", e)
            // Fallback to basic prompt
            buildSceneAnalysis(image, sensorData)
        }
    }
    
    /**
     * Build voice command processing prompt
     * 
     * Convert speech-to-text into rover action command.
     * 
     * @param audioText Transcribed speech text
     * @return Formatted prompt for command parsing
     */
    fun buildVoiceCommand(audioText: String): String {
        Logger.d(tag, "Building voice command prompt")
        
        return try {
            // Sanitize and truncate text
            val sanitized = sanitizeText(audioText)
            val truncated = truncateToLength(sanitized, 100)
            
            val substitutions = mapOf(
                "SPEECH_TEXT" to truncated
            )
            
            val prompt = PromptTemplates.substitute(
                PromptTemplates.VOICE_COMMAND,
                substitutions
            )
            
            Logger.d(tag, "Voice input: $truncated")
            
            PromptTemplates.withSystemPrompt(prompt)
        } catch (e: Exception) {
            Logger.e(tag, "Error building voice command prompt", e)
            PromptTemplates.VOICE_COMMAND
        }
    }
    
    /**
     * Build natural conversation prompt
     * 
     * For friendly interaction with humans.
     * 
     * @param userMessage User's message
     * @param context Conversation context (previous messages, state)
     * @return Formatted conversation prompt
     */
    fun buildConversation(
        userMessage: String,
        context: String = ""
    ): String {
        Logger.d(tag, "Building conversation prompt")
        
        return try {
            val sanitizedMessage = sanitizeText(userMessage)
            val truncatedMessage = truncateToLength(sanitizedMessage, 80)
            
            val sanitizedContext = sanitizeText(context)
            val truncatedContext = truncateToLength(sanitizedContext, 50)
            
            val substitutions = mapOf(
                "MESSAGE" to truncatedMessage,
                "CONTEXT" to truncatedContext.ifEmpty { "none" }
            )
            
            val prompt = PromptTemplates.substitute(
                PromptTemplates.CONVERSATION,
                substitutions
            )
            
            Logger.d(tag, "Conversation: $truncatedMessage")
            
            PromptTemplates.withSystemPrompt(prompt)
        } catch (e: Exception) {
            Logger.e(tag, "Error building conversation prompt", e)
            PromptTemplates.CONVERSATION
        }
    }
    
    /**
     * Build obstacle avoidance prompt
     * 
     * @param obstacleType Type of obstacle detected
     * @param sensorData Current sensor readings
     * @return Formatted prompt for avoidance decision
     */
    fun buildObstacleAvoidance(
        obstacleType: String,
        sensorData: StateManager.SensorData
    ): String {
        Logger.d(tag, "Building obstacle avoidance prompt")
        
        return try {
            val substitutions = mapOf(
                "OBSTACLE_TYPE" to sanitizeText(obstacleType)
            )
            
            val prompt = PromptTemplates.substitute(
                PromptTemplates.OBSTACLE_AVOIDANCE,
                substitutions
            )
            
            PromptTemplates.withSystemPrompt(prompt)
        } catch (e: Exception) {
            Logger.e(tag, "Error building obstacle prompt", e)
            PromptTemplates.OBSTACLE_AVOIDANCE
        }
    }
    
    /**
     * Sanitize text for safe JSON inclusion
     * 
     * Escapes quotes, newlines, and control characters.
     */
    private fun sanitizeText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
            .replace("\t", " ")
            .trim()
    }
    
    /**
     * Truncate text to maximum length
     * 
     * Ensures prompts stay within token budget.
     */
    private fun truncateToLength(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            Logger.w(tag, "Truncating text from ${text.length} to $maxLength chars")
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
    }
    
    /**
     * Estimate token count (rough approximation)
     * 
     * Assumes ~4 characters per token on average.
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 4) + text.count { it.isWhitespace() }
    }
}
