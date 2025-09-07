package com.ocrgpt

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class UIManager(private val context: Context) {
    
    companion object {
        private const val MILLIS_PER_MINUTE = 1000 * 60
        private const val MILLIS_PER_HOUR = 1000 * 60 * 60
        private const val MILLIS_PER_DAY = 1000 * 60 * 60 * 24
        private const val MINUTES_PER_HOUR = 60
        private const val HOURS_PER_DAY = 24
        private const val BYTES_PER_KB = 1024
        private const val BYTES_PER_MB = 1024 * 1024
        private const val BYTES_PER_GB = 1024 * 1024 * 1024
    }
    
    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun updateSelectedCount(textView: TextView, count: Int) {
        textView.text = "Selected: $count models"
    }

    fun setupModelSpinner(spinner: Spinner, models: List<ModelInfo>) {
        val modelNames = models.map { it.name }.toTypedArray()
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, modelNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    fun formatLastUsed(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / MILLIS_PER_MINUTE
        val hours = diff / MILLIS_PER_HOUR
        val days = diff / MILLIS_PER_DAY

        return when {
            minutes < MINUTES_PER_HOUR -> "Used $minutes minutes ago"
            hours < HOURS_PER_DAY -> "Used $hours hours ago"
            else -> "Used $days days ago"
        }
    }

    fun formatFileSize(bytes: Long): String =
        when {
            bytes < BYTES_PER_KB -> "$bytes B"
            bytes < BYTES_PER_MB -> "${bytes / BYTES_PER_KB} KB"
            bytes < BYTES_PER_GB -> "${bytes / BYTES_PER_MB} MB"
            else -> "${bytes / BYTES_PER_GB} GB"
        }
}
