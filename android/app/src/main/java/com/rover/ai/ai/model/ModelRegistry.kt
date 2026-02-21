package com.rover.ai.ai.model

import android.content.Context
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status of an AI model file
 */
enum class ModelFileStatus {
    MISSING,      // Model file not found
    FOUND,        // Model file detected but not loaded
    LOADED,       // Model loaded and ready
    ERROR         // Error loading or validating model
}

/**
 * Metadata for a single AI model
 * 
 * @property name Display name of the model
 * @property fileName File name in the models directory
 * @property expectedSizeBytes Approximate expected file size in bytes
 * @property sha256Checksum Optional SHA256 checksum for validation
 */
data class ModelMetadata(
    val name: String,
    val fileName: String,
    val expectedSizeBytes: Long,
    val sha256Checksum: String? = null
)

/**
 * Runtime status information for a model
 * 
 * @property metadata Model metadata
 * @property status Current status
 * @property actualSizeBytes Actual file size if found, null otherwise
 * @property errorMessage Error message if status is ERROR
 */
data class ModelInfo(
    val metadata: ModelMetadata,
    val status: ModelFileStatus,
    val actualSizeBytes: Long? = null,
    val errorMessage: String? = null
)

/**
 * Registry for managing AI model files stored in external storage.
 * 
 * Models are stored in: /sdcard/Android/data/com.rover.ai/files/models/
 * This path uses getExternalFilesDir("models") which is app-scoped storage.
 * No special permissions needed on Android 10+.
 * 
 * Responsibilities:
 * - Define expected model metadata
 * - Scan models directory for present/missing models
 * - Report model status to UI
 * - Provide file paths for model loading
 * 
 * Thread-safe singleton managed by Hilt.
 */
@Singleton
class ModelRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = Constants.TAG_AI
    
    // State flow for UI observation
    private val _modelStates = MutableStateFlow<Map<String, ModelInfo>>(emptyMap())
    val modelStates: StateFlow<Map<String, ModelInfo>> = _modelStates.asStateFlow()
    
    // Model definitions with metadata
    private val modelDefinitions = listOf(
        ModelMetadata(
            name = "Gemma 3n E2B-it",
            fileName = Constants.GEMMA_MODEL_FILE,
            expectedSizeBytes = 3_400_000_000L, // ~3.40GB
            sha256Checksum = null // Optional: Add checksum if available
        ),
        ModelMetadata(
            name = "YOLOv8 Nano",
            fileName = Constants.YOLO_MODEL_FILE,
            expectedSizeBytes = 12_700_000L, // ~12.7MB
            sha256Checksum = null
        ),
        ModelMetadata(
            name = "Depth Anything V2",
            fileName = Constants.DEPTH_MODEL_FILE,
            expectedSizeBytes = 94_300_000L, // ~94.3MB
            sha256Checksum = null
        )
    )
    
    /**
     * Get the models directory path
     * 
     * Uses getExternalFilesDir("models") which is app-scoped storage.
     * Directory is created if it doesn't exist.
     */
    fun getModelsDirectory(): File {
        val modelsDir = context.getExternalFilesDir("models")
        
        if (modelsDir == null) {
            Logger.e(tag, "Failed to get models directory - external storage not available")
            throw IllegalStateException("External storage not available")
        }
        
        // Create directory if it doesn't exist
        if (!modelsDir.exists()) {
            val created = modelsDir.mkdirs()
            Logger.i(tag, "Models directory created: $created at ${modelsDir.absolutePath}")
        }
        
        return modelsDir
    }
    
    /**
     * Get the absolute path for a specific model file
     * 
     * @param fileName Name of the model file
     * @return File object for the model (may not exist)
     */
    fun getModelFile(fileName: String): File {
        return File(getModelsDirectory(), fileName)
    }
    
    /**
     * Scan the models directory and update status for all models
     * 
     * Should be called on a background thread (uses Dispatchers.IO).
     * Updates _modelStates flow with current status.
     */
    suspend fun scanModels() = withContext(Dispatchers.IO) {
        Logger.d(tag, "Scanning models directory...")
        
        try {
            val modelsDir = getModelsDirectory()
            Logger.i(tag, "Models directory: ${modelsDir.absolutePath}")
            
            val newStates = mutableMapOf<String, ModelInfo>()
            
            for (metadata in modelDefinitions) {
                val file = File(modelsDir, metadata.fileName)
                
                val info = if (file.exists() && file.isFile) {
                    val actualSize = file.length()
                    
                    // Log size mismatch warning
                    if (actualSize < metadata.expectedSizeBytes * Constants.MODEL_SIZE_TOLERANCE_FACTOR) {
                        Logger.w(
                            tag,
                            "${metadata.name}: File size ${actualSize / 1_000_000}MB is smaller than expected ${metadata.expectedSizeBytes / 1_000_000}MB"
                        )
                    }
                    
                    Logger.i(tag, "${metadata.name}: FOUND (${actualSize / 1_000_000}MB)")
                    
                    ModelInfo(
                        metadata = metadata,
                        status = ModelFileStatus.FOUND,
                        actualSizeBytes = actualSize
                    )
                } else {
                    Logger.w(tag, "${metadata.name}: MISSING at ${file.absolutePath}")
                    
                    ModelInfo(
                        metadata = metadata,
                        status = ModelFileStatus.MISSING
                    )
                }
                
                newStates[metadata.fileName] = info
            }
            
            _modelStates.value = newStates
            
            val foundCount = newStates.values.count { it.status == ModelFileStatus.FOUND }
            val missingCount = newStates.values.count { it.status == ModelFileStatus.MISSING }
            Logger.i(tag, "Model scan complete: $foundCount found, $missingCount missing")
            
        } catch (e: Exception) {
            Logger.e(tag, "Error scanning models", e)
        }
    }
    
    /**
     * Check if a specific model is available
     * 
     * @param fileName Model file name
     * @return true if model file exists and is readable
     */
    fun isModelAvailable(fileName: String): Boolean {
        val state = _modelStates.value[fileName]
        return state?.status == ModelFileStatus.FOUND || state?.status == ModelFileStatus.LOADED
    }
    
    /**
     * Update the status of a specific model
     * 
     * Used by model loaders to report LOADED or ERROR status.
     * 
     * @param fileName Model file name
     * @param status New status
     * @param errorMessage Optional error message if status is ERROR
     */
    fun updateModelStatus(fileName: String, status: ModelFileStatus, errorMessage: String? = null) {
        val currentStates = _modelStates.value.toMutableMap()
        val currentInfo = currentStates[fileName]
        
        if (currentInfo != null) {
            currentStates[fileName] = currentInfo.copy(
                status = status,
                errorMessage = errorMessage
            )
            _modelStates.value = currentStates
            
            Logger.d(tag, "Updated ${currentInfo.metadata.name} status to $status")
        }
    }
    
    /**
     * Get formatted ADB push command for a model
     * 
     * @param fileName Model file name
     * @return ADB push command string
     */
    fun getAdbPushCommand(fileName: String): String {
        val destPath = getModelsDirectory().absolutePath
        return "adb push $fileName $destPath/$fileName"
    }
}
