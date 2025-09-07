package com.ocrgpt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageProcessor(
    private val context: Context,
) {
    companion object {
        private const val ROTATION_90 = 90f
        private const val ROTATION_180 = 180f
        private const val ROTATION_270 = 270f
        private const val FLIP_HORIZONTAL = -1f
        private const val FLIP_VERTICAL = -1f
        private const val NORMAL_SCALE = 1f
        private const val JPEG_QUALITY = 90
    }
    suspend fun loadImageFromUri(uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap
            } catch (e: IOException) {
                Log.e("ImageProcessor", "Error loading image from URI: ${e.message}")
                null
            } catch (e: SecurityException) {
                Log.e("ImageProcessor", "Security error loading image: ${e.message}")
                null
            } catch (e: IllegalStateException) {
                Log.e("ImageProcessor", "Illegal state error loading image: ${e.message}")
                null
            }
        }

    suspend fun loadImageFromPath(path: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(path)
            } catch (e: OutOfMemoryError) {
                Log.e("ImageProcessor", "Out of memory error loading image from path: ${e.message}")
                null
            } catch (e: IllegalStateException) {
                Log.e("ImageProcessor", "Illegal state error loading image from path: ${e.message}")
                null
            }
        }

    fun fixImageOrientation(
        bitmap: Bitmap,
        imagePath: String,
    ): Bitmap {
        return try {
            val exif = android.media.ExifInterface(imagePath)
            val orientation =
                exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_UNDEFINED,
                )

            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(ROTATION_90)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(ROTATION_180)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(ROTATION_270)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                    matrix.postScale(FLIP_HORIZONTAL, NORMAL_SCALE)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(NORMAL_SCALE, FLIP_VERTICAL)
                else -> return bitmap
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            Log.e("ImageProcessor", "Out of memory error fixing image orientation: ${e.message}")
            bitmap
        } catch (e: IllegalStateException) {
            Log.e("ImageProcessor", "Illegal state error fixing image orientation: ${e.message}")
            bitmap
        }
    }

    fun compressImage(
        bitmap: Bitmap,
        maxSize: Int = 1024,
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun saveImageToGallery(
        bitmap: Bitmap,
        filename: String,
    ): Uri? =
        try {
            val contentValues =
                android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }

            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                )

            uri?.let {
                val outputStream = context.contentResolver.openOutputStream(it)
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                outputStream?.close()
                it
            }
        } catch (e: SecurityException) {
            Log.e("ImageProcessor", "Security error saving image to gallery: ${e.message}")
            null
        } catch (e: IllegalStateException) {
            Log.e("ImageProcessor", "Illegal state error saving image to gallery: ${e.message}")
            null
        }
}
