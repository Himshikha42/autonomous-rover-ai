package com.rover.ai.memory

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Long-term memory interface and stub implementation.
 * 
 * Defines the contract for persistent storage of rover experiences:
 * - Object detection history
 * - Location history
 * - Interaction logs
 * - Achievement tracking
 * 
 * Current implementation is a stub that logs operations but doesn't persist.
 * Will be replaced with Room database implementation once entities are defined.
 * 
 * Thread-safe by design (Room handles threading).
 */
interface LongTermMemory {
    
    /**
     * Save a detected object to persistent storage.
     * 
     * @param objectClass The class/label of the object
     * @param confidence Detection confidence
     * @param x Position X coordinate
     * @param y Position Y coordinate
     * @param timestamp Detection timestamp
     */
    suspend fun saveObject(
        objectClass: String,
        confidence: Float,
        x: Float,
        y: Float,
        timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Retrieve object detection history.
     * 
     * @param objectClass Optional filter by object class
     * @param startTime Optional start time filter
     * @param endTime Optional end time filter
     * @param limit Maximum number of results
     * @return List of object detection records
     */
    suspend fun getObjectHistory(
        objectClass: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100
    ): List<ObjectRecord>
    
    /**
     * Save a location/position to persistent storage.
     * 
     * @param x Position X coordinate
     * @param y Position Y coordinate
     * @param heading Optional heading in degrees
     * @param landmark Optional associated landmark name
     * @param timestamp Position timestamp
     */
    suspend fun saveLocation(
        x: Float,
        y: Float,
        heading: Float? = null,
        landmark: String? = null,
        timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Retrieve location history.
     * 
     * @param startTime Optional start time filter
     * @param endTime Optional end time filter
     * @param limit Maximum number of results
     * @return List of location records
     */
    suspend fun getLocationHistory(
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100
    ): List<LocationRecord>
    
    /**
     * Save an interaction event (command, response, user action).
     * 
     * @param type Interaction type (e.g., "command", "speech", "gesture")
     * @param content Interaction content
     * @param metadata Optional metadata as key-value pairs
     * @param timestamp Interaction timestamp
     */
    suspend fun saveInteraction(
        type: String,
        content: String,
        metadata: Map<String, String> = emptyMap(),
        timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Retrieve interaction history.
     * 
     * @param type Optional filter by interaction type
     * @param startTime Optional start time filter
     * @param endTime Optional end time filter
     * @param limit Maximum number of results
     * @return List of interaction records
     */
    suspend fun getInteractions(
        type: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 100
    ): List<InteractionRecord>
    
    /**
     * Save an achievement/milestone.
     * 
     * @param name Achievement name/ID
     * @param description Human-readable description
     * @param metadata Optional metadata
     * @param timestamp Achievement timestamp
     */
    suspend fun saveAchievement(
        name: String,
        description: String,
        metadata: Map<String, String> = emptyMap(),
        timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Retrieve all achievements.
     * 
     * @param unlocked Optional filter by unlocked status
     * @return List of achievement records
     */
    suspend fun getAchievements(unlocked: Boolean? = null): List<AchievementRecord>
    
    /**
     * Data class for object detection records.
     */
    data class ObjectRecord(
        val id: Long = 0,
        val objectClass: String,
        val confidence: Float,
        val x: Float,
        val y: Float,
        val timestamp: Long
    )
    
    /**
     * Data class for location records.
     */
    data class LocationRecord(
        val id: Long = 0,
        val x: Float,
        val y: Float,
        val heading: Float?,
        val landmark: String?,
        val timestamp: Long
    )
    
    /**
     * Data class for interaction records.
     */
    data class InteractionRecord(
        val id: Long = 0,
        val type: String,
        val content: String,
        val metadata: Map<String, String>,
        val timestamp: Long
    )
    
    /**
     * Data class for achievement records.
     */
    data class AchievementRecord(
        val id: Long = 0,
        val name: String,
        val description: String,
        val metadata: Map<String, String>,
        val timestamp: Long,
        val unlocked: Boolean = true
    )
}

/**
 * Stub implementation of LongTermMemory.
 * 
 * Logs all operations but doesn't persist data yet.
 * Will be replaced with Room database implementation.
 */
@Singleton
class LongTermMemoryStub @Inject constructor() : LongTermMemory {
    
    init {
        Logger.i(Constants.TAG_MEMORY, "LongTermMemory stub initialized (no persistence)")
    }
    
    override suspend fun saveObject(
        objectClass: String,
        confidence: Float,
        x: Float,
        y: Float,
        timestamp: Long
    ) {
        Logger.d(Constants.TAG_MEMORY, "STUB: saveObject($objectClass, conf=$confidence, pos=($x,$y))")
        // TODO: Implement Room database persistence
    }
    
    override suspend fun getObjectHistory(
        objectClass: String?,
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<LongTermMemory.ObjectRecord> {
        Logger.d(Constants.TAG_MEMORY, "STUB: getObjectHistory(class=$objectClass, limit=$limit)")
        // TODO: Implement Room database query
        return emptyList()
    }
    
    override suspend fun saveLocation(
        x: Float,
        y: Float,
        heading: Float?,
        landmark: String?,
        timestamp: Long
    ) {
        Logger.d(Constants.TAG_MEMORY, "STUB: saveLocation(pos=($x,$y), heading=$heading, landmark=$landmark)")
        // TODO: Implement Room database persistence
    }
    
    override suspend fun getLocationHistory(
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<LongTermMemory.LocationRecord> {
        Logger.d(Constants.TAG_MEMORY, "STUB: getLocationHistory(limit=$limit)")
        // TODO: Implement Room database query
        return emptyList()
    }
    
    override suspend fun saveInteraction(
        type: String,
        content: String,
        metadata: Map<String, String>,
        timestamp: Long
    ) {
        Logger.d(Constants.TAG_MEMORY, "STUB: saveInteraction(type=$type, content=${content.take(30)}...)")
        // TODO: Implement Room database persistence
    }
    
    override suspend fun getInteractions(
        type: String?,
        startTime: Long?,
        endTime: Long?,
        limit: Int
    ): List<LongTermMemory.InteractionRecord> {
        Logger.d(Constants.TAG_MEMORY, "STUB: getInteractions(type=$type, limit=$limit)")
        // TODO: Implement Room database query
        return emptyList()
    }
    
    override suspend fun saveAchievement(
        name: String,
        description: String,
        metadata: Map<String, String>,
        timestamp: Long
    ) {
        Logger.i(Constants.TAG_MEMORY, "STUB: saveAchievement(name=$name, desc=$description)")
        // TODO: Implement Room database persistence
    }
    
    override suspend fun getAchievements(unlocked: Boolean?): List<LongTermMemory.AchievementRecord> {
        Logger.d(Constants.TAG_MEMORY, "STUB: getAchievements(unlocked=$unlocked)")
        // TODO: Implement Room database query
        return emptyList()
    }
}
