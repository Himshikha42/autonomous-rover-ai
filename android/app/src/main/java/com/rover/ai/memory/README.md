# Memory Package

Complete memory system implementation for the Autonomous Rover AI.

## Package Structure

```
com.rover.ai.memory/
├── ShortTermMemory.kt      - RAM-based circular buffer (340 lines)
├── SpatialMap.kt           - Grid-based occupancy map (417 lines)
├── LongTermMemory.kt       - Room DB interface + stub (282 lines)
└── MemoryModule.kt         - Hilt dependency injection (30 lines)
```

## Features

### ShortTermMemory.kt
- **Purpose**: Stores transient data with automatic expiration (TTL: 5 minutes)
- **Storage**:
  - Recent objects detected by vision (last 100 entries)
  - Last known human position
  - Recent commands sent to ESP32
  - Recent Gemma AI responses
- **Thread Safety**: Mutex + ConcurrentLinkedDeque
- **Key Methods**:
  - `addObject()`, `getRecentObjects()` - Vision detections
  - `setHumanPosition()`, `getHumanPosition()` - Human tracking
  - `addCommand()`, `getRecentCommands()` - Command history
  - `addGemmaResponse()`, `getRecentGemmaResponses()` - AI responses
  - `clear()` - Reset all buffers
  - `getStats()` - Monitoring and debugging

### SpatialMap.kt
- **Purpose**: Grid-based occupancy map for navigation and exploration
- **Grid**: 50x50 cells, 10cm per cell (from Constants)
- **Cell Types**: UNKNOWN, FREE, OCCUPIED, LANDMARK
- **Key Methods**:
  - `markCell()`, `getCell()` - Cell manipulation
  - `addLandmark()`, `queryLandmarks()` - Named landmarks
  - `getFrontierCells()` - Exploration targets
  - `updateFromUltrasonic()` - Sensor fusion
  - `reset()` - Clear map
  - `toAscii()` - Debug visualization
  - `getStats()` - Map statistics

### LongTermMemory.kt
- **Purpose**: Persistent storage interface (Room DB ready)
- **Current Status**: Stub implementation (logs only)
- **Data Types**:
  - ObjectRecord - Detection history
  - LocationRecord - Position history
  - InteractionRecord - Command/response logs
  - AchievementRecord - Milestones
- **Key Methods**:
  - `saveObject()`, `getObjectHistory()`
  - `saveLocation()`, `getLocationHistory()`
  - `saveInteraction()`, `getInteractions()`
  - `saveAchievement()`, `getAchievements()`

### MemoryModule.kt
- **Purpose**: Hilt dependency injection configuration
- **Provides**: Binds LongTermMemory interface to stub implementation
- **Scope**: Singleton

## Code Quality

✅ **100% Null Safety** - No `!!` operators used  
✅ **Thread-Safe** - Mutex/concurrent collections  
✅ **Structured Logging** - Via Logger utility  
✅ **KDoc Comments** - All public APIs documented  
✅ **Immutable State** - Data classes with copy()  
✅ **Constants** - All hardcoded values from core package  
✅ **Hilt Integration** - @Inject @Singleton pattern  

## Dependencies

All required dependencies already in `app/build.gradle.kts`:
- Kotlin Coroutines (`kotlinx-coroutines-android:1.7.3`)
- Hilt (`hilt-android:2.48`)
- Room (ready for future DB implementation)
- AndroidX Core KTX

## Usage Example

```kotlin
@Inject lateinit var shortTermMemory: ShortTermMemory
@Inject lateinit var spatialMap: SpatialMap
@Inject lateinit var longTermMemory: LongTermMemory

// Store detected object
shortTermMemory.addObject("person", 0.92f, 0.5f, 0.5f, 0.2f, 0.3f)

// Update human position
shortTermMemory.setHumanPosition(25.0f, 30.0f, 0.95f)

// Mark spatial map cell
spatialMap.markCell(10, 15, CellType.OCCUPIED, 0.8f)

// Add landmark
spatialMap.addLandmark(25, 25, "charging_station", 1.0f)

// Find exploration targets
val frontiers = spatialMap.getFrontierCells()

// Store in long-term memory (stub for now)
longTermMemory.saveObject("ball", 0.85f, 10.0f, 20.0f)
```

## Future Enhancements

### LongTermMemory (Room DB)
When ready to implement persistent storage:

1. Create Room entities in `data/` package:
   - `ObjectEntity`, `LocationEntity`, `InteractionEntity`, `AchievementEntity`

2. Create Room DAOs:
   - `ObjectDao`, `LocationDao`, `InteractionDao`, `AchievementDao`

3. Create `RoverDatabase.kt` with Room configuration

4. Replace `LongTermMemoryStub` with `LongTermMemoryImpl`

5. Update `MemoryModule.kt` to bind the new implementation

### Additional Features
- Spatial map path planning algorithms
- Memory consolidation (STM → LTM)
- Semantic memory (learned concepts)
- Episodic memory (experiences)
- Memory pruning/cleanup strategies

## Testing

To test the memory system:

```kotlin
// In a test or debug screen:
@Inject lateinit var shortTermMemory: ShortTermMemory
@Inject lateinit var spatialMap: SpatialMap

// Get statistics
val stmStats = shortTermMemory.getStats()
val mapStats = spatialMap.getStats()

// Visualize map
val mapAscii = spatialMap.toAscii()
Logger.i(TAG, mapAscii)
```

## Performance

- **ShortTermMemory**: O(1) add, O(n) query (n = capacity, max 100)
- **SpatialMap**: O(1) cell access, O(n²) full scan (n = grid size 50)
- **Memory Overhead**: ~50KB for STM + ~10KB for spatial map
- **Thread Contention**: Minimal (mutex-protected write operations only)

## Author Notes

All code follows the Autonomous Rover coding standards:
- Immutable data classes
- Suspend functions for async operations
- Structured logging with tags
- Constants from centralized configuration
- Hilt dependency injection
- Comprehensive KDoc documentation
