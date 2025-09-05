# OCR GPT Android App

A powerful, optimized Android application that combines Optical Character Recognition (OCR) with AI-powered question answering and conversational capabilities. Built with modern Android development practices and optimized for performance.

## ✨ Features

### 🔍 **Core Functionality**
- **📷 OCR Processing**: Extract text from images using Google ML Kit
- **🤖 AI Integration**: Connect to Groq API for intelligent responses
- **💬 Conversation Context**: Maintain conversation history for follow-up questions
- **🧠 Dynamic Models**: Fetch available text models at runtime; select one or many
- **🔑 Multiple API Keys**: Add/manage multiple keys with rotation and failover

### 🎨 **User Experience**
- **📱 Modern UI**: Clean, intuitive interface with WebView for rich text display
- **✂️ Image Cropping**: Built-in image cropping functionality with orientation support
- **📋 Copy/Paste**: Easy copying of prompts and responses with copy buttons for code blocks
- **🌙 Dark Theme**: Consistent dark-themed dialogs and controls
- **🌍 Language Support**: AI automatically replies in the input language
- **♿ Accessibility**: Full accessibility support with content descriptions

### ⚡ **Performance & Reliability**
- **🚀 Optimized Processing**: Efficient image processing with memory management
- **📊 Progress Indicators**: Real-time feedback during processing
- **🛡️ Error Handling**: Comprehensive error handling with user-friendly messages
- **💾 Memory Management**: Proper resource cleanup to prevent memory leaks
- **🔄 Background Processing**: Non-blocking UI with coroutines

## 🚀 Quick Start

### Prerequisites
- Android Studio (latest version)
- Android SDK (API level 21 or higher)
- Android device or emulator
- Groq API key

### Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/hyperionsolitude/OCR_GPT.git
   cd OCR_GPT
   ```

2. **Set up Android SDK**:
   - Set `ANDROID_HOME` environment variable to your Android SDK path
   - Or update `local.properties` with your SDK path: `sdk.dir=/path/to/your/Android/Sdk`

3. **Build and install**:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

4. **Configure API key**:
   - The app will prompt you for your Groq API key on first launch
   - Get your API key from [Groq Console](https://console.groq.com/)

## 📖 Usage Guide

### OCR Mode
1. **Capture or select** an image containing text
2. **Crop** the image to focus on relevant text (optional)
3. **Process OCR** to extract text automatically
4. **Send to AI** for intelligent analysis and answers

### Chat Mode
1. **Switch to Chat Mode** using the toggle button
2. **Type your question** directly
3. **Send to AI** for immediate response
4. **Continue conversation** with follow-up questions

### Advanced Features
- **All Models**: Compare responses from multiple AI models simultaneously
- **Model Selection**: Choose specific models for different use cases
- **API Key Management**: Add multiple API keys for better reliability
- **Conversation History**: Maintain context across multiple interactions

## 🛠️ Technical Architecture

### **Core Components**
- **MainActivity**: Main UI controller with OCR and AI processing
- **ModelManager**: Handles AI model selection and API communication
- **ApiKeyManager**: Manages multiple API keys with rotation
- **CustomCropActivity**: Advanced image cropping with orientation support
- **WebAppInterface**: JavaScript bridge for native Android functionality

### **Key Technologies**
- **Kotlin**: Primary programming language
- **ML Kit**: Google's on-device text recognition
- **Groq API**: AI model integration
- **WebView**: Rich text display with markdown support
- **CameraX**: Modern camera functionality
- **OkHttp**: Network requests and API communication
- **Kotlin Coroutines**: Asynchronous operations

### **Performance Optimizations**
- **Image Processing**: Optimized bitmap handling with memory management
- **OCR Processing**: Efficient text extraction with progress indicators
- **Memory Management**: Proper resource cleanup and leak prevention
- **Background Processing**: Non-blocking UI operations
- **Error Handling**: Comprehensive error recovery and user feedback

## 🔧 Configuration

### API Integration
The app integrates with Groq API for AI responses. Models are fetched dynamically at runtime, ensuring access to the latest models.

**Getting Your API Key**:
1. Visit [Groq Console](https://console.groq.com/)
2. Sign up or log in to your account
3. Navigate to the "API Keys" section
4. Create a new API key
5. Enter the key when prompted by the app

### Model Management
- Models are fetched dynamically from Groq API
- Only text-based models are included (excludes image, TTS, etc.)
- Models are sorted alphabetically for consistent ordering
- Fallback to default models if API is unavailable

### Language Behavior
- AI automatically detects input language and responds accordingly
- English responses by default unless specifically requested otherwise
- Supports multiple languages including Arabic, Spanish, French, etc.

## 📱 Permissions

The app requires the following permissions:
- **Camera**: For capturing images
- **Internet**: For AI API communication
- **Storage**: For saving and accessing images

Permissions are requested at runtime when needed.

## 🎯 Use Cases

### **Educational**
- Solve math problems from photos
- Analyze scientific diagrams
- Extract text from textbooks
- Get explanations for complex concepts

### **Professional**
- Process documents and forms
- Extract data from screenshots
- Analyze charts and graphs
- Translate text from images

### **Personal**
- Read text from signs and menus
- Extract text from handwritten notes
- Get information from product labels
- Solve puzzles and riddles

## 🔍 Troubleshooting

### Common Issues

**Build Issues**:
- Ensure Android SDK is properly configured
- Check that `local.properties` has correct SDK path
- Verify Gradle wrapper permissions: `chmod +x gradlew`

**API Issues**:
- Verify Groq API key is valid and active
- Check internet connection
- Ensure API key has sufficient credits
- Try refreshing models in settings

**OCR Issues**:
- Ensure image has clear, readable text
- Check lighting conditions
- Try cropping the image to focus on text
- Verify camera permissions are granted

**Performance Issues**:
- Close other apps to free up memory
- Restart the app if it becomes slow
- Check available storage space
- Update to the latest version

### Getting Help
- Check the logs for detailed error messages
- Ensure you're using the latest version
- Verify your device meets minimum requirements
- Contact support with specific error details

## 🚀 Performance Features

### **Optimizations**
- **Memory Management**: Automatic cleanup of bitmaps and resources
- **Image Processing**: Efficient resizing and enhancement algorithms
- **Background Processing**: Non-blocking UI operations
- **Progress Indicators**: Real-time feedback during processing
- **Error Recovery**: Graceful handling of failures

### **Accessibility**
- **Content Descriptions**: Full screen reader support
- **High Contrast**: Optimized for visibility
- **Large Text**: Support for system font scaling
- **Touch Targets**: Appropriately sized interactive elements

## 📊 System Requirements

- **Android Version**: 5.0 (API level 21) or higher
- **RAM**: 2GB minimum, 4GB recommended
- **Storage**: 100MB available space
- **Camera**: Required for OCR functionality
- **Internet**: Required for AI features

## 🔄 Updates & Maintenance

### **Version History**
- **v1.0**: Initial release with basic OCR and AI integration
- **v1.1**: Added conversation context and model selection
- **v1.2**: Implemented image cropping and copy functionality
- **v1.3**: Added accessibility features and performance optimizations

### **Future Roadmap**
- Enhanced image preprocessing
- Additional AI model support
- Offline OCR capabilities
- Advanced conversation management
- Export functionality

## 📄 License

This project is open source and available under the MIT License.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## 📞 Support

For support, questions, or feedback:
- Open an issue on GitHub
- Check the troubleshooting section
- Review the documentation

---

**Built with ❤️ for the Android community**