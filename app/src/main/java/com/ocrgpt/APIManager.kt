package com.ocrgpt

import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class APIManager {
    private val client = OkHttpClient()

    companion object {
        private const val MAX_TOKENS_LLAMA = 4096
        private const val MAX_TOKENS_MISTRAL = 4096
        private const val MAX_TOKENS_DEFAULT = 2048
        private const val TEMPERATURE_HIGH = 1.0
        private const val TEMPERATURE_DEFAULT = 0.7
        private const val TOP_P_HIGH = 1.0
        private const val TOP_P_DEFAULT = 0.9
    }

    suspend fun sendToGroqAPIWithModel(
        prompt: String,
        model: String,
        apiKey: String,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                Log.d("OCR", "Using AI model: $model")
                Log.d("OCR", "Full prompt being sent to AI: '$prompt'")

                val messagesArray = buildMessagesArray(prompt)
                val jsonBody = buildRequestJson(model, messagesArray)
                val request = buildGroqRequest(apiKey, jsonBody)
                executeGroqRequest(request)
            } catch (e: IOException) {
                Log.e("OCR", "Network error", e)
                "Network error: ${e.message}"
            } catch (e: JSONException) {
                Log.e("OCR", "JSON parsing error", e)
                "JSON parsing error: ${e.message}"
            } catch (e: IllegalArgumentException) {
                Log.e("OCR", "Argument error", e)
                "Argument error: ${e.message}"
            } catch (e: IllegalStateException) {
                Log.e("OCR", "State error", e)
                "State error: ${e.message}"
            }
        }

    private fun buildMessagesArray(prompt: String): JSONArray {
        val messagesArray = JSONArray()
        val userMessage =
            JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }
        messagesArray.put(userMessage)
        return messagesArray
    }

    private fun buildRequestJson(model: String, messagesArray: JSONArray): String =
        JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            addModelParameters(this, model)
        }.toString()

    private fun addModelParameters(jsonObject: JSONObject, model: String) {
        when (model) {
            "llama-3.3-70b-versatile" -> {
                jsonObject.put("temperature", TEMPERATURE_HIGH)
                jsonObject.put("max_tokens", MAX_TOKENS_LLAMA)
                jsonObject.put("top_p", TOP_P_HIGH)
            }
            "mistral-saba-24b" -> {
                jsonObject.put("temperature", TEMPERATURE_HIGH)
                jsonObject.put("max_tokens", MAX_TOKENS_MISTRAL)
                jsonObject.put("top_p", TOP_P_HIGH)
            }
            else -> {
                jsonObject.put("temperature", TEMPERATURE_DEFAULT)
                jsonObject.put("max_tokens", MAX_TOKENS_DEFAULT)
                jsonObject.put("top_p", TOP_P_DEFAULT)
            }
        }
    }

    private fun buildGroqRequest(
        apiKey: String,
        jsonBody: String,
    ): Request {
        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        return Request
            .Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun executeGroqRequest(request: Request): String {
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                parseGroqResponse(responseBody)
            } else {
                "API Error: ${response.code} - ${response.message}"
            }
        } catch (e: SocketTimeoutException) {
            Log.e("OCR", "Request timeout", e)
            "Request timeout. Please check your internet connection."
        } catch (e: UnknownHostException) {
            Log.e("OCR", "Network error", e)
            "Network error. Please check your internet connection."
        } catch (e: SSLException) {
            Log.e("OCR", "SSL error", e)
            "SSL error. Please check your network security settings."
        } catch (e: IOException) {
            Log.e("OCR", "IO error", e)
            "Network error: ${e.message}"
        }
    }

    private fun parseGroqResponse(responseBody: String): String =
        try {
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.getString("content")
            } else {
                "No response from AI"
            }
        } catch (e: JSONException) {
            Log.e("OCR", "Error parsing JSON response", e)
            "Error parsing AI response"
        }
}
