package com.rover.ai.communication

import com.rover.ai.core.Constants
import org.json.JSONObject

/**
 * Command to send to the rover ESP32.
 * 
 * Represents movement commands with configurable motor speed.
 * Serializes to JSON format expected by ESP32 firmware.
 * 
 * @property cmd Command name (FORWARD, BACKWARD, LEFT, RIGHT, STOP)
 * @property speed Motor speed 0-255, defaults to 180
 */
data class RoverCommand(
    val cmd: String,
    val speed: Int = Constants.DEFAULT_MOTOR_SPEED
) {
    /**
     * Serialize command to JSON string for WebSocket transmission.
     * 
     * @return JSON string like: {"cmd":"FORWARD","speed":180}
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("cmd", cmd)
            put("speed", speed)
        }.toString()
    }
    
    companion object {
        const val FORWARD = "FORWARD"
        const val BACKWARD = "BACKWARD"
        const val LEFT = "LEFT"
        const val RIGHT = "RIGHT"
        const val STOP = "STOP"
    }
}

/**
 * Infrared sensor data from the rover.
 * 
 * @property left Left IR sensor detection (true = obstacle detected)
 * @property center Center IR sensor detection
 * @property right Right IR sensor detection
 */
data class IrData(
    val left: Boolean = false,
    val center: Boolean = false,
    val right: Boolean = false
)

/**
 * Motor speed data from the rover.
 * 
 * @property left Left motor speed (-255 to 255)
 * @property right Right motor speed (-255 to 255)
 */
data class MotorData(
    val left: Int = 0,
    val right: Int = 0
)

/**
 * Complete sensor report from ESP32.
 * 
 * Contains all telemetry data including distance, IR sensors, cliff detection,
 * motor status, and movement state.
 * 
 * @property type Message type identifier (should be "sensor_report")
 * @property dist Ultrasonic distance reading in centimeters
 * @property ir Infrared sensor readings
 * @property cliff Cliff sensor detection status
 * @property edge Edge sensor detection status
 * @property moving Whether the rover is currently moving
 * @property cmd Current command being executed
 * @property motors Current motor speeds
 * @property lineFollow Line following mode enabled
 * @property timestamp ESP32 timestamp in milliseconds
 */
data class SensorReport(
    val type: String = "sensor_report",
    val dist: Float = 400.0f,
    val ir: IrData = IrData(),
    val cliff: Boolean = false,
    val edge: Boolean = false,
    val moving: Boolean = false,
    val cmd: String = "STOP",
    val motors: MotorData = MotorData(),
    val lineFollow: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Parse sensor report from JSON string received via WebSocket.
         * 
         * Handles missing fields gracefully with default values for robustness.
         * 
         * @param json JSON string from ESP32
         * @return Parsed SensorReport or null if parsing fails
         */
        fun fromJson(json: String): SensorReport? {
            return try {
                val obj = JSONObject(json)
                
                // Parse IR data
                val irObj = obj.optJSONObject("ir")
                val ir = if (irObj != null) {
                    IrData(
                        left = irObj.optBoolean("left", false),
                        center = irObj.optBoolean("center", false),
                        right = irObj.optBoolean("right", false)
                    )
                } else {
                    IrData()
                }
                
                // Parse motor data
                val motorsObj = obj.optJSONObject("motors")
                val motors = if (motorsObj != null) {
                    MotorData(
                        left = motorsObj.optInt("left", 0),
                        right = motorsObj.optInt("right", 0)
                    )
                } else {
                    MotorData()
                }
                
                SensorReport(
                    type = obj.optString("type", "sensor_report"),
                    dist = obj.optDouble("dist", 400.0).toFloat(),
                    ir = ir,
                    cliff = obj.optBoolean("cliff", false),
                    edge = obj.optBoolean("edge", false),
                    moving = obj.optBoolean("moving", false),
                    cmd = obj.optString("cmd", "STOP"),
                    motors = motors,
                    lineFollow = obj.optBoolean("line_follow", false),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Alert message from ESP32.
 * 
 * Represents critical events like obstacle detection, cliff warnings,
 * or low battery alerts.
 * 
 * @property type Message type identifier (should be "alert")
 * @property alert Alert category (OBSTACLE, CLIFF, BATTERY, etc.)
 * @property message Human-readable alert message
 * @property dist Distance reading associated with alert (if applicable)
 * @property timestamp ESP32 timestamp in milliseconds
 */
data class AlertReport(
    val type: String = "alert",
    val alert: String = "",
    val message: String = "",
    val dist: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Parse alert report from JSON string received via WebSocket.
         * 
         * @param json JSON string from ESP32
         * @return Parsed AlertReport or null if parsing fails
         */
        fun fromJson(json: String): AlertReport? {
            return try {
                val obj = JSONObject(json)
                AlertReport(
                    type = obj.optString("type", "alert"),
                    alert = obj.optString("alert", ""),
                    message = obj.optString("message", ""),
                    dist = obj.optDouble("dist", 0.0).toFloat(),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
