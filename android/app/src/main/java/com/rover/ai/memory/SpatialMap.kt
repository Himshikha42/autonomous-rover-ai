package com.rover.ai.memory

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Grid-based occupancy map for spatial memory and navigation.
 * 
 * Maintains a 2D grid representing the rover's understanding of its environment.
 * Each cell can be UNKNOWN, FREE, OCCUPIED, or a LANDMARK.
 * Used for path planning, exploration, and returning to known locations.
 * 
 * Grid coordinates:
 * - (0, 0) is at the top-left
 * - X increases to the right
 * - Y increases downward
 * 
 * Thread-safe using Mutex for all read/write operations.
 * 
 * @property gridSize Size of the grid (default from Constants)
 * @property cellSizeCm Size of each cell in centimeters (default from Constants)
 */
@Singleton
class SpatialMap @Inject constructor() {
    
    private val gridSize: Int = Constants.SPATIAL_MAP_GRID_SIZE
    private val cellSizeCm: Float = Constants.SPATIAL_MAP_CELL_SIZE_CM
    
    private val mutex = Mutex()
    
    // 2D array of grid cells
    private val grid: Array<Array<GridCell>> = Array(gridSize) { y ->
        Array(gridSize) { x ->
            GridCell(x = x, y = y, type = CellType.UNKNOWN, confidence = 0.0f, landmark = null)
        }
    }
    
    init {
        Logger.i(Constants.TAG_MEMORY, "SpatialMap initialized (${gridSize}x${gridSize} grid, cell=${cellSizeCm}cm)")
    }
    
    /**
     * Cell type enumeration.
     */
    enum class CellType {
        /** Cell has not been observed yet */
        UNKNOWN,
        
        /** Cell is confirmed free space (navigable) */
        FREE,
        
        /** Cell is occupied by an obstacle */
        OCCUPIED,
        
        /** Cell contains a named landmark */
        LANDMARK
    }
    
    /**
     * Immutable grid cell data.
     * 
     * @property x Grid X coordinate (0 to gridSize-1)
     * @property y Grid Y coordinate (0 to gridSize-1)
     * @property type Cell type (UNKNOWN, FREE, OCCUPIED, LANDMARK)
     * @property confidence Confidence value (0.0 to 1.0)
     * @property landmark Optional landmark name if type is LANDMARK
     */
    data class GridCell(
        val x: Int,
        val y: Int,
        val type: CellType,
        val confidence: Float,
        val landmark: String? = null
    )
    
    /**
     * Mark a cell with a specific type and confidence.
     * 
     * @param x Grid X coordinate
     * @param y Grid Y coordinate
     * @param type Cell type to set
     * @param confidence Confidence value (0.0 to 1.0), clamped automatically
     * @return true if cell was updated, false if coordinates out of bounds
     */
    suspend fun markCell(x: Int, y: Int, type: CellType, confidence: Float): Boolean {
        if (!isValidCoordinate(x, y)) {
            Logger.w(Constants.TAG_MEMORY, "Invalid coordinates: ($x, $y)")
            return false
        }
        
        val clampedConfidence = confidence.coerceIn(0.0f, 1.0f)
        
        mutex.withLock {
            val cell = grid[y][x]
            grid[y][x] = cell.copy(
                type = type,
                confidence = clampedConfidence
            )
        }
        
        Logger.d(Constants.TAG_MEMORY, "Marked cell ($x, $y) as $type (conf=${String.format("%.2f", clampedConfidence)})")
        return true
    }
    
    /**
     * Get the current state of a cell.
     * 
     * @param x Grid X coordinate
     * @param y Grid Y coordinate
     * @return GridCell at the specified coordinates, or null if out of bounds
     */
    suspend fun getCell(x: Int, y: Int): GridCell? {
        if (!isValidCoordinate(x, y)) {
            return null
        }
        
        return mutex.withLock {
            grid[y][x]
        }
    }
    
    /**
     * Add a named landmark at the specified grid location.
     * 
     * @param x Grid X coordinate
     * @param y Grid Y coordinate
     * @param name Landmark name (e.g., "charging_station", "ball_pile")
     * @param confidence Confidence value (0.0 to 1.0)
     * @return true if landmark was added, false if coordinates out of bounds
     */
    suspend fun addLandmark(x: Int, y: Int, name: String, confidence: Float = 1.0f): Boolean {
        if (!isValidCoordinate(x, y)) {
            Logger.w(Constants.TAG_MEMORY, "Cannot add landmark at invalid coordinates: ($x, $y)")
            return false
        }
        
        val clampedConfidence = confidence.coerceIn(0.0f, 1.0f)
        
        mutex.withLock {
            val cell = grid[y][x]
            grid[y][x] = cell.copy(
                type = CellType.LANDMARK,
                confidence = clampedConfidence,
                landmark = name
            )
        }
        
        Logger.i(Constants.TAG_MEMORY, "Added landmark '$name' at ($x, $y)")
        return true
    }
    
    /**
     * Query all landmarks in the map.
     * 
     * @param nameFilter Optional filter by landmark name (substring match)
     * @return List of cells containing landmarks
     */
    suspend fun queryLandmarks(nameFilter: String? = null): List<GridCell> {
        return mutex.withLock {
            grid.flatMap { row ->
                row.filter { cell ->
                    cell.type == CellType.LANDMARK &&
                    cell.landmark != null &&
                    (nameFilter == null || cell.landmark.contains(nameFilter, ignoreCase = true))
                }
            }
        }
    }
    
    /**
     * Get frontier cells for exploration.
     * 
     * Frontier cells are FREE cells that are adjacent to UNKNOWN cells.
     * These represent good exploration targets.
     * 
     * @param minConfidence Minimum confidence threshold for FREE cells
     * @return List of frontier cells sorted by distance from center
     */
    suspend fun getFrontierCells(minConfidence: Float = 0.5f): List<GridCell> {
        return mutex.withLock {
            val frontiers = mutableListOf<GridCell>()
            
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val cell = grid[y][x]
                    
                    // Must be a confident FREE cell
                    if (cell.type == CellType.FREE && cell.confidence >= minConfidence) {
                        // Check if any adjacent cell is UNKNOWN
                        if (hasAdjacentUnknown(x, y)) {
                            frontiers.add(cell)
                        }
                    }
                }
            }
            
            // Sort by distance from center (exploration strategy)
            val centerX = gridSize / 2
            val centerY = gridSize / 2
            frontiers.sortedBy { cell ->
                val dx = cell.x - centerX
                val dy = cell.y - centerY
                dx * dx + dy * dy
            }
        }
    }
    
    /**
     * Update cells based on ultrasonic sensor reading and estimated rover position.
     * 
     * @param roverX Rover's grid X coordinate
     * @param roverY Rover's grid Y coordinate
     * @param heading Rover's heading in degrees (0 = north, 90 = east)
     * @param distanceCm Ultrasonic distance reading in centimeters
     * @param sensorAngleOffset Sensor angle offset from heading in degrees (default 0)
     */
    suspend fun updateFromUltrasonic(
        roverX: Int,
        roverY: Int,
        heading: Float,
        distanceCm: Float,
        sensorAngleOffset: Float = 0.0f
    ) {
        if (!isValidCoordinate(roverX, roverY)) {
            Logger.w(Constants.TAG_MEMORY, "Invalid rover position: ($roverX, $roverY)")
            return
        }
        
        // Calculate sensor angle in radians
        val sensorAngle = Math.toRadians((heading + sensorAngleOffset).toDouble())
        
        // Calculate number of cells to the obstacle
        val cellsToObstacle = (distanceCm / cellSizeCm).toInt()
        
        mutex.withLock {
            // Mark cells as FREE from rover position to just before obstacle
            for (i in 1 until cellsToObstacle.coerceAtMost(gridSize / 2)) {
                val cellX = roverX + (i * Math.cos(sensorAngle)).toInt()
                val cellY = roverY + (i * Math.sin(sensorAngle)).toInt()
                
                if (isValidCoordinate(cellX, cellY)) {
                    val cell = grid[cellY][cellX]
                    // Only mark as FREE if not already a landmark
                    if (cell.type != CellType.LANDMARK) {
                        grid[cellY][cellX] = cell.copy(
                            type = CellType.FREE,
                            confidence = (cell.confidence + 0.3f).coerceAtMost(1.0f)
                        )
                    }
                }
            }
            
            // Mark obstacle cell as OCCUPIED
            val obstacleX = roverX + (cellsToObstacle * Math.cos(sensorAngle)).toInt()
            val obstacleY = roverY + (cellsToObstacle * Math.sin(sensorAngle)).toInt()
            
            if (isValidCoordinate(obstacleX, obstacleY)) {
                val cell = grid[obstacleY][obstacleX]
                // Only mark as OCCUPIED if not a landmark
                if (cell.type != CellType.LANDMARK) {
                    grid[obstacleY][obstacleX] = cell.copy(
                        type = CellType.OCCUPIED,
                        confidence = (cell.confidence + 0.5f).coerceAtMost(1.0f)
                    )
                }
            }
        }
        
        Logger.d(Constants.TAG_MEMORY, "Updated map from ultrasonic: dist=${distanceCm}cm, heading=${heading}°")
    }
    
    /**
     * Reset the entire spatial map to UNKNOWN state.
     * Use when relocalized or when map becomes unreliable.
     */
    suspend fun reset() {
        mutex.withLock {
            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    grid[y][x] = GridCell(
                        x = x,
                        y = y,
                        type = CellType.UNKNOWN,
                        confidence = 0.0f,
                        landmark = null
                    )
                }
            }
        }
        
        Logger.i(Constants.TAG_MEMORY, "Spatial map reset")
    }
    
    /**
     * Get all cells of a specific type.
     * 
     * @param type Cell type to query
     * @param minConfidence Minimum confidence threshold
     * @return List of cells matching the criteria
     */
    suspend fun getCellsByType(type: CellType, minConfidence: Float = 0.0f): List<GridCell> {
        return mutex.withLock {
            grid.flatMap { row ->
                row.filter { cell ->
                    cell.type == type && cell.confidence >= minConfidence
                }
            }
        }
    }
    
    /**
     * Check if a coordinate is within grid bounds.
     */
    private fun isValidCoordinate(x: Int, y: Int): Boolean {
        return x in 0 until gridSize && y in 0 until gridSize
    }
    
    /**
     * Check if a cell has any adjacent UNKNOWN cells.
     * Used for frontier detection.
     */
    private fun hasAdjacentUnknown(x: Int, y: Int): Boolean {
        val offsets = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        
        for ((dx, dy) in offsets) {
            val adjX = x + dx
            val adjY = y + dy
            
            if (isValidCoordinate(adjX, adjY)) {
                if (grid[adjY][adjX].type == CellType.UNKNOWN) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Get map statistics for monitoring and debugging.
     * 
     * @return Map containing cell counts by type and other metrics
     */
    suspend fun getStats(): Map<String, Any> {
        return mutex.withLock {
            val typeCounts = mutableMapOf<CellType, Int>()
            var totalConfidence = 0.0f
            var landmarkCount = 0
            
            for (row in grid) {
                for (cell in row) {
                    typeCounts[cell.type] = typeCounts.getOrDefault(cell.type, 0) + 1
                    totalConfidence += cell.confidence
                    if (cell.landmark != null) {
                        landmarkCount++
                    }
                }
            }
            
            val totalCells = gridSize * gridSize
            val avgConfidence = if (totalCells > 0) totalConfidence / totalCells else 0.0f
            
            mapOf(
                "grid_size" to gridSize,
                "cell_size_cm" to cellSizeCm,
                "total_cells" to totalCells,
                "unknown_cells" to typeCounts.getOrDefault(CellType.UNKNOWN, 0),
                "free_cells" to typeCounts.getOrDefault(CellType.FREE, 0),
                "occupied_cells" to typeCounts.getOrDefault(CellType.OCCUPIED, 0),
                "landmark_cells" to typeCounts.getOrDefault(CellType.LANDMARK, 0),
                "landmark_count" to landmarkCount,
                "avg_confidence" to avgConfidence,
                "exploration_pct" to ((totalCells - typeCounts.getOrDefault(CellType.UNKNOWN, 0)) * 100.0f / totalCells)
            )
        }
    }
    
    /**
     * Export the grid as a simple ASCII representation for debugging.
     * Useful for visualization in logs.
     * 
     * @return ASCII string representation of the grid
     */
    suspend fun toAscii(): String {
        return mutex.withLock {
            val sb = StringBuilder()
            sb.append("Spatial Map (${gridSize}x${gridSize}):\n")
            sb.append("  ")
            for (x in 0 until gridSize.coerceAtMost(20)) {
                sb.append(x % 10)
            }
            sb.append("\n")
            
            for (y in 0 until gridSize.coerceAtMost(20)) {
                sb.append(String.format("%2d", y))
                for (x in 0 until gridSize.coerceAtMost(20)) {
                    val cell = grid[y][x]
                    val char = when (cell.type) {
                        CellType.UNKNOWN -> '?'
                        CellType.FREE -> '.'
                        CellType.OCCUPIED -> '#'
                        CellType.LANDMARK -> '@'
                    }
                    sb.append(char)
                }
                sb.append("\n")
            }
            
            sb.toString()
        }
    }
}
