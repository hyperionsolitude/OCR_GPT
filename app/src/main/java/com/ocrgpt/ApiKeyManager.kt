package com.ocrgpt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

data class ApiKeyInfo(
    val key: String,
    val name: String,
    val isActive: Boolean = true,
    val usageCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis()
)

class ApiKeyManager(private val context: Context) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("api_keys_prefs", Context.MODE_PRIVATE)
    
    private val apiKeys = mutableListOf<ApiKeyInfo>()
    private val currentKeyIndex = AtomicInteger(0)
    private val mutex = Mutex()
    
    companion object {
        private const val TAG = "ApiKeyManager"
        private const val MAX_KEYS = 5
        private const val KEY_PREFIX = "api_key_"
        private const val KEY_COUNT = "key_count"
    }
    
    init {
        loadApiKeys()
    }
    
    private fun loadApiKeys() {
        val keyCount = sharedPreferences.getInt(KEY_COUNT, 0)
        apiKeys.clear()
        
        for (i in 0 until keyCount) {
            val key = sharedPreferences.getString("${KEY_PREFIX}${i}_key", null) ?: continue
            val name = sharedPreferences.getString("${KEY_PREFIX}${i}_name", "API Key ${i + 1}") ?: "API Key ${i + 1}"
            val isActive = sharedPreferences.getBoolean("${KEY_PREFIX}${i}_active", true)
            val usageCount = sharedPreferences.getInt("${KEY_PREFIX}${i}_usage", 0)
            val lastUsed = sharedPreferences.getLong("${KEY_PREFIX}${i}_last_used", System.currentTimeMillis())
            
            apiKeys.add(ApiKeyInfo(key, name, isActive, usageCount, lastUsed))
        }
        
        Log.d(TAG, "Loaded ${apiKeys.size} API keys")
    }
    
    private fun saveApiKeys() {
        val editor = sharedPreferences.edit()
        editor.putInt(KEY_COUNT, apiKeys.size)
        
        apiKeys.forEachIndexed { index, apiKey ->
            editor.putString("${KEY_PREFIX}${index}_key", apiKey.key)
            editor.putString("${KEY_PREFIX}${index}_name", apiKey.name)
            editor.putBoolean("${KEY_PREFIX}${index}_active", apiKey.isActive)
            editor.putInt("${KEY_PREFIX}${index}_usage", apiKey.usageCount)
            editor.putLong("${KEY_PREFIX}${index}_last_used", apiKey.lastUsed)
        }
        
        editor.apply()
        Log.d(TAG, "Saved ${apiKeys.size} API keys")
    }
    
    suspend fun addApiKey(key: String, name: String): Boolean {
        return mutex.withLock {
            if (apiKeys.size >= MAX_KEYS) {
                Log.w(TAG, "Maximum number of API keys reached")
                return@withLock false
            }
            
            if (apiKeys.any { it.key == key }) {
                Log.w(TAG, "API key already exists")
                return@withLock false
            }
            
            val newApiKey = ApiKeyInfo(key, name)
            apiKeys.add(newApiKey)
            saveApiKeys()
            Log.d(TAG, "Added new API key: $name")
            true
        }
    }
    
    suspend fun removeApiKey(index: Int): Boolean {
        return mutex.withLock {
            if (index < 0 || index >= apiKeys.size) {
                return@withLock false
            }
            
            val removedKey = apiKeys.removeAt(index)
            saveApiKeys()
            Log.d(TAG, "Removed API key: ${removedKey.name}")
            true
        }
    }
    
    suspend fun updateApiKey(index: Int, name: String, isActive: Boolean): Boolean {
        return mutex.withLock {
            if (index < 0 || index >= apiKeys.size) {
                return@withLock false
            }
            
            val updatedKey = apiKeys[index].copy(name = name, isActive = isActive)
            apiKeys[index] = updatedKey
            saveApiKeys()
            Log.d(TAG, "Updated API key: $name")
            true
        }
    }
    
    suspend fun getNextApiKey(): String? {
        return mutex.withLock {
            val activeKeys = apiKeys.filter { it.isActive }
            if (activeKeys.isEmpty()) {
                Log.w(TAG, "No active API keys available")
                return@withLock null
            }
            
            // Round-robin selection
            val index = currentKeyIndex.getAndIncrement() % activeKeys.size
            val selectedKey = activeKeys[index]
            
            // Update usage statistics
            val updatedKey = selectedKey.copy(
                usageCount = selectedKey.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            )
            
            val originalIndex = apiKeys.indexOfFirst { it.key == selectedKey.key }
            if (originalIndex >= 0) {
                apiKeys[originalIndex] = updatedKey
                saveApiKeys()
            }
            
            Log.d(TAG, "Using API key: ${selectedKey.name} (usage: ${updatedKey.usageCount})")
            selectedKey.key
        }
    }
    
    fun getAllApiKeys(): List<ApiKeyInfo> = apiKeys.toList()
    
    fun getActiveApiKeys(): List<ApiKeyInfo> = apiKeys.filter { it.isActive }
    
    suspend fun markApiKeyAsFailed(key: String) {
        mutex.withLock {
            val index = apiKeys.indexOfFirst { it.key == key }
            if (index >= 0) {
                // Temporarily disable the key (could implement more sophisticated logic)
                val failedKey = apiKeys[index].copy(isActive = false)
                apiKeys[index] = failedKey
                saveApiKeys()
                Log.w(TAG, "Marked API key as failed: ${failedKey.name}")
            }
        }
    }
    
    suspend fun resetFailedKeys() {
        mutex.withLock {
            var hasChanges = false
            apiKeys.forEachIndexed { index, apiKey ->
                if (!apiKey.isActive) {
                    apiKeys[index] = apiKey.copy(isActive = true)
                    hasChanges = true
                }
            }
            if (hasChanges) {
                saveApiKeys()
                Log.d(TAG, "Reset all failed API keys")
            }
        }
    }
}
