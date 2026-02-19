package com.rover.ai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rover.ai.communication.ConnectionManager
import com.rover.ai.core.Constants
import com.rover.ai.core.Logger
import com.rover.ai.core.StateManager

/**
 * Debug dashboard screen for rover telemetry and manual control.
 * 
 * Provides a comprehensive view of:
 * - Real-time sensor readings (ultrasonic, IR sensors)
 * - Battery status with visual indicators
 * - Motor speeds and movement state
 * - Current emotion and behavior mode
 * - AI system status (Gemma, YOLO)
 * - Manual control buttons for testing
 * 
 * Observes StateManager for reactive updates and sends commands via ConnectionManager.
 * 
 * @param stateManager Global state manager for rover state
 * @param connectionManager Connection manager for sending commands
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun DebugDashboardScreen(
    stateManager: StateManager,
    connectionManager: ConnectionManager,
    modifier: Modifier = Modifier
) {
    val roverState by stateManager.state.collectAsState()
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        item {
            Text(
                text = "Rover Debug Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp)
            )
        }
        
        // Disconnected banner
        if (roverState.connectionState == StateManager.ConnectionState.DISCONNECTED) {
            item {
                DisconnectedBanner()
            }
        }
        
        // Connection Status
        item {
            ConnectionStatusSection(roverState.connectionState)
        }
        
        // Distance Sensor
        item {
            val distanceCm = roverState.sensorData.distanceCm
            val color = when {
                distanceCm > 100f -> Color(0xFF4CAF50) // Green
                distanceCm > 50f -> Color(0xFFFFC107) // Yellow
                else -> Color(0xFFF44336) // Red
            }
            
            SensorCard(
                title = "Ultrasonic Distance",
                value = "${String.format("%.1f", distanceCm)} cm",
                icon = Icons.Default.Sensors,
                color = color
            )
        }
        
        // IR Sensors
        item {
            IRSensorSection(roverState.sensorData)
        }
        
        // Battery Level
        item {
            BatterySection(roverState.batteryLevel)
        }
        
        // Motor Speeds
        item {
            MotorSpeedsSection(roverState.sensorData)
        }
        
        // Emotion State
        item {
            SensorCard(
                title = "Emotion State",
                value = roverState.emotionState.name,
                icon = Icons.Default.EmojiEmotions,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        
        // Behavior Mode
        item {
            SensorCard(
                title = "Behavior Mode",
                value = roverState.behaviorMode.name,
                icon = Icons.Default.Psychology,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        
        // AI Status
        item {
            AIStatusSection(roverState)
        }
        
        // Manual Control Buttons
        item {
            ManualControlSection(connectionManager)
        }
        
        // Current Command
        item {
            SensorCard(
                title = "Current Command",
                value = roverState.currentCommand,
                icon = Icons.Default.DirectionsCar,
                color = if (roverState.isMoving) Color(0xFF2196F3) else Color.Gray
            )
        }
        
        // Error Display
        roverState.lastError?.let { error ->
            item {
                ErrorSection(error) {
                    stateManager.clearError()
                }
            }
        }
    }
}

/**
 * Prominent warning banner shown when the ESP32 is not connected.
 */
@Composable
private fun DisconnectedBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "⚡ ESP32: Not Connected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800)
            )
            Text(
                text = "WebSocket address: ${Constants.WS_URL}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFF9800)
            )
            Text(
                text = "Connect ESP32 to same WiFi network (${Constants.ESP32_SSID})",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFF9800)
            )
        }
    }
}

/**
 * Display connection status indicator.
 */
@Composable
private fun ConnectionStatusSection(connectionState: StateManager.ConnectionState) {
    val (color, statusText) = when (connectionState) {
        StateManager.ConnectionState.CONNECTED -> Color(0xFF4CAF50) to "Connected"
        StateManager.ConnectionState.CONNECTING -> Color(0xFFFFC107) to "Connecting..."
        StateManager.ConnectionState.RECONNECTING -> Color(0xFFFF9800) to "Reconnecting..."
        StateManager.ConnectionState.ERROR -> Color(0xFFF44336) to "Error"
        StateManager.ConnectionState.DISCONNECTED -> Color.Gray to "Disconnected"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Display IR sensor states as colored circles.
 */
@Composable
private fun IRSensorSection(sensorData: StateManager.SensorData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "IR Sensors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IRSensorIndicator("Left", sensorData.irLeft)
                IRSensorIndicator("Center", sensorData.irCenter)
                IRSensorIndicator("Right", sensorData.irRight)
            }
            
            if (sensorData.cliffDetected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ CLIFF DETECTED",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF44336),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Single IR sensor indicator.
 */
@Composable
private fun IRSensorIndicator(label: String, detected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (detected) Color(0xFFF44336) else Color(0xFF4CAF50))
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        
        Text(
            text = if (detected) "Line" else "Clear",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (detected) Color(0xFFF44336) else Color(0xFF4CAF50)
        )
    }
}

/**
 * Battery level display with progress bar.
 */
@Composable
private fun BatterySection(batteryLevel: Float) {
    val color = when {
        batteryLevel > 50f -> Color(0xFF4CAF50) // Green
        batteryLevel > 25f -> Color(0xFFFFC107) // Yellow
        else -> Color(0xFFF44336) // Red
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery",
                        tint = color,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Battery Level",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                Text(
                    text = "${String.format("%.1f", batteryLevel)}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = batteryLevel / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = color,
                trackColor = color.copy(alpha = 0.2f)
            )
        }
    }
}

/**
 * Motor speeds display with progress bars.
 */
@Composable
private fun MotorSpeedsSection(sensorData: StateManager.SensorData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Motor Speeds",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Left Motor
            MotorSpeedBar("Left Motor", sensorData.motorSpeedLeft)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Right Motor
            MotorSpeedBar("Right Motor", sensorData.motorSpeedRight)
        }
    }
}

/**
 * Single motor speed progress bar.
 */
@Composable
private fun MotorSpeedBar(label: String, speed: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "$speed / ${Constants.MAX_MOTOR_SPEED}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = speed.toFloat() / Constants.MAX_MOTOR_SPEED.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * AI system status display.
 */
@Composable
private fun AIStatusSection(roverState: StateManager.RoverState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "AI Systems",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AIStatusIndicator("Gemma", roverState.gemmaStatus)
                AIStatusIndicator("YOLO", roverState.yoloStatus)
            }
        }
    }
}

/**
 * Single AI status indicator.
 */
@Composable
private fun AIStatusIndicator(name: String, status: StateManager.AIStatus) {
    val (color, statusText) = when (status) {
        StateManager.AIStatus.IDLE -> Color.Gray to "Idle"
        StateManager.AIStatus.LOADING -> Color(0xFFFFC107) to "Loading"
        StateManager.AIStatus.READY -> Color(0xFF4CAF50) to "Ready"
        StateManager.AIStatus.INFERRING -> Color(0xFF2196F3) to "Running"
        StateManager.AIStatus.ERROR -> Color(0xFFF44336) to "Error"
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

/**
 * Manual control buttons section.
 */
@Composable
private fun ManualControlSection(connectionManager: ConnectionManager) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Manual Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Movement buttons row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        connectionManager.stop()
                        Logger.i(Constants.TAG_UI, "Manual: STOP")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Text("STOP")
                }
                
                Button(
                    onClick = {
                        connectionManager.moveForward()
                        Logger.i(Constants.TAG_UI, "Manual: FORWARD")
                    }
                ) {
                    Text("FWD")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Movement buttons row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        connectionManager.turnLeft()
                        Logger.i(Constants.TAG_UI, "Manual: LEFT")
                    }
                ) {
                    Text("LEFT")
                }
                
                Button(
                    onClick = {
                        connectionManager.turnRight()
                        Logger.i(Constants.TAG_UI, "Manual: RIGHT")
                    }
                ) {
                    Text("RIGHT")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Movement buttons row 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        connectionManager.moveBackward()
                        Logger.i(Constants.TAG_UI, "Manual: BACKWARD")
                    }
                ) {
                    Text("BACK")
                }
            }
        }
    }
}

/**
 * Error display section with dismiss button.
 */
@Composable
private fun ErrorSection(error: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = Color(0xFFF44336)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF44336)
                )
            }
            
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFF44336)
                )
            }
        }
    }
}
