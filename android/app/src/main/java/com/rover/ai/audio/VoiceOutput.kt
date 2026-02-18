package com.rover.ai.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import com.rover.ai.emotion.EmotionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice output manager interface for text-to-speech operations.
 */
interface IVoiceOutput {
    /**
     * Speak the given text using text-to-speech.
     * 
     * @param text The text to speak
     */
    fun speak(text: String)
    
    /**
     * Set voice parameters for speech synthesis.
     * 
     * @param pitch Pitch multiplier (0.5 to 2.0, 1.0 = normal)
     * @param speed Speech rate multiplier (0.5 to 2.0, 1.0 = normal)
     */
    fun setVoiceParams(pitch: Float, speed: Float)
    
    /**
     * Stop current speech and clear queue.
     */
    fun stop()
    
    /**
     * Observable TTS status state.
     */
    val ttsStatus: StateFlow<TtsStatus>
}

/**
 * Text-to-speech operational status states.
 */
enum class TtsStatus {
    /**
     * TTS is idle, not speaking.
     */
    IDLE,
    
    /**
     * TTS is currently speaking.
     */
    SPEAKING,
    
    /**
     * TTS encountered an error.
     */
    ERROR
}

/**
 * Android TextToSpeech wrapper for voice output.
 * 
 * Provides text-to-speech capabilities for the rover, allowing it to:
 * - Speak scene descriptions from Gemma AI
 * - Announce rover status and actions
 * - Provide audio feedback to users
 * 
 * Features:
 * - Android TTS API integration
 * - Emotion-aware voice modulation (pitch & speed)
 * - Speech queue management
 * - Status monitoring via StateFlow
 * - Initialization error handling
 * 
 * Voice Modulation by Emotion:
 * - HAPPY: Higher pitch (1.2), faster speed (1.1) - excited tone
 * - ALERT: Lower pitch (0.8), normal speed (1.0) - serious tone
 * - SLEEPY: Lower pitch (0.9), slower speed (0.8) - tired tone
 * - SCARED: Higher pitch (1.1), faster speed (1.2) - anxious tone
 * - CURIOUS: Normal pitch (1.0), slightly faster (1.1) - interested tone
 * - Default: Normal pitch (1.0), normal speed (1.0)
 * 
 * Usage:
 * ```
 * voiceOutput.speak("I see a person ahead")
 * voiceOutput.setVoiceParams(pitch = 1.2f, speed = 1.1f)
 * voiceOutput.stop()
 * ```
 */
@Singleton
class VoiceOutput @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateManager: StateManager
) : IVoiceOutput {
    
    companion object {
        private const val DEFAULT_PITCH = 1.0f
        private const val DEFAULT_SPEED = 1.0f
    }
    
    private var textToSpeech: TextToSpeech? = null
    
    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var currentPitch = DEFAULT_PITCH
    
    @Volatile
    private var currentSpeed = DEFAULT_SPEED
    
    private val _ttsStatus = MutableStateFlow(TtsStatus.IDLE)
    override val ttsStatus: StateFlow<TtsStatus> = _ttsStatus.asStateFlow()
    
    init {
        initializeTts()
    }
    
    /**
     * Initialize Android TextToSpeech engine.
     */
    private fun initializeTts() {
        try {
            Logger.i(Constants.TAG_AUDIO, "Initializing TextToSpeech...")
            
            textToSpeech = TextToSpeech(context) { status ->
                when (status) {
                    TextToSpeech.SUCCESS -> {
                        configureTts()
                        isInitialized = true
                        Logger.i(Constants.TAG_AUDIO, "TextToSpeech initialized successfully")
                    }
                    else -> {
                        Logger.e(Constants.TAG_AUDIO, "TextToSpeech initialization failed: $status")
                        _ttsStatus.value = TtsStatus.ERROR
                    }
                }
            }
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to initialize TextToSpeech", e)
            _ttsStatus.value = TtsStatus.ERROR
        }
    }
    
    /**
     * Configure TTS engine with default settings.
     */
    private fun configureTts() {
        val tts = textToSpeech ?: return
        
        try {
            // Set language to US English
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Logger.w(Constants.TAG_AUDIO, "US English not supported, using default language")
            }
            
            // Set default voice parameters
            tts.setPitch(currentPitch)
            tts.setSpeechRate(currentSpeed)
            
            // Set utterance progress listener
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _ttsStatus.value = TtsStatus.SPEAKING
                    Logger.d(Constants.TAG_AUDIO, "TTS started: $utteranceId")
                }
                
                override fun onDone(utteranceId: String?) {
                    _ttsStatus.value = TtsStatus.IDLE
                    Logger.d(Constants.TAG_AUDIO, "TTS completed: $utteranceId")
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _ttsStatus.value = TtsStatus.ERROR
                    Logger.e(Constants.TAG_AUDIO, "TTS error: $utteranceId")
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    _ttsStatus.value = TtsStatus.ERROR
                    Logger.e(Constants.TAG_AUDIO, "TTS error: $utteranceId, code: $errorCode")
                }
            })
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to configure TTS", e)
        }
    }
    
    /**
     * Speak the given text using text-to-speech.
     * 
     * Automatically adjusts pitch and speed based on current emotion state.
     * 
     * @param text The text to speak
     */
    override fun speak(text: String) {
        if (!isInitialized) {
            Logger.w(Constants.TAG_AUDIO, "TTS not initialized, cannot speak")
            return
        }
        
        val tts = textToSpeech ?: run {
            Logger.w(Constants.TAG_AUDIO, "TextToSpeech is null")
            return
        }
        
        if (text.isBlank()) {
            Logger.w(Constants.TAG_AUDIO, "Attempted to speak empty text")
            return
        }
        
        try {
            // Apply emotion-based voice parameters
            applyEmotionVoiceParams()
            
            // Speak the text
            val utteranceId = "utterance_${System.currentTimeMillis()}"
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            
            Logger.i(Constants.TAG_AUDIO, "Speaking: \"${text.take(50)}...\"")
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to speak text", e)
            _ttsStatus.value = TtsStatus.ERROR
        }
    }
    
    /**
     * Apply voice parameters based on current emotion state.
     */
    private fun applyEmotionVoiceParams() {
        val emotion = stateManager.currentState.emotionState
        
        val (pitch, speed) = when (emotion) {
            EmotionState.HAPPY -> Pair(1.2f, 1.1f)
            EmotionState.ALERT -> Pair(0.8f, 1.0f)
            EmotionState.SLEEPY -> Pair(0.9f, 0.8f)
            EmotionState.SCARED -> Pair(1.1f, 1.2f)
            EmotionState.CURIOUS -> Pair(1.0f, 1.1f)
            EmotionState.CONFUSED -> Pair(0.95f, 0.9f)
            EmotionState.THINKING -> Pair(1.0f, 0.9f)
            EmotionState.LOW_BATTERY -> Pair(0.85f, 0.8f)
            else -> Pair(DEFAULT_PITCH, DEFAULT_SPEED)
        }
        
        setVoiceParams(pitch, speed)
    }
    
    /**
     * Set voice parameters for speech synthesis.
     * 
     * @param pitch Pitch multiplier (0.5 to 2.0, 1.0 = normal)
     * @param speed Speech rate multiplier (0.5 to 2.0, 1.0 = normal)
     */
    override fun setVoiceParams(pitch: Float, speed: Float) {
        val tts = textToSpeech ?: return
        
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        
        try {
            tts.setPitch(currentPitch)
            tts.setSpeechRate(currentSpeed)
            
            Logger.d(
                Constants.TAG_AUDIO,
                "Voice params set: pitch=$currentPitch, speed=$currentSpeed"
            )
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to set voice params", e)
        }
    }
    
    /**
     * Stop current speech and clear queue.
     * 
     * Interrupts any ongoing speech and removes pending utterances.
     */
    override fun stop() {
        try {
            textToSpeech?.stop()
            _ttsStatus.value = TtsStatus.IDLE
            Logger.d(Constants.TAG_AUDIO, "TTS stopped")
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to stop TTS", e)
        }
    }
    
    /**
     * Check if TTS is currently speaking.
     * 
     * @return true if speaking, false otherwise
     */
    fun isSpeaking(): Boolean {
        return try {
            textToSpeech?.isSpeaking ?: false
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to check speaking status", e)
            false
        }
    }
    
    /**
     * Release all resources.
     * Should be called when voice output is no longer needed.
     */
    fun release() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
            _ttsStatus.value = TtsStatus.IDLE
            Logger.i(Constants.TAG_AUDIO, "VoiceOutput released")
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Error releasing VoiceOutput", e)
        }
    }
}
