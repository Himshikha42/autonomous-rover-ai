package com.rover.ai.ui.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rover.ai.ai.litert.InferenceSession
import com.rover.ai.ai.litert.InferenceRequest
import com.rover.ai.ai.litert.LiteRtModelManager
import com.rover.ai.ai.litert.ModelStatus
import com.rover.ai.ai.model.ModelRegistry
import com.rover.ai.ai.vision.DepthEstimator
import com.rover.ai.ai.vision.YoloDetector
import com.rover.ai.core.Constants
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI Test Screen
 *
 * Lets the user manually load and test each AI model independently.
 * Each model has a "Load Model" button (enabled when the model is found on disk
 * but not yet loaded) and a "Test" button (enabled only when loaded).
 */
@Composable
fun AITestScreen(
    modelManager: LiteRtModelManager,
    inferenceSession: InferenceSession,
    yoloDetector: YoloDetector,
    depthEstimator: DepthEstimator,
    modelRegistry: ModelRegistry,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val resultsScrollState = rememberScrollState()

    var results by remember { mutableStateOf("") }

    // Gemma status comes from the model manager's StateFlow
    val gemmaStatus by modelManager.modelStatus.collectAsState()
    // YOLO and Depth statuses are tracked locally (their interfaces don't expose a StateFlow)
    var yoloStatus by remember { mutableStateOf(ModelStatus.UNLOADED) }
    var depthStatus by remember { mutableStateOf(ModelStatus.UNLOADED) }

    // Per-model load times and error messages
    var gemmaLoadTimeMs by remember { mutableStateOf(0L) }
    var gemmaError by remember { mutableStateOf<String?>(null) }
    var yoloLoadTimeMs by remember { mutableStateOf(0L) }
    var yoloError by remember { mutableStateOf<String?>(null) }
    var depthLoadTimeMs by remember { mutableStateOf(0L) }
    var depthError by remember { mutableStateOf<String?>(null) }

    // File sizes from the model registry
    val modelStates by modelRegistry.modelStates.collectAsState()

    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun appendResult(text: String) {
        val timestamp = timeFormat.format(Date())
        results = "[$timestamp] $text\n$results"
    }

    fun formatSize(bytes: Long?): String {
        if (bytes == null) return ""
        return when {
            bytes >= 1_000_000_000L -> "${"%.1f".format(bytes / 1_000_000_000.0)}GB"
            bytes >= 1_000_000L -> "${bytes / 1_000_000}MB"
            else -> "${bytes / 1_000}KB"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Text(
            text = "🧪 AI Model Testing",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
        )

        // Gemma card
        ModelCard(
            title = "🧠 Gemma LLM",
            fileSize = formatSize(modelStates[Constants.GEMMA_MODEL_FILE]?.actualSizeBytes),
            status = gemmaStatus,
            loadTimeMs = gemmaLoadTimeMs,
            errorMessage = gemmaError,
            showGemmaHint = true,
            onLoad = {
                scope.launch {
                    gemmaError = null
                    gemmaLoadTimeMs = 0L
                    val start = System.currentTimeMillis()
                    appendResult("🧠 Loading Gemma (this may take 20-30s)…")
                    val ok = modelManager.loadModel()
                    gemmaLoadTimeMs = System.currentTimeMillis() - start
                    if (ok) {
                        appendResult("🧠 Gemma loaded in ${gemmaLoadTimeMs}ms")
                    } else {
                        gemmaError = "Load failed"
                        appendResult("🧠 Gemma load failed")
                    }
                }
            },
            onTest = {
                scope.launch {
                    appendResult("🧠 Testing Gemma LLM…")
                    val start = System.currentTimeMillis()
                    try {
                        val result = inferenceSession.runInference(
                            InferenceRequest(prompt = "Hello! Describe yourself in one sentence.")
                        )
                        val latency = System.currentTimeMillis() - start
                        appendResult(
                            "🧠 Gemma (${latency}ms, conf=${"%.2f".format(result.confidence)}):\n${result.text}"
                        )
                    } catch (e: Exception) {
                        appendResult("🧠 Gemma ERROR: ${e.message}")
                    }
                }
            }
        )

        // YOLO card
        ModelCard(
            title = "👁️ YOLO Detection",
            fileSize = formatSize(modelStates[Constants.YOLO_MODEL_FILE]?.actualSizeBytes),
            status = yoloStatus,
            loadTimeMs = yoloLoadTimeMs,
            errorMessage = yoloError,
            showGemmaHint = false,
            onLoad = {
                scope.launch {
                    yoloError = null
                    yoloLoadTimeMs = 0L
                    yoloStatus = ModelStatus.LOADING
                    appendResult("👁️ Loading YOLO…")
                    val start = System.currentTimeMillis()
                    val ok = yoloDetector.initialize()
                    yoloLoadTimeMs = System.currentTimeMillis() - start
                    yoloStatus = if (ok) ModelStatus.LOADED else ModelStatus.ERROR
                    if (ok) {
                        appendResult("👁️ YOLO loaded in ${yoloLoadTimeMs}ms")
                    } else {
                        yoloError = "Load failed"
                        appendResult("👁️ YOLO load failed")
                    }
                }
            },
            onTest = {
                scope.launch {
                    appendResult("👁️ Testing YOLO Detection…")
                    val start = System.currentTimeMillis()
                    try {
                        val testBitmap = createTestBitmap()
                        val detections = yoloDetector.detect(testBitmap)
                        val latency = System.currentTimeMillis() - start
                        val detectionText = if (detections.isEmpty()) "No objects detected"
                        else detections.joinToString(", ") {
                            "${it.label} (${"%.0f".format(it.confidence * 100)}%)"
                        }
                        appendResult("👁️ YOLO (${latency}ms): $detectionText")
                    } catch (e: Exception) {
                        appendResult("👁️ YOLO ERROR: ${e.message}")
                    }
                }
            }
        )

        // Depth card
        ModelCard(
            title = "📏 Depth Estimation",
            fileSize = formatSize(modelStates[Constants.DEPTH_MODEL_FILE]?.actualSizeBytes),
            status = depthStatus,
            loadTimeMs = depthLoadTimeMs,
            errorMessage = depthError,
            showGemmaHint = false,
            onLoad = {
                scope.launch {
                    depthError = null
                    depthLoadTimeMs = 0L
                    depthStatus = ModelStatus.LOADING
                    appendResult("📏 Loading Depth Estimator…")
                    val start = System.currentTimeMillis()
                    val ok = depthEstimator.initialize()
                    depthLoadTimeMs = System.currentTimeMillis() - start
                    depthStatus = if (ok) ModelStatus.LOADED else ModelStatus.ERROR
                    if (ok) {
                        appendResult("📏 Depth loaded in ${depthLoadTimeMs}ms")
                    } else {
                        depthError = "Load failed"
                        appendResult("📏 Depth load failed")
                    }
                }
            },
            onTest = {
                scope.launch {
                    appendResult("📏 Testing Depth Estimation…")
                    val start = System.currentTimeMillis()
                    try {
                        val testBitmap = createTestBitmap()
                        val depthMap = depthEstimator.estimateDepth(testBitmap)
                        val latency = System.currentTimeMillis() - start
                        if (depthMap != null) {
                            appendResult(
                                "📏 Depth (${latency}ms): ${depthMap.size} values, " +
                                    "min=${"%.3f".format(depthMap.min())}, max=${"%.3f".format(depthMap.max())}"
                            )
                        } else {
                            appendResult("📏 Depth (${latency}ms): model not available")
                        }
                    } catch (e: Exception) {
                        appendResult("📏 Depth ERROR: ${e.message}")
                    }
                }
            }
        )

        // Full pipeline test (requires both Gemma and YOLO loaded)
        Button(
            onClick = {
                scope.launch {
                    appendResult("📸 Running Full Pipeline Test…")
                    val start = System.currentTimeMillis()
                    try {
                        val testBitmap = createTestBitmap()
                        val detections = yoloDetector.detect(testBitmap)
                        val detectionText = if (detections.isEmpty()) "nothing"
                        else detections.joinToString(", ") {
                            "${it.label} (${"%.0f".format(it.confidence * 100)}%)"
                        }
                        val prompt = "I see: $detectionText. What should I do next?"
                        val gemmaResult = inferenceSession.runInference(
                            InferenceRequest(prompt = prompt, image = testBitmap)
                        )
                        val latency = System.currentTimeMillis() - start
                        appendResult(
                            "📸 Pipeline (${latency}ms):\n" +
                                "  YOLO: $detectionText\n" +
                                "  Gemma: ${gemmaResult.text}"
                        )
                    } catch (e: Exception) {
                        appendResult("📸 Pipeline ERROR: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = gemmaStatus == ModelStatus.LOADED && yoloStatus == ModelStatus.LOADED
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("📸 Full Pipeline Test")
        }

        // Clear results
        OutlinedButton(
            onClick = { results = "" },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear Results")
        }

        HorizontalDivider()

        // Results area
        Text(
            text = "Test Results",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(resultsScrollState)
                    .padding(12.dp)
            ) {
                if (results.isEmpty()) {
                    Text(
                        text = "No results yet. Load a model then tap Test.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = results,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Card for a single AI model showing name, file size, status badge,
 * a "Load Model" button and a "Test" button.
 *
 * Load button is enabled only when the model is UNLOADED (found but not yet loaded).
 * Test button is enabled only when the model is LOADED.
 */
@Composable
private fun ModelCard(
    title: String,
    fileSize: String,
    status: ModelStatus,
    loadTimeMs: Long,
    errorMessage: String?,
    showGemmaHint: Boolean,
    onLoad: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusLabel) = when (status) {
        ModelStatus.LOADED -> ComposeColor(0xFF4CAF50) to "LOADED"
        ModelStatus.LOADING -> ComposeColor(0xFF2196F3) to "LOADING"
        ModelStatus.ERROR -> ComposeColor(0xFFF44336) to "ERROR"
        ModelStatus.UNLOADED -> ComposeColor(0xFF9E9E9E) to "FOUND"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title row with status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status == ModelStatus.LOADING) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // File size and load time
            if (fileSize.isNotEmpty()) {
                Text(
                    text = if (loadTimeMs > 0) "Size: $fileSize | Loaded in ${loadTimeMs}ms"
                           else "Size: $fileSize",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Loading hint for large models
            if (status == ModelStatus.LOADING && showGemmaHint) {
                Text(
                    text = "Loading… this takes 20-30 seconds for a 3.4GB model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Error message
            if (errorMessage != null) {
                Text(
                    text = "Error: $errorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = ComposeColor(0xFFF44336)
                )
            }

            // Load and Test buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLoad,
                    modifier = Modifier.weight(1f),
                    enabled = status == ModelStatus.UNLOADED
                ) {
                    Text("Load Model")
                }
                Button(
                    onClick = onTest,
                    modifier = Modifier.weight(1f),
                    enabled = status == ModelStatus.LOADED
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test")
                }
            }
        }
    }
}

/**
 * Create a simple grey test bitmap (320x320) for offline testing.
 */
private fun createTestBitmap(): Bitmap {
    val size = 320
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.GRAY)
    return bitmap
}
