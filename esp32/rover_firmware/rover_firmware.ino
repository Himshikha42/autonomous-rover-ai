/**
 * Autonomous Rover ESP32 Firmware
 * 
 * Real-time safety and motor control layer for the Autonomous AI Rover.
 * Runs a 50Hz control loop with hardware-enforced safety overrides.
 * 
 * Features:
 * - WiFi Access Point (SSID: "RoverBrain", password: "rover2025")
 * - WebSocket server on port 81
 * - HC-SR04 ultrasonic distance sensing with voltage divider
 * - 3-channel IR line follower (cliff/edge detection + line tracking)
 * - L298N motor driver control via PWM
 * - Safety layer that ALWAYS overrides phone commands
 * - JSON protocol for communication
 * 
 * Safety Priorities (enforced in hardware):
 * 1. Cliff detection (IR center = no surface) → instant stop
 * 2. Edge detection (IR left/right = no surface) → instant stop
 * 3. Obstacle < 15cm → instant stop
 * 4. Obstacle < 40cm → reduce speed to 40%
 * 5. Motor timeout (no command for 2s) → stop
 * 6. Phone disconnect → stop all motors
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>

// ============================================================================
// PIN DEFINITIONS
// ============================================================================

// HC-SR04 Ultrasonic Sensor (with voltage divider on ECHO: 1KΩ + 2KΩ)
#define TRIG_PIN 5
#define ECHO_PIN 18

// 3-Channel IR Line Follower
#define IR_LEFT_PIN 19
#define IR_CENTER_PIN 21
#define IR_RIGHT_PIN 22

// L298N Motor Driver - Motor A (Left)
#define MOTOR_A_IN1 27
#define MOTOR_A_IN2 26
#define MOTOR_A_EN 14

// L298N Motor Driver - Motor B (Right)
#define MOTOR_B_IN1 32
#define MOTOR_B_IN2 33
#define MOTOR_B_EN 12

// ============================================================================
// CONFIGURATION CONSTANTS
// ============================================================================

// WiFi Access Point
const char* AP_SSID = "RoverBrain";
const char* AP_PASSWORD = "rover2025";
const IPAddress AP_IP(192, 168, 4, 1);
const IPAddress AP_GATEWAY(192, 168, 4, 1);
const IPAddress AP_SUBNET(255, 255, 255, 0);

// WebSocket
const uint16_t WS_PORT = 81;

// Safety Thresholds
const float OBSTACLE_STOP_CM = 15.0;
const float OBSTACLE_SLOW_CM = 40.0;
const float SLOW_SPEED_FACTOR = 0.4;
const unsigned long MOTOR_TIMEOUT_MS = 2000;
const unsigned long HEARTBEAT_INTERVAL_MS = 500;

// PWM Configuration
const int PWM_FREQ = 5000;
const int PWM_RESOLUTION = 8;
const int PWM_CHANNEL_A = 0;
const int PWM_CHANNEL_B = 1;

// Motor Speed Limits
const int MAX_SPEED = 255;
const int DEFAULT_SPEED = 180;

// Control Loop
const int CONTROL_LOOP_HZ = 50;
const int CONTROL_LOOP_MS = 1000 / CONTROL_LOOP_HZ;

// ============================================================================
// GLOBAL STATE
// ============================================================================

WebSocketsServer webSocket = WebSocketsServer(WS_PORT);

// Sensor Data
float distanceCm = 400.0;
bool irLeft = false;
bool irCenter = false;
bool irRight = false;
bool cliffDetected = false;
bool edgeDetected = false;

// Motor State
int motorSpeedLeft = 0;
int motorSpeedRight = 0;
String currentCommand = "STOP";
bool isMoving = false;

// Timing
unsigned long lastCommandTime = 0;
unsigned long lastSensorBroadcast = 0;
unsigned long lastControlLoop = 0;

// Connection State
bool phoneConnected = false;
uint8_t connectedClientNum = 0;

// Line Following Mode
bool lineFollowMode = false;

// ============================================================================
// FUNCTION PROTOTYPES
// ============================================================================

void setupWiFiAP();
void setupWebSocket();
void setupPins();
void setupPWM();
float readDistance();
void readIRSensors();
void checkSafety();
void executeCommand(String cmd, int speed);
void stopMotors();
void setMotorSpeed(int leftSpeed, int rightSpeed);
void lineFollowLogic();
void broadcastSensorData();
void broadcastAlert(String alertType, String message);
void webSocketEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t length);
void controlLoop();

// ============================================================================
// SETUP
// ============================================================================

void setup() {
  Serial.begin(115200);
  Serial.println("\n\n========================================");
  Serial.println("Autonomous Rover ESP32 Firmware v1.0");
  Serial.println("========================================\n");
  
  setupPins();
  setupPWM();
  setupWiFiAP();
  setupWebSocket();
  
  Serial.println("[INFO] Initialization complete");
  Serial.println("[INFO] Entering control loop at 50Hz\n");
  
  lastCommandTime = millis();
  lastSensorBroadcast = millis();
  lastControlLoop = millis();
}

// ============================================================================
// MAIN LOOP
// ============================================================================

void loop() {
  unsigned long currentTime = millis();
  
  // WebSocket handling
  webSocket.loop();
  
  // Control loop at 50Hz
  if (currentTime - lastControlLoop >= CONTROL_LOOP_MS) {
    lastControlLoop = currentTime;
    controlLoop();
  }
  
  // Sensor broadcast every 500ms
  if (phoneConnected && currentTime - lastSensorBroadcast >= HEARTBEAT_INTERVAL_MS) {
    lastSensorBroadcast = currentTime;
    broadcastSensorData();
  }
}

// ============================================================================
// CONTROL LOOP (50Hz)
// ============================================================================

void controlLoop() {
  // 1. Read sensors
  distanceCm = readDistance();
  readIRSensors();
  
  // 2. Check safety conditions
  checkSafety();
  
  // 3. Execute current command (if safe) or line following
  if (lineFollowMode) {
    lineFollowLogic();
  } else {
    // Motor timeout check
    if (millis() - lastCommandTime > MOTOR_TIMEOUT_MS && isMoving) {
      stopMotors();
      if (phoneConnected) {
        broadcastAlert("timeout", "Motor timeout - no command for 2 seconds");
      }
    }
  }
}

// ============================================================================
// WIFI ACCESS POINT SETUP
// ============================================================================

void setupWiFiAP() {
  Serial.println("[INIT] Setting up WiFi Access Point...");
  
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(AP_IP, AP_GATEWAY, AP_SUBNET);
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  
  Serial.print("[INFO] WiFi AP Started: ");
  Serial.println(AP_SSID);
  Serial.print("[INFO] IP Address: ");
  Serial.println(WiFi.softAPIP());
  Serial.print("[INFO] Password: ");
  Serial.println(AP_PASSWORD);
  Serial.println();
}

// ============================================================================
// WEBSOCKET SETUP
// ============================================================================

void setupWebSocket() {
  Serial.println("[INIT] Starting WebSocket server...");
  webSocket.begin();
  webSocket.onEvent(webSocketEvent);
  Serial.print("[INFO] WebSocket Server started on port ");
  Serial.println(WS_PORT);
  Serial.println();
}

// ============================================================================
// PIN SETUP
// ============================================================================

void setupPins() {
  Serial.println("[INIT] Configuring GPIO pins...");
  
  // Ultrasonic sensor
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  digitalWrite(TRIG_PIN, LOW);
  
  // IR sensors (digital input)
  pinMode(IR_LEFT_PIN, INPUT);
  pinMode(IR_CENTER_PIN, INPUT);
  pinMode(IR_RIGHT_PIN, INPUT);
  
  // Motor driver pins
  pinMode(MOTOR_A_IN1, OUTPUT);
  pinMode(MOTOR_A_IN2, OUTPUT);
  pinMode(MOTOR_B_IN1, OUTPUT);
  pinMode(MOTOR_B_IN2, OUTPUT);
  
  // Initialize all motor pins to LOW
  digitalWrite(MOTOR_A_IN1, LOW);
  digitalWrite(MOTOR_A_IN2, LOW);
  digitalWrite(MOTOR_B_IN1, LOW);
  digitalWrite(MOTOR_B_IN2, LOW);
  
  Serial.println("[INFO] GPIO pins configured");
}

// ============================================================================
// PWM SETUP
// ============================================================================

void setupPWM() {
  Serial.println("[INIT] Configuring PWM channels...");
  
  ledcSetup(PWM_CHANNEL_A, PWM_FREQ, PWM_RESOLUTION);
  ledcSetup(PWM_CHANNEL_B, PWM_FREQ, PWM_RESOLUTION);
  
  ledcAttachPin(MOTOR_A_EN, PWM_CHANNEL_A);
  ledcAttachPin(MOTOR_B_EN, PWM_CHANNEL_B);
  
  ledcWrite(PWM_CHANNEL_A, 0);
  ledcWrite(PWM_CHANNEL_B, 0);
  
  Serial.println("[INFO] PWM channels configured");
}

// ============================================================================
// ULTRASONIC DISTANCE READING
// ============================================================================

float readDistance() {
  // Send trigger pulse
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  
  // Read echo pulse with timeout (38ms for 400cm max range)
  long duration = pulseIn(ECHO_PIN, HIGH, 38000);
  
  // Calculate distance (speed of sound = 343 m/s = 0.0343 cm/µs)
  // Distance = (duration / 2) * 0.0343
  if (duration == 0) {
    return 400.0; // Timeout = max range
  }
  
  float distance = (duration / 2.0) * 0.0343;
  
  // Clamp to sensor range (2-400 cm)
  if (distance < 2.0) distance = 2.0;
  if (distance > 400.0) distance = 400.0;
  
  return distance;
}

// ============================================================================
// IR SENSOR READING
// ============================================================================

void readIRSensors() {
  // IR sensors: LOW = surface detected, HIGH = no surface (cliff/edge)
  irLeft = digitalRead(IR_LEFT_PIN) == LOW;
  irCenter = digitalRead(IR_CENTER_PIN) == LOW;
  irRight = digitalRead(IR_RIGHT_PIN) == LOW;
  
  // Cliff detection: center sensor sees no surface
  cliffDetected = !irCenter;
  
  // Edge detection: left or right sensor sees no surface
  edgeDetected = !irLeft || !irRight;
}

// ============================================================================
// SAFETY CHECKS
// ============================================================================

void checkSafety() {
  bool shouldStop = false;
  String alertMessage = "";
  
  // Priority 1: Cliff detection
  if (cliffDetected) {
    shouldStop = true;
    alertMessage = "cliff";
    if (isMoving) {
      stopMotors();
      if (phoneConnected) {
        broadcastAlert("cliff", "Cliff detected - emergency stop");
      }
    }
    return;
  }
  
  // Priority 2: Edge detection
  if (edgeDetected) {
    shouldStop = true;
    alertMessage = "edge";
    if (isMoving) {
      stopMotors();
      if (phoneConnected) {
        broadcastAlert("edge", "Edge detected - emergency stop");
      }
    }
    return;
  }
  
  // Priority 3: Obstacle too close
  if (distanceCm < OBSTACLE_STOP_CM && currentCommand == "FORWARD") {
    if (isMoving) {
      stopMotors();
      if (phoneConnected) {
        broadcastAlert("obstacle", "Obstacle < 15cm - stopped");
      }
    }
    return;
  }
  
  // Priority 4: Obstacle close - reduce speed
  if (distanceCm < OBSTACLE_SLOW_CM && currentCommand == "FORWARD") {
    int reducedSpeed = (int)(motorSpeedLeft * SLOW_SPEED_FACTOR);
    setMotorSpeed(reducedSpeed, reducedSpeed);
    // Don't broadcast alert for slow-down, it's normal operation
  }
}

// ============================================================================
// MOTOR CONTROL
// ============================================================================

void stopMotors() {
  digitalWrite(MOTOR_A_IN1, LOW);
  digitalWrite(MOTOR_A_IN2, LOW);
  digitalWrite(MOTOR_B_IN1, LOW);
  digitalWrite(MOTOR_B_IN2, LOW);
  
  ledcWrite(PWM_CHANNEL_A, 0);
  ledcWrite(PWM_CHANNEL_B, 0);
  
  motorSpeedLeft = 0;
  motorSpeedRight = 0;
  isMoving = false;
  currentCommand = "STOP";
}

void setMotorSpeed(int leftSpeed, int rightSpeed) {
  // Clamp speeds
  leftSpeed = constrain(leftSpeed, -MAX_SPEED, MAX_SPEED);
  rightSpeed = constrain(rightSpeed, -MAX_SPEED, MAX_SPEED);
  
  motorSpeedLeft = leftSpeed;
  motorSpeedRight = rightSpeed;
  
  // Motor A (Left)
  if (leftSpeed > 0) {
    digitalWrite(MOTOR_A_IN1, HIGH);
    digitalWrite(MOTOR_A_IN2, LOW);
    ledcWrite(PWM_CHANNEL_A, abs(leftSpeed));
  } else if (leftSpeed < 0) {
    digitalWrite(MOTOR_A_IN1, LOW);
    digitalWrite(MOTOR_A_IN2, HIGH);
    ledcWrite(PWM_CHANNEL_A, abs(leftSpeed));
  } else {
    digitalWrite(MOTOR_A_IN1, LOW);
    digitalWrite(MOTOR_A_IN2, LOW);
    ledcWrite(PWM_CHANNEL_A, 0);
  }
  
  // Motor B (Right)
  if (rightSpeed > 0) {
    digitalWrite(MOTOR_B_IN1, HIGH);
    digitalWrite(MOTOR_B_IN2, LOW);
    ledcWrite(PWM_CHANNEL_B, abs(rightSpeed));
  } else if (rightSpeed < 0) {
    digitalWrite(MOTOR_B_IN1, LOW);
    digitalWrite(MOTOR_B_IN2, HIGH);
    ledcWrite(PWM_CHANNEL_B, abs(rightSpeed));
  } else {
    digitalWrite(MOTOR_B_IN1, LOW);
    digitalWrite(MOTOR_B_IN2, LOW);
    ledcWrite(PWM_CHANNEL_B, 0);
  }
  
  isMoving = (leftSpeed != 0 || rightSpeed != 0);
}

// ============================================================================
// COMMAND EXECUTION
// ============================================================================

void executeCommand(String cmd, int speed) {
  currentCommand = cmd;
  lastCommandTime = millis();
  
  // Clamp speed
  if (speed <= 0 || speed > MAX_SPEED) {
    speed = DEFAULT_SPEED;
  }
  
  if (cmd == "STOP") {
    stopMotors();
  }
  else if (cmd == "FORWARD") {
    // Safety check before allowing forward
    if (cliffDetected || edgeDetected || distanceCm < OBSTACLE_STOP_CM) {
      stopMotors();
      return;
    }
    setMotorSpeed(speed, speed);
  }
  else if (cmd == "BACKWARD") {
    setMotorSpeed(-speed, -speed);
  }
  else if (cmd == "LEFT") {
    setMotorSpeed(speed / 2, speed);
  }
  else if (cmd == "RIGHT") {
    setMotorSpeed(speed, speed / 2);
  }
  else if (cmd == "SPIN_LEFT") {
    setMotorSpeed(-speed, speed);
  }
  else if (cmd == "SPIN_RIGHT") {
    setMotorSpeed(speed, -speed);
  }
  else if (cmd == "LINE_FOLLOW") {
    lineFollowMode = true;
  }
  else {
    // Unknown command
    stopMotors();
  }
}

// ============================================================================
// LINE FOLLOWING LOGIC
// ============================================================================

void lineFollowLogic() {
  // Exit line follow mode if cliff or edge detected
  if (cliffDetected || edgeDetected) {
    stopMotors();
    lineFollowMode = false;
    if (phoneConnected) {
      broadcastAlert("safety", "Line follow stopped - safety trigger");
    }
    return;
  }
  
  int baseSpeed = DEFAULT_SPEED;
  
  // 3-sensor line following logic
  if (irLeft && irCenter && irRight) {
    // All on line - go straight
    setMotorSpeed(baseSpeed, baseSpeed);
  }
  else if (!irLeft && irCenter && !irRight) {
    // Only center on line - go straight
    setMotorSpeed(baseSpeed, baseSpeed);
  }
  else if (irLeft && irCenter && !irRight) {
    // Line veering left - turn left
    setMotorSpeed(baseSpeed / 2, baseSpeed);
  }
  else if (!irLeft && irCenter && irRight) {
    // Line veering right - turn right
    setMotorSpeed(baseSpeed, baseSpeed / 2);
  }
  else if (irLeft && !irCenter && !irRight) {
    // Sharp left - spin left
    setMotorSpeed(baseSpeed / 4, baseSpeed);
  }
  else if (!irLeft && !irCenter && irRight) {
    // Sharp right - spin right
    setMotorSpeed(baseSpeed, baseSpeed / 4);
  }
  else if (!irLeft && !irCenter && !irRight) {
    // Lost line - stop
    stopMotors();
    lineFollowMode = false;
    if (phoneConnected) {
      broadcastAlert("line_lost", "Line following stopped - line lost");
    }
  }
}

// ============================================================================
// WEBSOCKET EVENT HANDLER
// ============================================================================

void webSocketEvent(uint8_t num, WStype_t type, uint8_t* payload, size_t length) {
  switch(type) {
    case WStype_DISCONNECTED:
      Serial.printf("[WS] Client #%u disconnected\n", num);
      if (num == connectedClientNum) {
        phoneConnected = false;
        stopMotors();
        lineFollowMode = false;
        broadcastAlert("connection", "Phone disconnected - motors stopped");
      }
      break;
      
    case WStype_CONNECTED:
      {
        IPAddress ip = webSocket.remoteIP(num);
        Serial.printf("[WS] Client #%u connected from %d.%d.%d.%d\n", 
                      num, ip[0], ip[1], ip[2], ip[3]);
        phoneConnected = true;
        connectedClientNum = num;
        
        // Send welcome message
        StaticJsonDocument<200> doc;
        doc["type"] = "connection";
        doc["status"] = "connected";
        doc["message"] = "ESP32 Ready";
        
        String output;
        serializeJson(doc, output);
        webSocket.sendTXT(num, output);
      }
      break;
      
    case WStype_TEXT:
      {
        Serial.printf("[WS] Received from #%u: %s\n", num, payload);
        
        // Parse JSON command
        StaticJsonDocument<200> doc;
        DeserializationError error = deserializeJson(doc, payload);
        
        if (error) {
          Serial.print("[ERROR] JSON parse error: ");
          Serial.println(error.c_str());
          return;
        }
        
        String cmd = doc["cmd"] | "STOP";
        int speed = doc["speed"] | DEFAULT_SPEED;
        
        executeCommand(cmd, speed);
      }
      break;
      
    default:
      break;
  }
}

// ============================================================================
// SENSOR DATA BROADCAST
// ============================================================================

void broadcastSensorData() {
  StaticJsonDocument<300> doc;
  
  doc["type"] = "sensor";
  doc["dist"] = distanceCm;
  
  JsonObject ir = doc.createNestedObject("ir");
  ir["left"] = irLeft;
  ir["center"] = irCenter;
  ir["right"] = irRight;
  
  doc["cliff"] = cliffDetected;
  doc["edge"] = edgeDetected;
  doc["moving"] = isMoving;
  doc["cmd"] = currentCommand;
  
  JsonObject motors = doc.createNestedObject("motors");
  motors["left"] = motorSpeedLeft;
  motors["right"] = motorSpeedRight;
  
  doc["line_follow"] = lineFollowMode;
  doc["timestamp"] = millis();
  
  String output;
  serializeJson(doc, output);
  webSocket.broadcastTXT(output);
}

// ============================================================================
// ALERT BROADCAST
// ============================================================================

void broadcastAlert(String alertType, String message) {
  StaticJsonDocument<200> doc;
  
  doc["type"] = "alert";
  doc["alert"] = alertType;
  doc["message"] = message;
  doc["dist"] = distanceCm;
  doc["timestamp"] = millis();
  
  String output;
  serializeJson(doc, output);
  webSocket.broadcastTXT(output);
  
  Serial.print("[ALERT] ");
  Serial.print(alertType);
  Serial.print(": ");
  Serial.println(message);
}
