package com.rover.ai.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.memory.SpatialMap
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Canvas-based spatial map visualization screen.
 * 
 * Displays the rover's internal occupancy grid map with:
 * - Color-coded cells (UNKNOWN: gray, FREE: green, OCCUPIED: red, LANDMARK: yellow)
 * - Rover position and heading indicator (triangle)
 * - Landmark labels
 * - Grid coordinates overlay
 * - Zoom and pan controls (pinch/drag gestures)
 * 
 * Observes SpatialMap from memory module for real-time updates.
 * Uses Canvas for efficient grid rendering.
 * 
 * @param spatialMap Spatial map instance to visualize
 * @param roverX Current rover X position in grid coordinates (default: center)
 * @param roverY Current rover Y position in grid coordinates (default: center)
 * @param roverHeading Current rover heading in degrees (0 = north, 90 = east)
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun MapViewScreen(
    spatialMap: SpatialMap,
    roverX: Int = Constants.SPATIAL_MAP_GRID_SIZE / 2,
    roverY: Int = Constants.SPATIAL_MAP_GRID_SIZE / 2,
    roverHeading: Float = 0f,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // Map grid cells state
    var gridCells by remember { mutableStateOf<List<SpatialMap.GridCell>>(emptyList()) }
    var landmarks by remember { mutableStateOf<List<SpatialMap.GridCell>>(emptyList()) }
    var mapStats by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    
    // Zoom and pan state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    // Load map data
    LaunchedEffect(Unit) {
        scope.launch {
            // Load all cells
            val allCells = mutableListOf<SpatialMap.GridCell>()
            for (y in 0 until Constants.SPATIAL_MAP_GRID_SIZE) {
                for (x in 0 until Constants.SPATIAL_MAP_GRID_SIZE) {
                    spatialMap.getCell(x, y)?.let { cell ->
                        allCells.add(cell)
                    }
                }
            }
            gridCells = allCells
            
            // Load landmarks
            landmarks = spatialMap.queryLandmarks()
            
            // Load stats
            mapStats = spatialMap.getStats()
            
            Logger.i(Constants.TAG_UI, "Map loaded: ${allCells.size} cells, ${landmarks.size} landmarks")
        }
    }
    
    // Refresh map periodically
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L) // Refresh every second
            scope.launch {
                val allCells = mutableListOf<SpatialMap.GridCell>()
                for (y in 0 until Constants.SPATIAL_MAP_GRID_SIZE) {
                    for (x in 0 until Constants.SPATIAL_MAP_GRID_SIZE) {
                        spatialMap.getCell(x, y)?.let { cell ->
                            allCells.add(cell)
                        }
                    }
                }
                gridCells = allCells
                landmarks = spatialMap.queryLandmarks()
                mapStats = spatialMap.getStats()
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            val gridSize = Constants.SPATIAL_MAP_GRID_SIZE
            val cellPixelSize = (size.minDimension / gridSize) * scale
            
            // Apply transformations
            val centerOffsetX = (size.width - (gridSize * cellPixelSize)) / 2 + offsetX
            val centerOffsetY = (size.height - (gridSize * cellPixelSize)) / 2 + offsetY
            
            // Draw grid cells
            gridCells.forEach { cell ->
                val cellColor = getCellColor(cell)
                
                drawRect(
                    color = cellColor,
                    topLeft = Offset(
                        x = centerOffsetX + cell.x * cellPixelSize,
                        y = centerOffsetY + cell.y * cellPixelSize
                    ),
                    size = Size(cellPixelSize, cellPixelSize)
                )
                
                // Draw cell border
                drawRect(
                    color = Color.Gray.copy(alpha = 0.3f),
                    topLeft = Offset(
                        x = centerOffsetX + cell.x * cellPixelSize,
                        y = centerOffsetY + cell.y * cellPixelSize
                    ),
                    size = Size(cellPixelSize, cellPixelSize),
                    style = Stroke(width = 1f)
                )
            }
            
            // Draw landmarks with labels
            landmarks.forEach { landmark ->
                landmark.landmark?.let { label ->
                    val centerX = centerOffsetX + (landmark.x + 0.5f) * cellPixelSize
                    val centerY = centerOffsetY + (landmark.y + 0.5f) * cellPixelSize
                    
                    // Draw landmark marker (star-like shape)
                    drawLandmarkMarker(
                        center = Offset(centerX, centerY),
                        size = cellPixelSize * 0.8f
                    )
                }
            }
            
            // Draw rover position and heading
            drawRover(
                x = centerOffsetX + (roverX + 0.5f) * cellPixelSize,
                y = centerOffsetY + (roverY + 0.5f) * cellPixelSize,
                heading = roverHeading,
                size = cellPixelSize * 1.5f
            )
        }
        
        // Map Statistics Overlay (Top Left)
        MapStatsOverlay(
            stats = mapStats,
            scale = scale,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        
        // Rover Position Overlay (Top Right)
        RoverPositionOverlay(
            x = roverX,
            y = roverY,
            heading = roverHeading,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        
        // Legend (Bottom Left)
        MapLegend(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
        
        // Zoom Controls Info (Bottom Right)
        ZoomInfo(
            scale = scale,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

/**
 * Get color for a grid cell based on its type and confidence.
 */
private fun getCellColor(cell: SpatialMap.GridCell): Color {
    return when (cell.type) {
        SpatialMap.CellType.UNKNOWN -> Color(0xFF9E9E9E) // Gray
        
        SpatialMap.CellType.FREE -> {
            // Green with varying opacity based on confidence
            Color(0xFF4CAF50).copy(alpha = 0.3f + cell.confidence * 0.7f)
        }
        
        SpatialMap.CellType.OCCUPIED -> {
            // Red with varying opacity based on confidence
            Color(0xFFF44336).copy(alpha = 0.3f + cell.confidence * 0.7f)
        }
        
        SpatialMap.CellType.LANDMARK -> Color(0xFFFFEB3B) // Yellow
    }
}

/**
 * Draw rover as a triangle pointing in heading direction.
 */
private fun DrawScope.drawRover(
    x: Float,
    y: Float,
    heading: Float,
    size: Float
) {
    rotate(heading, pivot = Offset(x, y)) {
        val path = Path().apply {
            // Triangle pointing upward (north)
            moveTo(x, y - size / 2) // Top point
            lineTo(x - size / 3, y + size / 2) // Bottom left
            lineTo(x + size / 3, y + size / 2) // Bottom right
            close()
        }
        
        drawPath(
            path = path,
            color = Color(0xFF2196F3) // Blue
        )
        
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Draw landmark marker.
 */
private fun DrawScope.drawLandmarkMarker(
    center: Offset,
    size: Float
) {
    // Draw a circle for landmark
    drawCircle(
        color = Color(0xFFFFEB3B), // Yellow
        radius = size / 2,
        center = center
    )
    
    drawCircle(
        color = Color.Black,
        radius = size / 2,
        center = center,
        style = Stroke(width = 2f)
    )
}

/**
 * Map statistics overlay.
 */
@Composable
private fun MapStatsOverlay(
    stats: Map<String, Any>,
    scale: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Text(
            text = "Map Statistics",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        stats.forEach { (key, value) ->
            when (key) {
                "exploration_pct" -> {
                    Text(
                        text = "Explored: ${String.format("%.1f", value as? Float ?: 0f)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                "landmark_count" -> {
                    Text(
                        text = "Landmarks: $value",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Rover position overlay.
 */
@Composable
private fun RoverPositionOverlay(
    x: Int,
    y: Int,
    heading: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "Rover Position",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "X: $x, Y: $y",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
        
        Text(
            text = "Heading: ${String.format("%.0f", heading)}°",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/**
 * Map legend.
 */
@Composable
private fun MapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Text(
            text = "Legend",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LegendItem(color = Color(0xFF9E9E9E), label = "Unknown")
        LegendItem(color = Color(0xFF4CAF50), label = "Free Space")
        LegendItem(color = Color(0xFFF44336), label = "Occupied")
        LegendItem(color = Color(0xFFFFEB3B), label = "Landmark")
        LegendItem(color = Color(0xFF2196F3), label = "Rover")
    }
}

/**
 * Single legend item.
 */
@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/**
 * Zoom info display.
 */
@Composable
private fun ZoomInfo(
    scale: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Zoom",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "${String.format("%.1f", scale)}x",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Pinch to zoom",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}
