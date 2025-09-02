# Quick Start Guide

## Prerequisites
- Android SDK (API level 21+)
- Java 8 or higher
- ADB (for device installation)

## Setup (3 steps)

### 1. Set Android SDK Path
Create `local.properties` file in the project root:
```
sdk.dir=/path/to/your/Android/Sdk
```

**OR** set environment variable:
```bash
export ANDROID_HOME=/path/to/your/Android/Sdk
```

### 2. Configure Your API Key
The app will prompt you for your Groq API key on first launch. You can also:
- Tap `⚙️ Settings` → `🔑 Manage API Keys` to add/edit multiple keys
- Long-press the mode toggle button to open API key settings quickly
- Get your API key from https://console.groq.com/

### 3. Build & Install
```bash
# Make build script executable
chmod +x build.sh

# Build the app
./build.sh

# Install on connected device
./gradlew installDebug
```

## Alternative: Manual Build
```bash
chmod +x gradlew
./gradlew assembleDebug
./gradlew installDebug
```

## Troubleshooting
- **"SDK not found"**: Check `local.properties` or `ANDROID_HOME`
- **"Permission denied"**: Run `chmod +x gradlew`
- **"Build failed"**: Check Java version (needs Java 8+)

## Features
- 📷 Camera & Gallery image capture
- ✂️ Image cropping
- 🔍 OCR text extraction
- 🤖 AI-powered Q&A with Groq API
- 💬 Conversation history
- 🔑 Multiple API keys with rotation and failover
- 🤖 Dynamic model fetching and multi-select model dialog
- 🌙 Dark-themed dialogs for consistency
- 🌍 Automatic language matching in responses
- 📱 Works on Android & HarmonyOS 