#!/bin/bash

# Voice Recorder AI - Build Script

echo "========================================="
echo "Voice Recorder AI - Build Script"
echo "========================================="
echo ""

# Check if we're in the right directory
if [ ! -f "gradlew" ]; then
    echo "Error: gradlew not found. Please run this script from the project root directory."
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

echo "Step 1: Cleaning project..."
./gradlew clean

echo ""
echo "Step 2: Building project..."
./gradlew build

echo ""
echo "Step 3: Assembling debug APK..."
./gradlew assembleDebug

echo ""
echo "========================================="
echo "Build complete!"
echo "========================================="
echo ""
echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "To install on device:"
echo "adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""

