package com.ocrgpt

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

class ImageFlowManager(
    private val activity: AppCompatActivity,
    private val imageProcessor: ImageProcessor,
) {
    fun loadImageFromUri(
        uri: Uri,
        contentResolver: ContentResolver = activity.contentResolver,
        onProcessed: (Bitmap) -> Unit,
    ) {
        try {
            Log.d("OCR", "Loading image from URI: $uri")
            contentResolver.openInputStream(uri).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val processedBitmap = imageProcessor.compressImage(bitmap)
                    onProcessed(processedBitmap)
                    Toast.makeText(activity, "Image loaded successfully", Toast.LENGTH_SHORT).show()
                    Log.d("OCR", "Image loaded: ${processedBitmap.width}x${processedBitmap.height}")
                } else {
                    Toast.makeText(activity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    Log.e("OCR", "Failed to decode bitmap from URI: $uri")
                }
            }
        } catch (e: IOException) {
            Toast.makeText(activity, "IO error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OCR", "IO error loading image from URI: $uri", e)
        } catch (e: SecurityException) {
            Toast.makeText(activity, "Permission error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OCR", "Permission error loading image from URI: $uri", e)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(activity, "Invalid image URI: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("OCR", "Invalid image URI: $uri", e)
        }
    }
}
