package com.rover.ai.core

/**
 * Global configuration constants for the Autonomous Rover system.
 * 
 * All hardcoded values are centralized here for easy maintenance.
 * These constants should be loaded from config/rover_config.json in production.
 */
object Constants {
    
    // ============================================================================
    // WEBSOCKET CONNECTION
    // ============================================================================
    
    const val ESP32_IP = "192.168.4.1"
    const val ESP32_PORT = 81
    const val WS_URL = "ws://$ESP32_IP:$ESP32_PORT"
    const val WS_RECONNECT_DELAY_MS = 2000L
    const val WS_MAX_RECONNECT_ATTEMPTS = 10
    const val WS_PING_INTERVAL_MS = 5000L
    
    // ============================================================================
    // WIFI SETTINGS
    // ============================================================================
    
    const val ESP32_SSID = "RoverBrain"
    const val ESP32_PASSWORD = "rover2025"
    
    // ============================================================================
    // SAFETY THRESHOLDS
    // ============================================================================
    
    const val OBSTACLE_STOP_CM = 15.0f
    const val OBSTACLE_SLOW_CM = 40.0f
    const val MOTOR_TIMEOUT_MS = 2000L
    const val HEARTBEAT_INTERVAL_MS = 500L
    const val MIN_EMOTION_DURATION_MS = 3000L
    const val WATCHDOG_TIMEOUT_MS = 5000L
    const val COMMAND_RATE_LIMIT_MS = 100L
    
    // ============================================================================
    // MOTOR CONTROL
    // ============================================================================
    
    const val DEFAULT_MOTOR_SPEED = 180
    const val MAX_MOTOR_SPEED = 255
    const val MIN_MOTOR_SPEED = 0
    const val SLOW_FACTOR = 0.4f
    const val TURN_SPEED_RATIO = 0.5f
    
    // ============================================================================
    // GEMMA AI MODEL
    // ============================================================================
    
    const val GEMMA_MODEL_NAME = "Gemma-3n-E2B-it"
    const val GEMMA_MODEL_FILE = "gemma_3n_e2b_it.tflite"
    const val GEMMA_ACCELERATOR = "GPU"
    const val GEMMA_MAX_TOKENS = 128
    const val GEMMA_TOP_K = 16
    const val GEMMA_TOP_P = 0.80f
    const val GEMMA_TEMPERATURE = 0.10f
    const val GEMMA_INFERENCE_INTERVAL_MS = 5000L
    const val GEMMA_IMAGE_INPUT_SIZE = 256
    const val GEMMA_TIMEOUT_MS = 10000L
    
    // ============================================================================
    // YOLO DETECTOR
    // ============================================================================
    
    const val YOLO_MODEL_FILE = "yolov8n.tflite"
    const val YOLO_DELEGATE = "GPU"
    const val YOLO_CONFIDENCE_THRESHOLD = 0.5f
    const val YOLO_TARGET_FPS = 12
    const val YOLO_INPUT_SIZE = 320
    const val YOLO_IOU_THRESHOLD = 0.45f
    
    // ============================================================================
    // DEPTH ESTIMATOR
    // ============================================================================
    
    const val DEPTH_MODEL_FILE = "depth_anything_v2.tflite"
    const val DEPTH_DELEGATE = "GPU"
    const val DEPTH_INPUT_SIZE = 256
    
    // ============================================================================
    // MODEL REGISTRY
    // ============================================================================
    
    const val MODEL_SIZE_TOLERANCE_FACTOR = 0.9f // 90% of expected size
    
    // ============================================================================
    // CAMERA SETTINGS
    // ============================================================================
    
    const val CAMERA_TARGET_FPS = 30
    const val CAMERA_RESOLUTION_WIDTH = 640
    const val CAMERA_RESOLUTION_HEIGHT = 480
    const val CAMERA_ANALYSIS_FPS = 15
    
    // ============================================================================
    // PERSONALITY SYSTEM
    // ============================================================================
    
    const val INITIAL_CURIOSITY = 0.5f
    const val INITIAL_SOCIABILITY = 0.5f
    const val INITIAL_CONFIDENCE = 0.3f
    const val INITIAL_ENERGY = 0.7f
    const val INITIAL_PLAYFULNESS = 0.5f
    const val PERSONALITY_CHANGE_RATE = 0.01f
    const val PERSONALITY_MIN = 0.0f
    const val PERSONALITY_MAX = 1.0f
    
    // ============================================================================
    // EMOTION SYSTEM
    // ============================================================================
    
    const val LOW_BATTERY_THRESHOLD = 15.0f
    const val CRITICAL_BATTERY_THRESHOLD = 5.0f
    const val EMOTION_HYSTERESIS_MS = 3000L
    const val EMOTION_TRANSITION_MS = 800L
    
    // ============================================================================
    // FACE ANIMATION
    // ============================================================================
    
    const val BLINK_INTERVAL_MIN_MS = 3000L
    const val BLINK_INTERVAL_MAX_MS = 7000L
    const val BLINK_DURATION_MS = 150L
    const val PARTICLE_COUNT = 20
    const val EYE_PUPIL_RADIUS = 0.3f
    const val EYE_SPACING = 0.4f
    
    // ============================================================================
    // MEMORY SYSTEM
    // ============================================================================
    
    const val SHORT_TERM_MEMORY_CAPACITY = 100
    const val SHORT_TERM_MEMORY_TTL_MS = 300000L // 5 minutes
    const val SPATIAL_MAP_GRID_SIZE = 50
    const val SPATIAL_MAP_CELL_SIZE_CM = 10.0f
    
    // ============================================================================
    // BEHAVIOR TREE
    // ============================================================================
    
    const val BEHAVIOR_TREE_TICK_MS = 200L
    const val EXPLORATION_TIMEOUT_MS = 30000L
    const val HUMAN_FOLLOW_DISTANCE_CM = 100.0f
    const val RETURN_HOME_BATTERY_THRESHOLD = 15.0f
    
    // ============================================================================
    // SOUND EFFECTS
    // ============================================================================
    
    const val SOUND_ENABLED = true
    const val SOUND_VOLUME = 0.7f
    
    // ============================================================================
    // DATABASE
    // ============================================================================
    
    const val DATABASE_NAME = "rover_database"
    const val DATABASE_VERSION = 1
    
    // ============================================================================
    // THREAD POOLS
    // ============================================================================
    
    const val VISION_THREAD_POOL_SIZE = 2
    const val AI_THREAD_POOL_SIZE = 2
    const val IO_THREAD_POOL_SIZE = 4
    
    // ============================================================================
    // TAGS FOR LOGGING
    // ============================================================================
    
    const val TAG_CORE = "Rover.Core"
    const val TAG_COMMUNICATION = "Rover.Comm"
    const val TAG_AI = "Rover.AI"
    const val TAG_VISION = "Rover.Vision"
    const val TAG_EMOTION = "Rover.Emotion"
    const val TAG_DECISION = "Rover.Decision"
    const val TAG_MEMORY = "Rover.Memory"
    const val TAG_UI = "Rover.UI"
    const val TAG_AUDIO = "Rover.Audio"
    const val TAG_PERCEPTION = "Rover.Perception"
}
