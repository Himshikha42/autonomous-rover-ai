package com.rover.ai.core

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured logging utility for the Autonomous Rover system.
 * 
 * Provides consistent, tagged logging with level filtering and optional file output.
 * NEVER use raw println(), System.out, or Log.d() with hardcoded tags elsewhere in the codebase.
 * 
 * Usage:
 * ```
 * Logger.d(Constants.TAG_AI, "Gemma inference started")
 * Logger.e(Constants.TAG_COMMUNICATION, "WebSocket connection failed", exception)
 * Logger.i(Constants.TAG_EMOTION, "Emotion transitioned to HAPPY")
 * ```
 */
object Logger {
    
    /**
     * Log levels following Android Log convention
     */
    enum class Level(val priority: Int) {
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6);
        
        companion object {
            fun fromPriority(priority: Int): Level {
                return values().find { it.priority == priority } ?: DEBUG
            }
        }
    }
    
    /**
     * Current minimum log level. Messages below this level will be filtered out.
     */
    var minLevel: Level = Level.DEBUG
    
    /**
     * Enable/disable file logging (not implemented yet, reserved for future)
     */
    var fileLoggingEnabled: Boolean = false
    
    /**
     * Timestamp formatter for log messages
     */
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    /**
     * Log a VERBOSE message
     */
    fun v(tag: String, message: String) {
        log(Level.VERBOSE, tag, message, null)
    }
    
    /**
     * Log a DEBUG message
     */
    fun d(tag: String, message: String) {
        log(Level.DEBUG, tag, message, null)
    }
    
    /**
     * Log an INFO message
     */
    fun i(tag: String, message: String) {
        log(Level.INFO, tag, message, null)
    }
    
    /**
     * Log a WARN message
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }
    
    /**
     * Log an ERROR message
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }
    
    /**
     * Internal logging function that handles filtering and formatting
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        if (level.priority < minLevel.priority) {
            return
        }
        
        // Format: [HH:mm:ss.SSS] [TAG] Message
        val timestamp = timestampFormat.format(Date())
        val formattedMessage = "[$timestamp] $message"
        
        // Log to Android logcat
        when (level) {
            Level.VERBOSE -> Log.v(tag, formattedMessage, throwable)
            Level.DEBUG -> Log.d(tag, formattedMessage, throwable)
            Level.INFO -> Log.i(tag, formattedMessage, throwable)
            Level.WARN -> Log.w(tag, formattedMessage, throwable)
            Level.ERROR -> Log.e(tag, formattedMessage, throwable)
        }
        
        // TODO: File logging if enabled
        if (fileLoggingEnabled) {
            // Future implementation: append to file
        }
    }
    
    /**
     * Log a structured event with key-value pairs
     */
    fun event(tag: String, eventName: String, properties: Map<String, Any>) {
        val propertiesStr = properties.entries.joinToString(", ") { "${it.key}=${it.value}" }
        i(tag, "EVENT: $eventName | $propertiesStr")
    }
    
    /**
     * Log performance metrics
     */
    fun perf(tag: String, operation: String, durationMs: Long) {
        d(tag, "PERF: $operation took ${durationMs}ms")
    }
    
    /**
     * Log method entry (for debugging complex flows)
     */
    fun enter(tag: String, methodName: String) {
        v(tag, "→ $methodName")
    }
    
    /**
     * Log method exit (for debugging complex flows)
     */
    fun exit(tag: String, methodName: String) {
        v(tag, "← $methodName")
    }
}
