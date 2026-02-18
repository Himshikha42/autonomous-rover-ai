#!/bin/bash
# Push AI models to device via ADB
# Usage: ./push_models.sh [/path/to/models/folder]

MODEL_DIR=${1:-.}
DEST=/sdcard/Android/data/com.rover.ai/files/models/

echo "========================================"
echo "AI Model Push Script for Autonomous Rover"
echo "========================================"
echo ""

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "❌ Error: ADB not found. Please install Android SDK Platform Tools."
    echo "   Download from: https://developer.android.com/studio/releases/platform-tools"
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ Error: No device connected via ADB."
    echo "   Please connect your device and enable USB debugging."
    exit 1
fi

echo "✓ ADB found"
echo "✓ Device connected"
echo ""
echo "Source directory: $MODEL_DIR"
echo "Destination: $DEST"
echo ""

# Create destination directory on device
echo "Creating models directory on device..."
adb shell mkdir -p "$DEST"

if [ $? -ne 0 ]; then
    echo "❌ Failed to create directory on device"
    exit 1
fi

echo "✓ Directory created"
echo ""

# Models to push
MODELS=(
    "gemma_3n_e2b_it.tflite"
    "yolov8n.tflite"
    "depth_anything_v2.tflite"
)

SUCCESS_COUNT=0
SKIP_COUNT=0
FAIL_COUNT=0

# Push each model
for model in "${MODELS[@]}"; do
    MODEL_PATH="$MODEL_DIR/$model"
    
    if [ -f "$MODEL_PATH" ]; then
        echo "Pushing $model..."
        
        # Get file size for progress
        SIZE=$(du -h "$MODEL_PATH" | cut -f1)
        echo "  Size: $SIZE"
        
        # Push the file
        adb push "$MODEL_PATH" "$DEST$model"
        
        if [ $? -eq 0 ]; then
            echo "  ✓ Successfully pushed $model"
            ((SUCCESS_COUNT++))
        else
            echo "  ❌ Failed to push $model"
            ((FAIL_COUNT++))
        fi
    else
        echo "⚠️  $model not found in $MODEL_DIR, skipping"
        ((SKIP_COUNT++))
    fi
    
    echo ""
done

echo "========================================"
echo "Push Summary:"
echo "  ✓ Successful: $SUCCESS_COUNT"
echo "  ⚠️  Skipped: $SKIP_COUNT"
echo "  ❌ Failed: $FAIL_COUNT"
echo "========================================"
echo ""

# Verify files on device
echo "Verifying models on device..."
adb shell ls -lh "$DEST"

echo ""
echo "Done! Open the app and go to Model Status screen to verify."
