package com.ocrgpt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class ModelInfo(
    val id: String,
    val name: String,
    val isSelected: Boolean = true,
    val isAvailable: Boolean = true,
    val category: String = "text",
)

class ModelManager(
    private val context: Context,
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)

    private val availableModels = mutableListOf<ModelInfo>()
    private val selectedModels = mutableSetOf<String>()

    companion object {
        private const val TAG = "ModelManager"
        private const val SELECTED_MODELS_KEY = "selected_models"

        // Default models as fallback
        private val DEFAULT_MODELS =
            listOf(
                ModelInfo("gemma2-9b-it", "Gemma 2 9B IT", true, true, "text"),
                ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", true, true, "text"),
            )
    }

    init {
        loadSelectedModels()
    }

    private fun loadSelectedModels() {
        val selectedModelsJson = sharedPreferences.getString(SELECTED_MODELS_KEY, "")
        if (!selectedModelsJson.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(selectedModelsJson)
                for (i in 0 until jsonArray.length()) {
                    selectedModels.add(jsonArray.getString(i))
                }
            } catch (e: JSONException) {
                Log.e(TAG, "JSON error loading selected models", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Illegal state error loading selected models", e)
            }
        }
        Log.d(TAG, "Loaded ${selectedModels.size} selected models")
    }

    private fun saveSelectedModels() {
        val jsonArray = JSONArray()
        selectedModels.forEach { modelId ->
            jsonArray.put(modelId)
        }
        sharedPreferences
            .edit()
            .putString(SELECTED_MODELS_KEY, jsonArray.toString())
            .apply()
        Log.d(TAG, "Saved ${selectedModels.size} selected models")
    }

    suspend fun fetchAvailableModels(apiKey: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request =
                Request
                    .Builder()
                    .url("https://api.groq.com/openai/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val dataArray = jsonResponse.getJSONArray("data")
                    val models = mutableListOf<ModelInfo>()

                    for (i in 0 until dataArray.length()) {
                        val model = dataArray.getJSONObject(i)
                        val modelId = model.getString("id")

                        // Filter for text-based models
                        if (isTextBasedModel(modelId)) {
                            val displayName = generateDisplayName(modelId)
                            val isSelected = selectedModels.contains(modelId) || selectedModels.isEmpty()
                            models.add(ModelInfo(modelId, displayName, isSelected, true, "text"))
                        }
                    }

                    // Sort models by name for consistent ordering
                    models.sortBy { it.name }
                    availableModels.clear()
                    availableModels.addAll(models)

                    // Update selected models if this is the first fetch
                    if (selectedModels.isEmpty() && models.isNotEmpty()) {
                        models.forEach { model ->
                            selectedModels.add(model.id)
                        }
                        saveSelectedModels()
                    }

                    Log.d(TAG, "Fetched ${models.size} models from API")
                    models
                } else {
                    Log.e(TAG, "Failed to fetch models: ${response.code} - $responseBody")
                    useDefaultModels()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error fetching models: ${e.message}", e)
                useDefaultModels()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Illegal state error fetching models: ${e.message}", e)
                useDefaultModels()
            }
        }

    private fun isTextBasedModel(modelId: String): Boolean {
        val excludedPatterns =
            listOf(
                "image",
                "vision",
                "tts",
                "audio",
                "whisper",
                "embedding",
                "dall-e",
            )

        return !excludedPatterns.any { pattern ->
            modelId.lowercase().contains(pattern)
        }
    }

    private fun generateDisplayName(modelId: String): String =
        when {
            modelId.contains("gemma") -> {
                val version = modelId.substringAfter("gemma").substringBefore("-")
                val size = modelId.substringAfter("-").substringBefore("-")
                "Gemma $version $size"
            }
            modelId.contains("llama") -> {
                val version = modelId.substringAfter("llama").substringBefore("-")
                val size = modelId.substringAfter("-").substringBefore("-")
                "Llama $version $size"
            }
            modelId.contains("mistral") -> {
                val variant = modelId.substringAfter("mistral-")
                "Mistral $variant"
            }
            modelId.contains("mixtral") -> {
                val variant = modelId.substringAfter("mixtral-")
                "Mixtral $variant"
            }
            else -> {
                // Capitalize and format the model name
                modelId.split("-").joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }

    private fun useDefaultModels(): List<ModelInfo> {
        availableModels.clear()
        availableModels.addAll(DEFAULT_MODELS)

        // Update selected models if empty
        if (selectedModels.isEmpty()) {
            DEFAULT_MODELS.forEach { model ->
                selectedModels.add(model.id)
            }
            saveSelectedModels()
        }

        Log.d(TAG, "Using default models: ${DEFAULT_MODELS.size}")
        return DEFAULT_MODELS
    }

    fun getModels(selectedOnly: Boolean = false): List<ModelInfo> = 
        if (selectedOnly) {
            availableModels.filter { selectedModels.contains(it.id) }
        } else {
            availableModels.toList()
        }

    fun getSelectedModelIds(): List<String> = selectedModels.toList()

    fun setModelSelection(modelId: String? = null, select: Boolean? = null): Boolean {
        return if (modelId != null) {
            // Toggle single model
            if (selectedModels.contains(modelId)) {
                selectedModels.remove(modelId)
                saveSelectedModels()
                Log.d(TAG, "Deselected model: $modelId")
                false
            } else {
                selectedModels.add(modelId)
                saveSelectedModels()
                Log.d(TAG, "Selected model: $modelId")
                true
            }
        } else {
            // Set all models
            if (select == true) {
                availableModels.forEach { model ->
                    selectedModels.add(model.id)
                }
                Log.d(TAG, "Selected all models")
            } else {
                selectedModels.clear()
                Log.d(TAG, "Deselected all models")
            }
            saveSelectedModels()
            select ?: false
        }
    }

    fun getModelInfo(modelId: String? = null): Any = 
        if (modelId != null) {
            selectedModels.contains(modelId)
        } else {
            Pair(availableModels.size, selectedModels.size)
        }
}
