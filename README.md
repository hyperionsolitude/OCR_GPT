# OCR GPT Android App

A powerful Android application that combines Optical Character Recognition (OCR) with AI-powered question answering and conversational capabilities.

## Features

- **📷 OCR Processing**: Extract text from images using ML Kit
- **🤖 AI Integration**: Connect to Groq API for intelligent responses
- **💬 Conversation Context**: Maintain conversation history for follow-up questions
- **🧠 Dynamic Models**: Fetch available text models at runtime; select one or many
- **🔑 Multiple API Keys**: Add/manage multiple keys with rotation and failover
- **📱 Modern UI**: Clean, intuitive interface with WebView for rich text display
- **✂️ Image Cropping**: Built-in image cropping functionality
- **📋 Copy/Paste**: Easy copying of prompts and responses
- **🌙 Dark Theme**: Consistent dark-themed dialogs and controls
- **🌍 Language Matching**: AI automatically replies in the input language

## Prerequisites

- Android Studio (latest version)
- Android SDK (API level 21 or higher)
- Android device or emulator
- Groq API key

## Setup Instructions

1. **Clone or download** this project
2. **Open in Android Studio**
3. **Set up Android SDK**:
   - Set `ANDROID_HOME` environment variable to your Android SDK path
   - Or update `local.properties` with your SDK path: `sdk.dir=/path/to/your/Android/Sdk`

4. **Configure your Groq API key**:
   - The app will prompt you for your API key on first launch
   - Access `⚙️ Settings` → `🔑 Manage API Keys` anytime to add/edit keys
   - Long-press the mode toggle button as a shortcut to API key settings
   - Get your API key from https://console.groq.com/

5. **Build and run**:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

## Usage

### OCR Mode
1. **Capture or select** an image containing text
2. **Crop** the image to focus on relevant text
3. **Process OCR** to extract text
4. **Send to AI** for intelligent analysis and answers

### Chat Mode
1. **Switch to Chat Mode** using the toggle button
2. **Type your questions** directly
3. **Get AI responses** with conversation context maintained

### Conversation Features
- **Follow-up questions** work with full context
- **"🆕 New Chat"** button to start fresh conversations
- **Conversation history** automatically maintained
- **Multiple AI models**: choose a model or run all selected models

### Settings Menu (⚙️)
- **🔑 Manage API Keys**: Add, edit, delete, activate/deactivate, reset failed, test all
- **🤖 Select AI Models**: Fetch latest Groq text models and multi-select via checkboxes
- **ℹ️ About**: App info and links

### Model Management
- Models are fetched dynamically from Groq API and filtered to text-only
- Use the dialog to select one or many models; quick buttons for Select All / Deselect All
- "All Models" option in the spinner runs the prompt across all selected models and aggregates outputs

### API Key Management
- Add multiple API keys and mark them active/inactive
- Keys rotate in a round‑robin manner to distribute usage and handle rate limits
- Automatic fail tracking disables repeatedly failing keys; you can reset failed

## Project Structure

```
OCR_GPT_Android_Project/
├── app/
│   ├── src/main/
│   │   ├── java/com/ocrgpt/
│   │   │   ├── MainActivity.kt                 # Main UI & app flow
│   │   │   ├── ApiKeyManager.kt                # Multi‑key storage, rotation, failover
│   │   │   ├── ModelManager.kt                 # Dynamic model fetch/filter/selection
│   │   │   ├── CustomCropActivity.kt           # Image cropping
│   │   │   └── ReviewImageActivity.kt          # Image review
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml           # Main screen
│   │   │   │   ├── dialog_model_selection.xml  # Models multi‑select dialog (dark theme)
│   │   │   │   ├── dialog_api_key_management.xml # API keys management (dark theme)
│   │   │   │   ├── item_api_key.xml            # API key item in dialog
│   │   │   │   └── item_model.xml              # Model item in dialog
│   │   │   ├── values/                         # Strings, styles, colors, etc.
│   │   │   └── drawable/                       # Images and icons
│   │   └── AndroidManifest.xml
│   └── build.gradle                            # App module Gradle config
├── gradle/
│   └── wrapper/                                # Gradle wrapper files
├── build.gradle                                # Root Gradle config
├── gradle.properties                           # Gradle settings
├── settings.gradle                             # Project settings
├── README.md                                   # This file
├── QUICK_START.md                              # Quick setup and usage
├── build.sh                                    # Helper build script
├── setup.sh                                    # Project setup helper
├── .gitignore                                  # Ignored files (includes local.properties, build)
├── gradlew                                     # Gradle wrapper script (Linux/Mac)
├── gradlew.bat                                 # Gradle wrapper script (Windows)
└── local.properties                            # SDK path (user‑specific; ignored)
```

Key modules:
- `ApiKeyManager.kt`: Stores multiple API keys in `SharedPreferences`, rotates keys, tracks usage/failures.
- `ModelManager.kt`: Fetches Groq models at runtime, filters to text models, persists selections.
- `dialog_model_selection.xml` / `dialog_api_key_management.xml`: Dark‑themed management dialogs.

## Dependencies

- **ML Kit**: Text recognition from images
- **CameraX**: Camera functionality
- **OkHttp**: Network requests to Groq API
- **Kotlin Coroutines**: Asynchronous operations
- **WebView**: Rich text display with markdown support

## API Integration

The app integrates with Groq API for AI responses. The app automatically fetches available text-based models from the Groq API at runtime, ensuring you always have access to the latest models. If the API is unavailable, it falls back to default models.

### Getting Your API Key
1. Visit [Groq Console](https://console.groq.com/)
2. Sign up or log in to your account
3. Navigate to the "API Keys" section
4. Create a new API key
5. The app will prompt you to enter this key on first launch

### Model Management
- Models are fetched dynamically from Groq API
- Only text-based models are included (excludes image, TTS, etc.)
- Models are sorted alphabetically for consistent ordering
- Fallback to default models if API is unavailable

### Language Behavior
- The AI automatically detects the user's input language and responds in the same language
- If the prompt is in English, replies are in English; Arabic → Arabic; etc.

### Permissions
- On first camera use, the app prompts for the Camera permission via the system dialog
- If denied, a helpful message explains why the permission is needed

## Building Without Downloads

This project includes:
- ✅ Gradle wrapper (no need to install Gradle)
- ✅ All source code
- ✅ Resource files
- ✅ Build configuration

The only external requirements are:
- Android SDK (standard development requirement)
- Groq API key (for AI functionality)

Tip: After fresh clone, you can immediately build and install:
```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Troubleshooting

### Build Issues
- Ensure Android SDK is properly configured
- Check that `local.properties` has correct SDK path
- Verify Gradle wrapper permissions: `chmod +x gradlew`

### API Issues
- Verify Groq API key is valid
- Check internet connection
- Ensure API key has sufficient credits

### OCR Issues
- Use clear, well-lit images
- Ensure text is properly oriented
- Try cropping to focus on relevant text

## License

This project is provided as-is for educational and development purposes.

## Support

For issues or questions, please check the troubleshooting section above or review the source code comments for implementation details. 