package com.ocrgpt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import android.content.ClipboardManager
import android.content.ClipData
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.widget.EditText
import android.widget.ScrollView
import android.text.TextWatcher
import android.text.Editable
import androidx.activity.result.ActivityResultLauncher
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.LinearLayout

// Data class to represent a conversation message
data class ConversationMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : AppCompatActivity() {
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
    private val CAMERA_REQUEST_CODE = 101
    private val CUSTOM_CROP_REQUEST_CODE = 102
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
    
    // Activity Result Launchers
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    
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
                    Toast.makeText(this@MainActivity, "Code copied to clipboard! (${text.length} chars)", Toast.LENGTH_SHORT).show()
                    Log.d("OCR", "Successfully copied ${text.length} characters to clipboard")
                } catch (e: Exception) {
                    Log.e("OCR", "Failed to copy to clipboard: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "Failed to copy to clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private lateinit var cropLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize managers
        sharedPreferences = getSharedPreferences("ocr_gpt_prefs", Context.MODE_PRIVATE)
        apiKeyManager = ApiKeyManager(this)
        modelManager = ModelManager(this)
        
        imageView = findViewById(R.id.image_preview)
        processButton = findViewById(R.id.btn_process)
        promptEditText = findViewById(R.id.et_prompt)
        // Enable vertical scrolling in EditText
        promptEditText.setVerticalScrollBarEnabled(true)
        promptEditText.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance())
        // Handle nested scrolling: let EditText handle scroll if it can, otherwise let parent ScrollView handle
        promptEditText.setOnTouchListener { v, _ ->
            if (v.hasFocus()) {
                v.parent.requestDisallowInterceptTouchEvent(canEditTextScrollVertically(promptEditText))
            }
            false
        }
        aiResponseWebView = findViewById(R.id.tv_ai_response)
        modelSpinner = findViewById(R.id.spinner_model)
        sendToAIButton = findViewById(R.id.btn_send_ai)
        reviewImageButton = findViewById(R.id.btn_review_image)
        modeToggleButton = findViewById(R.id.btn_mode_toggle)
        clearButton = findViewById(R.id.btn_clear)
        newConversationButton = findViewById(R.id.btn_new_conversation)
        processButton.isEnabled = false
        sendToAIButton.isEnabled = false
        reviewImageButton.isEnabled = false
        
        // Setup WebViews for markdown support
        setupWebViews()
        
        // Setup EditText focus handling
        setupEditTextFocus()
        
        // Setup EditText text change listener
        setupEditTextListener()
        
        // Check for API key and initialize models
        checkApiKeyAndInitializeModels()
        
        // Initialize model spinner
        setupModelSpinner()
        
        // Test UI initialization
        Log.d("OCR", "UI initialized - aiResponseWebView: $aiResponseWebView")
        
        // Test setting text to verify WebViews work
        setWebViewContent(aiResponseWebView, "No AI response yet...")
        promptEditText.hint = "Prompt will appear here after OCR..."
        
        findViewById<Button>(R.id.btn_capture).setOnClickListener { takePhoto() }
        findViewById<Button>(R.id.btn_gallery).setOnClickListener { openGallery() }
        processButton.setOnClickListener { processOCR() }
        sendToAIButton.setOnClickListener { sendToAI() }
        reviewImageButton.setOnClickListener { reviewCroppedImage() }
        modeToggleButton.setOnClickListener { toggleMode() }
        newConversationButton.setOnClickListener { startNewConversation() }
        clearButton.setOnClickListener { clearAll() }
        findViewById<Button>(R.id.btn_copy_ocr).setOnClickListener { copyToClipboard(promptEditText.text.toString(), "Prompt") }
        findViewById<Button>(R.id.btn_copy_ai).setOnClickListener { copyToClipboard(getWebViewText(aiResponseWebView), "AI Response") }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showSettingsDialog() }
        
        // Add long press on mode toggle to show API key settings
        modeToggleButton.setOnLongClickListener {
            showApiKeySettingsDialog()
            true
        }
        
        // Test MLKit initialization
        testMLKit()

        // Register Activity Result Launchers
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("OCR", "Camera result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                clearOldData()
                // Try multiple approaches for getting the photo URI
                var photoUri = currentPhotoUri
                
                // Check if camera returned data with URI
                result.data?.data?.let { dataUri ->
                    Log.d("OCR", "Camera returned data URI: $dataUri")
                    photoUri = dataUri
                }
                
                // If no data URI, use our prepared URI
                photoUri?.let { uri ->
                    Log.d("OCR", "Camera success, launching crop with URI: $uri")
                    try {
                        launchCropActivity(uri)
                    } catch (e: Exception) {
                        Log.e("OCR", "Error launching crop activity", e)
                        // Try to load image directly if crop fails
                        loadImageFromUri(uri)
                    }
                } ?: run {
                    Log.e("OCR", "Camera success but no photo URI available")
                    Toast.makeText(this, "Camera error: No photo URI", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("OCR", "Camera cancelled or failed: ${result.resultCode}")
                Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
            }
        }
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("OCR", "Gallery result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                clearOldData()
                val data = result.data
                val uri = data?.data
                uri?.let { 
                    Log.d("OCR", "Gallery selected URI: $uri")
                    try {
                        launchCropActivity(it)
                    } catch (e: Exception) {
                        Log.e("OCR", "Error launching crop activity from gallery", e)
                        // Try to load image directly if crop fails
                        loadImageFromUri(it)
                    }
                } ?: run {
                    Log.e("OCR", "Gallery success but no URI in data")
                    Toast.makeText(this, "Gallery error: No image selected", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("OCR", "Gallery cancelled: ${result.resultCode}")
                Toast.makeText(this, "Gallery cancelled", Toast.LENGTH_SHORT).show()
            }
        }
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
                Toast.makeText(this, "Crop cancelled", Toast.LENGTH_SHORT).show()
                clearOldData()
            }
        }
        
        // Initialize permission launcher
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
        val editText = EditText(this).apply {
            hint = "Enter your Groq API key"
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Groq API Key Required")
            .setMessage("To use AI features, you need a Groq API key.\n\n" +
                    "1. Visit https://console.groq.com/\n" +
                    "2. Sign up or log in\n" +
                    "3. Go to API Keys section\n" +
                    "4. Create a new API key\n" +
                    "5. Paste it below")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val apiKey = editText.text.toString().trim()
                if (apiKey.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val success = apiKeyManager.addApiKey(apiKey, "API Key 1")
                        withContext(Dispatchers.Main) {
                            if (success) {
                                initializeModels()
                            } else {
                                Toast.makeText(this@MainActivity, "Failed to save API key", Toast.LENGTH_SHORT).show()
                                showApiKeyDialog() // Show dialog again
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "API key cannot be empty", Toast.LENGTH_SHORT).show()
                    showApiKeyDialog() // Show dialog again
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                // Use default models without API key
                useDefaultModels()
                Toast.makeText(this, "Using default models. AI features may be limited.", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
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
            } catch (e: Exception) {
                Log.e("OCR", "Failed to fetch models: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    useDefaultModels()
                    Toast.makeText(this@MainActivity, "Using default models (API error: ${e.message})", Toast.LENGTH_LONG).show()
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
        
        val availableModels = modelManager.getAvailableModels()
        val checkboxes = mutableListOf<CheckBox>()
        
        // Create checkboxes for each model
        availableModels.forEach { model ->
            val modelView = LayoutInflater.from(this).inflate(R.layout.item_model, null)
            val checkbox = modelView.findViewById<CheckBox>(R.id.checkbox_selected)
            val modelNameText = modelView.findViewById<TextView>(R.id.tv_model_name)
            val modelIdText = modelView.findViewById<TextView>(R.id.tv_model_id)
            val categoryText = modelView.findViewById<TextView>(R.id.tv_model_category)
            
            checkbox.isChecked = modelManager.isModelSelected(model.id)
            modelNameText.text = model.name
            modelIdText.text = model.id
            categoryText.text = model.category.uppercase()
            
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                modelManager.toggleModelSelection(model.id)
                updateSelectedCount(selectedCountText)
            }
            
            checkboxes.add(checkbox)
            modelsLayout.addView(modelView)
        }
        
        updateSelectedCount(selectedCountText)
        
        selectAllButton.setOnClickListener {
            modelManager.selectAllModels()
            checkboxes.forEach { it.isChecked = true }
            updateSelectedCount(selectedCountText)
        }
        
        deselectAllButton.setOnClickListener {
            modelManager.deselectAllModels()
            checkboxes.forEach { it.isChecked = false }
            updateSelectedCount(selectedCountText)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Done") { _, _ ->
                setupModelSpinner()
                Toast.makeText(this, "Model selection updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }
    
    private fun updateSelectedCount(textView: TextView) {
        val count = modelManager.getSelectedModelCount()
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
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Done") { _, _ ->
                // Refresh model spinner in case API keys changed
                initializeModels()
            }
            .setNegativeButton("Cancel", null)
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
            previewText.text = "${apiKey.key.take(8)}...${apiKey.key.takeLast(4)}"
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
        val editText = EditText(this).apply {
            hint = "Enter API key"
        }
        val nameText = EditText(this).apply {
            hint = "Enter name for this key"
            setText("API Key ${apiKeyManager.getAllApiKeys().size + 1}")
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameText)
            addView(editText)
        }
        
        val dialog = AlertDialog.Builder(this)
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
                                Toast.makeText(this@MainActivity, "API key added successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Failed to add API key (duplicate or limit reached)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }
    
    private fun showEditApiKeyDialog(index: Int, apiKey: ApiKeyInfo) {
        val editText = EditText(this).apply {
            setText(apiKey.name)
        }
        
        val dialog = AlertDialog.Builder(this)
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
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }
    
    private fun showDeleteApiKeyDialog(index: Int, name: String) {
        val dialog = AlertDialog.Builder(this)
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
            }
            .setNegativeButton("Cancel", null)
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
                } catch (e: Exception) {
                    Log.e("OCR", "API key test failed for ${apiKey.name}: ${e.message}")
                }
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "API key test complete: $successCount/${activeKeys.size} working", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun formatLastUsed(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> "${diff / 86400000}d ago"
        }
    }
    
    private fun showApiKeySettingsDialog() {
        showApiKeyManagementDialog()
    }
    
    private fun showSettingsDialog() {
        val options = arrayOf(
            "🔑 Manage API Keys",
            "🤖 Select AI Models", 
            "ℹ️ About"
        )
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showApiKeyManagementDialog()
                    1 -> showModelSelectionDialog()
                    2 -> showAboutDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Customize button colors for dark theme
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }
    
    private fun showAboutDialog() {
        val message = """
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
        
        val dialog = AlertDialog.Builder(this)
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

    private fun setWebViewContent(webView: WebView, content: String) {
        // Store the content for the prompt WebView
        if (webView == aiResponseWebView) {
            currentPromptText = content
        }
        
        // Process content to add copy buttons to code blocks
        val processedContent = addCopyButtonsToCodeBlocks(content)
        
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: 'Roboto', sans-serif;
                        font-size: 14px;
                        line-height: 1.5;
                        color: #333;
                        background-color: #f5f5f5;
                        padding: 12px;
                        margin: 0;
                    }
                    .code-container {
                        position: relative;
                        margin: 8px 0;
                    }
                    pre {
                        background-color: #f8f8f8;
                        border: 1px solid #ddd;
                        border-radius: 4px;
                        padding: 8px;
                        overflow-x: auto;
                        font-family: 'Courier New', monospace;
                        margin: 0;
                    }
                    .copy-button {
                        position: absolute;
                        top: 8px;
                        right: 8px;
                        background-color: #4CAF50;
                        color: white;
                        border: none;
                        border-radius: 4px;
                        padding: 6px 12px;
                        font-size: 12px;
                        cursor: pointer;
                        z-index: 10;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.2);
                        transition: background-color 0.3s;
                    }
                    .copy-button:hover {
                        background-color: #45a049;
                    }
                    .copy-button:active {
                        background-color: #3d8b40;
                    }
                    .copy-button.copied {
                        background-color: #2196F3;
                    }
                    code {
                        background-color: #f1f1f1;
                        padding: 2px 4px;
                        border-radius: 3px;
                        font-family: 'Courier New', monospace;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: #2c3e50;
                        margin-top: 16px;
                        margin-bottom: 8px;
                    }
                    ul, ol {
                        padding-left: 20px;
                    }
                    blockquote {
                        border-left: 4px solid #ddd;
                        margin: 0;
                        padding-left: 16px;
                        color: #666;
                    }
                </style>
            </head>
            <body>
                $processedContent
                <script>
                    function copyCodeToClipboard(button) {
                        // Get the code from the data attribute
                        const codeText = button.getAttribute('data-code');
                        
                        if (!codeText) {
                            showCopyError(button);
                            return;
                        }
                        
                        // Decode HTML entities
                        const cleanCodeText = codeText
                            .replace(/&quot;/g, '"')
                            .replace(/&#10;/g, '\\n')
                            .replace(/&#13;/g, '\\r')
                            .replace(/&#9;/g, '\\t');
                        
                        try {
                            // Check if Android interface is available
                            if (window.Android && typeof window.Android.copyToClipboard === 'function') {
                                // Use native Android copy functionality
                                window.Android.copyToClipboard(cleanCodeText);
                                showCopySuccess(button);
                            } else {
                                // Fallback to WebView clipboard API
                                fallbackCopy(button, cleanCodeText);
                            }
                        } catch (err) {
                            showCopyError(button);
                        }
                    }
                    
                    function fallbackCopy(button, codeText) {
                        try {
                            // Create a temporary textarea element
                            const textarea = document.createElement('textarea');
                            textarea.value = codeText;
                            textarea.style.position = 'fixed';
                            textarea.style.left = '-999999px';
                            textarea.style.top = '-999999px';
                            document.body.appendChild(textarea);
                            
                            // Focus and select the text
                            textarea.focus();
                            textarea.select();
                            
                            // Try execCommand
                            const successful = document.execCommand('copy');
                            
                            // Remove the temporary textarea
                            document.body.removeChild(textarea);
                            
                            if (successful) {
                                showCopySuccess(button);
                            } else {
                                showCopyError(button);
                            }
                        } catch (err) {
                            showCopyError(button);
                        }
                    }
                    
                    function showCopySuccess(button) {
                        const originalText = button.textContent;
                        button.textContent = 'Copied!';
                        button.classList.add('copied');
                        
                        setTimeout(() => {
                            button.textContent = originalText;
                            button.classList.remove('copied');
                        }, 2000);
                    }
                    
                    function showCopyError(button) {
                        const originalText = button.textContent;
                        button.textContent = 'Failed';
                        button.style.backgroundColor = '#f44336';
                        
                        setTimeout(() => {
                            button.textContent = originalText;
                            button.style.backgroundColor = '#4CAF50';
                        }, 2000);
                    }
                    
                </script>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
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
            val uniqueId = "code-block-${codeBlockCounter++}-${System.currentTimeMillis()}-${(Math.random() * 1000).toInt()}"
            
            val codeBlockHtml = """
                <div class="code-container" id="$uniqueId">
                    <button class="copy-button" data-code="${codeContent.replace("\"", "&quot;").replace("\n", "&#10;").replace("\r", "&#13;").replace("\t", "&#9;")}" onclick="copyCodeToClipboard(this)">Copy</button>
                    <pre><code>$codeContent</code></pre>
                </div>
            """.trimIndent()
            
            // Replace this specific match
            processedContent = processedContent.replace(matchResult.value, codeBlockHtml)
        }
        
        // Convert line breaks to HTML (but only for non-code content)
        return processedContent.replace("\n", "<br>")
    }

    private fun escapeForJavaScript(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun getWebViewText(webView: WebView): String {
        return if (webView == aiResponseWebView) {
            currentPromptText
        } else {
            "AI Response content"
        }
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
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            
            Log.d("OCR", "Created photo file: ${photoFile.absolutePath}")
            Log.d("OCR", "Photo URI: $currentPhotoUri")
            
            // Try standard camera intent first - explicitly set to back camera
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
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
            
        } catch (e: Exception) {
            Log.e("OCR", "Error launching camera", e)
            Toast.makeText(this, "Error launching camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchHarmonyOSCamera(photoFile: File) {
        try {
            // Try to launch Huawei camera directly - explicitly set to back camera
            val huaweiCameraIntent = Intent().apply {
                setClassName("com.huawei.camera", "com.huawei.camera.ThirdCamera")
                putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                putExtra("android.intent.extras.CAMERA_FACING", 0) // 0 = back camera, 1 = front camera
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            
            Log.d("OCR", "Trying Huawei camera directly")
            cameraLauncher.launch(huaweiCameraIntent)
            
        } catch (e: Exception) {
            Log.e("OCR", "Huawei camera launch failed", e)
            // Fallback to standard intent anyway - explicitly set to back camera
            val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
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
            if ("file" == sourceUri.scheme || ("content" == sourceUri.scheme && sourceUri.authority?.contains(packageName) == true)) {
                val cropActivityInfo = packageManager.resolveActivity(intent, 0)?.activityInfo
                if (cropActivityInfo != null) {
                    Log.d("OCR", "Granting URI permission to crop activity: ${cropActivityInfo.packageName}")
                    grantUriPermission(
                        cropActivityInfo.packageName,
                        sourceUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } else {
                    Log.w("OCR", "Could not resolve crop activity for URI permission grant")
                }
            } else {
                Log.d("OCR", "Not granting URI permission for gallery/content URI: $sourceUri")
            }
            Log.d("OCR", "Starting crop activity with intent: $intent, URI: $sourceUri, flags: ${intent.flags}")
            cropLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("OCR", "Error launching crop activity", e)
            Toast.makeText(this, "Error launching crop: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fallback: try to load image directly
            try {
                loadImageFromUri(sourceUri)
            } catch (fallbackException: Exception) {
                Log.e("OCR", "Fallback also failed", fallbackException)
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
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
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OCR", "Error loading image from URI: $uri", e)
        }
    }

    private fun preprocessImageForOCR(originalBitmap: Bitmap): Bitmap {
        // Resize if too large (MLKit works better with reasonable sizes)
        val maxSize = 1024
        val width = originalBitmap.width
        val height = originalBitmap.height
        
        val resizedBitmap = if (width > maxSize || height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(width, height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } else {
            originalBitmap
        }
        
        // Enhance image for better OCR, especially for monitor captures
        return enhanceImageForOCR(resizedBitmap)
    }

    private fun enhanceImageForOCR(bitmap: Bitmap): Bitmap {
        try {
            // Create a mutable copy of the bitmap
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            // Get pixel data
            val pixels = IntArray(mutableBitmap.width * mutableBitmap.height)
            mutableBitmap.getPixels(pixels, 0, mutableBitmap.width, 0, 0, mutableBitmap.width, mutableBitmap.height)
            
            // Calculate average brightness to determine if image is too dark
            var totalBrightness = 0
            pixels.forEach { pixel ->
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                totalBrightness += (red + green + blue) / 3
            }
            val avgBrightness = totalBrightness / pixels.size
            
            // Determine enhancement parameters based on average brightness
            val brightnessAdjustment = when {
                avgBrightness < 100 -> 1.3f  // Dark image - increase brightness more
                avgBrightness < 150 -> 1.15f // Medium image - moderate increase
                else -> 1.05f                // Bright image - slight increase
            }
            
            val contrastAdjustment = 1.3f // Increase contrast for better text recognition
            
            // Enhance brightness and contrast
            val enhancedPixels = pixels.map { pixel ->
                val alpha = (pixel shr 24) and 0xFF
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                
                // Convert to grayscale for better OCR
                val gray = (red * 0.299 + green * 0.587 + blue * 0.114).toInt()
                
                // Apply brightness and contrast enhancement
                val adjustedGray = ((gray - 128) * contrastAdjustment + 128).toInt()
                val enhancedGray = (adjustedGray * brightnessAdjustment).toInt().coerceIn(0, 255)
                
                // For monitor captures, maintain some color information but enhance contrast
                val enhancedRed = (red * 0.7 + enhancedGray * 0.3).toInt().coerceIn(0, 255)
                val enhancedGreen = (green * 0.7 + enhancedGray * 0.3).toInt().coerceIn(0, 255)
                val enhancedBlue = (blue * 0.7 + enhancedGray * 0.3).toInt().coerceIn(0, 255)
                
                (alpha shl 24) or (enhancedRed shl 16) or (enhancedGreen shl 8) or enhancedBlue
            }.toIntArray()
            
            mutableBitmap.setPixels(enhancedPixels, 0, mutableBitmap.width, 0, 0, mutableBitmap.width, mutableBitmap.height)
            
            Log.d("OCR", "Image enhanced - Original avg brightness: $avgBrightness, Brightness adjustment: $brightnessAdjustment")
            
            return mutableBitmap
        } catch (e: Exception) {
            Log.e("OCR", "Error enhancing image: ${e.message}", e)
            return bitmap // Return original if enhancement fails
        }
    }

    private fun testMLKit() {
        Log.d("OCR", "Testing MLKit initialization...")
        try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Log.d("OCR", "MLKit TextRecognizer created successfully")
            Toast.makeText(this, "MLKit initialized successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("OCR", "MLKit initialization failed: ${e.message}", e)
            Toast.makeText(this, "MLKit initialization failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        try {
            Log.d("OCR", "Opening gallery...")
            
            // Try multiple gallery intents for better compatibility
            val galleryIntents = listOf(
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" },
                Intent(Intent.ACTION_PICK).apply { type = "image/*" }
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
        } catch (e: Exception) {
            Log.e("OCR", "Error opening gallery", e)
            Toast.makeText(this, "Error opening gallery: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processOCR() {
        currentBitmap?.let { bitmap ->
            Log.d("OCR", "Starting OCR processing...")
            Log.d("OCR", "Bitmap size: ${bitmap.width}x${bitmap.height}")
            Log.d("OCR", "Bitmap config: ${bitmap.config}")
            
            runOnUiThread {
                setWebViewContent(aiResponseWebView, "Processing OCR...")
                Log.d("OCR", "Set UI to 'Processing OCR...'")
            }
            
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                Log.d("OCR", "InputImage created successfully")
                
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                Log.d("OCR", "TextRecognizer created successfully")
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val extractedText = visionText.text
                        Log.d("OCR", "OCR completed successfully")
                        Log.d("OCR", "Extracted text length: ${extractedText.length}")
                        Log.d("OCR", "Extracted text: '$extractedText'")
                        
                        runOnUiThread {
                            if (extractedText.isNotEmpty()) {
                                Log.d("OCR", "Updating UI with extracted text")
                                Log.d("OCR", "Text to set: '$extractedText'")
                                
                                // Build the full prompt (matching original Python app)
                                val rules = """
Please answer the following question in English. Provide a compact step-by-step solution or reasoning, but for the final answer, use the format 'a = <answer>' (e.g., a = 5). Do not use verbose explanations, boxed math, or LaTeX for the answer. Only provide the answer in the specified format at the end.
After giving the answer, if possible, provide the Python code that solves the problem, formatted as a code block.
""".trimIndent()
                                
                                currentPrompt = "$rules\n\n```\n${extractedText.trim()}\n```"
                                promptEditText.setText(currentPrompt)
                                updateButtonStates()
                                
                                Log.d("OCR", "UI updated with full prompt")
                                Log.d("OCR", "Current prompt: '$currentPrompt'")
                            } else {
                                Log.w("OCR", "No text found in image")
                                promptEditText.setText("No text found in the image. Try:\n- Using a clearer image\n- Ensuring text is well-lit\n- Checking text orientation")
                                updateButtonStates()
                                Log.d("OCR", "Set 'no text found' message")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("OCR", "OCR failed: ${e.message}", e)
                        runOnUiThread {
                            setWebViewContent(aiResponseWebView, "OCR failed: ${e.message}\n\nPossible issues:\n- Check internet connection\n- Try a different image\n- Ensure image has clear text")
                            updateButtonStates()
                        }
                    }
            } catch (e: Exception) {
                Log.e("OCR", "Error creating InputImage: ${e.message}", e)
                runOnUiThread {
                    setWebViewContent(aiResponseWebView, "Error processing image: ${e.message}")
                    updateButtonStates()
                }
            }
        } ?: run {
            Log.w("OCR", "No bitmap available for OCR")
            runOnUiThread {
                Toast.makeText(this, "No image to process", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendToAI() {
        // Check if API key is available
        val activeKeys = apiKeyManager.getActiveApiKeys()
        if (activeKeys.isEmpty()) {
            Toast.makeText(this, "API key required. Please configure your Groq API key first.", Toast.LENGTH_LONG).show()
            showApiKeyDialog()
            return
        }
        
        // Get the current prompt from the text field (user may have edited it)
        val promptFromUI = promptEditText.text.toString()
        if (promptFromUI.isNotEmpty() && !promptFromUI.startsWith("No text found") && !promptFromUI.startsWith("Processing OCR...")) {
            Log.d("OCR", "Sending prompt to AI: '$promptFromUI'")
            
            val finalPrompt = if (isOCRMode) {
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
            val modelNames = (modelManager.getSelectedModels().map { it.name } + "All Models").toTypedArray()
            
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
                    Log.d("OCR", "Added user and AI messages to conversation history. Total messages: ${conversationHistory.size}")
                    updateConversationStatus()
                }
                
                withContext(Dispatchers.Main) {
                    Log.d("OCR", "Setting AI response in UI: '$response'")
                    setWebViewContent(aiResponseWebView, "AI Response:\n$response")
                    Log.d("OCR", "AI response updated in UI")
                    Log.d("OCR", "Current AI response text: 'AI Response updated'")
                }
            } catch (e: Exception) {
                Log.e("OCR", "AI processing failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    setWebViewContent(aiResponseWebView, "AI processing failed: ${e.message}")
                }
            }
        }
    }

    private fun processWithAllModels(prompt: String) {
        Log.d("OCR", "Starting processing with all models: '$prompt'")
        isProcessingAllModels = true
        
        // Clear previous responses
        modelResponses.clear()
        
        runOnUiThread {
            val selectedModels = modelManager.getSelectedModels()
            setWebViewContent(aiResponseWebView, "Processing with all models...\n\nPlease wait while we get responses from all models.\n\nModels being processed:\n${selectedModels.joinToString("\n") { "• ${it.name}" }}")
            Log.d("OCR", "Set AI UI to 'Processing with all models...'")
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jobs = mutableListOf<Job>()
                
                // Send requests to all selected models concurrently
                val selectedModels = modelManager.getSelectedModels()
                selectedModels.forEach { model ->
                    val job = launch {
                        try {
                            val response = sendToGroqAPIWithModel(prompt, model.id)
                            modelResponses[model.name] = response
                            Log.d("OCR", "Response from ${model.name}: $response")
                            
                            // Update progress on UI
                            withContext(Dispatchers.Main) {
                                updateAllModelsProgress()
                            }
                        } catch (e: Exception) {
                            modelResponses[model.name] = "Error: ${e.message}"
                            Log.e("OCR", "Error getting response from ${model.name}: ${e.message}", e)
                            
                            // Update progress on UI even for errors
                            withContext(Dispatchers.Main) {
                                updateAllModelsProgress()
                            }
                        }
                    }
                    jobs.add(job)
                }
                
                // Wait for all responses
                jobs.joinAll()
                
                // Add conversation history for all models mode
                if (isConversationMode) {
                    conversationHistory.add(ConversationMessage("user", prompt))
                    // For all models, we'll add a combined response
                    val combinedResponse = modelResponses.values.joinToString("\n\n---\n\n") { it }
                    conversationHistory.add(ConversationMessage("assistant", combinedResponse))
                    Log.d("OCR", "Added all models conversation to history. Total messages: ${conversationHistory.size}")
                    updateConversationStatus()
                }
                
                withContext(Dispatchers.Main) {
                    isProcessingAllModels = false
                    displayAllModelsResponse()
                    Log.d("OCR", "All models processing completed")
                }
            } catch (e: Exception) {
                Log.e("OCR", "All models processing failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isProcessingAllModels = false
                    setWebViewContent(aiResponseWebView, "All models processing failed: ${e.message}")
                }
            }
        }
    }

    private fun updateAllModelsProgress() {
        if (!isProcessingAllModels) return
        
        val selectedModels = modelManager.getSelectedModels()
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
        
        val selectedModels = modelManager.getSelectedModels()
        selectedModels.forEach { model ->
            val response = modelResponses[model.name] ?: "No response received"
            responseBuilder.append("### ${model.name}\n")
            responseBuilder.append("${response}\n\n")
            responseBuilder.append("---\n\n")
        }
        
        Log.d("OCR", "Displaying all models response with ${selectedModels.size} models")
        setWebViewContent(aiResponseWebView, responseBuilder.toString())
    }

    private fun cleanExtractedText(text: String): String {
        // Remove common UI prefixes and suffixes that might be added
        var cleaned = text.trim()
        
        // Remove "OCR Result:" prefix if present
        if (cleaned.startsWith("OCR Result:", ignoreCase = true)) {
            cleaned = cleaned.substringAfter("OCR Result:").trim()
        }
        
        // Remove "Processing OCR..." if present
        if (cleaned.startsWith("Processing OCR...", ignoreCase = true)) {
            cleaned = cleaned.substringAfter("Processing OCR...").trim()
        }
        
        // Remove any leading/trailing whitespace and newlines
        cleaned = cleaned.trim()
        
        Log.d("OCR", "Text cleaning: '$text' -> '$cleaned'")
        return cleaned
    }

    private suspend fun sendToGroqAPI(prompt: String): String {
        return withContext(Dispatchers.IO) {
            // Get selected model
            val selectedModels = modelManager.getSelectedModels()
            val selectedModel = if (selectedModels.isNotEmpty()) selectedModels.first().id else "gemma2-9b-it"
            
            Log.d("OCR", "Using AI model: $selectedModel")
            Log.d("OCR", "Full prompt being sent to AI: '$prompt'")
            
            return@withContext sendToGroqAPIWithModel(prompt, selectedModel)
        }
    }

    private suspend fun sendToGroqAPIWithModel(prompt: String, model: String): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            
            Log.d("OCR", "Using AI model: $model")
            Log.d("OCR", "Full prompt being sent to AI: '$prompt'")
            
            // Build messages array with conversation context
            val messagesArray = JSONArray()
            
            // Add system message to ensure English responses unless specifically requested otherwise
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", "You are a helpful AI assistant. Please respond in English by default. Only respond in Arabic or other languages if the user explicitly asks you to do so or if the question is specifically about Arabic language/culture. For all other queries, provide clear and helpful responses in English.")
            })
            
            if (isConversationMode && conversationHistory.isNotEmpty()) {
                // Add conversation history to provide context
                conversationHistory.forEach { message ->
                    messagesArray.put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
                // Add the current user message
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
                Log.d("OCR", "Including conversation history: ${conversationHistory.size} messages + current message")
            } else {
                // Single message mode (original behavior)
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            
            val jsonBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                // Use different parameters based on model (matching original Python app)
                when (model) {
                    "llama-3.3-70b-versatile" -> {
                        put("temperature", 1.0)
                        put("max_tokens", 1024)
                        put("top_p", 1.0)
                    }
                    "mistral-saba-24b" -> {
                        put("temperature", 1.0)
                        put("max_tokens", 1024)
                        put("top_p", 1.0)
                    }
                    else -> {
                        put("temperature", 0.3)
                        put("max_tokens", 4096)
                        put("top_p", 0.95)
                    }
                }
            }.toString()

            val apiKey = apiKeyManager.getNextApiKey()
            if (apiKey == null) {
                return@withContext "Error: No active API keys available"
            }
            
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "No response"
                
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        return@withContext message.getString("content")
                    }
                }
                return@withContext "Error: ${response.code} - $responseBody"
            } catch (e: IOException) {
                return@withContext "Network error: ${e.message}"
            }
        }
    }

    private fun setupModelSpinner() {
        val selectedModels = modelManager.getSelectedModels()
        val modelNames = (selectedModels.map { it.name } + "All Models" + "Manage Models").toTypedArray()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
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
                        // If we have responses from all models and user selects a specific model, show that response
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

    private fun copyToClipboard(text: String, label: String) {
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
                    scrollView?.smoothScrollTo(0, promptEditText.top - 100)
                }, 300)
            }
        }
    }

    private fun setupEditTextListener() {
        promptEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateButtonStates()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
            Toast.makeText(this, "Conversation mode active with ${conversationHistory.size} messages", Toast.LENGTH_LONG).show()
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
    
    private fun getConversationSummary(): String {
        if (conversationHistory.isEmpty()) {
            return "No conversation history"
        }
        
        val summary = StringBuilder()
        summary.append("## Conversation History (${conversationHistory.size} messages)\n\n")
        
        conversationHistory.forEachIndexed { index, message ->
            val role = if (message.role == "user") "👤 You" else "🤖 AI"
            val timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            summary.append("**$role** ($timestamp):\n")
            summary.append("${message.content}\n\n")
        }
        
        return summary.toString()
    }
    
    private fun showConversationHistory() {
        val history = getConversationSummary()
        setWebViewContent(aiResponseWebView, history)
        Toast.makeText(this, "Showing conversation history", Toast.LENGTH_SHORT).show()
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
                promptEditText.text.toString().trim().isNotEmpty()
            reviewImageButton.isEnabled = false
        }
        
        Log.d("OCR", "Button states updated - OCR Mode: $isOCRMode, Process: ${processButton.isEnabled}, Send: ${sendToAIButton.isEnabled}")
    }

    private fun canEditTextScrollVertically(editText: EditText): Boolean {
        val scrollY = editText.scrollY
        val scrollRange = editText.layout?.height ?: 0 - (editText.height - editText.compoundPaddingTop - editText.compoundPaddingBottom)
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

    companion object {
        private const val TAG = "OCRGPT"
    }
} 