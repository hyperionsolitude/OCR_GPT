package com.ocrgpt

import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiKeyDialogs(
    private val activity: AppCompatActivity,
    private val apiKeyManager: ApiKeyManager,
    private val modelManager: ModelManager,
    private val uiHelper: UIHelper,
) {
    companion object {
        private const val PREVIEW_START = 3
        private const val PREVIEW_END = 4
    }
    fun showManagementDialog() {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_api_key_management, null)
        val keysLayout = dialogView.findViewById<LinearLayout>(R.id.layout_api_keys)
        val addButton = dialogView.findViewById<Button>(R.id.btn_add_api_key)
        val resetButton = dialogView.findViewById<Button>(R.id.btn_reset_failed)
        val testButton = dialogView.findViewById<Button>(R.id.btn_test_keys)

        refreshApiKeyList(keysLayout)

        addButton.setOnClickListener { showAddApiKeyDialog() }
        resetButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                apiKeyManager.resetFailedKeys()
                withContext(Dispatchers.Main) {
                    refreshApiKeyList(keysLayout)
                    Toast.makeText(activity, "Reset all failed API keys", Toast.LENGTH_SHORT).show()
                }
            }
        }
        testButton.setOnClickListener { testAllApiKeys() }

        val dialog =
            AlertDialog
                .Builder(activity)
                .setView(dialogView)
                .setPositiveButton("Done") { _, _ ->
                    // Refresh model spinner in case API keys changed
                    CoroutineScope(Dispatchers.Main).launch {
                        // notify via toast; MainActivity should re-init models as needed
                        Toast.makeText(activity, "API key changes applied", Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton("Cancel", null)
                .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun refreshApiKeyList(layout: LinearLayout) {
        layout.removeAllViews()
        val apiKeys = apiKeyManager.getAllApiKeys()

        apiKeys.forEachIndexed { index, apiKey ->
            val keyView = LayoutInflater.from(activity).inflate(R.layout.item_api_key, null)
            val checkbox = keyView.findViewById<CheckBox>(R.id.checkbox_active)
            val nameText = keyView.findViewById<TextView>(R.id.tv_key_name)
            val previewText = keyView.findViewById<TextView>(R.id.tv_key_preview)
            val usageText = keyView.findViewById<TextView>(R.id.tv_usage_count)
            val lastUsedText = keyView.findViewById<TextView>(R.id.tv_last_used)
            val editButton = keyView.findViewById<Button>(R.id.btn_edit)
            val deleteButton = keyView.findViewById<Button>(R.id.btn_delete)

            checkbox.isChecked = apiKey.isActive
            nameText.text = apiKey.name
            previewText.text = "${apiKey.key.take(PREVIEW_START)}...${apiKey.key.takeLast(PREVIEW_END)}"
            usageText.text = "${apiKey.usageCount} uses"
            lastUsedText.text = uiHelper.formatValue(apiKey.lastUsed, UIHelper.FormatType.TIME)

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                CoroutineScope(Dispatchers.IO).launch { apiKeyManager.updateApiKey(index, apiKey.name, isChecked) }
            }
            editButton.setOnClickListener { showEditApiKeyDialog(index, apiKey) }
            deleteButton.setOnClickListener { showDeleteApiKeyDialog(index, apiKey.name) }

            layout.addView(keyView)
        }
    }

    private fun showAddApiKeyDialog() {
        val editText = EditText(activity).apply { hint = "Enter API key" }
        val defaultName = activity.getString(R.string.api_key_default_name, apiKeyManager.getAllApiKeys().size + 1)
        val nameText = EditText(activity).apply {
            hint = activity.getString(R.string.api_key_name_hint)
            setText(defaultName)
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameText)
            addView(editText)
        }
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.add_api_key_title)
                .setView(layout)
                .setPositiveButton(R.string.add) { dialog, _ ->
                    val key = editText.text.toString().trim()
                    val name = nameText.text.toString().trim()
                    if (key.isNotEmpty() && name.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val success = apiKeyManager.addApiKey(key, name)
                            withContext(Dispatchers.Main) {
                                val msgRes = if (success) R.string.api_key_added else R.string.api_key_add_failed
                                Toast.makeText(activity, msgRes, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun showEditApiKeyDialog(index: Int, apiKey: ApiKeyInfo) {
        val editText = EditText(activity).apply { setText(apiKey.name) }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Edit API Key")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        apiKeyManager.updateApiKey(index, newName, apiKey.isActive)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, "API key updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFF4CAF50.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }

    private fun showDeleteApiKeyDialog(index: Int, name: String) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Delete API Key")
            .setMessage("Are you sure you want to delete '$name'?")
            .setPositiveButton("Delete") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val success = apiKeyManager.removeApiKey(index)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity,
                            if (success) "API key deleted" else "Failed to delete API key",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFF44336.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFF4CAF50.toInt())
    }

    private fun testAllApiKeys() {
        Toast.makeText(activity, "Testing API keys...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val activeKeys = apiKeyManager.getActiveApiKeys()
            var successCount = 0
            activeKeys.forEach { apiKey ->
                try {
                    val models = modelManager.fetchAvailableModels(apiKey.key)
                    if (models.isNotEmpty()) successCount++
                } catch (_: Exception) {
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    activity,
                    "API key test complete: $successCount/${activeKeys.size} working",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}


