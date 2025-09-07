package com.ocrgpt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class APIHandler {
    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 60L
        private const val DEFAULT_MAX_TOKENS = 4096
        private const val DEFAULT_TEMPERATURE = 0.7
        private const val TEST_MAX_TOKENS = 10
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun sendToGroqAPI(
        text: String,
        apiKey: String,
        model: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                val json = createRequestJson(text, model, maxTokens)
                val request = buildRequest(apiKey, json)
                val response = client.newCall(request).execute()
                processResponse(response)
            } catch (e: SocketTimeoutException) {
                Log.e("API", "Timeout error: ${e.message}")
                "Request timeout. Please try again."
            } catch (e: UnknownHostException) {
                Log.e("API", "Network error: ${e.message}")
                "Network error. Please check your internet connection."
            } catch (e: SSLException) {
                Log.e("API", "SSL error: ${e.message}")
                "Connection error. Please try again."
            } catch (e: IOException) {
                Log.e("API", "IO error: ${e.message}")
                "Network error. Please check your connection."
            } catch (e: IllegalStateException) {
                Log.e("API", "Illegal state error: ${e.message}")
                "Illegal state error: ${e.message}"
            }
        }

    private fun createRequestJson(
        text: String,
        model: String,
        maxTokens: Int,
    ): JSONObject =
        JSONObject().apply {
            put(
                "messages",
                listOf(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    },
                ),
            )
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", DEFAULT_TEMPERATURE)
        }

    private fun buildRequest(
        apiKey: String,
        json: JSONObject,
    ): Request {
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        return Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
    }

    private fun processResponse(response: okhttp3.Response): String {
        val responseBody = response.body?.string() ?: ""

        return if (response.isSuccessful) {
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                message.getString("content")
            } else {
                "No response generated"
            }
        } else {
            Log.e("API", "API Error: ${response.code} - $responseBody")
            "API Error: ${response.code} - ${response.message}"
        }
    }

    suspend fun testAPIKey(
        apiKey: String,
    ): Boolean = withContext(Dispatchers.IO) {
            try {
                val response = sendToGroqAPI("Test", apiKey, "llama-3.1-8b-instant", TEST_MAX_TOKENS)
                response.isNotEmpty() && !response.startsWith("API Error") && !response.startsWith("Error")
            } catch (e: IllegalStateException) {
                Log.e("API", "Illegal state error testing API key: ${e.message}")
                false
            }
        }
}
