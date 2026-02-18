package com.rover.ai.memory

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short-term memory system using RAM-based circular buffers.
 * 
 * Stores transient data with automatic expiration:
 * - Recent objects detected by vision system
 * - Last known human position
 * - Recent commands sent to ESP32
 * - Recent Gemma AI responses
 * 
 * All data expires after TTL (default 5 minutes) and capacity is limited.
 * Thread-safe using Mutex for write operations and concurrent collections.
 * 
 * @property capacity Maximum number of entries per buffer (default from Constants)
 * @property ttlMs Time-to-live in milliseconds (default from Constants)
 */
@Singleton
class ShortTermMemory @Inject constructor() {
    
    private val capacity: Int = Constants.SHORT_TERM_MEMORY_CAPACITY
    private val ttlMs: Long = Constants.SHORT_TERM_MEMORY_TTL_MS
    
    private val mutex = Mutex()
    
    // Circular buffers using concurrent deque for thread-safety
    private val objects = ConcurrentLinkedDeque<TimestampedObject>()
    private val commands = ConcurrentLinkedDeque<TimestampedCommand>()
    private val gemmaResponses = ConcurrentLinkedDeque<TimestampedResponse>()
    
    // Human position is single-value, protected by mutex
    @Volatile
    private var humanPosition: TimestampedPosition? = null
    
    init {
        Logger.i(Constants.TAG_MEMORY, "ShortTermMemory initialized (capacity=$capacity, ttl=${ttlMs}ms)")
    }
    
    /**
     * Data class for detected objects with timestamp
     */
    data class TimestampedObject(
        val timestamp: Long,
        val objectClass: String,
        val confidence: Float,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
    
    /**
     * Data class for sent commands with timestamp
     */
    data class TimestampedCommand(
        val timestamp: Long,
        val command: String,
        val parameters: Map<String, Any>
    )
    
    /**
     * Data class for Gemma responses with timestamp
     */
    data class TimestampedResponse(
        val timestamp: Long,
        val prompt: String,
        val response: String,
        val tokensUsed: Int
    )
    
    /**
     * Data class for human position with timestamp
     */
    data class TimestampedPosition(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val confidence: Float
    )
    
    /**
     * Add a detected object to short-term memory.
     * Automatically removes expired entries and enforces capacity limit.
     * 
     * @param objectClass The class/label of the detected object (e.g., "person", "ball")
     * @param confidence Detection confidence (0.0 to 1.0)
     * @param x Bounding box center X coordinate (normalized 0-1)
     * @param y Bounding box center Y coordinate (normalized 0-1)
     * @param width Bounding box width (normalized 0-1)
     * @param height Bounding box height (normalized 0-1)
     */
    suspend fun addObject(
        objectClass: String,
        confidence: Float,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        val obj = TimestampedObject(
            timestamp = System.currentTimeMillis(),
            objectClass = objectClass,
            confidence = confidence,
            x = x,
            y = y,
            width = width,
            height = height
        )
        
        mutex.withLock {
            objects.addLast(obj)
            enforceCapacityAndTTL(objects)
        }
        
        Logger.d(Constants.TAG_MEMORY, "Added object: $objectClass (conf=${String.format("%.2f", confidence)})")
    }
    
    /**
     * Get recent objects detected within the TTL window.
     * 
     * @param minConfidence Optional minimum confidence threshold (default 0.0)
     * @param objectClass Optional filter by specific object class
     * @return List of recent objects, most recent first
     */
    suspend fun getRecentObjects(
        minConfidence: Float = 0.0f,
        objectClass: String? = null
    ): List<TimestampedObject> {
        mutex.withLock {
            enforceCapacityAndTTL(objects)
        }
        
        val now = System.currentTimeMillis()
        return objects
            .filter { now - it.timestamp <= ttlMs }
            .filter { it.confidence >= minConfidence }
            .filter { objectClass == null || it.objectClass == objectClass }
            .sortedByDescending { it.timestamp }
    }
    
    /**
     * Set the last known human position.
     * Overwrites any previous position.
     * 
     * @param x Position X coordinate (normalized or in map coordinates)
     * @param y Position Y coordinate (normalized or in map coordinates)
     * @param confidence Detection confidence (0.0 to 1.0)
     */
    suspend fun setHumanPosition(x: Float, y: Float, confidence: Float) {
        mutex.withLock {
            humanPosition = TimestampedPosition(
                timestamp = System.currentTimeMillis(),
                x = x,
                y = y,
                confidence = confidence
            )
        }
        
        Logger.d(Constants.TAG_MEMORY, "Human position updated: ($x, $y) conf=${String.format("%.2f", confidence)}")
    }
    
    /**
     * Get the last known human position if within TTL.
     * 
     * @return Last known human position, or null if expired or never set
     */
    suspend fun getHumanPosition(): TimestampedPosition? {
        val position = humanPosition
        if (position != null) {
            val age = System.currentTimeMillis() - position.timestamp
            if (age > ttlMs) {
                mutex.withLock {
                    humanPosition = null
                }
                return null
            }
        }
        return position
    }
    
    /**
     * Add a command sent to ESP32 to short-term memory.
     * 
     * @param command The command string (e.g., "MOVE_FORWARD", "TURN_LEFT")
     * @param parameters Optional parameters sent with the command
     */
    suspend fun addCommand(command: String, parameters: Map<String, Any> = emptyMap()) {
        val cmd = TimestampedCommand(
            timestamp = System.currentTimeMillis(),
            command = command,
            parameters = parameters
        )
        
        mutex.withLock {
            commands.addLast(cmd)
            enforceCapacityAndTTL(commands)
        }
        
        Logger.d(Constants.TAG_MEMORY, "Added command: $command ${if (parameters.isNotEmpty()) parameters else ""}")
    }
    
    /**
     * Get recent commands sent within the TTL window.
     * 
     * @param limit Maximum number of commands to return (default: all)
     * @return List of recent commands, most recent first
     */
    suspend fun getRecentCommands(limit: Int? = null): List<TimestampedCommand> {
        mutex.withLock {
            enforceCapacityAndTTL(commands)
        }
        
        val now = System.currentTimeMillis()
        val filtered = commands
            .filter { now - it.timestamp <= ttlMs }
            .sortedByDescending { it.timestamp }
        
        return if (limit != null && limit > 0) {
            filtered.take(limit)
        } else {
            filtered
        }
    }
    
    /**
     * Add a Gemma AI response to short-term memory.
     * 
     * @param prompt The input prompt sent to Gemma
     * @param response The text response from Gemma
     * @param tokensUsed Number of tokens used in generation
     */
    suspend fun addGemmaResponse(prompt: String, response: String, tokensUsed: Int = 0) {
        val resp = TimestampedResponse(
            timestamp = System.currentTimeMillis(),
            prompt = prompt,
            response = response,
            tokensUsed = tokensUsed
        )
        
        mutex.withLock {
            gemmaResponses.addLast(resp)
            enforceCapacityAndTTL(gemmaResponses)
        }
        
        Logger.d(Constants.TAG_MEMORY, "Added Gemma response: ${response.take(50)}...")
    }
    
    /**
     * Get recent Gemma responses within the TTL window.
     * 
     * @param limit Maximum number of responses to return (default: all)
     * @return List of recent responses, most recent first
     */
    suspend fun getRecentGemmaResponses(limit: Int? = null): List<TimestampedResponse> {
        mutex.withLock {
            enforceCapacityAndTTL(gemmaResponses)
        }
        
        val now = System.currentTimeMillis()
        val filtered = gemmaResponses
            .filter { now - it.timestamp <= ttlMs }
            .sortedByDescending { it.timestamp }
        
        return if (limit != null && limit > 0) {
            filtered.take(limit)
        } else {
            filtered
        }
    }
    
    /**
     * Clear all short-term memory buffers.
     * Use cautiously - this removes all transient state.
     */
    suspend fun clear() {
        mutex.withLock {
            objects.clear()
            commands.clear()
            gemmaResponses.clear()
            humanPosition = null
        }
        
        Logger.i(Constants.TAG_MEMORY, "Short-term memory cleared")
    }
    
    /**
     * Enforce capacity limit and TTL expiration on a deque.
     * Removes oldest entries if over capacity, and all expired entries.
     * Must be called within mutex lock.
     */
    private fun <T> enforceCapacityAndTTL(deque: ConcurrentLinkedDeque<T>) where T : Any {
        val now = System.currentTimeMillis()
        
        // Remove expired entries from the front (oldest)
        while (deque.isNotEmpty()) {
            val first = deque.peekFirst()
            val timestamp = when (first) {
                is TimestampedObject -> first.timestamp
                is TimestampedCommand -> first.timestamp
                is TimestampedResponse -> first.timestamp
                else -> break
            }
            
            if (now - timestamp > ttlMs) {
                deque.pollFirst()
            } else {
                break
            }
        }
        
        // Enforce capacity limit by removing oldest entries
        while (deque.size > capacity) {
            deque.pollFirst()
        }
    }
    
    /**
     * Get memory statistics for monitoring and debugging.
     * 
     * @return Map containing size and capacity info for each buffer
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "objects_count" to objects.size,
            "commands_count" to commands.size,
            "gemma_responses_count" to gemmaResponses.size,
            "has_human_position" to (humanPosition != null),
            "capacity" to capacity,
            "ttl_ms" to ttlMs
        )
    }
}
