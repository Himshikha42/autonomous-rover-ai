package com.rover.ai.perception

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Camera manager interface for frame capture operations.
 * Abstracts camera implementation details from the rest of the system.
 */
interface ICameraManager {
    /**
     * Start the camera and begin frame capture.
     * Requires CAMERA permission.
     * 
     * @param lifecycleOwner Android lifecycle owner for camera binding
     */
    fun startCamera(lifecycleOwner: LifecycleOwner)
    
    /**
     * Stop the camera and release resources.
     */
    fun stopCamera()
    
    /**
     * Capture the latest frame from the camera.
     * 
     * @return Bitmap of the current frame, or null if camera is not running
     */
    fun captureFrame(): Bitmap?
    
    /**
     * Observable camera status state.
     */
    val cameraStatus: StateFlow<CameraStatus>
}

/**
 * Camera operational status states.
 */
enum class CameraStatus {
    /**
     * Camera is not running.
     */
    STOPPED,
    
    /**
     * Camera is initializing.
     */
    STARTING,
    
    /**
     * Camera is running and capturing frames.
     */
    RUNNING,
    
    /**
     * Camera encountered an error.
     */
    ERROR
}

/**
 * CameraX-based implementation of camera frame capture.
 * 
 * Manages camera lifecycle, captures frames at configurable FPS,
 * and provides Bitmap frames for AI processing (Gemma, YOLO).
 * 
 * Features:
 * - CameraX integration with ImageAnalysis use case
 * - Configurable FPS from Constants.CAMERA_ANALYSIS_FPS
 * - Frame capture at Constants.CAMERA_RESOLUTION_WIDTH x HEIGHT
 * - Thread-safe frame access with synchronized buffers
 * - Lifecycle-aware start/stop
 * - Status monitoring via StateFlow
 * 
 * Usage:
 * ```
 * cameraManager.startCamera(lifecycleOwner)
 * val frame = cameraManager.captureFrame()
 * cameraManager.stopCamera()
 * ```
 */
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ICameraManager {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService? = null
    
    @Volatile
    private var latestFrame: Bitmap? = null
    
    private val _cameraStatus = MutableStateFlow(CameraStatus.STOPPED)
    override val cameraStatus: StateFlow<CameraStatus> = _cameraStatus.asStateFlow()
    
    /**
     * Start the camera and begin frame capture.
     * 
     * Initializes CameraX, configures ImageAnalysis use case,
     * and begins capturing frames at the configured FPS.
     * 
     * @param lifecycleOwner Android lifecycle owner for camera binding
     */
    override fun startCamera(lifecycleOwner: LifecycleOwner) {
        try {
            Logger.i(Constants.TAG_PERCEPTION, "Starting camera...")
            _cameraStatus.value = CameraStatus.STARTING
            
            // Initialize camera executor if needed
            if (cameraExecutor == null) {
                cameraExecutor = Executors.newSingleThreadExecutor()
            }
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindCamera(lifecycleOwner)
                } catch (e: Exception) {
                    Logger.e(Constants.TAG_PERCEPTION, "Failed to get camera provider", e)
                    _cameraStatus.value = CameraStatus.ERROR
                }
            }, ContextCompat.getMainExecutor(context))
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Failed to start camera", e)
            _cameraStatus.value = CameraStatus.ERROR
        }
    }
    
    /**
     * Bind camera to lifecycle and configure image analysis.
     */
    private fun bindCamera(lifecycleOwner: LifecycleOwner) {
        try {
            val provider = cameraProvider ?: run {
                Logger.e(Constants.TAG_PERCEPTION, "Camera provider is null")
                _cameraStatus.value = CameraStatus.ERROR
                return
            }
            
            // Unbind all use cases before rebinding
            provider.unbindAll()
            
            // Configure image analysis use case
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(
                    android.util.Size(
                        Constants.CAMERA_RESOLUTION_WIDTH,
                        Constants.CAMERA_RESOLUTION_HEIGHT
                    )
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    val executor = cameraExecutor ?: run {
                        Logger.e(Constants.TAG_PERCEPTION, "Camera executor is null")
                        _cameraStatus.value = CameraStatus.ERROR
                        return
                    }
                    
                    analysis.setAnalyzer(executor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            // Bind to lifecycle
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
            
            _cameraStatus.value = CameraStatus.RUNNING
            Logger.i(Constants.TAG_PERCEPTION, "Camera started successfully")
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Failed to bind camera", e)
            _cameraStatus.value = CameraStatus.ERROR
        }
    }
    
    /**
     * Process image from ImageProxy and convert to Bitmap.
     */
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            synchronized(this) {
                latestFrame?.recycle()
                latestFrame = bitmap
            }
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Failed to process image", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * Stop the camera and release resources.
     * 
     * Unbinds camera, clears frame buffer, and shuts down executor.
     */
    override fun stopCamera() {
        try {
            Logger.i(Constants.TAG_PERCEPTION, "Stopping camera...")
            
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageAnalysis = null
            
            synchronized(this) {
                latestFrame?.recycle()
                latestFrame = null
            }
            
            cameraExecutor?.shutdown()
            cameraExecutor = null
            
            _cameraStatus.value = CameraStatus.STOPPED
            Logger.i(Constants.TAG_PERCEPTION, "Camera stopped")
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Error stopping camera", e)
            _cameraStatus.value = CameraStatus.ERROR
        }
    }
    
    /**
     * Capture the latest frame from the camera.
     * 
     * Returns a copy of the current frame to prevent concurrent modification.
     * Caller is responsible for recycling the returned Bitmap when done.
     * 
     * @return Bitmap copy of the current frame, or null if camera is not running
     */
    override fun captureFrame(): Bitmap? {
        return try {
            synchronized(this) {
                latestFrame?.copy(latestFrame?.config ?: Bitmap.Config.ARGB_8888, false)
            }
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Failed to capture frame", e)
            null
        }
    }
}
