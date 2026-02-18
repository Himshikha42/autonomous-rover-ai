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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level connection management for rover communication.
 * 
 * Wraps WebSocketClient and provides:
 * - Heartbeat monitoring to detect ESP32 communication loss
 * - Automatic state synchronization with StateManager
 * - Simplified API for sending commands
 * - Health monitoring and automatic recovery
 * - Reactive state exposure via StateFlows
 * 
 * Use this class instead of WebSocketClient directly for most operations.
 * 
 * @property webSocketClient Low-level WebSocket communication handler
 * @property stateManager Global state manager for rover state
 * @property threadManager Thread dispatcher manager for coroutine execution
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val webSocketClient: WebSocketClient,
    private val stateManager: StateManager,
    private val threadManager: ThreadManager
) {
    
    private val tag = Constants.TAG_COMMUNICATION
    
    private val scope = CoroutineScope(threadManager.communicationDispatcher)
    
    private var heartbeatJob: Job? = null
    private var lastSensorReportTime = 0L
    
    /**
     * Connection health state.
     */
    enum class HealthState {
        HEALTHY,      // Receiving sensor reports regularly
        DEGRADED,     // Connected but no recent sensor data
        UNHEALTHY     // Connection lost or not receiving data
    }
    
    private val _healthState = MutableStateFlow(HealthState.UNHEALTHY)
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()
    
    private val _sensorData = MutableStateFlow(SensorReport())
    val sensorData: StateFlow<SensorReport> = _sensorData.asStateFlow()
    
    private val _lastAlert = MutableStateFlow<AlertReport?>(null)
    val lastAlert: StateFlow<AlertReport?> = _lastAlert.asStateFlow()
    
    /**
     * Current connection state from WebSocketClient.
     */
    val connectionState: StateFlow<WebSocketClient.ConnectionState> = 
        webSocketClient.connectionState
    
    init {
        setupWebSocketCallbacks()
        startHeartbeatMonitoring()
    }
    
    /**
     * Connect to ESP32 rover.
     * 
     * Initiates WebSocket connection and starts monitoring.
     */
    fun connect() {
        Logger.i(tag, "ConnectionManager: Starting connection")
        webSocketClient.connect()
    }
    
    /**
     * Disconnect from ESP32 rover.
     * 
     * Gracefully closes connection and stops monitoring.
     */
    fun disconnect() {
        Logger.i(tag, "ConnectionManager: Stopping connection")
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocketClient.disconnect()
        updateHealthState(HealthState.UNHEALTHY)
    }
    
    /**
     * Send movement command to rover.
     * 
     * @param command RoverCommand to execute
     * @return true if command was sent successfully
     */
    fun sendCommand(command: RoverCommand): Boolean {
        val sent = webSocketClient.sendCommand(command)
        if (sent) {
            stateManager.updateMovementState(
                isMoving = command.cmd != RoverCommand.STOP,
                command = command.cmd
            )
        }
        return sent
    }
    
    /**
     * Send FORWARD command.
     * 
     * @param speed Motor speed (0-255)
     * @return true if command was sent
     */
    fun moveForward(speed: Int = Constants.DEFAULT_MOTOR_SPEED): Boolean {
        return sendCommand(RoverCommand(RoverCommand.FORWARD, speed))
    }
    
    /**
     * Send BACKWARD command.
     * 
     * @param speed Motor speed (0-255)
     * @return true if command was sent
     */
    fun moveBackward(speed: Int = Constants.DEFAULT_MOTOR_SPEED): Boolean {
        return sendCommand(RoverCommand(RoverCommand.BACKWARD, speed))
    }
    
    /**
     * Send LEFT command.
     * 
     * @param speed Motor speed (0-255)
     * @return true if command was sent
     */
    fun turnLeft(speed: Int = Constants.DEFAULT_MOTOR_SPEED): Boolean {
        return sendCommand(RoverCommand(RoverCommand.LEFT, speed))
    }
    
    /**
     * Send RIGHT command.
     * 
     * @param speed Motor speed (0-255)
     * @return true if command was sent
     */
    fun turnRight(speed: Int = Constants.DEFAULT_MOTOR_SPEED): Boolean {
        return sendCommand(RoverCommand(RoverCommand.RIGHT, speed))
    }
    
    /**
     * Send STOP command.
     * 
     * @return true if command was sent
     */
    fun stop(): Boolean {
        return sendCommand(RoverCommand(RoverCommand.STOP, 0))
    }
    
    /**
     * Force reconnection attempt.
     * 
     * Useful after network changes or manual intervention.
     */
    fun reconnect() {
        Logger.i(tag, "ConnectionManager: Forcing reconnection")
        webSocketClient.reconnect()
    }
    
    /**
     * Check if currently connected to rover.
     * 
     * @return true if connected and healthy
     */
    fun isConnected(): Boolean {
        return connectionState.value == WebSocketClient.ConnectionState.CONNECTED &&
               healthState.value == HealthState.HEALTHY
    }
    
    /**
     * Setup WebSocket callbacks to handle incoming messages.
     */
    private fun setupWebSocketCallbacks() {
        webSocketClient.onSensorReport = { report ->
            handleSensorReport(report)
        }
        
        webSocketClient.onAlert = { alert ->
            handleAlert(alert)
        }
        
        webSocketClient.onConnected = {
            Logger.i(tag, "WebSocket connected")
            updateHealthState(HealthState.DEGRADED) // Wait for sensor data
        }
        
        webSocketClient.onDisconnected = { reason ->
            Logger.w(tag, "WebSocket disconnected: $reason")
            updateHealthState(HealthState.UNHEALTHY)
        }
    }
    
    /**
     * Handle incoming sensor report.
     * 
     * Updates state manager and tracks last report time for heartbeat monitoring.
     * 
     * @param report Sensor report from ESP32
     */
    private fun handleSensorReport(report: SensorReport) {
        lastSensorReportTime = System.currentTimeMillis()
        _sensorData.value = report
        
        // Update StateManager with sensor data
        stateManager.updateSensorData(
            StateManager.SensorData(
                distanceCm = report.dist,
                irLeft = report.ir.left,
                irCenter = report.ir.center,
                irRight = report.ir.right,
                cliffDetected = report.cliff,
                edgeDetected = report.edge,
                motorSpeedLeft = report.motors.left,
                motorSpeedRight = report.motors.right,
                lineFollowMode = report.lineFollow
            )
        )
        
        stateManager.updateMovementState(
            isMoving = report.moving,
            command = report.cmd
        )
        
        // Mark connection as healthy
        if (_healthState.value != HealthState.HEALTHY) {
            updateHealthState(HealthState.HEALTHY)
        }
        
        Logger.v(tag, "Sensor: dist=${report.dist}cm, IR=[${report.ir.left},${report.ir.center},${report.ir.right}], cmd=${report.cmd}")
    }
    
    /**
     * Handle incoming alert message.
     * 
     * Logs alert and updates state for UI consumption.
     * 
     * @param alert Alert report from ESP32
     */
    private fun handleAlert(alert: AlertReport) {
        _lastAlert.value = alert
        Logger.w(tag, "ALERT: ${alert.alert} - ${alert.message} (dist=${alert.dist}cm)")
        
        // Update StateManager with alert information
        stateManager.setError("${alert.alert}: ${alert.message}")
    }
    
    /**
     * Start heartbeat monitoring to detect communication loss.
     * 
     * Monitors time since last sensor report and degrades health state
     * if ESP32 stops sending data (even if WebSocket is connected).
     */
    private fun startHeartbeatMonitoring() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(Constants.HEARTBEAT_INTERVAL_MS)
                checkHeartbeat()
            }
        }
    }
    
    /**
     * Check heartbeat and update health state.
     * 
     * Detects if ESP32 has stopped sending sensor reports even though
     * WebSocket connection appears active.
     */
    private fun checkHeartbeat() {
        val timeSinceLastReport = System.currentTimeMillis() - lastSensorReportTime
        val isConnected = connectionState.value == WebSocketClient.ConnectionState.CONNECTED
        
        when {
            !isConnected -> {
                // WebSocket not connected
                if (_healthState.value != HealthState.UNHEALTHY) {
                    updateHealthState(HealthState.UNHEALTHY)
                }
            }
            
            timeSinceLastReport > Constants.WATCHDOG_TIMEOUT_MS -> {
                // Connected but not receiving data
                if (_healthState.value != HealthState.DEGRADED) {
                    Logger.w(tag, "No sensor data for ${timeSinceLastReport}ms, connection degraded")
                    updateHealthState(HealthState.DEGRADED)
                }
                
                // If no data for too long, force reconnect
                if (timeSinceLastReport > Constants.WATCHDOG_TIMEOUT_MS * 2) {
                    Logger.e(tag, "Heartbeat timeout, forcing reconnect")
                    webSocketClient.reconnect()
                }
            }
            
            else -> {
                // Healthy - receiving data regularly
                if (_healthState.value != HealthState.HEALTHY) {
                    updateHealthState(HealthState.HEALTHY)
                }
            }
        }
    }
    
    /**
     * Update health state and synchronize with StateManager.
     * 
     * @param newState New health state
     */
    private fun updateHealthState(newState: HealthState) {
        val oldState = _healthState.value
        if (oldState != newState) {
            _healthState.value = newState
            Logger.d(tag, "Health state: $oldState -> $newState")
            
            // Update StateManager connection state based on health
            val connectionState = when (newState) {
                HealthState.HEALTHY -> StateManager.ConnectionState.CONNECTED
                HealthState.DEGRADED -> StateManager.ConnectionState.CONNECTED
                HealthState.UNHEALTHY -> StateManager.ConnectionState.DISCONNECTED
            }
            stateManager.updateConnectionState(connectionState)
        }
    }
}
