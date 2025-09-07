@file:Suppress("LargeClass", "TooManyFunctions")

package com.ocrgpt

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class to represent a conversation message
data class ConversationMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
)

class MainActivity : AppCompatActivity() {
    companion object {
        private const val UNIQUE_ID_MULTIPLIER = 1000
        private const val PROGRESS_DELAY_MS = 100L
        private const val PROGRESS_DURATION_MS = 300L

        // Removed: old enhancement-related constants

        // API constants
        private const val MAX_REQUEST_SIZE = 1024
        private const val TIMEOUT_FACTOR = 0.3f
        private const val MAX_TOKENS = 4096
        private const val TEMPERATURE = 0.95f
        private const val LEGACY_RESOLVE_FLAGS = 0
        private const val ANDROID_API_TIRAMISU = 33

        // Removed: old color processing bit constants
    }

    private lateinit var imageView: ImageView
    private lateinit var processButton: Button
    private lateinit var promptEditText: EditText
    private lateinit var aiResponseWebView: WebView
    private lateinit var modelSpinner: Spinner
    private lateinit var sendToAIButton: Button
    private lateinit var reviewImageButton: Button
    private lateinit var modeToggleButton: Button
    private lateinit var clearButton: Button
    private lateinit var newConversationButton: Button
    private var currentBitmap: Bitmap? = null
    private var currentPhotoUri: Uri? = null
    private var currentPrompt: String = ""
    private var currentPromptText: String = "" // Store the actual text content
    private var croppedImageUri: Uri? = null
    private var isOCRMode: Boolean = true

    // Store responses from all models
    private val modelResponses = mutableMapOf<String, String>()
    private var isProcessingAllModels = false

    // Conversation management
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private var isConversationMode = true // Default to conversation mode

    // API Key and Model management
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var modelManager: ModelManager
    private lateinit var sharedPreferences: SharedPreferences

    // New refactored classes
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var apiHandler: APIHandler
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var uiHelper: UIHelper

    // Coordinators
    private lateinit var activityResultCoordinator: ActivityResultCoordinator
    private lateinit var imageFlowManager: ImageFlowManager
    private lateinit var apiKeyDialogs: ApiKeyDialogs
    private lateinit var settingsDialogs: SettingsDialogs

    // Progress indicator
    private var progressDialog: AlertDialog? = null

    // JavaScript interface for native Android functionality
    @Suppress("unused")
    inner class WebAppInterface {
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            Log.d("OCR", "copyToClipboard called with text length: ${text.length}")
            runOnUiThread {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Code", text)
                    clipboard.setPrimaryClip(clip)

                    // Show toast message
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Code copied to clipboard! (${text.length} chars)",
                            Toast.LENGTH_SHORT,
                        ).show()
                    Log.d("OCR", "Successfully copied ${text.length} characters to clipboard")
                } catch (e: SecurityException) {
                    Log.e("OCR", "Failed to copy to clipboard: ${e.message}", e)
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Failed to copy to clipboard: ${e.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                } catch (e: IllegalStateException) {
                    Log.e("OCR", "Illegal state error copying to clipboard: ${e.message}", e)
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Failed to copy to clipboard: ${e.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeManagers()
        initializeViews()
        setupUI()
        setupClickListeners()
        setupActivityResultLaunchers()
    }

    private fun initializeManagers() {
        sharedPreferences = getSharedPreferences("ocr_gpt_prefs", Context.MODE_PRIVATE)
        apiKeyManager = ApiKeyManager(this)
        modelManager = ModelManager(this)

        // Initialize new refactored classes
        ocrProcessor = OCRProcessor()
        apiHandler = APIHandler()
        imageProcessor = ImageProcessor(this)
        uiHelper = UIHelper(this)
    }

    private fun initializeViews() {
        imageView = findViewById(R.id.image_preview)
        processButton = findViewById(R.id.btn_process)
        promptEditText = findViewById(R.id.et_prompt)
        aiResponseWebView = findViewById(R.id.tv_ai_response)
        modelSpinner = findViewById(R.id.spinner_model)
        sendToAIButton = findViewById(R.id.btn_send_ai)
        reviewImageButton = findViewById(R.id.btn_review_image)
        modeToggleButton = findViewById(R.id.btn_mode_toggle)
        clearButton = findViewById(R.id.btn_clear)
        newConversationButton = findViewById(R.id.btn_new_conversation)
    }

    private fun setupUI() {
        setupEditText()
        setupWebViews()
        setupEditTextFocus()
        setupEditTextListener()
        checkApiKeyAndInitializeModels()
        setupModelSpinner()
        testMLKit()

        Log.d("OCR", "UI initialized - aiResponseWebView: $aiResponseWebView")
        setWebViewContent(aiResponseWebView, "No AI response yet...")
        promptEditText.hint = "Prompt will appear here after OCR..."

        processButton.isEnabled = false
        sendToAIButton.isEnabled = false
        reviewImageButton.isEnabled = false
    }

    private fun setupEditText() {
        promptEditText.setVerticalScrollBarEnabled(true)
        promptEditText.setMovementMethod(
            android.text.method.ScrollingMovementMethod
                .getInstance(),
        )
        promptEditText.setOnTouchListener { v, _ ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(canEditTextScrollVertically(promptEditText))
            }
            false
        }
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btn_capture).setOnClickListener { takePhoto() }
        findViewById<Button>(R.id.btn_gallery).setOnClickListener { openGallery() }
        processButton.setOnClickListener { processOCR() }
        sendToAIButton.setOnClickListener { sendToAI() }
        reviewImageButton.setOnClickListener { reviewCroppedImage() }
        modeToggleButton.setOnClickListener { toggleMode() }
        newConversationButton.setOnClickListener { startNewConversation() }
        clearButton.setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btn_copy_ocr).setOnClickListener {
            copyToClipboard(promptEditText.text.toString(), "Prompt")
        }
        findViewById<Button>(R.id.btn_copy_ai).setOnClickListener {
            copyToClipboard(getWebViewText(aiResponseWebView), "AI Response")
        }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showSettingsDialog() }

        modeToggleButton.setOnLongClickListener {
            showApiKeySettingsDialog()
            true
        }
    }

    private fun setupActivityResultLaunchers() {
        activityResultCoordinator =
            ActivityResultCoordinator(
                this,
                onCameraSuccess = { result -> handleCameraSuccess(result) },
                onGallerySuccess = { result -> handleGallerySuccess(result) },
                onCropSuccess = { uri ->
                    croppedImageUri = uri
                    loadImageFromUri(uri)
                    updateButtonStates()
                    Log.d("OCR", "New cropped image set: $uri")
                },
            )
        activityResultCoordinator.init()
        apiKeyDialogs = ApiKeyDialogs(this, apiKeyManager, modelManager, uiHelper)
        settingsDialogs = SettingsDialogs(this, apiKeyDialogs)
        imageFlowManager = ImageFlowManager(this, ImageProcessor(this))
    }

    // extracted to ActivityResultCoordinator

    private fun handleCameraSuccess(result: ActivityResult) {
        clearOldData()
        var photoUri = currentPhotoUri

        result.data?.data?.let { dataUri ->
            Log.d("OCR", "Camera returned data URI: $dataUri")
            photoUri = dataUri
        }

        photoUri?.let { uri ->
            Log.d("OCR", "Camera success, launching crop with URI: $uri")
            try {
                launchCropActivity(uri)
            } catch (e: ActivityNotFoundException) {
                Log.e("OCR", "Crop activity not found", e)
                loadImageFromUri(uri)
            } catch (e: SecurityException) {
                Log.e("OCR", "Permission denied for crop activity", e)
                loadImageFromUri(uri)
            }
        } ?: run {
            Log.e("OCR", "Camera success but no photo URI available")
            Toast.makeText(this, "Camera error: No photo URI", Toast.LENGTH_SHORT).show()
        }
    }

    // extracted to ActivityResultCoordinator

    private fun handleGallerySuccess(result: ActivityResult) {
        clearOldData()
        val data = result.data
        val uri = data?.data
        uri?.let {
            Log.d("OCR", "Gallery selected URI: $uri")
            try {
                launchCropActivity(it)
            } catch (e: ActivityNotFoundException) {
                Log.e("OCR", "Crop activity not found from gallery", e)
            } catch (e: SecurityException) {
                Log.e("OCR", "Permission denied for crop activity from gallery", e)
                loadImageFromUri(it)
            }
        } ?: run {
            Log.e("OCR", "Gallery success but no URI in data")
            Toast.makeText(this, "Gallery error: No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    // extracted to ActivityResultCoordinator

    // extracted to ActivityResultCoordinator

    private fun checkApiKeyAndInitializeModels() {
        val activeKeys = apiKeyManager.getActiveApiKeys()
        if (activeKeys.isEmpty()) {
            showApiKeyDialog()
        } else {
            // Initialize models with existing API keys
            initializeModels()
        }
    }

    private fun showApiKeyDialog() {
        val editText =
            EditText(this).apply {
                hint = "Enter your Groq API key"
            }

        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Groq API Key Required")
                .setMessage(
                    "To use AI features, you need a Groq API key.\n\n" +
                        "1. Visit https://console.groq.com/\n" +
                        "2. Sign up or log in\n" +
                        "3. Go to API Keys section\n" +
                        "4. Create a new API key\n" +
                        "5. Paste it below",
                ).setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    val apiKey = editText.text.toString().trim()
                    if (apiKey.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val success = apiKeyManager.addApiKey(apiKey, "API Key 1")
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    initializeModels()
                                } else {
                                    Toast
                                        .makeText(
                                            this@MainActivity,
                                            "Failed to save API key",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    showApiKeyDialog() // Show dialog again
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "API key cannot be empty", Toast.LENGTH_SHORT).show()
                        showApiKeyDialog() // Show dialog again
                    }
                }.setNegativeButton("Skip") { _, _ ->
                    // Use default models without API key
                    useDefaultModels()
                    Toast.makeText(this, "Using default models. AI features may be limited.", Toast.LENGTH_LONG).show()
                }.setCancelable(false)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun initializeModels() {
        val activeKeys = apiKeyManager.getActiveApiKeys()
        if (activeKeys.isEmpty()) {
            useDefaultModels()
            return
        }

        // Fetch available models from Groq API using the first active key
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val models = modelManager.fetchAvailableModels(activeKeys.first().key)
                withContext(Dispatchers.Main) {
                    if (models.isNotEmpty()) {
                        setupModelSpinner()
                        Log.d("OCR", "Loaded ${models.size} models from API")
                        Toast.makeText(this@MainActivity, "Loaded ${models.size} AI models", Toast.LENGTH_SHORT).show()
                    } else {
                        useDefaultModels()
                    }
                }
            } catch (e: IOException) {
                Log.e("OCR", "Network error fetching models: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    useDefaultModels()
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Network error, using default models: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            } catch (e: IllegalStateException) {
                Log.e("OCR", "Illegal state error fetching models: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    useDefaultModels()
                    Toast
                        .makeText(
                            this@MainActivity,
                            "Error fetching models, using defaults: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private fun useDefaultModels() {
        // The ModelManager will handle default models internally
        setupModelSpinner()
        Log.d("OCR", "Using default models")
    }

    private fun showModelSelectionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_model_selection, null)
        val modelsLayout = dialogView.findViewById<LinearLayout>(R.id.layout_models)
        val selectedCountText = dialogView.findViewById<TextView>(R.id.tv_selected_count)
        val selectAllButton = dialogView.findViewById<Button>(R.id.btn_select_all)
        val deselectAllButton = dialogView.findViewById<Button>(R.id.btn_deselect_all)

        val availableModels = modelManager.getModels()
        val checkboxes = mutableListOf<CheckBox>()

        // Create checkboxes for each model
        availableModels.forEach { model ->
            val modelView = LayoutInflater.from(this).inflate(R.layout.item_model, null)
            val checkbox = modelView.findViewById<CheckBox>(R.id.checkbox_selected)
            val modelNameText = modelView.findViewById<TextView>(R.id.tv_model_name)
            val modelIdText = modelView.findViewById<TextView>(R.id.tv_model_id)
            val categoryText = modelView.findViewById<TextView>(R.id.tv_model_category)

            checkbox.isChecked = modelManager.getModelInfo(model.id) as Boolean
            modelNameText.text = model.name
            modelIdText.text = model.id
            categoryText.text = model.category.uppercase()

            checkbox.setOnCheckedChangeListener { _, _ ->
                modelManager.setModelSelection(model.id)
                updateSelectedCount(selectedCountText)
            }

            checkboxes.add(checkbox)
            modelsLayout.addView(modelView)
        }

        updateSelectedCount(selectedCountText)

        selectAllButton.setOnClickListener {
            modelManager.setModelSelection(select = true)
            checkboxes.forEach { it.isChecked = true }
            updateSelectedCount(selectedCountText)
        }

        deselectAllButton.setOnClickListener {
            modelManager.setModelSelection(select = false)
            checkboxes.forEach { it.isChecked = false }
            updateSelectedCount(selectedCountText)
        }

        val dialog =
            AlertDialog
                .Builder(this)
                .setView(dialogView)
                .setPositiveButton("Done") { _, _ ->
                    setupModelSpinner()
                    Toast.makeText(this, "Model selection updated", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancel", null)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun updateSelectedCount(textView: TextView) {
        val count = modelManager.getModels(true).size
        textView.text = "Selected: $count models"
    }

    // Dialogs handled by ApiKeyDialogs / SettingsDialogs
    // Use settingsDialogs/apiKeyDialogs directly where needed
    private fun showApiKeySettingsDialog() {
        apiKeyDialogs.showManagementDialog()
    }

    private fun showSettingsDialog() {
        settingsDialogs.showSettingsDialog(
            { showModelSelectionDialog() },
            { showAboutDialog() },
        )
    }

    private fun showAboutDialog() {
        val message =
            """
            OCR GPT Android App
            
            Version: 2.0 (Optimized)
            
            Features:
            • Multiple API key management
            • Dynamic model selection
            • Automatic rate limit handling
            • Enhanced OCR processing
            • Conversation history
            
            Get API keys from:
            https://console.groq.com/
            """.trimIndent()

        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("About OCR GPT")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
    }

    private fun setupWebViews() {
        // Setup AI response WebView
        aiResponseWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
        }

        // Add JavaScript interface for native copy functionality
        aiResponseWebView.addJavascriptInterface(WebAppInterface(), "Android")

        // Set initial content
        setWebViewContent(aiResponseWebView, "No AI response yet...")
    }

    private fun setWebViewContent(
        webView: WebView,
        content: String,
    ) {
        // Store the content for the prompt WebView
        if (webView == aiResponseWebView) {
            currentPromptText = content
        }

        // Process content to add copy buttons to code blocks
        val processedContent = addCopyButtonsToCodeBlocks(content)
        uiHelper.setWebViewContent(webView, processedContent)
    }

    private fun addCopyButtonsToCodeBlocks(content: String): String {
        // First convert markdown code blocks to HTML with copy buttons
        val codeBlockRegex = Regex("```([\\s\\S]*?)```")

        // Find all matches
        val allMatches = codeBlockRegex.findAll(content).toList()

        if (allMatches.isEmpty()) {
            return content.replace("\n", "<br>")
        }

        // Process each code block individually to ensure all are replaced
        var processedContent = content
        var codeBlockCounter = 0

        // Process in reverse order to avoid index shifting issues
        allMatches.reversed().forEach { matchResult ->
            val codeContent = matchResult.groupValues[1]
            val uniqueId =
                "code-block-${codeBlockCounter++}-${System.currentTimeMillis()}-" +
                    "${(Math.random() * UNIQUE_ID_MULTIPLIER).toInt()}"

            val codeBlockHtml =
                """
                <div class="code-container" id="$uniqueId">
                    <button class="copy-button" data-code="${codeContent.replace(
                    "\"",
                    "&quot;",
                ).replace(
                    "\n",
                    "&#10;",
                ).replace("\r", "&#13;").replace("\t", "&#9;")}" onclick="copyCodeToClipboard(this)">Copy</button>
                    <pre><code>$codeContent</code></pre>
                </div>
                """.trimIndent()

            // Replace this specific match
            processedContent = processedContent.replace(matchResult.value, codeBlockHtml)
        }

        // Convert line breaks to HTML for non-code parts only
        val codeBlockRegexHtml = Regex("(<div class=\"code-container\"[\\s\\S]*?</div>)")
        val parts = codeBlockRegexHtml.split(processedContent)
        val blocks = codeBlockRegexHtml.findAll(processedContent).toList()

        val rebuilt = StringBuilder()
        parts.forEachIndexed { index, part ->
            // Replace newlines with <br> only in non-code parts
            rebuilt.append(part.replace("\n", "<br>"))
            if (index < blocks.size) {
                // Append the code block untouched
                rebuilt.append(blocks[index].value)
            }
        }
        return rebuilt.toString()
    }

    private fun getWebViewText(webView: WebView): String =
        if (webView == aiResponseWebView) {
            currentPromptText
        } else {
            "AI Response content"
        }

    private fun takePhoto() {
        activityResultCoordinator.takePhoto()
    }

    // camera handled by ActivityResultCoordinator

    // Harmony camera handled by ActivityResultCoordinator

    // file creation handled by ActivityResultCoordinator

    @Suppress("LongMethod")
    private fun launchCropActivity(sourceUri: Uri) {
        try {
            Log.d("OCR", "Launching crop activity with URI: $sourceUri")
            // Only check file existence for file:// URIs
            if ("file" == sourceUri.scheme) {
                val file = File(sourceUri.path ?: "")
                if (!file.exists()) {
                    Log.e("OCR", "File does not exist for URI: $sourceUri")
                    Toast.makeText(this, "Image file does not exist", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            // For content:// URIs, just try to open the input stream
            val inputStream = contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                Log.e("OCR", "Cannot open input stream for URI: $sourceUri")
                Toast.makeText(this, "Cannot access selected image", Toast.LENGTH_SHORT).show()
                return
            }
            inputStream.close()
            val intent = Intent(this, CustomCropActivity::class.java)
            intent.putExtra(CustomCropActivity.EXTRA_IMAGE_URI, sourceUri.toString())
            // Always add read permission
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Only grant URI permission for app-owned URIs (camera/FileProvider)
            if ("file" == sourceUri.scheme ||
                ("content" == sourceUri.scheme && sourceUri.authority?.contains(packageName) == true)
            ) {
                val cropActivityInfo =
                    if (android.os.Build.VERSION.SDK_INT >= ANDROID_API_TIRAMISU) {
                        val flags =
                            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                                .toLong()
                        packageManager
                            .resolveActivity(
                                intent,
                                android.content.pm.PackageManager.ResolveInfoFlags
                                    .of(flags),
                            )?.activityInfo
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.resolveActivity(intent, LEGACY_RESOLVE_FLAGS)?.activityInfo
                    }
                if (cropActivityInfo != null) {
                    Log.d("OCR", "Granting URI permission to crop activity: ${cropActivityInfo.packageName}")
                    grantUriPermission(
                        cropActivityInfo.packageName,
                        sourceUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } else {
                    Log.w("OCR", "Could not resolve crop activity for URI permission grant")
                }
            } else {
                Log.d("OCR", "Not granting URI permission for gallery/content URI: $sourceUri")
            }
            Log.d("OCR", "Starting crop via coordinator for URI: $sourceUri")
            activityResultCoordinator.launchCrop(sourceUri)
        } catch (e: ActivityNotFoundException) {
            Log.e("OCR", "Crop activity not found", e)
            Toast.makeText(this, "Crop activity not available: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fallback: try to load image directly
        } catch (e: SecurityException) {
            Log.e("OCR", "Crop activity permission denied", e)
            Toast.makeText(this, "Crop permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fallback: try to load image directly
            try {
                loadImageFromUri(sourceUri)
            } catch (fallbackException: IOException) {
                Log.e("OCR", "Fallback IO error", fallbackException)
                Toast.makeText(this, "Failed to process image: IO error", Toast.LENGTH_SHORT).show()
            } catch (fallbackException: SecurityException) {
                Log.e("OCR", "Fallback permission error", fallbackException)
                Toast.makeText(this, "Failed to process image: Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        imageFlowManager.loadImageFromUri(uri) { processedBitmap ->
            currentBitmap = processedBitmap
            imageView.setImageBitmap(processedBitmap)
            updateButtonStates()
        }
    }

    // Removed: preprocessing delegated to ImageProcessor/ImageFlowManager

    // Removed: image enhancement delegated to ImageProcessor

    // Removed: image enhancement helpers extracted

    // Removed: image enhancement helpers extracted

    // Removed: image enhancement helpers extracted

    private fun testMLKit() {
        Log.d("OCR", "Testing MLKit initialization...")
        try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Log.d("OCR", "MLKit TextRecognizer created successfully")
            Toast.makeText(this, "MLKit initialized successfully", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Log.e("OCR", "MLKit initialization failed: ${e.message}", e)
            Toast.makeText(this, "MLKit initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e("OCR", "MLKit permission denied: ${e.message}", e)
            Toast.makeText(this, "MLKit permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        try {
            Log.d("OCR", "Opening gallery...")

            // Try multiple gallery intents for better compatibility
            val galleryIntents =
                listOf(
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                    Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" },
                    Intent(Intent.ACTION_PICK).apply { type = "image/*" },
                )

            var resolvedIntent: Intent? = null

            for (intent in galleryIntents) {
                val resolveInfo = intent.resolveActivity(packageManager)
                if (resolveInfo != null) {
                    Log.d("OCR", "Found gallery app")
                    resolvedIntent = intent
                    break
                }
            }

            if (resolvedIntent != null) {
                Log.d("OCR", "Launching gallery intent")
                activityResultCoordinator.launchGalleryPicker()
            } else {
                Log.e("OCR", "No gallery app found")
                Toast.makeText(this, "No gallery app found on this device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ActivityNotFoundException) {
            Log.e("OCR", "Gallery activity not found", e)
            Toast.makeText(this, "Gallery not available: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e("OCR", "Gallery permission denied", e)
            Toast.makeText(this, "Gallery permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processOCR() {
        currentBitmap?.let { bitmap ->
            Log.d("OCR", "Starting OCR processing...")
            Log.d("OCR", "Bitmap size: ${bitmap.width}x${bitmap.height}")
            Log.d("OCR", "Bitmap config: ${bitmap.config}")

            runOnUiThread {
                showProgressIndicator("Extracting text from image...")
                setWebViewContent(aiResponseWebView, "Processing OCR...")
                Log.d("OCR", "Set UI to 'Processing OCR...'")
            }

            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                Log.d("OCR", "InputImage created successfully")

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                Log.d("OCR", "TextRecognizer created successfully")

                recognizer
                    .process(image)
                    .addOnSuccessListener { visionText ->
                        handleOCRSuccess(visionText.text)
                    }.addOnFailureListener { e ->
                        handleOCRFailure(e)
                    }
            } catch (e: IllegalStateException) {
                handleOCRException(e)
            }
        } ?: run {
            Log.w("OCR", "No bitmap available for OCR")
            runOnUiThread {
                Toast.makeText(this, "No image to process", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleOCRSuccess(extractedText: String) {
        Log.d("OCR", "OCR completed successfully")
        Log.d("OCR", "Extracted text length: ${extractedText.length}")
        Log.d("OCR", "Extracted text: '$extractedText'")

        runOnUiThread {
            hideProgressIndicator()
            if (extractedText.isNotEmpty()) {
                updateUIWithExtractedText(extractedText)
            } else {
                updateUIWithNoTextFound()
            }
        }
    }

    private fun updateUIWithExtractedText(extractedText: String) {
        Log.d("OCR", "Updating UI with extracted text")
        Log.d("OCR", "Text to set: '$extractedText'")

        val rules = buildOCRRules()
        currentPrompt = "$rules\n\n```\n${extractedText.trim()}\n```"
        promptEditText.setText(currentPrompt)
        updateButtonStates()

        Toast.makeText(this@MainActivity, "Text extracted successfully!", Toast.LENGTH_SHORT).show()
        Log.d("OCR", "UI updated with full prompt")
        Log.d("OCR", "Current prompt: '$currentPrompt'")
    }

    private fun updateUIWithNoTextFound() {
        Log.w("OCR", "No text found in image")
        promptEditText.setText(
            "No text found in the image. Try:\n- Using a clearer image\n" +
                "- Ensuring text is well-lit\n- Checking text orientation",
        )
        updateButtonStates()
        Toast.makeText(this@MainActivity, "No text found in image", Toast.LENGTH_SHORT).show()
        Log.d("OCR", "Set 'no text found' message")
    }

    private fun buildOCRRules(): String =
        """
        Please answer the following question in English. Provide a compact step-by-step solution or reasoning, 
        but for the final answer, use the format 'a = <answer>' (e.g., a = 5). Do not use verbose explanations, 
        boxed math, or LaTeX for the answer. Only provide the answer in the specified format at the end.
        After giving the answer, if possible, provide the Python code that solves the problem, formatted as a code block.
        """.trimIndent()

    private fun handleOCRFailure(e: Exception) {
        Log.e("OCR", "OCR failed: ${e.message}", e)
        runOnUiThread {
            hideProgressIndicator()
            setWebViewContent(
                aiResponseWebView,
                "OCR failed: ${e.message}\n\nPossible issues:\n- Check internet connection\n" +
                    "- Try a different image\n- Ensure image has clear text",
            )
            updateButtonStates()
            Toast.makeText(this@MainActivity, "OCR processing failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleOCRException(e: IllegalStateException) {
        Log.e("OCR", "Illegal state error creating InputImage: ${e.message}", e)
        runOnUiThread {
            hideProgressIndicator()
            setWebViewContent(aiResponseWebView, "Error processing image: ${e.message}")
            updateButtonStates()
            Toast.makeText(this@MainActivity, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendToAI() {
        // Check if API key is available
        val activeKeys = apiKeyManager.getActiveApiKeys()
        if (activeKeys.isEmpty()) {
            Toast
                .makeText(
                    this,
                    "API key required. Please configure your Groq API key first.",
                    Toast.LENGTH_LONG,
                ).show()
            showApiKeyDialog()
            return
        }

        // Get the current prompt from the text field (user may have edited it)
        val promptFromUI = promptEditText.text.toString()
        if (promptFromUI.isNotEmpty() && !promptFromUI.startsWith("No text found") &&
            !promptFromUI.startsWith("Processing OCR...")
        ) {
            Log.d("OCR", "Sending prompt to AI: '$promptFromUI'")

            val finalPrompt =
                if (isOCRMode) {
                    // In OCR mode, use the prompt as-is (already formatted with rules)
                    promptFromUI
                } else {
                    // In text-only mode, add language instruction
                    "Please respond in English: $promptFromUI"
                }

            // Note: We'll add the user message to conversation history AFTER getting AI response
            // to avoid duplication in the API call

            // Check if "All Models" is selected
            val selectedModelPosition = modelSpinner.selectedItemPosition
            val modelNames = (modelManager.getModels(true).map { it.name } + "All Models").toTypedArray()

            if (selectedModelPosition >= 0 && modelNames[selectedModelPosition] == "All Models") {
                processWithAllModels(finalPrompt)
            } else {
                processWithAI(finalPrompt)
            }
        } else {
            Toast.makeText(this, "No valid prompt to send", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processWithAI(prompt: String) {
        Log.d("OCR", "Starting AI processing with prompt: '$prompt'")
        runOnUiThread {
            showProgressIndicator("Processing with AI...")
            setWebViewContent(aiResponseWebView, "Processing with AI...")
            Log.d("OCR", "Set AI UI to 'Processing with AI...'")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = sendToGroqAPI(prompt)
                Log.d("OCR", "AI response received: $response")

                // Add both user message and AI response to conversation history if in conversation mode
                if (isConversationMode) {
                    conversationHistory.add(ConversationMessage("user", prompt))
                    conversationHistory.add(ConversationMessage("assistant", response))
                    Log.d(
                        "OCR",
                        "Added user and AI messages to conversation history. " +
                            "Total messages: ${conversationHistory.size}",
                    )
                    updateConversationStatus()
                }

                withContext(Dispatchers.Main) {
                    hideProgressIndicator()
                    Log.d("OCR", "Setting AI response in UI: '$response'")
                    setWebViewContent(aiResponseWebView, "AI Response:\n$response")
                    Log.d("OCR", "AI response updated in UI")
                    Log.d("OCR", "Current AI response text: 'AI Response updated'")
                }
            } catch (e: IOException) {
                Log.e("OCR", "Network error in AI processing: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    hideProgressIndicator()
                    setWebViewContent(aiResponseWebView, "Network error: ${e.message}")
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IllegalStateException) {
                Log.e("OCR", "Illegal state error in AI processing: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    hideProgressIndicator()
                    setWebViewContent(aiResponseWebView, "AI processing failed: ${e.message}")
                    Toast.makeText(this@MainActivity, "AI processing failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processWithAllModels(prompt: String) {
        Log.d("OCR", "Starting processing with all models: '$prompt'")
        isProcessingAllModels = true
        modelResponses.clear()

        setupAllModelsUI()
        processAllModelsAsync(prompt)
    }

    private fun setupAllModelsUI() {
        runOnUiThread {
            val selectedModels = modelManager.getModels(true)
            setWebViewContent(
                aiResponseWebView,
                "Processing with all models...\n\nPlease wait while we get responses from all models.\n\n" +
                    "Models being processed:\n${selectedModels.joinToString("\n") { "• ${it.name}" }}",
            )
            Log.d("OCR", "Set AI UI to 'Processing with all models...'")
        }
    }

    private fun processAllModelsAsync(prompt: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jobs = processAllModelsConcurrently(prompt)
                jobs.joinAll()
                handleAllModelsCompletion(prompt)
            } catch (e: IOException) {
                handleAllModelsError("Network error in all models processing: ${e.message}")
            } catch (e: IllegalStateException) {
                handleAllModelsError("Illegal state error in all models processing: ${e.message}")
            }
        }
    }

    private fun processAllModelsConcurrently(prompt: String): List<Job> {
        val jobs = mutableListOf<Job>()
        val selectedModels = modelManager.getModels(true)

        selectedModels.forEach { model ->
            val job =
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = sendToGroqAPIWithModel(prompt, model.id)
                        modelResponses[model.name] = response
                        Log.d("OCR", "Response from ${model.name}: $response")
                        withContext(Dispatchers.Main) { updateAllModelsProgress() }
                    } catch (e: IOException) {
                        modelResponses[model.name] = "Network error: ${e.message}"
                        Log.e("OCR", "Network error getting response from ${model.name}: ${e.message}", e)
                    } catch (e: IllegalStateException) {
                        modelResponses[model.name] = "Error: ${e.message}"
                        Log.e("OCR", "Illegal state error getting response from ${model.name}: ${e.message}", e)
                        withContext(Dispatchers.Main) { updateAllModelsProgress() }
                    }
                }
            jobs.add(job)
        }
        return jobs
    }

    private suspend fun handleAllModelsCompletion(prompt: String) {
        if (isConversationMode) {
            addAllModelsToConversationHistory(prompt)
        }

        withContext(Dispatchers.Main) {
            isProcessingAllModels = false
            displayAllModelsResponse()
            Log.d("OCR", "All models processing completed")
        }
    }

    private fun addAllModelsToConversationHistory(prompt: String) {
        conversationHistory.add(ConversationMessage("user", prompt))
        val combinedResponse = modelResponses.values.joinToString("\n\n---\n\n") { it }
        conversationHistory.add(ConversationMessage("assistant", combinedResponse))
        Log.d("OCR", "Added all models conversation to history. Total messages: ${conversationHistory.size}")
        updateConversationStatus()
    }

    private suspend fun handleAllModelsError(errorMessage: String) {
        Log.e("OCR", errorMessage)
        withContext(Dispatchers.Main) {
            isProcessingAllModels = false
            setWebViewContent(aiResponseWebView, errorMessage.substringAfter(": "))
        }
    }

    private fun updateAllModelsProgress() {
        if (!isProcessingAllModels) return

        val selectedModels = modelManager.getModels(true)
        val completedCount = modelResponses.size
        val totalCount = selectedModels.size
        val progressText = StringBuilder()
        progressText.append("Processing with all models...\n\n")
        progressText.append("Progress: $completedCount/$totalCount models completed\n\n")

        selectedModels.forEach { model ->
            if (modelResponses.containsKey(model.name)) {
                progressText.append("✅ ${model.name} - Completed\n")
            } else {
                progressText.append("⏳ ${model.name} - Processing...\n")
            }
        }

        setWebViewContent(aiResponseWebView, progressText.toString())
    }

    private fun displayAllModelsResponse() {
        val responseBuilder = StringBuilder()
        responseBuilder.append("## All Models Response\n\n")

        val selectedModels = modelManager.getModels(true)
        selectedModels.forEach { model ->
            val response = modelResponses[model.name] ?: "No response received"
            responseBuilder.append("### ${model.name}\n")
            responseBuilder.append("${response}\n\n")
            responseBuilder.append("---\n\n")
        }

        Log.d("OCR", "Displaying all models response with ${selectedModels.size} models")
        setWebViewContent(aiResponseWebView, responseBuilder.toString())
    }

    private suspend fun sendToGroqAPI(prompt: String): String {
        return withContext(Dispatchers.IO) {
            // Get selected model
            val selectedModels = modelManager.getModels(true)
            val selectedModel = if (selectedModels.isNotEmpty()) selectedModels.first().id else "gemma2-9b-it"

            Log.d("OCR", "Using AI model: $selectedModel")
            Log.d("OCR", "Full prompt being sent to AI: '$prompt'")

            return@withContext sendToGroqAPIWithModel(prompt, selectedModel)
        }
    }

    private suspend fun sendToGroqAPIWithModel(
        prompt: String,
        model: String,
    ): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            Log.d("OCR", "Using AI model: $model")
            Log.d("OCR", "Full prompt being sent to AI: '$prompt'")

            val messagesArray = buildMessagesArray(prompt)
            val jsonBody = buildRequestJson(model, messagesArray)
            val apiKey = apiKeyManager.getNextApiKey()

            if (apiKey == null) {
                return@withContext "Error: No active API keys available"
            }

            val request = buildGroqRequest(apiKey, jsonBody)
            executeGroqRequest(client, request)
        }
    }

    private fun buildMessagesArray(prompt: String): JSONArray {
        val messagesArray = JSONArray()

        // Add system message
        messagesArray.put(
            JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    "You are a helpful AI assistant. Please respond in English by default. " +
                        "Only respond in Arabic or other languages if the user explicitly asks you to do so " +
                        "or if the question is specifically about Arabic language/culture. " +
                        "For all other queries, provide clear and helpful responses in English.",
                )
            },
        )

        if (isConversationMode && conversationHistory.isNotEmpty()) {
            addConversationHistory(messagesArray, prompt)
        } else {
            addSingleMessage(messagesArray, prompt)
        }

        return messagesArray
    }

    private fun addConversationHistory(
        messagesArray: JSONArray,
        prompt: String,
    ) {
        conversationHistory.forEach { message ->
            messagesArray.put(
                JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                },
            )
        }
        addSingleMessage(messagesArray, prompt)
        Log.d("OCR", "Including conversation history: ${conversationHistory.size} messages + current message")
    }

    private fun addSingleMessage(
        messagesArray: JSONArray,
        prompt: String,
    ) {
        messagesArray.put(
            JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            },
        )
    }

    private fun buildRequestJson(
        model: String,
        messagesArray: JSONArray,
    ): String =
        JSONObject()
            .apply {
                put("model", model)
                put("messages", messagesArray)
                addModelParameters(this, model)
            }.toString()

    private fun addModelParameters(
        jsonObject: JSONObject,
        model: String,
    ) {
        when (model) {
            "llama-3.3-70b-versatile" -> {
                jsonObject.put("temperature", 1.0)
                jsonObject.put("max_tokens", MAX_REQUEST_SIZE)
                jsonObject.put("top_p", 1.0)
            }
            "mistral-saba-24b" -> {
                jsonObject.put("temperature", 1.0)
                jsonObject.put("max_tokens", MAX_REQUEST_SIZE)
                jsonObject.put("top_p", 1.0)
            }
            else -> {
                jsonObject.put("temperature", TIMEOUT_FACTOR)
                jsonObject.put("max_tokens", MAX_TOKENS)
                jsonObject.put("top_p", TEMPERATURE)
            }
        }
    }

    private fun buildGroqRequest(
        apiKey: String,
        jsonBody: String,
    ): Request =
        Request
            .Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

    private fun executeGroqRequest(
        client: OkHttpClient,
        request: Request,
    ): String =
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "No response"

            if (response.isSuccessful) {
                parseGroqResponse(responseBody)
            } else {
                "Error: ${response.code} - $responseBody"
            }
        } catch (e: IOException) {
            "Network error: ${e.message}"
        }

    private fun parseGroqResponse(responseBody: String): String {
        val jsonResponse = JSONObject(responseBody)
        val choices = jsonResponse.getJSONArray("choices")
        return if (choices.length() > 0) {
            val message = choices.getJSONObject(0).getJSONObject("message")
            message.getString("content")
        } else {
            "No response generated"
        }
    }

    private fun setupModelSpinner() {
        val selectedModels = modelManager.getModels(true)
        val modelNames = (selectedModels.map { it.name } + "All Models" + "Manage Models").toTypedArray()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter

        modelSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long,
                ) {
                    val selectedModelName = modelNames[position]
                    Log.d("OCR", "Selected model: $selectedModelName")

                    when {
                        selectedModelName == "Manage Models" -> {
                            showModelSelectionDialog()
                        }
                        selectedModelName == "All Models" -> {
                            if (modelResponses.isNotEmpty()) {
                                displayAllModelsResponse()
                            }
                        }
                        else -> {
                            // If we have responses from all models and user selects a specific model,
                            // show that response
                            if (modelResponses.isNotEmpty()) {
                                val response = modelResponses[selectedModelName]
                                if (response != null) {
                                    setWebViewContent(aiResponseWebView, "## ${selectedModelName}\n\n$response")
                                }
                            }
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Do nothing
                }
            }
    }

    private fun copyToClipboard(
        text: String,
        label: String,
    ) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        Log.d("OCR", "$label copied to clipboard")
    }

    private fun reviewCroppedImage() {
        croppedImageUri?.let { uri ->
            val intent = Intent(this, ReviewImageActivity::class.java)
            intent.putExtra("image_uri", uri.toString())
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "No cropped image available to review.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEditTextFocus() {
        promptEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Post a delayed action to scroll after the keyboard appears
                promptEditText.postDelayed({
                    val scrollView = findViewById<ScrollView>(R.id.scroll_view)
                    scrollView?.smoothScrollTo(0, promptEditText.top - PROGRESS_DELAY_MS.toInt())
                }, PROGRESS_DURATION_MS.toLong())
            }
        }
    }

    private fun setupEditTextListener() {
        promptEditText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    // No action needed before text changes
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    updateButtonStates()
                }

                override fun afterTextChanged(s: Editable?) {
                    // No action needed after text changes
                }
            },
        )
    }

    private fun toggleMode() {
        isOCRMode = !isOCRMode
        val modeText = if (isOCRMode) "📷 OCR Mode" else "💬 Chat Mode"
        modeToggleButton.text = modeText

        if (isOCRMode) {
            // Switch to OCR mode
            promptEditText.hint = "Prompt will appear here after OCR..."
            // Clear any OCR-specific content
            if (promptEditText.text.startsWith("Please answer the following question")) {
                promptEditText.setText("")
            }
        } else {
            // Switch to chat mode
            promptEditText.hint = "Type your message here..."
            // Clear any OCR-specific content
            if (promptEditText.text.startsWith("Please answer the following question")) {
                promptEditText.setText("")
            }
        }

        // Show conversation status
        if (isConversationMode && conversationHistory.isNotEmpty()) {
            Toast
                .makeText(
                    this,
                    "Conversation mode active with ${conversationHistory.size} messages",
                    Toast.LENGTH_LONG,
                ).show()
        }

        updateButtonStates()
        Toast.makeText(this, "Switched to ${if (isOCRMode) "OCR" else "Chat"} mode", Toast.LENGTH_SHORT).show()
        Log.d("OCR", "Toggled mode to ${if (isOCRMode) "OCR" else "Chat"}")
    }

    private fun clearAll() {
        currentBitmap = null
        imageView.setImageBitmap(null)
        processButton.isEnabled = false
        sendToAIButton.isEnabled = false
        reviewImageButton.isEnabled = false
        croppedImageUri = null
        setWebViewContent(aiResponseWebView, "No AI response yet...")
        promptEditText.setText("")

        // Clear model responses
        modelResponses.clear()
        isProcessingAllModels = false

        // Reset hint based on current mode
        if (isOCRMode) {
            promptEditText.hint = "Prompt will appear here after OCR..."
        } else {
            promptEditText.hint = "Type your message here..."
        }

        updateButtonStates()
        Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
        Log.d("OCR", "All data cleared")
    }

    private fun startNewConversation() {
        // Clear conversation history
        conversationHistory.clear()

        // Clear AI response
        setWebViewContent(aiResponseWebView, "No AI response yet...")

        // Clear prompt if it's not OCR content
        if (!promptEditText.text.startsWith("Please answer the following question")) {
            promptEditText.setText("")
        }

        updateConversationStatus()
        Toast.makeText(this, "New conversation started", Toast.LENGTH_SHORT).show()
        Log.d("OCR", "New conversation started. History cleared.")
    }

    private fun updateConversationStatus() {
        if (conversationHistory.isNotEmpty()) {
            val status = "💬 Conversation: ${conversationHistory.size} messages"
            // You could update a status TextView here if you add one to the layout
            Log.d("OCR", status)
        }
    }

    private fun updateButtonStates() {
        if (isOCRMode) {
            // OCR Mode: Process button enabled when image is loaded, Send to AI enabled when prompt is ready
            processButton.isEnabled = currentBitmap != null
            sendToAIButton.isEnabled = promptEditText.text.isNotEmpty() &&
                !promptEditText.text.startsWith("No text found") &&
                !promptEditText.text.startsWith("Processing OCR...") &&
                !promptEditText.text.startsWith("OCR failed") &&
                !promptEditText.text.startsWith("Error processing")
            reviewImageButton.isEnabled = croppedImageUri != null
        } else {
            // Chat Mode: Process button disabled, Send to AI enabled when user types something
            processButton.isEnabled = false
            sendToAIButton.isEnabled = promptEditText.text.isNotEmpty() &&
                promptEditText.text
                    .toString()
                    .trim()
                    .isNotEmpty()
            reviewImageButton.isEnabled = false
        }

        Log.d(
            "OCR",
            "Button states updated - OCR Mode: $isOCRMode, " +
                "Process: ${processButton.isEnabled}, Send: ${sendToAIButton.isEnabled}",
        )
    }

    private fun canEditTextScrollVertically(editText: EditText): Boolean {
        val scrollY = editText.scrollY
        val scrollRange =
            editText.layout?.height
                ?: 0 - (editText.height - editText.compoundPaddingTop - editText.compoundPaddingBottom)
        if (scrollRange <= 0) return false
        return (scrollY > 0) || (scrollY < scrollRange - 1)
    }

    private fun clearOldData() {
        // Clear old cropped image and related data
        croppedImageUri = null
        currentBitmap = null
        imageView.setImageBitmap(null)
        promptEditText.setText("")
        setWebViewContent(aiResponseWebView, "No AI response yet...")
        // Clear model responses
        modelResponses.clear()
        isProcessingAllModels = false
        Log.d("OCR", "Cleared old data for new capture")
    }

    private fun showProgressIndicator(message: String) {
        hideProgressIndicator() // Hide any existing dialog

        progressDialog =
            AlertDialog
                .Builder(this)
                .setTitle("Processing...")
                .setMessage(message)
                .setCancelable(false)
                .create()

        progressDialog?.show()
    }

    private fun hideProgressIndicator() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources to prevent memory leaks
        hideProgressIndicator()
        currentBitmap?.recycle()
        currentBitmap = null
        modelResponses.clear()
        conversationHistory.clear()
    }
}
