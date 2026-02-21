@echo off
REM Push AI models to device via ADB
REM Usage: push_models.bat [path\to\models\folder]

setlocal enabledelayedexpansion

if "%1"=="" (
    set MODEL_DIR=.
) else (
    set MODEL_DIR=%1
)

set DEST=/sdcard/Android/data/com.rover.ai/files/models/

echo ========================================
echo AI Model Push Script for Autonomous Rover
echo ========================================
echo.

REM Check if ADB is available
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: ADB not found. Please install Android SDK Platform Tools.
    echo   Download from: https://developer.android.com/studio/releases/platform-tools
    exit /b 1
)

REM Check if device is connected
adb devices | find "device" >nul
if %ERRORLEVEL% neq 0 (
    echo Error: No device connected via ADB.
    echo   Please connect your device and enable USB debugging.
    exit /b 1
)

echo + ADB found
echo + Device connected
echo.
echo Source directory: %MODEL_DIR%
echo Destination: %DEST%
echo.

REM Create destination directory on device
echo Creating models directory on device...
adb shell mkdir -p "%DEST%"

if %ERRORLEVEL% neq 0 (
    echo Failed to create directory on device
    exit /b 1
)

echo + Directory created
echo.

REM Models to push
set MODELS=gemma-3n-E2B-it-int4.litertlm Yolo-v8-Detection.tflite Depth-Anything-V2.tflite

set SUCCESS_COUNT=0
set SKIP_COUNT=0
set FAIL_COUNT=0

REM Push each model
for %%M in (%MODELS%) do (
    set MODEL_PATH=%MODEL_DIR%\%%M
    
    if exist "!MODEL_PATH!" (
        echo Pushing %%M...
        
        REM Push the file
        adb push "!MODEL_PATH!" "%DEST%%%M"
        
        if !ERRORLEVEL! equ 0 (
            echo   + Successfully pushed %%M
            set /a SUCCESS_COUNT+=1
        ) else (
            echo   - Failed to push %%M
            set /a FAIL_COUNT+=1
        )
    ) else (
        echo Warning: %%M not found in %MODEL_DIR%, skipping
        set /a SKIP_COUNT+=1
    )
    
    echo.
)

echo ========================================
echo Push Summary:
echo   + Successful: %SUCCESS_COUNT%
echo   - Skipped: %SKIP_COUNT%
echo   - Failed: %FAIL_COUNT%
echo ========================================
echo.

REM Verify files on device
echo Verifying models on device...
adb shell ls -lh "%DEST%"

echo.
echo Done! Open the app and go to Model Status screen to verify.
pause
