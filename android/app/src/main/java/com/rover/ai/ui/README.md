# Rover AI - UI Package

This package contains all user interface components for the Autonomous Rover Android application, built with Jetpack Compose and Material3.

## Package Structure

```
ui/
├── dashboard/          # Debug telemetry dashboard
│   ├── DebugDashboardScreen.kt
│   └── SensorCard.kt
├── camera/            # Live camera preview with AI overlays
│   └── CameraViewScreen.kt
├── map/               # Spatial map visualization
│   └── MapViewScreen.kt
├── face/              # Animated rover face (emotion display)
│   ├── RoverFaceScreen.kt
│   ├── EyeRenderer.kt
│   ├── MouthRenderer.kt
│   ├── FaceAnimationController.kt
│   └── ParticleSystem.kt
├── navigation/        # Navigation graph
│   └── RoverNavHost.kt
└── theme/             # Material3 theming
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

## Screen Descriptions

### Dashboard Package
**Purpose:** Real-time telemetry monitoring and manual control interface

**DebugDashboardScreen**
- Scrollable LazyColumn with sensor data sections
- Real-time updates via StateManager observation
- Color-coded indicators:
  - Distance: Green (>100cm), Yellow (50-100cm), Red (<50cm)
  - Battery: Green (>50%), Yellow (25-50%), Red (<25%)
  - IR Sensors: Red (line detected), Green (clear)
- Manual control buttons for testing
- AI system status indicators
- Error display with dismiss functionality

**SensorCard**
- Reusable Material3 card component
- Displays icon, title, and value
- Used throughout dashboard for consistent styling

### Camera Package
**Purpose:** Live camera feed with AI vision overlays

**CameraViewScreen**
- CameraX integration for rear camera preview
- YOLO bounding boxes drawn via Canvas
- Object labels with confidence percentages
- Distance indicator (ultrasonic) in top-right
- FPS counter in top-left
- Gemma AI analysis text at bottom
- Data classes: `DetectedObject`, `BoundingBox`

### Map Package
**Purpose:** Spatial navigation map visualization

**MapViewScreen**
- Canvas-based grid rendering (50x50 cells by default)
- Color-coded cell types:
  - Gray: UNKNOWN (not yet explored)
  - Green: FREE (navigable space)
  - Red: OCCUPIED (obstacles)
  - Yellow: LANDMARK (named locations)
- Blue triangle: Rover position and heading
- Zoom/pan gestures (pinch and drag)
- Overlays:
  - Map statistics (exploration %, landmarks)
  - Rover position (X, Y, heading)
  - Legend
  - Zoom level indicator
- Auto-refresh every 1 second

### Face Package
**Purpose:** Animated rover face expressing emotions

**RoverFaceScreen**
- Canvas-based custom drawing
- Animated eyes (blinking, pupil movement)
- Animated mouth (various expressions)
- Particle effects (hearts, sparkles, question marks)
- Real-time emotion state rendering
- Smooth transitions between emotions

## Architecture Patterns

### State Management
All screens use **reactive state observation** via Kotlin StateFlow:

```kotlin
val roverState by stateManager.state.collectAsState()
```

This ensures UI automatically updates when state changes, following unidirectional data flow.

### Dependency Injection
Components receive dependencies via parameters (no direct DI in Composables):

```kotlin
@Composable
fun DebugDashboardScreen(
    stateManager: StateManager,
    connectionManager: ConnectionManager,
    modifier: Modifier = Modifier
)
```

Higher-level components (Activity, ViewModels) handle Hilt injection.

### Navigation
Navigation is handled via Jetpack Compose Navigation in `RoverNavHost.kt`:

```kotlin
NavHost(navController, startDestination = "dashboard") {
    composable("dashboard") { DebugDashboardScreen(...) }
    composable("camera") { CameraViewScreen(...) }
    composable("map") { MapViewScreen(...) }
    composable("face") { RoverFaceScreen(...) }
}
```

## Design System

### Material3 Theme
- Uses custom color scheme defined in `theme/Color.kt`
- Typography system in `theme/Type.kt`
- Theme configuration in `theme/Theme.kt`

### Color Coding Standards
- **Green (#4CAF50)**: Healthy, safe, good status
- **Yellow (#FFC107)**: Warning, moderate status
- **Red (#F44336)**: Error, danger, critical status
- **Blue (#2196F3)**: Active, moving, processing
- **Gray (#9E9E9E)**: Inactive, unknown, disabled

### Component Guidelines
- Use `MaterialTheme.colorScheme` for colors
- Use `MaterialTheme.typography` for text styles
- Minimum touch target: 48dp
- Consistent padding: 8dp, 12dp, 16dp
- Card elevation: 4dp default

## Performance Considerations

### Camera Screen
- CameraX preview uses hardware acceleration
- Canvas overlays are efficient (no bitmap allocation)
- Bounding boxes updated at YOLO inference rate (~12 FPS)

### Map Screen
- Canvas drawing is efficient for grid rendering
- Cells are cached between redraws
- Zoom/pan uses transform gestures (no redraw during gesture)
- Auto-refresh limited to 1 Hz to reduce overhead

### Dashboard Screen
- LazyColumn for efficient scrolling
- StateFlow prevents unnecessary recompositions
- Color calculations are simple (no complex blending)

## Testing

### Manual Testing
1. **Dashboard**: Verify all sensors update in real-time
2. **Camera**: Check bounding boxes align with detected objects
3. **Map**: Test zoom/pan gestures, verify rover position updates
4. **Face**: Trigger different emotions, verify smooth transitions

### Unit Testing
- Test color determination logic (green/yellow/red thresholds)
- Test bounding box coordinate calculations
- Test grid cell type determination
- Test rover heading triangle rotation

### UI Testing
- Compose UI tests for button interactions
- Screenshot tests for visual regression
- Accessibility tests (TalkBack, content descriptions)

## Dependencies Required

```kotlin
// Compose
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")

// CameraX
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// Lifecycle
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
```

## Future Enhancements

### Dashboard
- [ ] Add graph/chart views for sensor history
- [ ] Add configurable alert thresholds
- [ ] Add export telemetry data functionality

### Camera
- [ ] Add manual zoom controls
- [ ] Add screenshot capture button
- [ ] Add AR distance markers

### Map
- [ ] Add path planning visualization
- [ ] Add waypoint markers
- [ ] Add grid export/import functionality
- [ ] Add 3D map view option

### General
- [ ] Add dark/light theme toggle
- [ ] Add screen recording capability
- [ ] Add gesture shortcuts
- [ ] Add multi-screen layout for tablets

## Code Style

All UI code follows these conventions:

✅ **Null Safety**: No `!!` operators, use `?.let {}` instead
✅ **Immutability**: Prefer `val` over `var`
✅ **Composition**: Break down large Composables into smaller ones
✅ **Documentation**: KDoc comments on all public functions
✅ **Logging**: Use `Logger.i/d/w/e` with appropriate tags
✅ **Constants**: Use `Constants` object, no magic numbers
✅ **State Hoisting**: State belongs at the highest recomposition point

## Troubleshooting

### Camera Preview Not Showing
- Check camera permissions in `AndroidManifest.xml`
- Verify CameraX dependencies are correct versions
- Check device has back camera

### Map Not Updating
- Verify `SpatialMap` is being updated by navigation system
- Check auto-refresh coroutine is running
- Verify grid coordinates are within bounds (0 to gridSize-1)

### Dashboard Frozen
- Check StateManager state updates are occurring
- Verify coroutine scope is active
- Check for exceptions in logs

### Bounding Boxes Misaligned
- Verify canvas size matches preview size
- Check coordinate normalization (0.0 to 1.0)
- Ensure bounding box calculations use same aspect ratio

## Contact

For questions or issues with UI components, refer to:
- Constants.TAG_UI for relevant logs
- StateManager for state structure
- Material3 documentation for styling guidelines
