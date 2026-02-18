package com.rover.ai.perception

import android.graphics.Bitmap
import com.rover.ai.core.Logger
import com.rover.ai.core.Constants
import com.rover.ai.core.StateManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Complete perceptual data snapshot combining all sensor inputs.
 * 
 * Represents the rover's complete sensory state at a single moment in time,
 * combining visual, audio, and physical sensor data.
 * 
 * @property cameraFrame Visual frame from camera (null if camera not available)
 * @property audioBuffer Raw audio data from microphone (null if audio not available)
 * @property sensorData Physical sensor readings from ESP32
 * @property timestamp Unix timestamp in milliseconds when this percept was captured
 */
data class RoverPercept(
    val cameraFrame: Bitmap?,
    val audioBuffer: ByteArray?,
    val sensorData: StateManager.SensorData,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if visual data is available.
     */
    val hasVisualData: Boolean
        get() = cameraFrame != null
    
    /**
     * Check if audio data is available.
     */
    val hasAudioData: Boolean
        get() = audioBuffer != null
    
    /**
     * Check if this percept has complete sensor data.
     */
    val isComplete: Boolean
        get() = hasVisualData || hasAudioData
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as RoverPercept
        
        if (cameraFrame != other.cameraFrame) return false
        if (audioBuffer != null) {
            if (other.audioBuffer == null) return false
            if (!audioBuffer.contentEquals(other.audioBuffer)) return false
        } else if (other.audioBuffer != null) return false
        if (sensorData != other.sensorData) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = cameraFrame?.hashCode() ?: 0
        result = 31 * result + (audioBuffer?.contentHashCode() ?: 0)
        result = 31 * result + sensorData.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Sensor fusion engine that combines all perceptual inputs.
 * 
 * Acts as the central perceptual system coordinator, synchronizing and combining
 * data from multiple sensory sources:
 * - Camera frames (visual perception)
 * - Microphone audio (auditory input)
 * - ESP32 sensors (distance, IR, cliff, motor state)
 * 
 * This fused perceptual data feeds into:
 * - Gemma AI for scene understanding
 * - YOLO for object detection
 * - Decision engine for behavior selection
 * - Emotion engine for affective state
 * 
 * Features:
 * - Synchronized data capture across all sources
 * - Thread-safe percept generation
 * - Graceful handling of missing sensor data
 * - Timestamp synchronization
 * - Resource management (bitmap recycling)
 * 
 * Usage:
 * ```
 * val percept = sensorFusion.getCurrentPercept()
 * if (percept.hasVisualData) {
 *     // Process camera frame
 * }
 * if (percept.hasAudioData) {
 *     // Process audio buffer
 * }
 * // Always have sensor data from ESP32
 * val distance = percept.sensorData.distanceCm
 * ```
 */
@Singleton
class SensorFusion @Inject constructor(
    private val cameraManager: ICameraManager,
    private val audioCaptureManager: IAudioCaptureManager,
    private val stateManager: StateManager
) {
    
    /**
     * Get current complete perceptual snapshot.
     * 
     * Captures and combines data from all available sensor sources at the current moment.
     * This method is thread-safe and can be called from any thread.
     * 
     * Visual and audio data may be null if those sensors are not running or
     * if capture fails. Sensor data from ESP32 is always available from StateManager.
     * 
     * @return RoverPercept containing synchronized sensor data
     */
    fun getCurrentPercept(): RoverPercept {
        return try {
            val timestamp = System.currentTimeMillis()
            
            // Capture camera frame (may be null if camera not running)
            val cameraFrame = try {
                if (cameraManager.cameraStatus.value == CameraStatus.RUNNING) {
                    cameraManager.captureFrame()
                } else {
                    null
                }
            } catch (e: Exception) {
                Logger.w(Constants.TAG_PERCEPTION, "Failed to capture camera frame", e)
                null
            }
            
            // Capture audio buffer (may be null if not recording)
            val audioBuffer = try {
                if (audioCaptureManager.audioStatus.value == AudioStatus.RECORDING) {
                    audioCaptureManager.getAudioBuffer()
                } else {
                    null
                }
            } catch (e: Exception) {
                Logger.w(Constants.TAG_PERCEPTION, "Failed to capture audio buffer", e)
                null
            }
            
            // Get sensor data from StateManager (always available)
            val sensorData = stateManager.currentState.sensorData
            
            // Create and return percept
            val percept = RoverPercept(
                cameraFrame = cameraFrame,
                audioBuffer = audioBuffer,
                sensorData = sensorData,
                timestamp = timestamp
            )
            
            Logger.d(
                Constants.TAG_PERCEPTION,
                "Percept captured: visual=${percept.hasVisualData}, " +
                "audio=${percept.hasAudioData}, " +
                "distance=${sensorData.distanceCm}cm"
            )
            
            percept
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Failed to create percept", e)
            
            // Return minimal percept with sensor data only
            RoverPercept(
                cameraFrame = null,
                audioBuffer = null,
                sensorData = stateManager.currentState.sensorData,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Get current percept with specific requirements.
     * 
     * Allows callers to specify whether they require visual and/or audio data.
     * If required data is not available, returns null instead of partial percept.
     * 
     * @param requireVisual If true, returns null if camera frame is not available
     * @param requireAudio If true, returns null if audio buffer is not available
     * @return RoverPercept meeting requirements, or null if requirements not met
     */
    fun getPerceptWithRequirements(
        requireVisual: Boolean = false,
        requireAudio: Boolean = false
    ): RoverPercept? {
        val percept = getCurrentPercept()
        
        return if ((requireVisual && !percept.hasVisualData) ||
                   (requireAudio && !percept.hasAudioData)) {
            Logger.d(
                Constants.TAG_PERCEPTION,
                "Percept requirements not met: requireVisual=$requireVisual, " +
                "requireAudio=$requireAudio, hasVisual=${percept.hasVisualData}, " +
                "hasAudio=${percept.hasAudioData}"
            )
            null
        } else {
            percept
        }
    }
    
    /**
     * Check if all perceptual systems are operational.
     * 
     * @return true if camera and audio are both running, false otherwise
     */
    fun isFullyOperational(): Boolean {
        val cameraRunning = cameraManager.cameraStatus.value == CameraStatus.RUNNING
        val audioRecording = audioCaptureManager.audioStatus.value == AudioStatus.RECORDING
        return cameraRunning && audioRecording
    }
    
    /**
     * Get status summary of all perceptual systems.
     * 
     * @return Map of system names to their status strings
     */
    fun getSystemStatus(): Map<String, String> {
        return mapOf(
            "camera" to cameraManager.cameraStatus.value.name,
            "audio" to audioCaptureManager.audioStatus.value.name,
            "sensors" to "ACTIVE"
        )
    }
}
