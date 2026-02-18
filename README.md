# 🤖 Autonomous Embodied AI Rover

**An offline, embodied AI companion robot powered by smartphone intelligence**

Build a fully autonomous rover using an **iQOO 13 smartphone** (Snapdragon 8 Elite, ~75 TOPS NPU) as the AI brain, **ESP32** for real-time motor/sensor control, and **Gemma 3n E2B-it** (3.7GB multimodal model) for vision, audio, and text understanding.

---

## ✨ Key Features

- **🧠 Dual-Speed AI Architecture**: Fast YOLO Nano (10-15 FPS) for reactive obstacle avoidance + Slow Gemma 3n (~5s intervals) for strategic scene understanding
- **😊 Animated Emotion System**: Dynamic facial expressions on phone display with 10 emotion states (HAPPY, CURIOUS, ALERT, SCARED, etc.)
- **🎯 Behavior Tree**: Autonomous modes including EXPLORE, FOLLOW_HUMAN, LINE_FOLLOW, PATROL, RETURN_HOME
- **🛡️ Multi-Layer Safety**: Hardware-enforced safety on ESP32 (cliff detection, obstacle avoidance, motor timeout) that ALWAYS overrides AI commands
- **🗺️ Spatial Memory**: Grid-based occupancy mapping with landmark tracking and frontier-based exploration
- **🌱 Evolving Personality**: Traits (curiosity, sociability, confidence) that change based on experiences and interactions
- **📡 100% Offline**: All AI inference runs locally on device — no cloud dependencies

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    iQOO 13 (HIGH BRAIN)                 │
│  ┌───────────────────────────────────────────────────┐  │
│  │  UI Layer: Animated Face + Dashboard + Camera     │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Emotion Engine: FSM with 10 states + Personality │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Decision Fusion: YOLO + Gemma + Behavior Tree    │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  AI: Gemma 3n (GPU) + YOLO Nano (NPU)             │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Memory: Short-term (RAM) + Long-term (Room DB)   │  │
│  └───────────────────────────────────────────────────┘  │
│                         ↕ WebSocket                      │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│                    ESP32 (LOW BRAIN)                    │
│  ┌───────────────────────────────────────────────────┐  │
│  │  Safety Layer (HIGHEST PRIORITY)                  │  │
│  │  • Cliff Detection → Instant Stop                 │  │
│  │  • Obstacle < 15cm → Block Forward                │  │
│  │  • Motor Timeout → Stop All                       │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Sensors: HC-SR04 + 3-ch IR Line Follower         │  │
│  ├───────────────────────────────────────────────────┤  │
│  │  Actuators: L298N Motor Driver (4 wheels)         │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## 🔧 Hardware Requirements

| Component | Model/Spec | Approx. Cost | Purpose |
|-----------|------------|--------------|---------|
| **Smartphone** | iQOO 13 (Snapdragon 8 Elite) | ~$600 | AI Brain, Face Display, Camera |
| **Microcontroller** | ESP32 DevKit V1 | ~$6 | Motor/Sensor Control |
| **Motor Driver** | L298N Dual H-Bridge | ~$3 | 4-Wheel Motor Control |
| **Ultrasonic Sensor** | HC-SR04 | ~$2 | Distance Measurement (5-400cm) |
| **IR Line Follower** | 3-Channel Module | ~$3 | Cliff Detection + Line Tracking |
| **Chassis** | 4-Wheel Robot Car Kit | ~$15 | Mechanical Base |
| **Battery** | 7.4V LiPo 2200mAh | ~$12 | Power Source |
| **Resistors** | 1KΩ + 2KΩ | $0.20 | HC-SR04 Voltage Divider |
| **Misc** | Jumper Wires, USB Cable | ~$5 | Wiring |
| **Total** | | **~$646** | |

---

## 🚀 Validated Benchmarks (Real Hardware Tests on iQOO 13)

**Gemma 3n E2B-it Performance** — Tested with Google AI Edge / LiteRT-LM:

| Mode | 1st Token | Prefill | Decode | Total Latency |
|------|-----------|---------|--------|---------------|
| **GPU** ✅ | 2.19s | 258.21 tok/s | 16.07 tok/s | **4.12 seconds** |
| CPU | 6.23s | 97.00 tok/s | 9.52 tok/s | 22.73 seconds |

**Optimal Configuration** (validated on device):
- **TopK**: 16
- **TopP**: 0.80
- **Temperature**: 0.10
- **Max Tokens**: 128
- **Accelerator**: GPU
- **Prompt**: Compact scene analysis (~50 tokens input)
- **Output**: Valid JSON in ~4 seconds

**YOLO Nano**:
- **FPS**: 10-15 with GPU/NPU delegate
- **Input Size**: 320x320
- **Latency**: ~65-100ms per frame

---

## 📦 Software Prerequisites

### ESP32 Development
- **PlatformIO** 6.1+ (or Arduino IDE 2.0+)
- **ESP32 Board Support** (Arduino-ESP32 2.0.11+)
- **Libraries**:
  - `WebSocketsServer` by Links2004
  - `ArduinoJson` 6.21+

### Android Development
- **Android Studio** Hedgehog (2023.1.1+)
- **Kotlin** 1.9+
- **Gradle** 8.2+
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **JDK**: 17

### AI Models (not included in repo)
- **Gemma 3n E2B-it**: Download from [Google AI Edge](https://ai.google.dev/edge)
- **YOLO Nano TFLite**: Convert from [Ultralytics YOLOv8](https://github.com/ultralytics/ultralytics)

---

## 🏃 Quick Start

### 1. ESP32 Setup

```bash
cd esp32/rover_firmware
pio run --target upload
pio device monitor
```

**Expected Output**:
```
[INFO] WiFi AP Started: RoverBrain
[INFO] IP: 192.168.4.1
[INFO] WebSocket Server Started on port 81
[INFO] Sensors initialized
[INFO] Motors ready
```

### 2. Android Build

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**First Launch**:
1. Phone creates WiFi AP or connects to ESP32 AP "RoverBrain"
2. App connects to `ws://192.168.4.1:81`
3. Face animation starts (wake-up sequence)
4. Sensor data should appear on dashboard

### 3. Place AI Models

Place downloaded models in:
- `android/app/src/main/assets/gemma_3n_e2b_it.tflite`
- `android/app/src/main/assets/yolov8n.tflite`

---

## 📁 Project Structure

```
autonomous-rover-ai/
├── esp32/
│   └── rover_firmware/
│       ├── rover_firmware.ino      # Complete ESP32 firmware
│       └── platformio.ini           # Build config
├── android/
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/rover/ai/
│   │       │   ├── core/            # StateManager, Logger, Constants
│   │       │   ├── communication/   # WebSocket, Protocol
│   │       │   ├── ai/              # Gemma, YOLO, Prompts
│   │       │   ├── decision/        # Behavior Tree, Safety, Fusion
│   │       │   ├── emotion/         # Emotion Engine, Personality
│   │       │   ├── memory/          # Spatial Map, STM, LTM
│   │       │   ├── perception/      # Camera, Audio, Sensors
│   │       │   ├── ui/              # Face, Dashboard, Camera, Map
│   │       │   └── audio/           # Sound Effects, TTS
│   │       └── res/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── config/
│   └── rover_config.json            # All configuration parameters
├── docs/
│   ├── ARCHITECTURE.md              # System design
│   ├── HARDWARE_SETUP.md            # Wiring guide
│   ├── API_PROTOCOL.md              # WebSocket protocol
│   ├── AI_PIPELINE.md               # AI system details
│   ├── DEVELOPMENT_ROADMAP.md       # 5-phase build plan
│   └── TESTING_GUIDE.md             # Test instructions
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🗺️ Development Roadmap

### Phase 1: Foundation (Week 1-2)
- ✅ ESP32 firmware with sensors, motors, WebSocket
- ✅ Android app skeleton with communication layer
- ✅ Basic manual control (forward, backward, turn, stop)

### Phase 2: Vision & Reactivity (Week 3-4)
- ⬜ YOLO Nano integration for real-time obstacle detection
- ⬜ Animated face UI with basic emotion states
- ⬜ Reactive obstacle avoidance (fast path)

### Phase 3: Behavior & Planning (Week 5-6)
- ⬜ Behavior tree implementation (EXPLORE, PATROL, etc.)
- ⬜ Spatial mapping and memory system
- ⬜ Audio input/output (TTS + speech recognition)

### Phase 4: Intelligence & Emotion (Week 7-8)
- ⬜ Gemma 3n integration (multimodal scene analysis)
- ⬜ Decision fusion engine (merge YOLO + Gemma + sensors)
- ⬜ Full emotion system with personality evolution
- ⬜ Line following mode

### Phase 5: Polish & Advanced Features (Week 9-10)
- ⬜ Performance optimization (thermal management, battery efficiency)
- ⬜ Stress testing (long-duration runs)
- ⬜ Advanced UI features (camera overlay, map view)
- ⬜ Sound effects and voice output

---

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Follow code quality guidelines (see `docs/ARCHITECTURE.md`)
4. Write tests for new features
5. Submit a pull request

**Code Quality Requirements**:
- ✅ 100% null safety (no `!!` operators)
- ✅ Exhaustive `when` statements
- ✅ Structured logging (use `Logger` utility)
- ✅ Immutable state (`data class` with `val`)
- ✅ Interface-based abstractions
- ✅ KDoc comments on public APIs

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Google AI Edge** for Gemma 3n and LiteRT-LM
- **Ultralytics** for YOLO architecture
- **Espressif** for ESP32 platform
- **JetBrains** for Kotlin and Compose

---

## 📧 Contact

For questions, issues, or collaboration:
- **GitHub Issues**: [Report bugs or request features](https://github.com/Himshikha42/autonomous-rover-ai/issues)
- **Discussions**: [Ask questions or share ideas](https://github.com/Himshikha42/autonomous-rover-ai/discussions)

---

**Built with ❤️ for embodied AI research and robotics education**
