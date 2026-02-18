package com.rover.ai.audio

import android.content.Context
import android.media.SoundPool
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import com.rover.ai.emotion.EmotionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Types of sound effects the rover can play.
 * 
 * Each sound corresponds to a specific emotion or event,
 * providing audio feedback for the rover's internal state.
 */
enum class SoundType {
    /**
     * Cheerful beep sound for happy emotion.
     */
    HAPPY_BEEP,
    
    /**
     * Alert buzzing sound for warning/alert emotion.
     */
    WARNING_BUZZ,
    
    /**
     * Curious/inquisitive sound effect.
     */
    CURIOUS_HMM,
    
    /**
     * Sad/disappointed tone.
     */
    SAD_TONE,
    
    /**
     * Startup initialization jingle.
     */
    STARTUP_JINGLE,
    
    /**
     * Notification ding sound.
     */
    ALERT_DING,
    
    /**
     * Achievement/success fanfare.
     */
    ACHIEVEMENT_FANFARE
}

/**
 * Sound effect player for robot audio feedback.
 * 
 * Manages and plays short sound effects using Android SoundPool for low-latency playback.
 * Automatically plays sounds based on emotion state changes from StateManager.
 * 
 * Features:
 * - SoundPool for efficient short sound playback
 * - Sound effect preloading for zero-latency playback
 * - Volume control
 * - Automatic emotion-triggered sound playback
 * - Thread-safe sound management
 * 
 * Sound Mapping:
 * - HAPPY → HAPPY_BEEP
 * - ALERT → WARNING_BUZZ
 * - CURIOUS → CURIOUS_HMM
 * - SCARED → WARNING_BUZZ
 * - SLEEPY → SAD_TONE
 * - LOW_BATTERY → ALERT_DING
 * - Startup → STARTUP_JINGLE
 * - Task completion → ACHIEVEMENT_FANFARE
 * 
 * Usage:
 * ```
 * soundEffectPlayer.preloadSounds()
 * soundEffectPlayer.playSound(SoundType.HAPPY_BEEP)
 * soundEffectPlayer.setVolume(0.8f)
 * ```
 * 
 * Note: Sound effect audio files should be placed in res/raw/ directory.
 * Placeholders are used if files are not yet available.
 */
@Singleton
class SoundEffectPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateManager: StateManager
) {
    
    companion object {
        private const val MAX_STREAMS = 4
    }
    
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<SoundType, Int>()
    
    @Volatile
    private var currentVolume: Float = Constants.SOUND_VOLUME
    
    @Volatile
    private var isEnabled: Boolean = Constants.SOUND_ENABLED
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var lastEmotionState: EmotionState? = null
    
    init {
        initializeSoundPool()
        observeEmotionChanges()
    }
    
    /**
     * Initialize SoundPool for audio playback.
     */
    private fun initializeSoundPool() {
        try {
            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .build()
            
            Logger.i(Constants.TAG_AUDIO, "SoundPool initialized")
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to initialize SoundPool", e)
        }
    }
    
    /**
     * Preload all sound effects into memory.
     * 
     * Should be called during app initialization to ensure
     * sounds are ready for immediate playback.
     * 
     * Note: Currently uses placeholder resource IDs.
     * Replace with actual sound files in res/raw/ directory.
     */
    fun preloadSounds() {
        val pool = soundPool ?: run {
            Logger.e(Constants.TAG_AUDIO, "SoundPool not initialized")
            return
        }
        
        try {
            Logger.i(Constants.TAG_AUDIO, "Preloading sound effects...")
            
            // TODO: Replace with actual sound file resource IDs
            // For now, using placeholder approach
            // Example: soundMap[SoundType.HAPPY_BEEP] = pool.load(context, R.raw.happy_beep, 1)
            
            // Placeholder: Log that sounds would be loaded here
            Logger.w(
                Constants.TAG_AUDIO,
                "Sound effect files not yet available. Using silent placeholders."
            )
            
            // Create placeholder entries so playSound() doesn't fail
            SoundType.values().forEach { soundType ->
                soundMap[soundType] = -1 // -1 indicates placeholder
            }
            
            Logger.i(Constants.TAG_AUDIO, "Sound effects preloaded (placeholders)")
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to preload sounds", e)
        }
    }
    
    /**
     * Play a specific sound effect.
     * 
     * @param soundType The type of sound to play
     */
    fun playSound(soundType: SoundType) {
        if (!isEnabled) {
            Logger.d(Constants.TAG_AUDIO, "Sound disabled, not playing $soundType")
            return
        }
        
        val pool = soundPool ?: run {
            Logger.w(Constants.TAG_AUDIO, "SoundPool not initialized")
            return
        }
        
        val soundId = soundMap[soundType] ?: run {
            Logger.w(Constants.TAG_AUDIO, "Sound not loaded: $soundType")
            return
        }
        
        if (soundId == -1) {
            // Placeholder sound
            Logger.d(Constants.TAG_AUDIO, "Would play sound: $soundType (placeholder)")
            return
        }
        
        try {
            pool.play(
                soundId,
                currentVolume,  // left volume
                currentVolume,  // right volume
                1,              // priority
                0,              // loop (0 = no loop)
                1.0f            // playback rate
            )
            
            Logger.d(Constants.TAG_AUDIO, "Playing sound: $soundType")
            
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Failed to play sound: $soundType", e)
        }
    }
    
    /**
     * Set playback volume for all sound effects.
     * 
     * @param volume Volume level from 0.0 (silent) to 1.0 (max)
     */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0.0f, 1.0f)
        Logger.d(Constants.TAG_AUDIO, "Volume set to $currentVolume")
    }
    
    /**
     * Enable or disable sound effect playback.
     * 
     * @param enabled true to enable sounds, false to disable
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Logger.i(Constants.TAG_AUDIO, "Sound effects ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Observe emotion state changes and play corresponding sounds.
     */
    private fun observeEmotionChanges() {
        scope.launch {
            stateManager.state
                .map { it.emotionState }
                .distinctUntilChanged()
                .collect { emotion ->
                    handleEmotionChange(emotion)
                }
        }
    }
    
    /**
     * Handle emotion state change and play appropriate sound.
     */
    private fun handleEmotionChange(emotion: EmotionState) {
        // Don't play sound on first initialization
        if (lastEmotionState == null) {
            lastEmotionState = emotion
            return
        }
        
        // Don't play sound if emotion hasn't changed
        if (lastEmotionState == emotion) {
            return
        }
        
        lastEmotionState = emotion
        
        // Map emotion to sound
        val soundType = when (emotion) {
            EmotionState.HAPPY -> SoundType.HAPPY_BEEP
            EmotionState.ALERT -> SoundType.WARNING_BUZZ
            EmotionState.CURIOUS -> SoundType.CURIOUS_HMM
            EmotionState.SCARED -> SoundType.WARNING_BUZZ
            EmotionState.SLEEPY -> SoundType.SAD_TONE
            EmotionState.LOW_BATTERY -> SoundType.ALERT_DING
            EmotionState.CONFUSED -> SoundType.CURIOUS_HMM
            else -> null
        }
        
        soundType?.let { playSound(it) }
    }
    
    /**
     * Play startup jingle.
     * Should be called when the app/rover initializes.
     */
    fun playStartup() {
        playSound(SoundType.STARTUP_JINGLE)
    }
    
    /**
     * Play achievement fanfare.
     * Should be called when a task completes successfully.
     */
    fun playAchievement() {
        playSound(SoundType.ACHIEVEMENT_FANFARE)
    }
    
    /**
     * Release all resources.
     * Should be called when the player is no longer needed.
     */
    fun release() {
        try {
            scope.cancel()
            soundPool?.release()
            soundPool = null
            soundMap.clear()
            Logger.i(Constants.TAG_AUDIO, "SoundEffectPlayer released")
        } catch (e: Exception) {
            Logger.e(Constants.TAG_AUDIO, "Error releasing SoundEffectPlayer", e)
        }
    }
}
