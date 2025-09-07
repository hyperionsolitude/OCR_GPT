package com.ocrgpt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_KEY_API_KEY = "groq_api_key"
        private const val PREFS_KEY_SELECTED_MODELS = "selected_models"
        private const val PREFS_KEY_AUTO_COPY = "auto_copy"
        private const val PREFS_KEY_SHOW_TIMESTAMPS = "show_timestamps"
        private const val PREFS_KEY_DEFAULT_MODEL = "default_model"
        private const val DEFAULT_MODEL = "llama-3.1-8b-instant"
        private const val SETTINGS_API_KEY_INDEX = 0
        private const val SETTINGS_MODEL_INDEX = 1
        private const val SETTINGS_DISPLAY_INDEX = 2
        private const val SETTINGS_ABOUT_INDEX = 3
    }
    
    fun handleApiKey(action: String, value: String? = null): String? = when (action) {
        "get" -> prefs.getString(PREFS_KEY_API_KEY, "") ?: ""
        "save" -> {
            prefs.edit().putString(PREFS_KEY_API_KEY, value).apply()
            null
        }
        else -> null
    }
    
    fun handleSelectedModels(action: String, models: Set<String>? = null): Set<String>? = when (action) {
        "get" -> prefs.getStringSet(PREFS_KEY_SELECTED_MODELS, emptySet()) ?: emptySet()
        "save" -> {
            prefs.edit().putStringSet(PREFS_KEY_SELECTED_MODELS, models).apply()
            null
        }
        else -> null
    }
    
    fun handleAutoCopy(action: String, enabled: Boolean? = null): Boolean? = when (action) {
        "get" -> prefs.getBoolean(PREFS_KEY_AUTO_COPY, false)
        "set" -> {
            prefs.edit().putBoolean(PREFS_KEY_AUTO_COPY, enabled ?: false).apply()
            null
        }
        else -> null
    }
    
    fun handleTimestamps(action: String, enabled: Boolean? = null): Boolean? = when (action) {
        "get" -> prefs.getBoolean(PREFS_KEY_SHOW_TIMESTAMPS, true)
        "set" -> {
            prefs.edit().putBoolean(PREFS_KEY_SHOW_TIMESTAMPS, enabled ?: true).apply()
            null
        }
        else -> null
    }
    
    fun handleDefaultModel(action: String, model: String? = null): String? = when (action) {
        "get" -> prefs.getString(PREFS_KEY_DEFAULT_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        "set" -> {
            prefs.edit().putString(PREFS_KEY_DEFAULT_MODEL, model).apply()
            null
        }
        else -> null
    }
    
    suspend fun showSettingsDialog(): Unit = withContext(Dispatchers.Main) {
        try {
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Settings")
            
            val settings = arrayOf(
                "API Key Management",
                "Model Selection", 
                "Display Options",
                "About"
            )
            
            builder.setItems(settings) { _, which ->
                when (which) {
                    SETTINGS_API_KEY_INDEX -> showApiKeySettings()
                    SETTINGS_MODEL_INDEX -> showModelSettings()
                    SETTINGS_DISPLAY_INDEX -> showDisplaySettings()
                    SETTINGS_ABOUT_INDEX -> showAboutDialog()
                }
            }
            
            builder.setNegativeButton("Cancel", null)
            builder.show()
        } catch (e: IllegalStateException) {
            Log.e("SettingsManager", "Illegal state error showing settings dialog: ${e.message}")
            Toast.makeText(context, "Error opening settings", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Log.e("SettingsManager", "Invalid argument error showing settings dialog: ${e.message}")
            Toast.makeText(context, "Error opening settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showApiKeySettings() {
        // Implementation for API key settings
        Toast.makeText(context, "API Key settings", Toast.LENGTH_SHORT).show()
    }
    
    private fun showModelSettings() {
        // Implementation for model settings
        Toast.makeText(context, "Model settings", Toast.LENGTH_SHORT).show()
    }
    
    private fun showDisplaySettings() {
        // Implementation for display settings
        Toast.makeText(context, "Display settings", Toast.LENGTH_SHORT).show()
    }
    
    private fun showAboutDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("About OCR GPT")
        builder.setMessage("Version 1.0\n\nAn AI-powered OCR application with Groq integration.")
        builder.setPositiveButton("OK", null)
        builder.show()
    }
}
