#!/bin/bash

# OCR GPT Android App - Build Script
# This script builds the app without requiring Android Studio

echo "🚀 Building OCR GPT Android App..."

# Check if we're in the right directory
if [ ! -f "gradlew" ]; then
    echo "❌ Error: Please run this script from the android project root directory"
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

# Check if local.properties exists and has SDK path
if [ ! -f "local.properties" ]; then
    echo "⚠️  Warning: local.properties not found"
    echo "   Please create local.properties with your Android SDK path:"
    echo "   sdk.dir=/path/to/your/Android/Sdk"
    echo ""
    echo "   Or set ANDROID_HOME environment variable"
    echo ""
fi

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Build debug APK
echo "🔨 Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📱 APK location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on connected device:"
    echo "   ./gradlew installDebug"
    echo ""
    echo "Or manually:"
    echo "   adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo "❌ Build failed!"
    exit 1
fi 