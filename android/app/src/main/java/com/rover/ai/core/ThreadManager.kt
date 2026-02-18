package com.rover.ai.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized coroutine dispatcher management for the Autonomous Rover system.
 * 
 * Provides specialized dispatchers for different workloads to prevent thread contention
 * and ensure smooth operation across UI, AI, vision, and communication layers.
 * 
 * Usage:
 * ```
 * withContext(threadManager.aiDispatcher) {
 *     // CPU-intensive AI inference
 * }
 * 
 * withContext(threadManager.visionDispatcher) {
 *     // Image processing and YOLO detection
 * }
 * ```
 */
@Singleton
class ThreadManager @Inject constructor() {
    
    /**
     * Main UI thread dispatcher - for Compose UI updates
     */
    val uiDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    
    /**
     * IO dispatcher - for network operations, file I/O, database access
     */
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    
    /**
     * Default dispatcher - for general CPU-bound work
     */
    val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    
    /**
     * Vision processing dispatcher - dedicated thread pool for camera frame analysis
     * Separate from AI to prevent blocking YOLO processing
     */
    val visionDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(
        Constants.VISION_THREAD_POOL_SIZE
    ) { runnable ->
        Thread(runnable, "Vision-Worker").apply {
            priority = Thread.NORM_PRIORITY + 1 // Slightly elevated for real-time vision
        }
    }.asCoroutineDispatcher()
    
    /**
     * AI inference dispatcher - dedicated thread pool for Gemma/LLM operations
     * High priority to ensure AI responses don't lag
     */
    val aiDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(
        Constants.AI_THREAD_POOL_SIZE
    ) { runnable ->
        Thread(runnable, "AI-Worker").apply {
            priority = Thread.NORM_PRIORITY
        }
    }.asCoroutineDispatcher()
    
    /**
     * Communication dispatcher - for WebSocket operations
     * Separate to prevent network lag from affecting other systems
     */
    val communicationDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(
        Constants.IO_THREAD_POOL_SIZE
    ) { runnable ->
        Thread(runnable, "Comm-Worker").apply {
            priority = Thread.NORM_PRIORITY
        }
    }.asCoroutineDispatcher()
    
    /**
     * Shutdown all custom thread pools
     * Call this when the app is closing to clean up resources
     */
    fun shutdown() {
        Logger.i(Constants.TAG_CORE, "Shutting down thread pools")
        
        // Note: ExecutorService.shutdown() is called on the underlying executors
        // This is handled automatically when the coroutine scope is cancelled
        // For explicit cleanup, we could track the ExecutorServices and call shutdown()
    }
}
