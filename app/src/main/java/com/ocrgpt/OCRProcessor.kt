package com.ocrgpt

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OCRProcessor {
    companion object {
        private const val MIN_IMAGE_WIDTH = 800
        private const val MIN_IMAGE_HEIGHT = 600
    }

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImageForOCR(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val result =
                    suspendCancellableCoroutine { continuation ->
                        textRecognizer
                            .process(image)
                            .addOnSuccessListener { visionText ->
                                continuation.resume(visionText)
                            }.addOnFailureListener { exception ->
                                continuation.resumeWithException(exception)
                            }
                    }
                val extractedText = result.text

                Log.d("OCR", "Extracted text length: ${extractedText.length}")
                extractedText
            } catch (e: IOException) {
                Log.e("OCR", "IO error processing image for OCR: ${e.message}")
                "Error extracting text: ${e.message}"
            } catch (e: IllegalStateException) {
                Log.e("OCR", "Illegal state error processing image for OCR: ${e.message}")
                "Error extracting text: ${e.message}"
            }
        }

    suspend fun enhanceImageForOCR(bitmap: Bitmap): Bitmap =
        withContext(Dispatchers.IO) {
            try {
                // Basic image enhancement for better OCR results
                val width = bitmap.width
                val height = bitmap.height

                // Scale up if image is too small
                val scaleFactor =
                    if (width < MIN_IMAGE_WIDTH || height < MIN_IMAGE_HEIGHT) {
                        maxOf(MIN_IMAGE_WIDTH.toDouble() / width, MIN_IMAGE_HEIGHT.toDouble() / height)
                    } else {
                        1.0
                    }

                if (scaleFactor > 1.0) {
                    val newWidth = (width * scaleFactor).toInt()
                    val newHeight = (height * scaleFactor).toInt()
                    Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                } else {
                    bitmap
                }
            } catch (e: OutOfMemoryError) {
                Log.e("OCR", "Out of memory error enhancing image: ${e.message}")
                bitmap
            } catch (e: IllegalStateException) {
                Log.e("OCR", "Illegal state error enhancing image: ${e.message}")
                bitmap
            }
        }

    fun cleanup() {
        textRecognizer.close()
    }
}
