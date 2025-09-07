package com.ocrgpt

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsDialogs(
    private val activity: AppCompatActivity,
    private val apiKeyDialogs: ApiKeyDialogs,
) {
    fun showSettingsDialog(
        onModelsClick: () -> Unit,
        onAboutClick: () -> Unit,
    ) {
        val options = arrayOf("🔑 Manage API Keys", "🤖 Select AI Models", "ℹ️ About")
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle("Settings")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> apiKeyDialogs.showManagementDialog()
                        1 -> onModelsClick()
                        2 -> onAboutClick()
                    }
                }.setNegativeButton("Cancel", null)
                .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFF44336.toInt())
    }
}
