package com.rover.ai.ui.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
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
import com.rover.ai.ai.litert.LiteRtModelManager
import com.rover.ai.ai.litert.ModelStatus
import com.rover.ai.ai.vision.DepthEstimator
import com.rover.ai.ai.vision.YoloDetector
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI Test Screen
 *
 * Lets the user manually trigger inference on each AI model and view results.
 * Tests can be run independently or as a combined pipeline.
 */
@Composable
fun AITestScreen(
    modelManager: LiteRtModelManager,
    inferenceSession: InferenceSession,
    yoloDetector: YoloDetector,
    depthEstimator: DepthEstimator,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val resultsScrollState = rememberScrollState()

    var results by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }

    val modelStatus by modelManager.modelStatus.collectAsState()

    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun appendResult(text: String) {
        val timestamp = timeFormat.format(Date())
        results = "[$timestamp] $text\n$results"
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

        // Model status cards
        ModelStatusRow(modelStatus = modelStatus)

        HorizontalDivider()

        // Test buttons
        if (isRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Running test…", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Gemma LLM test
        Button(
            onClick = {
                scope.launch {
                    isRunning = true
                    appendResult("🧠 Testing Gemma LLM…")
                    val start = System.currentTimeMillis()
                    try {
                        val prompt = "Hello, describe yourself in one sentence."
                        val result = inferenceSession.runInference(
                            com.rover.ai.ai.litert.InferenceRequest(prompt = prompt)
                        )
                        val latency = System.currentTimeMillis() - start
                        appendResult(
                            "🧠 Gemma response (${latency}ms, conf=${
                                "%.2f".format(result.confidence)
                            }):\n${result.text}"
                        )
                    } catch (e: Exception) {
                        appendResult("🧠 Gemma ERROR: ${e.message}")
                    } finally {
                        isRunning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            Icon(Icons.Default.Memory, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("🧠 Test Gemma LLM")
        }

        // YOLO detection test
        Button(
            onClick = {
                scope.launch {
                    isRunning = true
                    appendResult("👁️ Testing YOLO Detection…")
                    val start = System.currentTimeMillis()
                    try {
                        val testBitmap = createTestBitmap()
                        val detections = yoloDetector.detect(testBitmap)
                        val latency = System.currentTimeMillis() - start
                        val detectionText = if (detections.isEmpty()) {
                            "No objects detected"
                        } else {
                            detections.joinToString(", ") {
                                "${it.label} (${"%.0f".format(it.confidence * 100)}%)"
                            }
                        }
                        appendResult("👁️ YOLO (${latency}ms): $detectionText")
                    } catch (e: Exception) {
                        appendResult("👁️ YOLO ERROR: ${e.message}")
                    } finally {
                        isRunning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("👁️ Test YOLO Detection")
        }

        // Depth estimation test
        Button(
            onClick = {
                scope.launch {
                    isRunning = true
                    appendResult("📏 Testing Depth Estimation…")
                    val start = System.currentTimeMillis()
                    try {
                        val testBitmap = createTestBitmap()
                        val depthMap = depthEstimator.estimateDepth(testBitmap)
                        val latency = System.currentTimeMillis() - start
                        if (depthMap != null) {
                            val minDepth = depthMap.min()
                            val maxDepth = depthMap.max()
                            appendResult(
                                "📏 Depth (${latency}ms): ${depthMap.size} values, " +
                                    "min=${"%.3f".format(minDepth)}, max=${"%.3f".format(maxDepth)}"
                            )
                        } else {
                            appendResult("📏 Depth (${latency}ms): model not available")
                        }
                    } catch (e: Exception) {
                        appendResult("📏 Depth ERROR: ${e.message}")
                    } finally {
                        isRunning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("📏 Test Depth Estimation")
        }

        // Full pipeline test
        Button(
            onClick = {
                scope.launch {
                    isRunning = true
                    appendResult("📸 Running Full Pipeline Test…")
                    val start = System.currentTimeMillis()
                    try {
                        val testBitmap = createTestBitmap()

                        // Run YOLO
                        val detections = yoloDetector.detect(testBitmap)
                        val detectionText = if (detections.isEmpty()) "nothing"
                        else detections.joinToString(", ") {
                            "${it.label} (${"%.0f".format(it.confidence * 100)}%)"
                        }

                        // Build prompt from YOLO results and run Gemma
                        val prompt = "I see: $detectionText. What should I do next?"
                        val gemmaResult = inferenceSession.runInference(
                            com.rover.ai.ai.litert.InferenceRequest(
                                prompt = prompt,
                                image = testBitmap
                            )
                        )

                        val latency = System.currentTimeMillis() - start
                        appendResult(
                            "📸 Pipeline (${latency}ms):\n" +
                                "  YOLO: $detectionText\n" +
                                "  Gemma: ${gemmaResult.text}"
                        )
                    } catch (e: Exception) {
                        appendResult("📸 Pipeline ERROR: ${e.message}")
                    } finally {
                        isRunning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
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
                        text = "No results yet. Tap a test button above.",
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
 * Row showing status chips for each AI model.
 */
@Composable
private fun ModelStatusRow(modelStatus: ModelStatus) {
    val (color, label) = when (modelStatus) {
        ModelStatus.LOADED -> ComposeColor(0xFF4CAF50) to "LOADED"
        ModelStatus.LOADING -> ComposeColor(0xFF2196F3) to "LOADING"
        ModelStatus.ERROR -> ComposeColor(0xFFF44336) to "ERROR"
        ModelStatus.UNLOADED -> ComposeColor(0xFF9E9E9E) to "UNLOADED"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Model Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠 Gemma LLM: ", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
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
