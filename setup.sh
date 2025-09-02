#!/bin/bash

# OCR GPT Android Project Setup Script
echo "🚀 Setting up OCR GPT Android Project..."

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME environment variable not set."
    echo "Please set ANDROID_HOME to your Android SDK path."
    echo "Example: export ANDROID_HOME=/path/to/Android/Sdk"
    echo ""
    echo "Or update local.properties with your SDK path:"
    echo "sdk.dir=/path/to/your/Android/Sdk"
    exit 1
fi

echo "✅ Android SDK found at: $ANDROID_HOME"

# Make gradlew executable
chmod +x gradlew

# Clean and build
echo "🧹 Cleaning project..."
./gradlew clean

echo "🔨 Building project..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo ""
    echo "📱 To install on connected device:"
    echo "   ./gradlew installDebug"
    echo ""
    echo "📋 To run the app:"
    echo "   adb shell am start -n com.ocrgpt/.MainActivity"
    echo ""
    echo "⚠️  Remember to configure your Groq API key in the app settings"
else
    echo "❌ Build failed. Please check the error messages above."
    exit 1
fi 