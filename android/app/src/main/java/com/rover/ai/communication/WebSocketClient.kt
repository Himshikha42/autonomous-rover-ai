package com.rover.ai.communication

import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager
import com.rover.ai.core.ThreadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp-based WebSocket client for ESP32 rover communication.
 * 
 * Features:
 * - Automatic reconnection with exponential backoff
 * - Connection state tracking via StateFlow
 * - Message parsing and routing to callbacks
 * - Proper lifecycle management
 * - Thread-safe command sending
 * - Structured error handling
 * 
 * @property stateManager Global state manager for updating rover state
 * @property threadManager Thread dispatcher manager for coroutine execution
 */
@Singleton
class WebSocketClient @Inject constructor(
    private val stateManager: StateManager,
    private val threadManager: ThreadManager
) {
    
    /**
     * WebSocket connection states.
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ERROR
    }
    
    private val tag = Constants.TAG_COMMUNICATION
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var webSocket: WebSocket? = null
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(threadManager.communicationDispatcher)
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(Constants.WS_PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()
    
    /**
     * Callback for sensor report messages from ESP32.
     */
    var onSensorReport: ((SensorReport) -> Unit)? = null
    
    /**
     * Callback for alert messages from ESP32.
     */
    var onAlert: ((AlertReport) -> Unit)? = null
    
    /**
     * Callback for connection established event.
     */
    var onConnected: (() -> Unit)? = null
    
    /**
     * Callback for connection lost event.
     */
    var onDisconnected: ((reason: String) -> Unit)? = null
    
    /**
     * Connect to ESP32 WebSocket server.
     * 
     * Initiates connection to ws://192.168.4.1:81.
     * Updates connection state and triggers auto-reconnect on failure.
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED || 
            _connectionState.value == ConnectionState.CONNECTING) {
            Logger.d(tag, "Already connected or connecting, ignoring connect request")
            return
        }
        
        Logger.i(tag, "Initiating WebSocket connection to ${Constants.WS_URL}")
        updateState(ConnectionState.CONNECTING)
        
        val request = Request.Builder()
            .url(Constants.WS_URL)
            .build()
        
        try {
            webSocket = okHttpClient.newWebSocket(request, webSocketListener)
        } catch (e: Exception) {
            Logger.e(tag, "Failed to create WebSocket", e)
            handleConnectionFailure("Failed to create WebSocket: ${e.message}")
        }
    }
    
    /**
     * Disconnect from ESP32 WebSocket server.
     * 
     * Gracefully closes connection and cancels any pending reconnection attempts.
     */
    fun disconnect() {
        Logger.i(tag, "Disconnecting WebSocket")
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        
        updateState(ConnectionState.DISCONNECTED)
    }
    
    /**
     * Send command to rover via WebSocket.
     * 
     * Commands are serialized to JSON and sent if connection is active.
     * Fails silently if not connected to prevent blocking.
     * 
     * @param command RoverCommand to send
     * @return true if command was sent, false if not connected
     */
    fun sendCommand(command: RoverCommand): Boolean {
        val ws = webSocket
        if (ws == null || _connectionState.value != ConnectionState.CONNECTED) {
            Logger.w(tag, "Cannot send command, not connected: ${command.cmd}")
            return false
        }
        
        return try {
            val json = command.toJson()
            val sent = ws.send(json)
            if (sent) {
                Logger.d(tag, "Sent command: ${command.cmd} (speed=${command.speed})")
            } else {
                Logger.w(tag, "Failed to queue command: ${command.cmd}")
            }
            sent
        } catch (e: Exception) {
            Logger.e(tag, "Error sending command: ${command.cmd}", e)
            false
        }
    }
    
    /**
     * Manually trigger reconnection.
     * 
     * Useful for forcing reconnect after network changes.
     */
    fun reconnect() {
        Logger.i(tag, "Manual reconnect triggered")
        disconnect()
        reconnectAttempts = 0
        connect()
    }
    
    private val webSocketListener = object : WebSocketListener() {
        
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Logger.i(tag, "WebSocket connection established")
            reconnectAttempts = 0
            updateState(ConnectionState.CONNECTED)
            stateManager.updateConnectionState(StateManager.ConnectionState.CONNECTED)
            
            scope.launch {
                onConnected?.invoke()
            }
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            Logger.v(tag, "Received message: ${text.take(100)}")
            parseAndRouteMessage(text)
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Logger.i(tag, "WebSocket closing: code=$code, reason=$reason")
            webSocket.close(1000, null)
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Logger.i(tag, "WebSocket closed: code=$code, reason=$reason")
            handleConnectionLoss("Connection closed: $reason")
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Logger.e(tag, "WebSocket failure: ${t.message}", t)
            handleConnectionFailure("Connection failed: ${t.message}")
        }
    }
    
    /**
     * Parse incoming JSON message and route to appropriate callback.
     * 
     * Determines message type and delegates to sensor report or alert handler.
     * 
     * @param message Raw JSON string from ESP32
     */
    private fun parseAndRouteMessage(message: String) {
        try {
            // Quick type check to avoid unnecessary parsing
            when {
                message.contains("\"type\":\"sensor_report\"") || 
                message.contains("\"dist\":") -> {
                    val report = SensorReport.fromJson(message)
                    if (report != null) {
                        scope.launch {
                            onSensorReport?.invoke(report)
                        }
                    } else {
                        Logger.w(tag, "Failed to parse sensor report")
                    }
                }
                
                message.contains("\"type\":\"alert\"") -> {
                    val alert = AlertReport.fromJson(message)
                    if (alert != null) {
                        scope.launch {
                            onAlert?.invoke(alert)
                        }
                    } else {
                        Logger.w(tag, "Failed to parse alert report")
                    }
                }
                
                else -> {
                    Logger.d(tag, "Unknown message type: ${message.take(50)}")
                }
            }
        } catch (e: Exception) {
            Logger.e(tag, "Error parsing message", e)
        }
    }
    
    /**
     * Handle connection loss and trigger reconnection logic.
     * 
     * @param reason Human-readable reason for connection loss
     */
    private fun handleConnectionLoss(reason: String) {
        Logger.w(tag, "Connection lost: $reason")
        updateState(ConnectionState.DISCONNECTED)
        stateManager.updateConnectionState(StateManager.ConnectionState.DISCONNECTED)
        
        scope.launch {
            onDisconnected?.invoke(reason)
        }
        
        scheduleReconnect()
    }
    
    /**
     * Handle connection failure and trigger reconnection logic.
     * 
     * @param reason Human-readable reason for connection failure
     */
    private fun handleConnectionFailure(reason: String) {
        Logger.e(tag, "Connection failure: $reason")
        updateState(ConnectionState.ERROR)
        stateManager.updateConnectionState(StateManager.ConnectionState.ERROR)
        stateManager.setError(reason)
        
        scope.launch {
            onDisconnected?.invoke(reason)
        }
        
        scheduleReconnect()
    }
    
    /**
     * Schedule automatic reconnection with exponential backoff.
     * 
     * Implements exponential backoff up to MAX_RECONNECT_ATTEMPTS.
     * Backoff formula: min(2^attempt * base_delay, 60s)
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= Constants.WS_MAX_RECONNECT_ATTEMPTS) {
            Logger.e(tag, "Max reconnect attempts reached, giving up")
            updateState(ConnectionState.ERROR)
            stateManager.setError("Failed to reconnect after ${Constants.WS_MAX_RECONNECT_ATTEMPTS} attempts")
            return
        }
        
        reconnectAttempts++
        updateState(ConnectionState.RECONNECTING)
        stateManager.updateConnectionState(StateManager.ConnectionState.RECONNECTING)
        
        // Exponential backoff: 2s, 4s, 8s, 16s, 32s, 60s (max)
        val delayMs = (Constants.WS_RECONNECT_DELAY_MS * (1 shl (reconnectAttempts - 1)))
            .coerceAtMost(60_000L)
        
        Logger.i(tag, "Scheduling reconnect attempt $reconnectAttempts in ${delayMs}ms")
        
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            Logger.i(tag, "Reconnect attempt $reconnectAttempts")
            connect()
        }
    }
    
    /**
     * Update connection state and log transition.
     * 
     * @param newState New connection state
     */
    private fun updateState(newState: ConnectionState) {
        val oldState = _connectionState.value
        if (oldState != newState) {
            _connectionState.value = newState
            Logger.d(tag, "Connection state: $oldState -> $newState")
        }
    }
}
