# Autonomous Rover AI - Android App

Android application for controlling and monitoring an autonomous AI-powered rover with real-time video streaming, object detection, and intelligent navigation.

## Features

- 🎥 Real-time video streaming from rover camera
- 🤖 On-device AI object detection using TensorFlow Lite
- 🎮 Manual and autonomous control modes
- 📊 Live telemetry and sensor data visualization
- 🗺️ Path planning and obstacle avoidance
- 📱 Modern Material 3 UI with Jetpack Compose

## Tech Stack

- **Language**: Kotlin 1.9.22
- **UI Framework**: Jetpack Compose (BOM 2024.02.00)
- **Architecture**: MVVM with Clean Architecture
- **Dependency Injection**: Hilt 2.48
- **Database**: Room 2.6.1
- **Networking**: OkHttp 4.12.0
- **Camera**: CameraX 1.3.1
- **AI/ML**: TensorFlow Lite 2.14.0
- **Async**: Kotlin Coroutines 1.7.3
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API level 34
- Gradle 8.2

## Building the Project

1. Clone the repository
2. Open Android Studio
3. Open the `android` directory as a project
4. Sync Gradle files
5. Run the app on a device or emulator

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Install on connected device
./gradlew installDebug
```

## Project Structure

```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/rover/ai/
│   │   │   │   ├── ui/              # UI layer (Compose)
│   │   │   │   ├── data/            # Data layer (Room, Network)
│   │   │   │   ├── domain/          # Domain layer (Use cases)
│   │   │   │   ├── di/              # Dependency injection
│   │   │   │   ├── util/            # Utilities
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── RoverApplication.kt
│   │   │   ├── res/                 # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                    # Unit tests
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Permissions

The app requires the following permissions:
- `INTERNET` - Network communication with rover
- `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` - WiFi management
- `CAMERA` - Local camera for debugging (optional)
- `RECORD_AUDIO` - Audio streaming (optional)
- `WAKE_LOCK` - Keep screen on during operation

## Configuration

Configure rover connection settings in the app or through the settings screen:
- Rover IP address
- Video stream port
- Control command port

## License

See LICENSE file in the root directory.
