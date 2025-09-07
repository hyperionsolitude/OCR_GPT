package com.ocrgpt

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ConversationEntry(
    val timestamp: Long,
    val prompt: String,
    val response: String,
    val model: String
)

class ConversationManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("conversation_history", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_KEY_CONVERSATIONS = "conversations"
        private const val MAX_CONVERSATIONS = 100
    }
    
    suspend fun addConversation(prompt: String, response: String, model: String): Unit = withContext(Dispatchers.IO) {
        try {
            val conversations = getConversations().toMutableList()
            val newEntry = ConversationEntry(
                timestamp = System.currentTimeMillis(),
                prompt = prompt,
                response = response,
                model = model
            )
            
            conversations.add(newEntry)
            
            // Keep only the last MAX_CONVERSATIONS entries
            if (conversations.size > MAX_CONVERSATIONS) {
                conversations.removeAt(0)
            }
            
            saveConversations(conversations)
        } catch (e: IllegalStateException) {
            Log.e("ConversationManager", "Illegal state error adding conversation: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.e("ConversationManager", "Invalid argument error adding conversation: ${e.message}")
        }
    }
    
    fun getConversations(): List<ConversationEntry> {
        return try {
            val jsonString = prefs.getString(PREFS_KEY_CONVERSATIONS, "[]") ?: "[]"
            val jsonArray = JSONArray(jsonString)
            val conversations = mutableListOf<ConversationEntry>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                conversations.add(
                    ConversationEntry(
                        timestamp = jsonObject.getLong("timestamp"),
                        prompt = jsonObject.getString("prompt"),
                        response = jsonObject.getString("response"),
                        model = jsonObject.getString("model")
                    )
                )
            }
            
            conversations
        } catch (e: IllegalStateException) {
            Log.e("ConversationManager", "Illegal state error loading conversations: ${e.message}")
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e("ConversationManager", "Invalid argument error loading conversations: ${e.message}")
            emptyList()
        }
    }
    
    private fun saveConversations(conversations: List<ConversationEntry>) {
        try {
            val jsonArray = JSONArray()
            for (conversation in conversations) {
                val jsonObject = JSONObject().apply {
                    put("timestamp", conversation.timestamp)
                    put("prompt", conversation.prompt)
                    put("response", conversation.response)
                    put("model", conversation.model)
                }
                jsonArray.put(jsonObject)
            }
            
            prefs.edit().putString(PREFS_KEY_CONVERSATIONS, jsonArray.toString()).apply()
        } catch (e: IllegalStateException) {
            Log.e("ConversationManager", "Illegal state error saving conversations: ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.e("ConversationManager", "Invalid argument error saving conversations: ${e.message}")
        }
    }
    
    fun clearConversations() {
        prefs.edit().remove(PREFS_KEY_CONVERSATIONS).apply()
    }
    
    fun getConversationCount(): Int = getConversations().size
    
    fun getLastConversation(): ConversationEntry? = getConversations().lastOrNull()
    
    fun getConversationsByModel(model: String): List<ConversationEntry> = 
        getConversations().filter { it.model == model }
    
    fun exportConversations(): String {
        return try {
            val conversations = getConversations()
            val jsonArray = JSONArray()
            for (conversation in conversations) {
                val jsonObject = JSONObject().apply {
                    put("timestamp", conversation.timestamp)
                    put("prompt", conversation.prompt)
                    put("response", conversation.response)
                    put("model", conversation.model)
                }
                jsonArray.put(jsonObject)
            }
            jsonArray.toString(2)
        } catch (e: IllegalStateException) {
            Log.e("ConversationManager", "Illegal state error exporting conversations: ${e.message}")
            "[]"
        } catch (e: IllegalArgumentException) {
            Log.e("ConversationManager", "Invalid argument error exporting conversations: ${e.message}")
            "[]"
        }
    }
}
