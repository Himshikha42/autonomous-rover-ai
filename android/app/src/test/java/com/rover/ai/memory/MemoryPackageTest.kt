package com.rover.ai.memory

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Example unit tests for the Memory package.
 * 
 * These tests demonstrate usage patterns and verify basic functionality.
 * Note: In real Android tests, you would use a test runner and potentially mock dependencies.
 */
class MemoryPackageTest {
    
    private lateinit var shortTermMemory: ShortTermMemory
    private lateinit var spatialMap: SpatialMap
    private lateinit var longTermMemory: LongTermMemory
    
    @Before
    fun setup() {
        shortTermMemory = ShortTermMemory()
        spatialMap = SpatialMap()
        longTermMemory = LongTermMemoryStub()
    }
    
    @Test
    fun testShortTermMemory_addAndRetrieveObjects() = runBlocking {
        // Add some detected objects
        shortTermMemory.addObject("person", 0.95f, 0.5f, 0.5f, 0.2f, 0.3f)
        shortTermMemory.addObject("ball", 0.82f, 0.3f, 0.7f, 0.1f, 0.1f)
        shortTermMemory.addObject("person", 0.88f, 0.6f, 0.4f, 0.25f, 0.35f)
        
        // Retrieve all recent objects
        val allObjects = shortTermMemory.getRecentObjects()
        assertEquals(3, allObjects.size)
        
        // Filter by object class
        val persons = shortTermMemory.getRecentObjects(objectClass = "person")
        assertEquals(2, persons.size)
        
        // Filter by confidence
        val highConfidence = shortTermMemory.getRecentObjects(minConfidence = 0.9f)
        assertEquals(1, highConfidence.size)
        assertEquals("person", highConfidence[0].objectClass)
    }
    
    @Test
    fun testShortTermMemory_humanPosition() = runBlocking {
        // No position initially
        assertNull(shortTermMemory.getHumanPosition())
        
        // Set human position
        shortTermMemory.setHumanPosition(25.0f, 30.0f, 0.95f)
        
        // Retrieve human position
        val position = shortTermMemory.getHumanPosition()
        assertNotNull(position)
        assertEquals(25.0f, position?.x ?: 0f, 0.001f)
        assertEquals(30.0f, position?.y ?: 0f, 0.001f)
        assertEquals(0.95f, position?.confidence ?: 0f, 0.001f)
    }
    
    @Test
    fun testShortTermMemory_commands() = runBlocking {
        // Add commands
        shortTermMemory.addCommand("MOVE_FORWARD", mapOf("speed" to 180))
        shortTermMemory.addCommand("TURN_LEFT", mapOf("angle" to 90))
        shortTermMemory.addCommand("STOP", emptyMap())
        
        // Retrieve recent commands
        val commands = shortTermMemory.getRecentCommands()
        assertEquals(3, commands.size)
        
        // Most recent first
        assertEquals("STOP", commands[0].command)
        assertEquals("TURN_LEFT", commands[1].command)
        assertEquals("MOVE_FORWARD", commands[2].command)
        
        // Retrieve with limit
        val lastTwo = shortTermMemory.getRecentCommands(limit = 2)
        assertEquals(2, lastTwo.size)
    }
    
    @Test
    fun testShortTermMemory_gemmaResponses() = runBlocking {
        // Add Gemma responses
        shortTermMemory.addGemmaResponse(
            "What do you see?",
            "I see a person and a ball in front of me.",
            42
        )
        
        shortTermMemory.addGemmaResponse(
            "Go to the ball",
            "Moving towards the ball now.",
            28
        )
        
        // Retrieve responses
        val responses = shortTermMemory.getRecentGemmaResponses()
        assertEquals(2, responses.size)
        assertEquals("Moving towards the ball now.", responses[0].response)
    }
    
    @Test
    fun testShortTermMemory_clear() = runBlocking {
        // Add data
        shortTermMemory.addObject("person", 0.9f, 0.5f, 0.5f, 0.2f, 0.3f)
        shortTermMemory.setHumanPosition(25.0f, 30.0f, 0.95f)
        shortTermMemory.addCommand("MOVE", emptyMap())
        
        // Verify data exists
        assertEquals(1, shortTermMemory.getRecentObjects().size)
        assertNotNull(shortTermMemory.getHumanPosition())
        assertEquals(1, shortTermMemory.getRecentCommands().size)
        
        // Clear all
        shortTermMemory.clear()
        
        // Verify empty
        assertEquals(0, shortTermMemory.getRecentObjects().size)
        assertNull(shortTermMemory.getHumanPosition())
        assertEquals(0, shortTermMemory.getRecentCommands().size)
    }
    
    @Test
    fun testSpatialMap_markAndGetCell() = runBlocking {
        // Mark a cell as occupied
        val success = spatialMap.markCell(10, 15, SpatialMap.CellType.OCCUPIED, 0.8f)
        assertTrue(success)
        
        // Retrieve the cell
        val cell = spatialMap.getCell(10, 15)
        assertNotNull(cell)
        assertEquals(SpatialMap.CellType.OCCUPIED, cell?.type)
        assertEquals(0.8f, cell?.confidence ?: 0f, 0.001f)
        
        // Invalid coordinates
        val invalidCell = spatialMap.getCell(100, 100)
        assertNull(invalidCell)
    }
    
    @Test
    fun testSpatialMap_landmarks() = runBlocking {
        // Add landmarks
        spatialMap.addLandmark(25, 25, "charging_station", 1.0f)
        spatialMap.addLandmark(10, 10, "ball_pile", 0.9f)
        spatialMap.addLandmark(40, 40, "home_base", 1.0f)
        
        // Query all landmarks
        val allLandmarks = spatialMap.queryLandmarks()
        assertEquals(3, allLandmarks.size)
        
        // Query by name filter
        val chargingStations = spatialMap.queryLandmarks("charging")
        assertEquals(1, chargingStations.size)
        assertEquals("charging_station", chargingStations[0].landmark)
        
        // Verify cell type
        val landmarkCell = spatialMap.getCell(25, 25)
        assertEquals(SpatialMap.CellType.LANDMARK, landmarkCell?.type)
    }
    
    @Test
    fun testSpatialMap_frontierCells() = runBlocking {
        // Create a small explored area with unknown surroundings
        // Mark center as FREE
        spatialMap.markCell(25, 25, SpatialMap.CellType.FREE, 1.0f)
        
        // Mark surrounding cells as FREE
        spatialMap.markCell(24, 25, SpatialMap.CellType.FREE, 0.9f)
        spatialMap.markCell(26, 25, SpatialMap.CellType.FREE, 0.9f)
        spatialMap.markCell(25, 24, SpatialMap.CellType.FREE, 0.9f)
        spatialMap.markCell(25, 26, SpatialMap.CellType.FREE, 0.9f)
        
        // Get frontier cells (FREE cells adjacent to UNKNOWN)
        val frontiers = spatialMap.getFrontierCells(minConfidence = 0.5f)
        
        // Should have frontiers since FREE cells are adjacent to UNKNOWN
        assertTrue(frontiers.isNotEmpty())
        
        // All frontier cells should be FREE type
        frontiers.forEach { cell ->
            assertEquals(SpatialMap.CellType.FREE, cell.type)
        }
    }
    
    @Test
    fun testSpatialMap_updateFromUltrasonic() = runBlocking {
        // Simulate rover at center facing north (heading = 0)
        val roverX = 25
        val roverY = 25
        val heading = 0.0f
        val distanceCm = 50.0f  // 5 cells away (10cm per cell)
        
        // Update map based on sensor reading
        spatialMap.updateFromUltrasonic(roverX, roverY, heading, distanceCm)
        
        // Cells between rover and obstacle should be marked FREE
        // The exact cell at obstacle should be OCCUPIED
        // Note: This is a simplified test, actual behavior depends on calculations
        
        val stats = spatialMap.getStats()
        assertTrue(stats["free_cells"] as Int > 0)
    }
    
    @Test
    fun testSpatialMap_reset() = runBlocking {
        // Mark some cells
        spatialMap.markCell(10, 10, SpatialMap.CellType.OCCUPIED, 0.8f)
        spatialMap.markCell(20, 20, SpatialMap.CellType.FREE, 0.9f)
        spatialMap.addLandmark(30, 30, "test", 1.0f)
        
        // Verify cells are marked
        var stats = spatialMap.getStats()
        assertTrue(stats["free_cells"] as Int > 0 || stats["occupied_cells"] as Int > 0)
        
        // Reset map
        spatialMap.reset()
        
        // All cells should be UNKNOWN
        stats = spatialMap.getStats()
        assertEquals(50 * 50, stats["unknown_cells"])
        assertEquals(0, stats["free_cells"])
        assertEquals(0, stats["occupied_cells"])
        assertEquals(0, stats["landmark_cells"])
    }
    
    @Test
    fun testSpatialMap_getCellsByType() = runBlocking {
        // Mark various cell types
        spatialMap.markCell(10, 10, SpatialMap.CellType.FREE, 0.9f)
        spatialMap.markCell(11, 10, SpatialMap.CellType.FREE, 0.8f)
        spatialMap.markCell(20, 20, SpatialMap.CellType.OCCUPIED, 0.95f)
        spatialMap.markCell(21, 20, SpatialMap.CellType.OCCUPIED, 0.7f)
        
        // Get FREE cells
        val freeCells = spatialMap.getCellsByType(SpatialMap.CellType.FREE, minConfidence = 0.0f)
        assertEquals(2, freeCells.size)
        
        // Get high-confidence FREE cells
        val highConfFreeCells = spatialMap.getCellsByType(SpatialMap.CellType.FREE, minConfidence = 0.85f)
        assertEquals(1, highConfFreeCells.size)
        
        // Get OCCUPIED cells
        val occupiedCells = spatialMap.getCellsByType(SpatialMap.CellType.OCCUPIED)
        assertEquals(2, occupiedCells.size)
    }
    
    @Test
    fun testSpatialMap_stats() = runBlocking {
        // Mark some cells
        spatialMap.markCell(10, 10, SpatialMap.CellType.FREE, 0.9f)
        spatialMap.markCell(20, 20, SpatialMap.CellType.OCCUPIED, 0.8f)
        spatialMap.addLandmark(30, 30, "test", 1.0f)
        
        val stats = spatialMap.getStats()
        
        // Verify stats structure
        assertTrue(stats.containsKey("grid_size"))
        assertTrue(stats.containsKey("cell_size_cm"))
        assertTrue(stats.containsKey("total_cells"))
        assertTrue(stats.containsKey("unknown_cells"))
        assertTrue(stats.containsKey("free_cells"))
        assertTrue(stats.containsKey("occupied_cells"))
        assertTrue(stats.containsKey("landmark_cells"))
        assertTrue(stats.containsKey("exploration_pct"))
        
        assertEquals(50, stats["grid_size"])
        assertEquals(10.0f, stats["cell_size_cm"])
        assertEquals(2500, stats["total_cells"])
    }
    
    @Test
    fun testLongTermMemory_stubOperations() = runBlocking {
        // All operations should log but not fail
        longTermMemory.saveObject("person", 0.9f, 10.0f, 20.0f)
        longTermMemory.saveLocation(15.0f, 25.0f, 90.0f, "landmark1")
        longTermMemory.saveInteraction("command", "MOVE_FORWARD", mapOf("speed" to "180"))
        longTermMemory.saveAchievement("first_detection", "Detected first object")
        
        // Queries should return empty lists (stub implementation)
        val objects = longTermMemory.getObjectHistory()
        assertEquals(0, objects.size)
        
        val locations = longTermMemory.getLocationHistory()
        assertEquals(0, locations.size)
        
        val interactions = longTermMemory.getInteractions()
        assertEquals(0, interactions.size)
        
        val achievements = longTermMemory.getAchievements()
        assertEquals(0, achievements.size)
    }
    
    @Test
    fun testShortTermMemory_stats() {
        val stats = shortTermMemory.getStats()
        
        // Verify stats structure
        assertTrue(stats.containsKey("objects_count"))
        assertTrue(stats.containsKey("commands_count"))
        assertTrue(stats.containsKey("gemma_responses_count"))
        assertTrue(stats.containsKey("has_human_position"))
        assertTrue(stats.containsKey("capacity"))
        assertTrue(stats.containsKey("ttl_ms"))
        
        assertEquals(100, stats["capacity"])
        assertEquals(300000L, stats["ttl_ms"])
    }
}
