package com.rover.ai.ai.vision

import android.content.Context
import android.graphics.Bitmap
import com.rover.ai.ai.model.ModelFileStatus
import com.rover.ai.ai.model.ModelRegistry
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.ThreadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bounding box for detected object
 * 
 * @property x Left coordinate (0.0 to 1.0, normalized)
 * @property y Top coordinate (0.0 to 1.0, normalized)
 * @property width Width (0.0 to 1.0, normalized)
 * @property height Height (0.0 to 1.0, normalized)
 */
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

/**
 * Detected object with confidence and location
 * 
 * @property label Object class label (e.g., "person", "chair", "car")
 * @property confidence Detection confidence (0.0 to 1.0)
 * @property bbox Bounding box location
 */
data class Detection(
    val label: String,
    val confidence: Float,
    val bbox: BoundingBox
)

/**
 * Interface for YOLO object detection
 */
interface YoloDetector {
    /**
     * Initialize detector and load model
     * 
     * @return true if successful, false otherwise
     */
    suspend fun initialize(): Boolean
    
    /**
     * Run object detection on image
     * 
     * @param bitmap Input image
     * @return List of detected objects
     */
    suspend fun detect(bitmap: Bitmap): List<Detection>
    
    /**
     * Release resources
     */
    suspend fun release()
}

/**
 * Implementation of YOLO Nano object detector using TensorFlow Lite
 * 
 * Uses YOLOv8 Nano for real-time object detection on mobile devices.
 * Optimized for speed with GPU/NPU acceleration.
 * 
 * This is a stub implementation until actual YOLO model is integrated.
 * 
 * Thread-safe singleton managed by Hilt.
 */
@Singleton
class YoloDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val threadManager: ThreadManager,
    private val modelRegistry: ModelRegistry
) : YoloDetector {
    
    private val tag = Constants.TAG_VISION
    
    @Volatile
    private var isInitialized = false
    
    private var interpreter: Interpreter? = null

    // Actual input size queried from the model at runtime.
    // YOLO_INPUT_SIZE is used as a fallback default only.
    private var actualInputSize: Int = Constants.YOLO_INPUT_SIZE
    
    // COCO dataset labels (80 classes)
    private val labels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane",
        "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird",
        "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat",
        "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
        "wine glass", "cup", "fork", "knife", "spoon",
        "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut",
        "cake", "chair", "couch", "potted plant", "bed",
        "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven",
        "toaster", "sink", "refrigerator", "book", "clock",
        "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )
    
    /**
     * Initialize YOLO model
     * 
     * In production:
     * 1. Check if model file exists in external storage
     * 2. Load YOLOv8n.tflite from external storage
     * 3. Configure GPU delegate
     * 4. Initialize interpreter
     * 5. Warm up with dummy input
     */
    override suspend fun initialize(): Boolean = withContext(threadManager.visionDispatcher) {
        Logger.d(tag, "Initializing YOLO detector")
        
        if (isInitialized) {
            Logger.w(tag, "YOLO already initialized")
            return@withContext true
        }
        
        // Check if model is available
        if (!modelRegistry.isModelAvailable(Constants.YOLO_MODEL_FILE)) {
            Logger.w(tag, "YOLO model not found in external storage - disabling object detection")
            modelRegistry.updateModelStatus(
                Constants.YOLO_MODEL_FILE,
                ModelFileStatus.MISSING,
                "Model file not found. Re-export with: yolo export model=yolov8n.pt format=tflite, then push via ADB."
            )
            return@withContext false
        }
        
        return@withContext try {
            val startTime = System.currentTimeMillis()
            
            // Get model file from external storage
            val modelFile = modelRegistry.getModelFile(Constants.YOLO_MODEL_FILE)
            
            if (!modelFile.exists() || !modelFile.canRead()) {
                Logger.e(tag, "YOLO model file not accessible at ${modelFile.absolutePath}")
                modelRegistry.updateModelStatus(
                    Constants.YOLO_MODEL_FILE,
                    ModelFileStatus.ERROR,
                    "Model file not accessible"
                )
                return@withContext false
            }
            
            Logger.i(tag, "Loading YOLO from: ${modelFile.absolutePath} (${modelFile.length() / 1_000_000}MB)")
            
            val modelBuffer = loadModelBuffer(modelFile)
            val options = Interpreter.Options().apply {
                numThreads = 2
            }
            try {
                interpreter = Interpreter(modelBuffer, options)
            } catch (e: IllegalArgumentException) {
                Logger.e(tag, "YOLO model file is not a valid TFLite model. " +
                    "Please re-export using: yolo export model=yolov8n.pt format=tflite", e)
                modelRegistry.updateModelStatus(
                    Constants.YOLO_MODEL_FILE,
                    ModelFileStatus.ERROR,
                    "Invalid TFLite model. Re-export with: yolo export model=yolov8n.pt format=tflite"
                )
                return@withContext false
            }

            // Query actual input size from model to avoid tensor size mismatch
            val inputShape = interpreter!!.getInputTensor(0).shape() // e.g., [1, 640, 640, 3]
            if (inputShape[1] != inputShape[2]) {
                Logger.w(tag, "YOLO model has non-square input (${inputShape[1]}x${inputShape[2]}); using height as input size")
            }
            actualInputSize = inputShape[1] // assuming square input; log warning if not
            Logger.i(tag, "YOLO input shape: ${inputShape.contentToString()} (${actualInputSize}x${actualInputSize})")
            Logger.i(tag, "YOLO output shape: ${interpreter!!.getOutputTensor(0).shape().contentToString()}")

            val loadTime = System.currentTimeMillis() - startTime
            
            isInitialized = true
            modelRegistry.updateModelStatus(Constants.YOLO_MODEL_FILE, ModelFileStatus.LOADED)
            
            Logger.i(tag, "YOLO initialized in ${loadTime}ms (stub mode)")
            Logger.perf(tag, "yolo_init", loadTime)
            
            true
        } catch (e: Exception) {
            Logger.e(tag, "Failed to initialize YOLO", e)
            modelRegistry.updateModelStatus(
                Constants.YOLO_MODEL_FILE,
                ModelFileStatus.ERROR,
                "Failed to load: ${e.message}"
            )
            isInitialized = false
            false
        }
    }
    
    /**
     * Detect objects in image
     * 
     * Runs on vision dispatcher to avoid blocking main thread.
     */
    override suspend fun detect(bitmap: Bitmap): List<Detection> = withContext(threadManager.visionDispatcher) {
        if (!isInitialized) {
            Logger.w(tag, "YOLO not initialized")
            return@withContext emptyList()
        }
        
        Logger.d(tag, "Running YOLO detection")
        
        return@withContext try {
            val startTime = System.currentTimeMillis()
            
            val preprocessed = preprocessImage(bitmap)
            val inputBuffer = bitmapToBuffer(preprocessed)

            val interp = interpreter ?: return@withContext emptyList<Detection>()

            // Determine output shape from model
            val outputShape = interp.getOutputTensor(0).shape()
            // YOLOv8 TFLite output: [1, numFeatures, numBoxes] where numFeatures = 4 + numClasses
            val numFeatures = outputShape[1]
            val numBoxes = outputShape[2]
            val output = Array(1) { Array(numFeatures) { FloatArray(numBoxes) } }
            
            interp.run(inputBuffer, output)
            
            val detections = postProcess(output, numFeatures, numBoxes)
            val filtered = nonMaxSuppression(detections)
            
            val inferenceTime = System.currentTimeMillis() - startTime
            
            Logger.d(tag, "YOLO detected ${filtered.size} objects in ${inferenceTime}ms")
            Logger.perf(tag, "yolo_inference", inferenceTime)
            
            filtered
        } catch (e: Exception) {
            Logger.e(tag, "YOLO detection failed", e)
            emptyList()
        }
    }
    
    /**
     * Release model resources
     */
    override suspend fun release() {
        Logger.d(tag, "Releasing YOLO resources")
        
        try {
            interpreter?.close()
            interpreter = null
            
            isInitialized = false
            Logger.i(tag, "YOLO resources released")
        } catch (e: Exception) {
            Logger.e(tag, "Error releasing YOLO", e)
        }
    }
    
    /**
     * Preprocess image for YOLO input
     *
     * Resize to model input size (queried from model at runtime)
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        return if (bitmap.width != actualInputSize || bitmap.height != actualInputSize) {
            Bitmap.createScaledBitmap(bitmap, actualInputSize, actualInputSize, true)
        } else {
            bitmap
        }
    }

    /**
     * Convert bitmap to a normalized float ByteBuffer for TFLite input (NHWC).
     */
    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * actualInputSize * actualInputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(actualInputSize * actualInputSize)
        bitmap.getPixels(pixels, 0, actualInputSize, 0, 0, actualInputSize, actualInputSize)

        for (pixel in pixels) {
            buffer.putFloat((pixel shr 16 and 0xFF) / 255.0f)  // R
            buffer.putFloat((pixel shr 8 and 0xFF) / 255.0f)   // G
            buffer.putFloat((pixel and 0xFF) / 255.0f)          // B
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Load model file into a MappedByteBuffer for TFLite.
     */
    private fun loadModelBuffer(modelFile: File): MappedByteBuffer {
        return FileInputStream(modelFile).use { inputStream ->
            val fileChannel = inputStream.channel
            fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
        }
    }

    /**
     * Post-process YOLOv8 TFLite output tensor.
     *
     * Expected output shape: [1, numFeatures, numBoxes]
     * where numFeatures = 4 (cx, cy, w, h) + numClasses
     */
    private fun postProcess(
        output: Array<Array<FloatArray>>,
        numFeatures: Int,
        numBoxes: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numClasses = numFeatures - 4
        val inputSize = actualInputSize.toFloat()

        for (i in 0 until numBoxes) {
            var bestClass = 0
            var bestScore = 0f
            for (c in 0 until numClasses) {
                val score = output[0][4 + c][i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            if (bestScore >= Constants.YOLO_CONFIDENCE_THRESHOLD && bestClass < labels.size) {
                val cx = output[0][0][i]
                val cy = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]

                detections.add(
                    Detection(
                        label = labels[bestClass],
                        confidence = bestScore,
                        bbox = BoundingBox(
                            x = ((cx - w / 2f) / inputSize).coerceIn(0f, 1f),
                            y = ((cy - h / 2f) / inputSize).coerceIn(0f, 1f),
                            width = (w / inputSize).coerceIn(0f, 1f),
                            height = (h / inputSize).coerceIn(0f, 1f)
                        )
                    )
                )
            }
        }

        return detections
    }
    /**
     * Apply Non-Maximum Suppression (NMS)
     * 
     * Filters overlapping detections based on IoU threshold.
     */
    private fun nonMaxSuppression(
        detections: List<Detection>,
        iouThreshold: Float = Constants.YOLO_IOU_THRESHOLD
    ): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence descending
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()
        
        for (detection in sorted) {
            var shouldAdd = true
            
            for (selected_detection in selected) {
                val iou = calculateIoU(detection.bbox, selected_detection.bbox)
                if (iou > iouThreshold) {
                    shouldAdd = false
                    break
                }
            }
            
            if (shouldAdd) {
                selected.add(detection)
            }
        }
        
        return selected
    }
    
    /**
     * Calculate Intersection over Union (IoU) between two bounding boxes
     */
    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x, box2.x)
        val y1 = maxOf(box1.y, box2.y)
        val x2 = minOf(box1.x + box1.width, box2.x + box2.width)
        val y2 = minOf(box1.y + box1.height, box2.y + box2.height)
        
        if (x2 < x1 || y2 < y1) {
            return 0f
        }
        
        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = box1.width * box1.height
        val area2 = box2.width * box2.height
        val union = area1 + area2 - intersection
        
        return if (union > 0) intersection / union else 0f
    }
}
