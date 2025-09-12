# Quick Start Guide

## Prerequisites
- Android SDK (API level 21+; compileSdk 35 recommended)
- JDK 17
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
- Get your API key from https://console.groq.com/

### 3. Build & Install
```bash
# Make Gradle wrapper executable
chmod +x gradlew

# Build the app
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## Troubleshooting
- "SDK not found": Check `local.properties` or `ANDROID_HOME`
- "Permission denied": Run `chmod +x gradlew`
- "Build failed": Ensure JDK 17 is installed and selected

## Features
- 📷 Camera & Gallery image capture
- ✂️ Image cropping
- 🔍 OCR text extraction (Google ML Kit)
- 🤖 AI-powered Q&A with Groq API
- 💬 Conversation history
- 🔑 Multiple API keys with rotation and failover
- 🤖 Dynamic model fetching and multi-select model dialog
- 🌙 Dark theme 