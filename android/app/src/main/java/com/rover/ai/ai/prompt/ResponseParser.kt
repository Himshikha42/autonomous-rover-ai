package com.rover.ai.ai.prompt

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parsed scene analysis result
 * 
 * @property clear Whether the path ahead is clear
 * @property obstacles List of detected obstacles
 * @property humanCount Number of humans in scene
 * @property action Suggested rover action
 * @property emotion Emotional response
 */
data class SceneAnalysis(
    val clear: Boolean,
    val obstacles: List<String>,
    val humanCount: Int,
    val action: String,
    val emotion: String
)

/**
 * Parsed voice command result
 * 
 * @property command Command name (forward, stop, etc.)
 * @property parameters Optional command parameters
 * @property emotion Emotional response
 */
data class VoiceCommand(
    val command: String,
    val parameters: Map<String, Any>,
    val emotion: String
)

/**
 * Robust JSON parser for Gemma model outputs
 * 
 * Handles:
 * - Malformed JSON with fallback parsing
 * - Missing fields with sensible defaults
 * - Type mismatches with conversion
 * - Extra text before/after JSON
 * 
 * Singleton object (stateless utility).
 */
object ResponseParser {
    
    private val tag = Constants.TAG_AI
    
    /**
     * Parse scene analysis JSON response
     * 
     * Expected format:
     * {"clear":bool,"obs":[str],"human":int,"act":str,"emo":str}
     * 
     * @param jsonString Raw JSON string from model
     * @return SceneAnalysis object, or null if parsing fails completely
     */
    fun parseSceneAnalysis(jsonString: String): SceneAnalysis? {
        Logger.d(tag, "Parsing scene analysis response")
        
        return try {
            val cleaned = extractJson(jsonString)
            if (cleaned.isEmpty()) {
                Logger.e(tag, "No JSON found in response")
                return null
            }
            
            val json = JSONObject(cleaned)
            
            // Extract fields with fallback defaults
            val clear = json.optBoolean("clear", false)
            val obstacles = parseObstacleList(json.optJSONArray("obs"))
            val humanCount = json.optInt("human", 0)
            val action = json.optString("act", "stop")
            val emotion = json.optString("emo", "neutral")
            
            val result = SceneAnalysis(
                clear = clear,
                obstacles = obstacles,
                humanCount = humanCount,
                action = action,
                emotion = emotion
            )
            
            Logger.d(tag, "Parsed: clear=$clear, obstacles=${obstacles.size}, humans=$humanCount")
            
            result
        } catch (e: JSONException) {
            Logger.e(tag, "JSON parsing failed", e)
            // Try fallback parsing
            tryFallbackParsing(jsonString)
        } catch (e: Exception) {
            Logger.e(tag, "Unexpected parsing error", e)
            null
        }
    }
    
    /**
     * Parse voice command JSON response
     * 
     * Expected format:
     * {"cmd":str,"params":{},"emo":str}
     * 
     * @param jsonString Raw JSON string
     * @return VoiceCommand object, or null if parsing fails
     */
    fun parseVoiceCommand(jsonString: String): VoiceCommand? {
        Logger.d(tag, "Parsing voice command response")
        
        return try {
            val cleaned = extractJson(jsonString)
            if (cleaned.isEmpty()) {
                return null
            }
            
            val json = JSONObject(cleaned)
            
            val command = json.optString("cmd", "stop")
            val params = parseParameters(json.optJSONObject("params"))
            val emotion = json.optString("emo", "neutral")
            
            VoiceCommand(
                command = command,
                parameters = params,
                emotion = emotion
            )
        } catch (e: Exception) {
            Logger.e(tag, "Voice command parsing failed", e)
            null
        }
    }
    
    /**
     * Extract JSON substring from text
     * 
     * Handles cases where model outputs extra text before/after JSON.
     * Looks for first '{' and matching '}'.
     */
    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        if (start == -1) {
            return ""
        }
        
        var depth = 0
        var end = start
        
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        
        return if (end > start) {
            text.substring(start, end + 1)
        } else {
            ""
        }
    }
    
    /**
     * Parse obstacle list from JSON array
     * 
     * Handles null, empty, or malformed arrays.
     */
    private fun parseObstacleList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) {
            return emptyList()
        }
        
        val obstacles = mutableListOf<String>()
        
        try {
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (item.isNotEmpty()) {
                    obstacles.add(item)
                }
            }
        } catch (e: Exception) {
            Logger.w(tag, "Error parsing obstacle array", e)
        }
        
        return obstacles
    }
    
    /**
     * Parse parameters object to Map
     */
    private fun parseParameters(jsonObject: JSONObject?): Map<String, Any> {
        if (jsonObject == null) {
            return emptyMap()
        }
        
        val params = mutableMapOf<String, Any>()
        
        try {
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.get(key)
                params[key] = value
            }
        } catch (e: Exception) {
            Logger.w(tag, "Error parsing parameters", e)
        }
        
        return params
    }
    
    /**
     * Fallback parsing for malformed JSON
     * 
     * Attempts to extract key information using string matching.
     */
    private fun tryFallbackParsing(text: String): SceneAnalysis? {
        Logger.w(tag, "Attempting fallback parsing")
        
        return try {
            // Look for key indicators in text
            val clear = text.contains("clear\":true", ignoreCase = true) ||
                       text.contains("path is clear", ignoreCase = true)
            
            val humanCount = extractNumber(text, "human")
            
            val action = when {
                text.contains("stop", ignoreCase = true) -> "stop"
                text.contains("forward", ignoreCase = true) -> "forward"
                text.contains("back", ignoreCase = true) -> "back"
                text.contains("left", ignoreCase = true) -> "left"
                text.contains("right", ignoreCase = true) -> "right"
                else -> "stop"
            }
            
            Logger.i(tag, "Fallback parsing succeeded")
            
            SceneAnalysis(
                clear = clear,
                obstacles = emptyList(),
                humanCount = humanCount,
                action = action,
                emotion = "uncertain"
            )
        } catch (e: Exception) {
            Logger.e(tag, "Fallback parsing also failed", e)
            null
        }
    }
    
    /**
     * Extract number from text
     * 
     * Looks for pattern like "key":123 or "key":123,
     */
    private fun extractNumber(text: String, key: String): Int {
        return try {
            val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
            val match = pattern.find(text)
            match?.groupValues?.get(1)?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Validate scene analysis result
     * 
     * Check if result is reasonable and safe.
     */
    fun validateSceneAnalysis(analysis: SceneAnalysis): Boolean {
        // Validate action is known command
        val validActions = setOf("stop", "forward", "back", "left", "right", "explore", "follow")
        if (analysis.action.lowercase() !in validActions) {
            Logger.w(tag, "Invalid action: ${analysis.action}")
            return false
        }
        
        // Validate human count is reasonable
        if (analysis.humanCount < 0 || analysis.humanCount > 10) {
            Logger.w(tag, "Unreasonable human count: ${analysis.humanCount}")
            return false
        }
        
        return true
    }
}
