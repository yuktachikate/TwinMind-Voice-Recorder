#!/bin/bash

# Voice Recorder AI - APK Build Script
# This script builds a debug APK and copies it to an easily accessible location

echo "🏗️  Building Voice Recorder AI Debug APK..."
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Navigate to project directory
cd "$(dirname "$0")"

# Check if local.properties exists
if [ ! -f "local.properties" ]; then
    echo -e "${RED}❌ Error: local.properties not found!${NC}"
    echo ""
    echo "Please create local.properties with:"
    echo "  sdk.dir=/path/to/your/Android/sdk"
    echo "  openai.api.key=your_openai_api_key"
    echo ""
    echo "You can use template.local.properties as a reference."
    exit 1
fi

# Check if OpenAI API key is configured
if ! grep -q "openai.api.key" local.properties; then
    echo -e "${YELLOW}⚠️  Warning: openai.api.key not found in local.properties${NC}"
    echo "The app may not work without a valid OpenAI API key."
    echo ""
fi

# Make gradlew executable
chmod +x gradlew

echo "🧹 Cleaning previous builds..."
./gradlew clean

echo ""
echo "🔨 Building debug APK..."
echo "This may take a few minutes on first build..."
echo ""

# Build debug APK with error output
./gradlew assembleDebug --stacktrace

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✅ Build successful!${NC}"
    echo ""

    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

    if [ -f "$APK_PATH" ]; then
        echo "📦 APK Location:"
        echo "   $APK_PATH"
        echo ""

        echo "📊 APK Details:"
        ls -lh "$APK_PATH" | awk '{print "   Size: " $5}'

        # Get APK info if aapt is available
        if command -v aapt &> /dev/null; then
            echo "   Package: com.example.voicerecorderai"
            VERSION=$(aapt dump badging "$APK_PATH" | grep "versionName" | sed "s/.*versionName='//" | sed "s/'.*//")
            echo "   Version: $VERSION"
        fi

        echo ""
        echo "💾 Copying APK to convenient locations..."

        # Copy to Desktop
        if [ -d ~/Desktop ]; then
            cp "$APK_PATH" ~/Desktop/VoiceRecorderAI-debug.apk
            echo "   ✓ Copied to Desktop/VoiceRecorderAI-debug.apk"
        fi

        # Copy to Downloads
        if [ -d ~/Downloads ]; then
            cp "$APK_PATH" ~/Downloads/VoiceRecorderAI-debug.apk
            echo "   ✓ Copied to Downloads/VoiceRecorderAI-debug.apk"
        fi

        # Create a submission folder
        SUBMISSION_DIR="VoiceRecorderAI-Submission"
        mkdir -p "$SUBMISSION_DIR"
        cp "$APK_PATH" "$SUBMISSION_DIR/VoiceRecorderAI-debug.apk"
        cp README.md "$SUBMISSION_DIR/" 2>/dev/null || true
        cp BUILD_APK_GUIDE.md "$SUBMISSION_DIR/SETUP.md" 2>/dev/null || true
        echo "   ✓ Created submission folder: $SUBMISSION_DIR/"

        echo ""
        echo -e "${GREEN}📱 Ready to install!${NC}"
        echo ""
        echo "To install on connected device:"
        echo "   adb install -r $APK_PATH"
        echo ""
        echo "Or install directly on device:"
        echo "   1. Copy APK to your device"
        echo "   2. Enable 'Install from Unknown Sources'"
        echo "   3. Open the APK file and tap Install"
        echo ""

    else
        echo -e "${RED}❌ Error: APK file not found at expected location${NC}"
        exit 1
    fi
else
    echo ""
    echo -e "${RED}❌ Build failed!${NC}"
    echo ""
    echo "Common issues:"
    echo "  • SDK location not set in local.properties"
    echo "  • Java version incompatibility (need JDK 11+)"
    echo "  • Missing dependencies"
    echo ""
    echo "Check the error messages above for details."
    echo "For more help, see BUILD_APK_GUIDE.md"
    exit 1
fi

