package com.rover.ai.ui.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live camera preview screen with AI vision overlays.
 * 
 * Displays:
 * - CameraX preview from rear camera
 * - YOLO bounding boxes for detected objects
 * - Object labels and confidence scores
 * - Gemma analysis text at bottom
 * - Ultrasonic distance reading at top-right
 * 
 * Uses CameraX for preview and Canvas for overlay rendering.
 * Observes vision system outputs and StateManager for reactive updates.
 * 
 * @param stateManager Global state manager for sensor data
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun CameraViewScreen(
    stateManager: StateManager,
    modifier: Modifier = Modifier
) {
    val roverState by stateManager.state.collectAsState()
    
    // Mock vision data (in real implementation, inject VisionSystem)
    val detectedObjects = remember { mutableStateOf<List<DetectedObject>>(emptyList()) }
    val gemmaAnalysis = remember { mutableStateOf("Analyzing environment...") }
    val yoloFps = remember { mutableStateOf(0f) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Camera Preview
        CameraPreview(
            modifier = Modifier.fillMaxSize()
        )
        
        // YOLO Bounding Box Overlays
        if (detectedObjects.value.isNotEmpty()) {
            BoundingBoxOverlay(
                detectedObjects = detectedObjects.value,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Distance Indicator (Top Right)
        DistanceIndicator(
            distanceCm = roverState.sensorData.distanceCm,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        
        // FPS Counter (Top Left)
        FpsCounter(
            fps = yoloFps.value,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        
        // Gemma Analysis Text (Bottom)
        GemmaAnalysisOverlay(
            analysis = gemmaAnalysis.value,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

/**
 * CameraX preview view.
 */
@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    
    LaunchedEffect(Unit) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            
            Logger.i(Constants.TAG_UI, "Camera preview initialized")
        } catch (e: Exception) {
            Logger.e(Constants.TAG_UI, "Camera initialization failed: ${e.message}")
        }
    }
    
    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

/**
 * Canvas overlay for YOLO bounding boxes.
 */
@Composable
private fun BoundingBoxOverlay(
    detectedObjects: List<DetectedObject>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        detectedObjects.forEach { obj ->
            // Draw bounding box
            drawRect(
                color = obj.color,
                topLeft = Offset(
                    x = obj.boundingBox.left * size.width,
                    y = obj.boundingBox.top * size.height
                ),
                size = Size(
                    width = obj.boundingBox.width * size.width,
                    height = obj.boundingBox.height * size.height
                ),
                style = Stroke(width = 4f)
            )
            
            // Label will be drawn separately as Text composable
        }
    }
    
    // Draw labels as Text composables (positioned absolutely)
    Box(modifier = modifier) {
        detectedObjects.forEach { obj ->
            val labelX = obj.boundingBox.left
            val labelY = obj.boundingBox.top - 0.03f // Slightly above box
            
            Text(
                text = "${obj.label} ${String.format("%.0f", obj.confidence * 100)}%",
                color = obj.color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(
                        x = (labelX * 400).dp, // Approximate positioning
                        y = (labelY * 800).dp
                    )
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(4.dp)
            )
        }
    }
}

/**
 * Distance indicator display.
 */
@Composable
private fun DistanceIndicator(
    distanceCm: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        distanceCm > 100f -> Color(0xFF4CAF50) // Green
        distanceCm > 50f -> Color(0xFFFFC107) // Yellow
        else -> Color(0xFFF44336) // Red
    }
    
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Distance",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            
            Text(
                text = "${String.format("%.1f", distanceCm)} cm",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

/**
 * FPS counter display.
 */
@Composable
private fun FpsCounter(
    fps: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "YOLO FPS",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
            
            Text(
                text = String.format("%.1f", fps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (fps >= Constants.YOLO_TARGET_FPS) Color(0xFF4CAF50) else Color(0xFFFFC107)
            )
        }
    }
}

/**
 * Gemma AI analysis text overlay.
 */
@Composable
private fun GemmaAnalysisOverlay(
    analysis: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp)
    ) {
        Text(
            text = analysis,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

/**
 * Data class representing a detected object from YOLO.
 * 
 * @property label Object class label
 * @property confidence Detection confidence (0.0 to 1.0)
 * @property boundingBox Normalized bounding box (0.0 to 1.0 coordinates)
 * @property color Display color for this object
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val color: Color = Color(0xFF2196F3) // Default blue
)

/**
 * Normalized bounding box coordinates.
 * All values are 0.0 to 1.0 (percentage of screen dimensions).
 * 
 * @property left Left edge (0.0 = left side of screen)
 * @property top Top edge (0.0 = top of screen)
 * @property width Box width as percentage
 * @property height Box height as percentage
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)
