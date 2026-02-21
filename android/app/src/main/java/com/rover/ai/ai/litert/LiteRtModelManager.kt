package com.rover.ai.ai.litert

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.rover.ai.ai.model.ModelFileStatus
import com.rover.ai.ai.model.ModelRegistry
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Model loading and lifecycle status
 */
enum class ModelStatus {
    UNLOADED,
    LOADING,
    LOADED,
    ERROR
}

/**
 * Model metadata information
 */
data class ModelInfo(
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val accelerator: String,
    val loadTimeMs: Long
)

/**
 * Interface for LiteRT/TensorFlow Lite model management
 */
interface LiteRtModelManager {
    /**
     * Current model loading status
     */
    val modelStatus: StateFlow<ModelStatus>
    
    /**
     * Load the Gemma 3n model from assets into memory
     * 
     * @return true if successful, false otherwise
     */
    suspend fun loadModel(): Boolean
    
    /**
     * Unload the model from memory to free resources
     */
    suspend fun unloadModel()
    
    /**
     * Check if model is currently loaded
     */
    fun isLoaded(): Boolean
    
    /**
     * Get information about the loaded model
     * 
     * @return ModelInfo if loaded, null otherwise
     */
    fun getModelInfo(): ModelInfo?

    /**
     * Generate a text response from the loaded LLM
     *
     * @param prompt Input text prompt
     * @return Generated response string
     * @throws IllegalStateException if model is not loaded
     */
    suspend fun generateResponse(prompt: String): String

    /**
     * Generate a streaming text response from the loaded LLM
     *
     * @param prompt Input text prompt
     * @param onPartialResult Called with each text chunk as it is generated
     * @param onDone Called when generation is complete
     * @param onError Called if an error occurs
     */
    fun generateResponseStreaming(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    )
}

/**
 * Implementation of LiteRT model manager for Gemma 3n
 * 
 * Manages lifecycle of on-device LLM model using the Google LiteRT LLM
 * (com.google.ai.edge.litertlm) library's LlmInference API.
 * The YOLO and Depth models use the standard TFLite interpreter since they
 * are .tflite format.
 * 
 * Thread-safe singleton managed by Hilt.
 */
@Singleton
class LiteRtModelManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRegistry: ModelRegistry
) : LiteRtModelManager {
    
    private val tag = Constants.TAG_AI
    
    private val _modelStatus = MutableStateFlow(ModelStatus.UNLOADED)
    override val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()
    
    private var modelInfo: ModelInfo? = null
    
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    
    /**
     * Load model from external storage using Engine API
     * 
     * In production, this will:
     * 1. Check if model file exists in external storage
     * 2. Build EngineConfig with model path, backend, maxNumTokens
     * 3. Create Engine(engineConfig) and call engine.initialize()
     * 4. Create conversation via engine.createConversation(ConversationConfig(...))
     */
    override suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        Logger.d(tag, "Loading Gemma 3n model: ${Constants.GEMMA_MODEL_FILE}")
        
        if (_modelStatus.value == ModelStatus.LOADED) {
            Logger.w(tag, "Model already loaded")
            return@withContext true
        }
        
        // Check if model is available
        if (!modelRegistry.isModelAvailable(Constants.GEMMA_MODEL_FILE)) {
            Logger.w(tag, "Gemma model not found in external storage - disabling LLM capabilities")
            _modelStatus.value = ModelStatus.ERROR
            modelRegistry.updateModelStatus(
                Constants.GEMMA_MODEL_FILE,
                ModelFileStatus.MISSING,
                "Model file not found. Please sideload via ADB."
            )
            return@withContext false
        }
        
        _modelStatus.value = ModelStatus.LOADING
        
        return@withContext try {
            val startTime = System.currentTimeMillis()
            
            // Get model file from external storage
            val modelFile = modelRegistry.getModelFile(Constants.GEMMA_MODEL_FILE)
            
            if (!modelFile.exists() || !modelFile.canRead()) {
                Logger.e(tag, "Model file not accessible at ${modelFile.absolutePath}")
                _modelStatus.value = ModelStatus.ERROR
                modelRegistry.updateModelStatus(
                    Constants.GEMMA_MODEL_FILE,
                    ModelFileStatus.ERROR,
                    "Model file not accessible"
                )
                return@withContext false
            }
            
            Logger.i(tag, "Loading model from: ${modelFile.absolutePath} (${modelFile.length() / 1_000_000}MB)")
            
            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU,
                maxNumTokens = Constants.GEMMA_MAX_TOKENS,
            )
            val eng = Engine(engineConfig)
            eng.initialize()
            val conv = eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = Constants.GEMMA_TOP_K.toInt(),
                        topP = Constants.GEMMA_TOP_P.toDouble(),
                        temperature = Constants.GEMMA_TEMPERATURE.toDouble(),
                    ),
                )
            )
            engine = eng
            conversation = conv
            
            val loadTime = System.currentTimeMillis() - startTime
            
            modelInfo = ModelInfo(
                name = Constants.GEMMA_MODEL_NAME,
                version = "1.0",
                sizeBytes = modelFile.length(),
                accelerator = Constants.GEMMA_ACCELERATOR,
                loadTimeMs = loadTime
            )
            
            _modelStatus.value = ModelStatus.LOADED
            modelRegistry.updateModelStatus(Constants.GEMMA_MODEL_FILE, ModelFileStatus.LOADED)
            Logger.i(tag, "Model loaded successfully in ${loadTime}ms")
            
            true
        } catch (e: Exception) {
            Logger.e(tag, "Failed to load model", e)
            _modelStatus.value = ModelStatus.ERROR
            modelRegistry.updateModelStatus(
                Constants.GEMMA_MODEL_FILE,
                ModelFileStatus.ERROR,
                "Failed to load: ${e.message}"
            )
            modelInfo = null
            false
        }
    }
    
    /**
     * Unload model and free memory
     */
    override suspend fun unloadModel() {
        Logger.d(tag, "Unloading model")
        
        try {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            
            modelInfo = null
            _modelStatus.value = ModelStatus.UNLOADED
            
            Logger.i(tag, "Model unloaded successfully")
        } catch (e: Exception) {
            Logger.e(tag, "Error unloading model", e)
            _modelStatus.value = ModelStatus.ERROR
        }
    }
    
    override fun isLoaded(): Boolean {
        return _modelStatus.value == ModelStatus.LOADED
    }
    
    override fun getModelInfo(): ModelInfo? {
        return modelInfo
    }

    /**
     * Generate a text response from the loaded Gemma LLM
     */
    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val conv = conversation
            ?: throw IllegalStateException("Model not loaded")
        Logger.d(tag, "Generating response for prompt (${prompt.length} chars)")
        val startTime = System.currentTimeMillis()
        val result = CompletableDeferred<String>()
        val sb = StringBuilder()
        val contents = mutableListOf<Content>()
        contents.add(Content.Text(prompt))
        conv.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    sb.append(message.toString())
                }
                override fun onDone() {
                    result.complete(sb.toString())
                }
                override fun onError(throwable: Throwable) {
                    result.completeExceptionally(throwable)
                }
            },
        )
        val response = result.await()
        Logger.perf(tag, "llm_inference", System.currentTimeMillis() - startTime)
        response
    }

    /**
     * Generate a streaming text response from the loaded Gemma LLM
     */
    override fun generateResponseStreaming(
        prompt: String,
        onPartialResult: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val conv = conversation
            ?: run { onError(IllegalStateException("Model not loaded")); return }
        Logger.d(tag, "Streaming response for prompt (${prompt.length} chars)")
        val contents = mutableListOf<Content>()
        contents.add(Content.Text(prompt))
        conv.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    onPartialResult(message.toString())
                }
                override fun onDone() {
                    onDone()
                }
                override fun onError(throwable: Throwable) {
                    onError(throwable)
                }
            },
        )
    }
}
