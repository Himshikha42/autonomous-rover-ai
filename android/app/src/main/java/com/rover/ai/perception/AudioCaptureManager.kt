package com.rover.ai.perception

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio capture manager interface for microphone recording operations.
 * Abstracts audio recording implementation from the rest of the system.
 */
interface IAudioCaptureManager {
    /**
     * Start capturing audio from microphone.
     * Requires RECORD_AUDIO permission.
     */
    fun startCapture()
    
    /**
     * Stop capturing audio and release resources.
     */
    fun stopCapture()
    
    /**
     * Get the latest audio buffer from the microphone.
     * 
     * @return ByteArray containing raw audio data, or null if not recording
     */
    fun getAudioBuffer(): ByteArray?
    
    /**
     * Observable audio capture status state.
     */
    val audioStatus: StateFlow<AudioStatus>
}

/**
 * Audio capture operational status states.
 */
enum class AudioStatus {
    /**
     * Audio capture is not running.
     */
    STOPPED,
    
    /**
     * Audio capture is recording.
     */
    RECORDING,
    
    /**
     * Audio capture encountered an error.
     */
    ERROR
}

/**
 * AudioRecord-based implementation of microphone audio capture.
 * 
 * Manages microphone access, captures audio buffers for voice processing,
 * and provides raw PCM audio data for Gemma voice input or speech recognition.
 * 
 * Features:
 * - AudioRecord API integration
 * - Configurable sample rate (16kHz default)
 * - Mono channel recording
 * - 16-bit PCM encoding
 * - Thread-safe buffer access
 * - Permission checking
 * - Status monitoring via StateFlow
 * 
 * Audio Configuration:
 * - Sample Rate: 16000 Hz (suitable for speech)
 * - Channel: MONO
 * - Encoding: 16-bit PCM
 * - Buffer size: 1 second of audio data
 * 
 * Usage:
 * ```
 * audioCaptureManager.startCapture()
 * val audioData = audioCaptureManager.getAudioBuffer()
 * audioCaptureManager.stopCapture()
 * ```
 */
@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context
) : IAudioCaptureManager {
    
    companion object {
        // Audio configuration constants
        private const val SAMPLE_RATE = 16000 // 16kHz for speech
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        
        // Buffer size: 1 second of audio
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_ENCODING
        ).coerceAtLeast(SAMPLE_RATE * 2) // 2 bytes per sample (16-bit)
    }
    
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    
    @Volatile
    private var isRecording = false
    
    @Volatile
    private var latestBuffer: ByteArray? = null
    
    private val _audioStatus = MutableStateFlow(AudioStatus.STOPPED)
    override val audioStatus: StateFlow<AudioStatus> = _audioStatus.asStateFlow()
    
    /**
     * Start capturing audio from microphone.
     * 
     * Initializes AudioRecord, checks permissions, and begins recording
     * audio data into a circular buffer.
     */
    override fun startCapture() {
        try {
            // Check for RECORD_AUDIO permission
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Logger.e(Constants.TAG_PERCEPTION, "RECORD_AUDIO permission not granted")
                _audioStatus.value = AudioStatus.ERROR
                return
            }
            
            Logger.i(Constants.TAG_PERCEPTION, "Starting audio capture...")
            
            // Stop any existing recording
            stopCapture()
            
            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_ENCODING,
                BUFFER_SIZE
            )
            
            val record = audioRecord ?: run {
                Logger.e(Constants.TAG_PERCEPTION, "Failed to create AudioRecord")
                _audioStatus.value = AudioStatus.ERROR
                return
            }
            
            // Check if AudioRecord initialized properly
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e(Constants.TAG_PERCEPTION, "AudioRecord not initialized")
                _audioStatus.value = AudioStatus.ERROR
                return
            }
            
            // Start recording
            record.startRecording()
            isRecording = true
            _audioStatus.value = AudioStatus.RECORDING
            
            // Start recording thread
            recordingThread = Thread {
                captureAudioLoop()
            }.apply {
                name = "AudioCaptureThread"
                start()
            }
            
            Logger.i(Constants.TAG_PERCEPTION, "Audio capture started successfully")
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Failed to start audio capture", e)
            _audioStatus.value = AudioStatus.ERROR
        }
    }
    
    /**
     * Audio capture loop running on background thread.
     */
    private fun captureAudioLoop() {
        val buffer = ByteArray(BUFFER_SIZE)
        val record = audioRecord
        
        if (record == null) {
            Logger.e(Constants.TAG_PERCEPTION, "AudioRecord is null in capture loop")
            isRecording = false
            return
        }
        
        try {
            while (isRecording) {
                val bytesRead = record.read(buffer, 0, buffer.size)
                
                if (bytesRead > 0) {
                    // Store latest audio buffer
                    synchronized(this) {
                        latestBuffer = buffer.copyOf(bytesRead)
                    }
                } else if (bytesRead < 0) {
                    Logger.w(Constants.TAG_PERCEPTION, "AudioRecord read error: $bytesRead")
                }
            }
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Error in audio capture loop", e)
            _audioStatus.value = AudioStatus.ERROR
        }
    }
    
    /**
     * Stop capturing audio and release resources.
     * 
     * Stops recording, shuts down recording thread, and releases AudioRecord.
     */
    override fun stopCapture() {
        try {
            Logger.i(Constants.TAG_PERCEPTION, "Stopping audio capture...")
            
            isRecording = false
            
            // Wait for recording thread to finish
            recordingThread?.join(1000)
            recordingThread = null
            
            // Stop and release AudioRecord
            audioRecord?.apply {
                try {
                    if (state == AudioRecord.STATE_INITIALIZED) {
                        stop()
                    }
                    release()
                } catch (e: Exception) {
                    Logger.w(Constants.TAG_PERCEPTION, "Error releasing AudioRecord", e)
                }
            }
            audioRecord = null
            
            synchronized(this) {
                latestBuffer = null
            }
            
            _audioStatus.value = AudioStatus.STOPPED
            Logger.i(Constants.TAG_PERCEPTION, "Audio capture stopped")
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Error stopping audio capture", e)
            _audioStatus.value = AudioStatus.ERROR
        }
    }
    
    /**
     * Get the latest audio buffer from the microphone.
     * 
     * Returns a copy of the current audio buffer to prevent concurrent modification.
     * Buffer contains raw 16-bit PCM audio data at 16kHz sample rate.
     * 
     * @return ByteArray containing raw audio data, or null if not recording
     */
    override fun getAudioBuffer(): ByteArray? {
        return try {
            synchronized(this) {
                latestBuffer?.copyOf()
            }
        } catch (e: Exception) {
            Logger.e(Constants.TAG_PERCEPTION, "Failed to get audio buffer", e)
            null
        }
    }
}
