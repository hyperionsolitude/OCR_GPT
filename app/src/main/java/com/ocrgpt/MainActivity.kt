package com.ocrgpt

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResult
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
        // Time constants (in milliseconds)
        private const val ONE_MINUTE_MS = 60000L
        private const val ONE_HOUR_MS = 3600000L
        private const val ONE_DAY_MS = 86400000L

        // Image processing constants
        private const val MAX_IMAGE_SIZE = 1024
        private const val UNIQUE_ID_MULTIPLIER = 1000
        private const val PROGRESS_DELAY_MS = 100L
        private const val PROGRESS_DURATION_MS = 300L

        // Color constants
        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val ALPHA_SHIFT = 24
        private const val COLOR_MASK = 0xFF
        private const val BRIGHTNESS_THRESHOLD_LOW = 100
        private const val BRIGHTNESS_THRESHOLD_HIGH = 150
        private const val BRIGHTNESS_FACTOR_LOW = 1.3f
        private const val BRIGHTNESS_FACTOR_HIGH = 1.15f
        private const val BRIGHTNESS_FACTOR_DEFAULT = 1.05f
        private const val CONTRAST_FACTOR = 1.3f
        private const val GRAYSCALE_RED_WEIGHT = 0.299f
        private const val GRAYSCALE_GREEN_WEIGHT = 0.587f
        private const val GRAYSCALE_BLUE_WEIGHT = 0.114f
        private const val GRAYSCALE_OFFSET = 128
        private const val MAX_COLOR_VALUE = 255
        private const val ENHANCEMENT_FACTOR = 0.7f
        private const val ENHANCEMENT_OFFSET = 0.3f

        // API constants
        private const val MAX_REQUEST_SIZE = 1024
        private const val TIMEOUT_FACTOR = 0.3f
        private const val MAX_TOKENS = 4096
        private const val TEMPERATURE = 0.95f

        // API key display constants
        private const val API_KEY_PREVIEW_START = 8
        private const val API_KEY_PREVIEW_END = 4

        // Color processing constants
        private const val COLOR_CHANNELS = 3
        private const val RED_SHIFT_BITS = 16
        private const val GREEN_SHIFT_BITS = 8
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
    private var currentPhotoPath: String? = null
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

    // Activity Result Launchers
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

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

    private lateinit var cropLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

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
        promptEditText.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance())
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
        setupCameraLauncher()
        setupGalleryLauncher()
        setupCropLauncher()
        setupPermissionLauncher()
    }

    private fun setupCameraLauncher() {
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("OCR", "Camera result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                handleCameraSuccess(result)
            } else {
                Log.d("OCR", "Camera cancelled or failed: ${result.resultCode}")
                Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    private fun setupGalleryLauncher() {
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("OCR", "Gallery result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                handleGallerySuccess(result)
            } else {
                Log.d("OCR", "Gallery cancelled: ${result.resultCode}")
                Toast.makeText(this, "Gallery cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    private fun setupCropLauncher() {
        cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data?.hasExtra(CustomCropActivity.EXTRA_CROPPED_URI) == true) {
                val croppedUriString = data.getStringExtra(CustomCropActivity.EXTRA_CROPPED_URI)
                croppedUriString?.let { uriString ->
                    val uri = Uri.parse(uriString)
                    croppedImageUri = uri
                    loadImageFromUri(uri)
                    updateButtonStates()
                    Log.d("OCR", "New cropped image set: $uriString")
                }
            } else {
                Log.d("OCR", "Crop cancelled or failed: ${result.resultCode}")
            }
        }
    }

    private fun setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("OCR", "Camera permission granted")
                launchCamera()
            } else {
                Log.d("OCR", "Camera permission denied")
                Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
            }
        }
    }

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

            checkbox.setOnCheckedChangeListener { _, isChecked ->
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
        val count = (modelManager.getModelInfo() as Pair<Int, Int>).second
        textView.text = "Selected: $count models"
    }

    private fun showApiKeyManagementDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_api_key_management, null)
        val keysLayout = dialogView.findViewById<LinearLayout>(R.id.layout_api_keys)
        val addButton = dialogView.findViewById<Button>(R.id.btn_add_api_key)
        val resetButton = dialogView.findViewById<Button>(R.id.btn_reset_failed)
        val testButton = dialogView.findViewById<Button>(R.id.btn_test_keys)

        refreshApiKeyList(keysLayout)

        addButton.setOnClickListener {
            showAddApiKeyDialog()
        }

        resetButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                apiKeyManager.resetFailedKeys()
                withContext(Dispatchers.Main) {
                    refreshApiKeyList(keysLayout)
                    Toast.makeText(this@MainActivity, "Reset all failed API keys", Toast.LENGTH_SHORT).show()
                }
            }
        }

        testButton.setOnClickListener {
            testAllApiKeys()
        }

        val dialog =
            AlertDialog
                .Builder(this)
                .setView(dialogView)
                .setPositiveButton("Done") { _, _ ->
                    // Refresh model spinner in case API keys changed
                    initializeModels()
                }.setNegativeButton("Cancel", null)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun refreshApiKeyList(layout: LinearLayout) {
        layout.removeAllViews()
        val apiKeys = apiKeyManager.getAllApiKeys()

        apiKeys.forEachIndexed { index, apiKey ->
            val keyView = LayoutInflater.from(this).inflate(R.layout.item_api_key, null)
            val checkbox = keyView.findViewById<CheckBox>(R.id.checkbox_active)
            val nameText = keyView.findViewById<TextView>(R.id.tv_key_name)
            val previewText = keyView.findViewById<TextView>(R.id.tv_key_preview)
            val usageText = keyView.findViewById<TextView>(R.id.tv_usage_count)
            val lastUsedText = keyView.findViewById<TextView>(R.id.tv_last_used)
            val editButton = keyView.findViewById<Button>(R.id.btn_edit)
            val deleteButton = keyView.findViewById<Button>(R.id.btn_delete)

            checkbox.isChecked = apiKey.isActive
            nameText.text = apiKey.name
            previewText.text = "${apiKey.key.take(API_KEY_PREVIEW_START)}...${apiKey.key.takeLast(API_KEY_PREVIEW_END)}"
            usageText.text = "${apiKey.usageCount} uses"
            lastUsedText.text = formatLastUsed(apiKey.lastUsed)

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                CoroutineScope(Dispatchers.IO).launch {
                    apiKeyManager.updateApiKey(index, apiKey.name, isChecked)
                }
            }

            editButton.setOnClickListener {
                showEditApiKeyDialog(index, apiKey)
            }

            deleteButton.setOnClickListener {
                showDeleteApiKeyDialog(index, apiKey.name)
            }

            layout.addView(keyView)
        }
    }

    private fun showAddApiKeyDialog() {
        val editText =
            EditText(this).apply {
                hint = "Enter API key"
            }
        val nameText =
            EditText(this).apply {
                hint = "Enter name for this key"
                setText("API Key ${apiKeyManager.getAllApiKeys().size + 1}")
            }

        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(nameText)
                addView(editText)
            }

        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Add New API Key")
                .setView(layout)
                .setPositiveButton("Add") { _, _ ->
                    val key = editText.text.toString().trim()
                    val name = nameText.text.toString().trim()
                    if (key.isNotEmpty() && name.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val success = apiKeyManager.addApiKey(key, name)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast
                                        .makeText(
                                            this@MainActivity,
                                            "API key added successfully",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                } else {
                                    Toast
                                        .makeText(
                                            this@MainActivity,
                                            "Failed to add API key (duplicate or limit reached)",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        }
                    }
                }.setNegativeButton("Cancel", null)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun showEditApiKeyDialog(
        index: Int,
        apiKey: ApiKeyInfo,
    ) {
        val editText =
            EditText(this).apply {
                setText(apiKey.name)
            }

        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Edit API Key")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    val newName = editText.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            apiKeyManager.updateApiKey(index, newName, apiKey.isActive)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "API key updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.setNegativeButton("Cancel", null)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun showDeleteApiKeyDialog(
        index: Int,
        name: String,
    ) {
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Delete API Key")
                .setMessage("Are you sure you want to delete '$name'?")
                .setPositiveButton("Delete") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val success = apiKeyManager.removeApiKey(index)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(this@MainActivity, "API key deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Failed to delete API key", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.setNegativeButton("Cancel", null)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFF44336.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFF4CAF50.toInt())
    }

    private fun testAllApiKeys() {
        Toast.makeText(this, "Testing API keys...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val activeKeys = apiKeyManager.getActiveApiKeys()
            var successCount = 0

            activeKeys.forEach { apiKey ->
                try {
                    val models = modelManager.fetchAvailableModels(apiKey.key)
                    if (models.isNotEmpty()) {
                        successCount++
                    }
                } catch (e: IOException) {
                    Log.e("OCR", "Network error testing API key ${apiKey.name}: ${e.message}")
                } catch (e: IllegalStateException) {
                    Log.e("OCR", "Illegal state error testing API key ${apiKey.name}: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                Toast
                    .makeText(
                        this@MainActivity,
                        "API key test complete: $successCount/${activeKeys.size} working",
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    private fun formatLastUsed(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < ONE_MINUTE_MS -> "Just now"
            diff < ONE_HOUR_MS -> "${diff / ONE_MINUTE_MS}m ago"
            diff < ONE_DAY_MS -> "${diff / ONE_HOUR_MS}h ago"
            else -> "${diff / ONE_DAY_MS}d ago"
        }
    }

    private fun showApiKeySettingsDialog() {
        showApiKeyManagementDialog()
    }

    private fun showSettingsDialog() {
        val options =
            arrayOf(
                "🔑 Manage API Keys",
                "🤖 Select AI Models",
                "ℹ️ About",
            )

        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle("Settings")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showApiKeyManagementDialog()
                        1 -> showModelSelectionDialog()
                        2 -> showAboutDialog()
                    }
                }.setNegativeButton("Cancel", null)
                .create()

        dialog.show()

        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
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
            val codeContent = matchResult.groupValues[1].trim()
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

        // Convert line breaks to HTML (but only for non-code content)
        return processedContent.replace("\n", "<br>")
    }

    private fun getWebViewText(webView: WebView): String =
        if (webView == aiResponseWebView) {
            currentPromptText
        } else {
            "AI Response content"
        }

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            // Request camera permission
            Log.d("OCR", "Requesting camera permission")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            // Use cache directory for better HarmonyOS compatibility
            val photoFile = createImageFileInCache()
            currentPhotoPath = photoFile.absolutePath
            currentPhotoUri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    photoFile,
                )

            Log.d("OCR", "Created photo file: ${photoFile.absolutePath}")
            Log.d("OCR", "Photo URI: $currentPhotoUri")

            // Try standard camera intent first - explicitly set to back camera
            val intent =
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                    putExtra("android.intent.extras.CAMERA_FACING", 0) // 0 = back camera, 1 = front camera
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

            // Try to resolve activity first, but don't fail if it returns null on HarmonyOS
            val resolveInfo = intent.resolveActivity(packageManager)
            Log.d("OCR", "Resolve activity result: $resolveInfo")

            if (resolveInfo != null) {
                // Standard approach works
                Log.d("OCR", "Launching standard camera intent")
                cameraLauncher.launch(intent)
            } else {
                // Try HarmonyOS-specific approach
                Log.d("OCR", "Standard camera not found, trying HarmonyOS approach")
                launchHarmonyOSCamera(photoFile)
            }
        } catch (e: ActivityNotFoundException) {
            Log.e("OCR", "Camera activity not found", e)
            Toast.makeText(this, "Camera not available: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e("OCR", "Camera permission denied", e)
            Toast.makeText(this, "Camera permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchHarmonyOSCamera(
        @Suppress("UNUSED_PARAMETER") photoFile: File,
    ) {
        try {
            // Try to launch Huawei camera directly - explicitly set to back camera
            val huaweiCameraIntent =
                Intent().apply {
                    setClassName("com.huawei.camera", "com.huawei.camera.ThirdCamera")
                    putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                    putExtra("android.intent.extras.CAMERA_FACING", 0) // 0 = back camera, 1 = front camera
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

            Log.d("OCR", "Trying Huawei camera directly")
            cameraLauncher.launch(huaweiCameraIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e("OCR", "Huawei camera not found", e)
            // Fallback to standard intent anyway - explicitly set to back camera
        } catch (e: SecurityException) {
            Log.e("OCR", "Huawei camera permission denied", e)
            // Fallback to standard intent anyway - explicitly set to back camera
            val fallbackIntent =
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                    putExtra("android.intent.extras.CAMERA_FACING", 0) // 0 = back camera, 1 = front camera
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            cameraLauncher.launch(fallbackIntent)
        }
    }

    private fun createImageFileInCache(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = cacheDir // Use cache directory instead of external files
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

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
                val cropActivityInfo = packageManager.resolveActivity(intent, 0)?.activityInfo
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
            Log.d("OCR", "Starting crop activity with intent: $intent, URI: $sourceUri, flags: ${intent.flags}")
            cropLauncher.launch(intent)
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
        try {
            Log.d("OCR", "Loading image from URI: $uri")
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Preprocess image for better OCR
                val processedBitmap = preprocessImageForOCR(bitmap)
                currentBitmap = processedBitmap
                imageView.setImageBitmap(processedBitmap)
                updateButtonStates()
                Toast.makeText(this, "Image loaded successfully", Toast.LENGTH_SHORT).show()
                Log.d("OCR", "Image loaded: ${processedBitmap.width}x${processedBitmap.height}")
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                Log.e("OCR", "Failed to decode bitmap from URI: $uri")
            }
        } catch (e: IOException) {
            Toast.makeText(this, "IO error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OCR", "IO error loading image from URI: $uri", e)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OCR", "Permission denied loading image from URI: $uri", e)
        }
    }

    private fun preprocessImageForOCR(originalBitmap: Bitmap): Bitmap =
        try {
            // Resize if too large (MLKit works better with reasonable sizes)
            val maxSize = MAX_IMAGE_SIZE
            val width = originalBitmap.width
            val height = originalBitmap.height

            val resizedBitmap =
                if (width > maxSize || height > maxSize) {
                    val scale = maxSize.toFloat() / maxOf(width, height)
                    val newWidth = (width * scale).toInt()
                    val newHeight = (height * scale).toInt()
                    Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                } else {
                    originalBitmap
                }

            // Enhance image for better OCR, especially for monitor captures
            val enhancedBitmap = enhanceImageForOCR(resizedBitmap)

            // Clean up intermediate bitmap if we created one
            if (width > maxSize || height > maxSize) {
                resizedBitmap.recycle()
            }

            enhancedBitmap
        } catch (e: OutOfMemoryError) {
            Log.e("OCR", "Out of memory preprocessing image: ${e.message}", e)
            originalBitmap // Return original if preprocessing fails
        } catch (e: IllegalStateException) {
            Log.e("OCR", "Illegal state error preprocessing image: ${e.message}", e)
            originalBitmap // Return original if preprocessing fails
        }

    private fun enhanceImageForOCR(bitmap: Bitmap): Bitmap {
        return try {
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val pixels = IntArray(mutableBitmap.width * mutableBitmap.height)
            mutableBitmap.getPixels(pixels, 0, mutableBitmap.width, 0, 0, mutableBitmap.width, mutableBitmap.height)

            val avgBrightness = calculateAverageBrightness(pixels)
            val brightnessAdjustment = determineBrightnessAdjustment(avgBrightness)
            val contrastAdjustment = CONTRAST_FACTOR

            val enhancedPixels = enhancePixels(pixels, brightnessAdjustment, contrastAdjustment)
            mutableBitmap.setPixels(
                enhancedPixels,
                0,
                mutableBitmap.width,
                0,
                0,
                mutableBitmap.width,
                mutableBitmap.height,
            )

            Log.d(
                "OCR",
                "Image enhanced - Original avg brightness: $avgBrightness, " +
                    "Brightness adjustment: $brightnessAdjustment",
            )
            mutableBitmap
        } catch (e: OutOfMemoryError) {
            Log.e("OCR", "Out of memory enhancing image: ${e.message}", e)
            bitmap
        } catch (e: IllegalStateException) {
            Log.e("OCR", "Illegal state error enhancing image: ${e.message}", e)
            bitmap
        }
    }

    private fun calculateAverageBrightness(pixels: IntArray): Int {
        var totalBrightness = 0
        pixels.forEach { pixel ->
            val red = (pixel shr RED_SHIFT_BITS) and COLOR_MASK
            val green = (pixel shr GREEN_SHIFT_BITS) and COLOR_MASK
            val blue = pixel and COLOR_MASK
            totalBrightness += (red + green + blue) / COLOR_CHANNELS
        }
        return totalBrightness / pixels.size
    }

    private fun determineBrightnessAdjustment(avgBrightness: Int): Float =
        when {
            avgBrightness < BRIGHTNESS_THRESHOLD_LOW -> BRIGHTNESS_FACTOR_LOW
            avgBrightness < BRIGHTNESS_THRESHOLD_HIGH -> BRIGHTNESS_FACTOR_HIGH
            else -> BRIGHTNESS_FACTOR_DEFAULT
        }

    private fun enhancePixels(pixels: IntArray, brightnessAdjustment: Float, contrastAdjustment: Float): IntArray =
        pixels.map { pixel ->
            val alpha = (pixel shr ALPHA_SHIFT) and COLOR_MASK
            val red = (pixel shr RED_SHIFT) and COLOR_MASK
            val green = (pixel shr GREEN_SHIFT) and COLOR_MASK
            val blue = pixel and COLOR_MASK

            val gray =
                (red * GRAYSCALE_RED_WEIGHT + green * GRAYSCALE_GREEN_WEIGHT + blue * GRAYSCALE_BLUE_WEIGHT).toInt()
            val adjustedGray =
                ((gray - GRAYSCALE_OFFSET) * contrastAdjustment + GRAYSCALE_OFFSET).toInt()
            val enhancedGray =
                (adjustedGray * brightnessAdjustment).toInt().coerceIn(0, MAX_COLOR_VALUE)

            val enhancedRed =
                (red * ENHANCEMENT_FACTOR + enhancedGray * ENHANCEMENT_OFFSET).toInt().coerceIn(0, MAX_COLOR_VALUE)
            val enhancedGreen =
                (green * ENHANCEMENT_FACTOR + enhancedGray * ENHANCEMENT_OFFSET).toInt().coerceIn(0, MAX_COLOR_VALUE)
            val enhancedBlue =
                (blue * ENHANCEMENT_FACTOR + enhancedGray * ENHANCEMENT_OFFSET).toInt().coerceIn(0, MAX_COLOR_VALUE)

            (alpha shl ALPHA_SHIFT) or (enhancedRed shl RED_SHIFT) or (enhancedGreen shl GREEN_SHIFT) or enhancedBlue
        }.toIntArray()

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
                galleryLauncher.launch(resolvedIntent)
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
            val job = CoroutineScope(Dispatchers.IO).launch {
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

    private fun addConversationHistory(messagesArray: JSONArray, prompt: String) {
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

    private fun addSingleMessage(messagesArray: JSONArray, prompt: String) {
        messagesArray.put(
            JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            },
        )
    }

    private fun buildRequestJson(model: String, messagesArray: JSONArray): String =
        JSONObject()
            .apply {
                put("model", model)
                put("messages", messagesArray)
                addModelParameters(this, model)
            }.toString()

    private fun addModelParameters(jsonObject: JSONObject, model: String) {
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

    private fun buildGroqRequest(apiKey: String, jsonBody: String): Request =
        Request
            .Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

    private fun executeGroqRequest(client: OkHttpClient, request: Request): String {
        return try {
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
