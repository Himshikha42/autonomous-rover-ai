package com.rover.ai.ai.litert

import android.os.Build
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger

/**
 * Hardware acceleration delegate options
 */
enum class DelegateType {
    CPU,
    GPU,
    NNAPI,
    HEXAGON // Qualcomm DSP
}

/**
 * Delegate configuration
 */
data class DelegateConfig(
    val type: DelegateType,
    val numThreads: Int = 2,
    val allowFp16: Boolean = true,
    val allowInt8: Boolean = false
)

/**
 * Auto-detect and select best hardware delegate for TensorFlow Lite
 * 
 * Detects available acceleration options on device and selects optimal delegate
 * for inference performance. Priority: GPU > NNAPI > CPU.
 * 
 * Singleton object (no Hilt needed, stateless utility).
 */
object DelegateSelector {
    
    private val tag = Constants.TAG_AI
    
    /**
     * Detect all available hardware delegates on current device
     * 
     * Checks for:
     * - GPU delegate (via OpenGL ES 3.1+)
     * - NNAPI delegate (Android 8.1+)
     * - CPU fallback (always available)
     * 
     * @return List of available delegate names
     */
    fun detectAvailableDelegates(): List<String> {
        Logger.d(tag, "Detecting available hardware delegates")
        
        val available = mutableListOf<String>()
        
        // CPU is always available
        available.add("CPU")
        
        // Check GPU support
        if (isGpuAvailable()) {
            available.add("GPU")
            Logger.d(tag, "GPU delegate available")
        }
        
        // Check NNAPI support (Android 8.1+)
        if (isNnapiAvailable()) {
            available.add("NNAPI")
            Logger.d(tag, "NNAPI delegate available")
        }
        
        // Check Hexagon DSP (Qualcomm only)
        if (isHexagonAvailable()) {
            available.add("HEXAGON")
            Logger.d(tag, "Hexagon DSP delegate available")
        }
        
        Logger.i(tag, "Available delegates: ${available.joinToString(", ")}")
        return available
    }
    
    /**
     * Select best available delegate based on performance priority
     * 
     * Priority order:
     * 1. GPU (fastest for vision + LLM)
     * 2. NNAPI (good balance)
     * 3. CPU (fallback)
     * 
     * @return Name of selected delegate ("GPU", "NNAPI", or "CPU")
     */
    fun selectBestDelegate(): String {
        Logger.d(tag, "Selecting best delegate")
        
        val available = detectAvailableDelegates()
        
        val selected = when {
            "GPU" in available -> "GPU"
            "NNAPI" in available -> "NNAPI"
            else -> "CPU"
        }
        
        Logger.i(tag, "Selected delegate: $selected")
        return selected
    }
    
    /**
     * Get recommended configuration for specific delegate
     * 
     * @param delegateName Name of delegate ("GPU", "NNAPI", "CPU")
     * @return DelegateConfig with optimal settings
     */
    fun getConfigForDelegate(delegateName: String): DelegateConfig {
        return when (delegateName.uppercase()) {
            "GPU" -> DelegateConfig(
                type = DelegateType.GPU,
                numThreads = 1, // GPU uses single thread
                allowFp16 = true,
                allowInt8 = false
            )
            "NNAPI" -> DelegateConfig(
                type = DelegateType.NNAPI,
                numThreads = 1,
                allowFp16 = true,
                allowInt8 = true
            )
            "HEXAGON" -> DelegateConfig(
                type = DelegateType.HEXAGON,
                numThreads = 1,
                allowFp16 = true,
                allowInt8 = true
            )
            else -> DelegateConfig(
                type = DelegateType.CPU,
                numThreads = 4,
                allowFp16 = false,
                allowInt8 = true
            )
        }
    }
    
    /**
     * Check if GPU delegate is available
     * 
     * Requires OpenGL ES 3.1+ for TFLite GPU delegate
     */
    private fun isGpuAvailable(): Boolean {
        return try {
            // Stub: In real implementation, check OpenGL ES version
            // val configurationInfo = activityManager.deviceConfigurationInfo
            // configurationInfo.reqGlEsVersion >= 0x00030001
            
            // Assume GPU available on Android 7.0+ (API 24+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        } catch (e: Exception) {
            Logger.w(tag, "Error checking GPU availability", e)
            false
        }
    }
    
    /**
     * Check if NNAPI delegate is available
     * 
     * NNAPI available on Android 8.1+ (API 27+)
     */
    private fun isNnapiAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
    }
    
    /**
     * Check if Hexagon DSP delegate is available
     * 
     * Only on Qualcomm Snapdragon devices with HVX support
     */
    private fun isHexagonAvailable(): Boolean {
        return try {
            // Check if device manufacturer is Qualcomm
            val manufacturer = Build.MANUFACTURER.lowercase()
            val board = Build.BOARD.lowercase()
            
            // Very basic detection - not reliable without actual SDK
            manufacturer.contains("qualcomm") || board.contains("msm")
        } catch (e: Exception) {
            Logger.w(tag, "Error checking Hexagon availability", e)
            false
        }
    }
    
    /**
     * Validate if specific delegate is supported on this device
     * 
     * @param delegateName Delegate to check
     * @return true if supported, false otherwise
     */
    fun isSupported(delegateName: String): Boolean {
        val available = detectAvailableDelegates()
        return delegateName.uppercase() in available.map { it.uppercase() }
    }
}
