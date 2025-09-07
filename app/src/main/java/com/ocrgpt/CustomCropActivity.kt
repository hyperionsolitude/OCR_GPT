package com.ocrgpt

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CustomCropActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_CROPPED_URI = "cropped_uri"

        private const val MAX_DISPLAY_SIZE = 2048
        private const val JPEG_QUALITY = 90

        // Image rotation constants
        private const val ROTATION_90_DEGREES = 90f
        private const val ROTATION_180_DEGREES = 180f
        private const val ROTATION_270_DEGREES = 270f
        private const val SCALE_FLIP_HORIZONTAL = -1f
        private const val SCALE_FLIP_VERTICAL = -1f
        private const val SCALE_NORMAL = 1f
    }

    private lateinit var imageView: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var cropButton: Button
    private lateinit var cancelButton: Button
    private var originalBitmap: Bitmap? = null
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CustomCrop", "onCreate called. Intent: $intent, extras: ${intent.extras}")
        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        Log.d("CustomCrop", "Received EXTRA_IMAGE_URI: $uriString")
        setContentView(R.layout.activity_custom_crop)

        imageView = findViewById(R.id.crop_image_view)
        cropOverlay = findViewById(R.id.crop_overlay)
        cropButton = findViewById(R.id.btn_crop)
        cancelButton = findViewById(R.id.btn_cancel)

        imageUri = uriString?.let { Uri.parse(it) }
        if (imageUri == null) {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            Log.e("CustomCrop", "No image provided, finishing activity.")
            finish()
            return
        }

        loadImage()
        setupButtons()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("CustomCrop", "Configuration changed: ${newConfig.orientation}")

        // Update the crop overlay when orientation changes
        runOnUiThread {
            cropOverlay.handleCropRect("update")
        }
    }

    private fun loadImage() {
        try {
            // First, decode image bounds to get dimensions
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            val inputStream = contentResolver.openInputStream(imageUri!!)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate sample size to fit in memory
            val maxSize = MAX_DISPLAY_SIZE // Maximum dimension for display
            val sampleSize =
                maxOf(
                    options.outWidth / maxSize,
                    options.outHeight / maxSize,
                    1,
                )

            // Decode the actual bitmap with sampling
            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
                }

            val decodeInputStream = contentResolver.openInputStream(imageUri!!)
            var bitmap = BitmapFactory.decodeStream(decodeInputStream, null, decodeOptions)
            decodeInputStream?.close()

            if (bitmap != null) {
                // Fix image orientation
                bitmap = fixImageOrientation(bitmap)
                originalBitmap = bitmap
                imageView.setImageBitmap(originalBitmap)
                cropOverlay.setImageBitmap(originalBitmap!!, imageView)
                Log.d("CustomCrop", "Image loaded: ${originalBitmap!!.width}x${originalBitmap!!.height}")
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CustomCrop", "Error loading image", e)
            finish()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CustomCrop", "Permission denied", e)
            finish()
        }
    }

    private fun fixImageOrientation(bitmap: Bitmap): Bitmap =
        try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            val exif =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    androidx.exifinterface.media.ExifInterface(inputStream!!)
                } else {
                    @Suppress("DEPRECATION")
                    androidx.exifinterface.media.ExifInterface(imageUri!!.path!!)
                }
            inputStream?.close()

            val orientation =
                exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )

            val matrix = Matrix()
            when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 ->
                    matrix.postRotate(ROTATION_90_DEGREES)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 ->
                    matrix.postRotate(ROTATION_180_DEGREES)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 ->
                    matrix.postRotate(ROTATION_270_DEGREES)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                    matrix.postScale(SCALE_FLIP_HORIZONTAL, SCALE_NORMAL)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                    matrix.postScale(SCALE_NORMAL, SCALE_FLIP_VERTICAL)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(ROTATION_90_DEGREES)
                    matrix.postScale(SCALE_FLIP_HORIZONTAL, SCALE_NORMAL)
                }
                androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(ROTATION_270_DEGREES)
                    matrix.postScale(SCALE_FLIP_HORIZONTAL, SCALE_NORMAL)
                }
            }

            if (matrix.isIdentity) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: IOException) {
            Log.e("CustomCrop", "Error fixing image orientation", e)
            bitmap
        } catch (e: OutOfMemoryError) {
            Log.e("CustomCrop", "Out of memory fixing image orientation", e)
            bitmap
        }

    private fun setupButtons() {
        cropButton.setOnClickListener {
            performCrop()
        }

        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun performCrop() {
        try {
            val cropRect = cropOverlay.handleCropRect("get")!!
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                Toast.makeText(this, "Invalid crop area", Toast.LENGTH_SHORT).show()
                return
            }

            val originalHighResBitmap = loadOriginalImage()
            if (originalHighResBitmap == null) {
                Toast.makeText(this, "Error loading original image for cropping", Toast.LENGTH_SHORT).show()
                return
            }

            processCropImage(cropRect, originalHighResBitmap)
        } catch (e: IOException) {
            Toast.makeText(this, "Error cropping image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CustomCrop", "Error cropping image", e)
        } catch (e: OutOfMemoryError) {
            Toast.makeText(this, "Out of memory cropping image", Toast.LENGTH_SHORT).show()
            Log.e("CustomCrop", "Out of memory cropping image", e)
        }
    }

    private fun processCropImage(
        cropRect: RectF,
        originalBitmap: Bitmap,
    ) {
        val imageBounds = getImageBoundsInView()
        Log.d("CustomCrop", "Image bounds in view: $imageBounds")
        Log.d("CustomCrop", "Crop rect in overlay: $cropRect")

        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height

        val imageLeft = (cropRect.left - imageBounds.left) / imageBounds.width() * originalWidth
        val imageTop = (cropRect.top - imageBounds.top) / imageBounds.height() * originalHeight
        val imageRight = (cropRect.right - imageBounds.left) / imageBounds.width() * originalWidth
        val imageBottom = (cropRect.bottom - imageBounds.top) / imageBounds.height() * originalHeight

        val actualCropRect =
            Rect(
                imageLeft.toInt(),
                imageTop.toInt(),
                imageRight.toInt(),
                imageBottom.toInt(),
            )

        actualCropRect.left = actualCropRect.left.coerceIn(0, originalWidth)
        actualCropRect.top = actualCropRect.top.coerceIn(0, originalHeight)
        actualCropRect.right = actualCropRect.right.coerceIn(actualCropRect.left, originalWidth)
        actualCropRect.bottom = actualCropRect.bottom.coerceIn(actualCropRect.top, originalHeight)

        Log.d("CustomCrop", "Original image: ${originalWidth}x$originalHeight")
        Log.d(
            "CustomCrop",
            "Final crop rect: ${actualCropRect.width()}x${actualCropRect.height()} " +
                "at (${actualCropRect.left},${actualCropRect.top})",
        )

        val croppedBitmap =
            Bitmap.createBitmap(
                originalBitmap,
                actualCropRect.left,
                actualCropRect.top,
                actualCropRect.width(),
                actualCropRect.height(),
            )

        val croppedFile = File(cacheDir, "custom_cropped_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(croppedFile)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        outputStream.close()

        val croppedUri = Uri.fromFile(croppedFile)
        val resultIntent =
            Intent().apply {
                putExtra(EXTRA_CROPPED_URI, croppedUri.toString())
            }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun getImageBoundsInView(): RectF {
        val imageView = findViewById<ImageView>(R.id.crop_image_view)
        val drawable = imageView?.drawable

        if (imageView == null || drawable == null) {
            return RectF(0f, 0f, imageView?.width?.toFloat() ?: 0f, imageView?.height?.toFloat() ?: 0f)
        }

        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        // Calculate scale to fit the image within the view (fitCenter)
        val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)

        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale

        // Calculate the position to center the image
        val left = (viewWidth - scaledImageWidth) / 2
        val top = (viewHeight - scaledImageHeight) / 2

        return RectF(left, top, left + scaledImageWidth, top + scaledImageHeight)
    }

    private fun loadOriginalImage(): Bitmap? =
        try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                bitmap = fixImageOrientation(bitmap)
            }

            bitmap
        } catch (e: IOException) {
            Log.e("CustomCrop", "Error loading original image", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.e("CustomCrop", "Out of memory loading original image", e)
            null
        }
}

class CropOverlayView
    @JvmOverloads
    constructor(
        context: android.content.Context,
        attrs: android.util.AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private var bitmap: Bitmap? = null
        private val cropRect = RectF(CROP_RECT_LEFT, CROP_RECT_TOP, CROP_RECT_RIGHT, CROP_RECT_BOTTOM)
        private val paint =
            Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = STROKE_WIDTH
            }
        private val cornerPaint =
            Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL
            }
        private val overlayPaint =
            Paint().apply {
                color = Color.argb(OVERLAY_ALPHA, 0, 0, 0)
                style = Paint.Style.FILL
            }

        private var isDragging = false
        private var dragHandle = -1

        companion object {
            private const val HANDLE_SIZE = 60f // Increased handle size for better touch response
            private const val TOUCH_SLOP = 30f // Increased touch slop for better responsiveness
            private const val CROP_RECT_LEFT = 100f
            private const val CROP_RECT_TOP = 100f
            private const val CROP_RECT_RIGHT = 300f
            private const val CROP_RECT_BOTTOM = 300f
            private const val STROKE_WIDTH = 4f
            private const val HANDLE_INDEX_TOP_RIGHT = 1
            private const val HANDLE_INDEX_BOTTOM_LEFT = 2
            private const val HANDLE_INDEX_BOTTOM_RIGHT = 3
            private const val HANDLE_INDEX_TOP = 4
            private const val HANDLE_INDEX_BOTTOM = 5
            private const val HANDLE_INDEX_LEFT = 6
            private const val HANDLE_INDEX_RIGHT = 7
            private const val OVERLAY_ALPHA = 128
            private const val CROP_SIZE_RATIO = 0.6f
            private const val MIN_CROP_SIZE = 200f
        }

        private val handleSize = HANDLE_SIZE
        private val touchSlop = TOUCH_SLOP

        private val handles =
            arrayOf(
                RectF(), // top-left
                RectF(), // top-right
                RectF(), // bottom-left
                RectF(), // bottom-right
                RectF(), // top
                RectF(), // bottom
                RectF(), // left
                RectF(), // right
            )

        private var imageView: ImageView? = null

        fun setImageBitmap(
            bitmap: Bitmap,
            imageView: ImageView,
        ) {
            this.bitmap = bitmap
            this.imageView = imageView
            // Initialize crop rectangle to center of actual image area
            post {
                initializeCropRectangle()
            }
        }

        private fun initializeCropRectangle() {
            val imageBounds = getImageBoundsInView()
            val centerX = imageBounds.centerX()
            val centerY = imageBounds.centerY()

            // Make crop rectangle responsive to orientation
            val availableWidth = imageBounds.width()
            val availableHeight = imageBounds.height()
            val minDimension = minOf(availableWidth, availableHeight)

            // Use 60% of the smaller dimension, but ensure minimum size
            val size = maxOf(minDimension * CROP_SIZE_RATIO, MIN_CROP_SIZE)

            cropRect.set(
                centerX - size / 2,
                centerY - size / 2,
                centerX + size / 2,
                centerY + size / 2,
            )

            // Ensure crop rectangle is within bounds
            cropRect.left = cropRect.left.coerceIn(imageBounds.left, imageBounds.right - handleSize)
            cropRect.top = cropRect.top.coerceIn(imageBounds.top, imageBounds.bottom - handleSize)
            cropRect.right = cropRect.right.coerceIn(cropRect.left + handleSize, imageBounds.right)
            cropRect.bottom = cropRect.bottom.coerceIn(cropRect.top + handleSize, imageBounds.bottom)

            updateHandlePositions()
            invalidate()
        }

        private fun getImageBoundsInView(): RectF {
            val imageView = this.imageView
            val drawable = imageView?.drawable

            if (imageView == null || drawable == null) {
                return RectF(0f, 0f, width.toFloat(), height.toFloat())
            }

            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val imageWidth = drawable.intrinsicWidth.toFloat()
            val imageHeight = drawable.intrinsicHeight.toFloat()

            // Calculate scale to fit the image within the view (fitCenter)
            val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)

            val scaledImageWidth = imageWidth * scale
            val scaledImageHeight = imageHeight * scale

            // Calculate the position to center the image
            val left = (viewWidth - scaledImageWidth) / 2
            val top = (viewHeight - scaledImageHeight) / 2

            return RectF(left, top, left + scaledImageWidth, top + scaledImageHeight)
        }

        fun handleCropRect(action: String): RectF? =
            when (action) {
                "get" -> RectF(cropRect)
                "update" -> {
                    // Recalculate crop rectangle position when orientation changes
                    post {
                        val imageBounds = getImageBoundsInView()
                        val currentCropRect = RectF(cropRect)

                        // Only update if we have valid image bounds
                        if (imageBounds.width() > 0 && imageBounds.height() > 0) {
                            // Calculate the relative position within the image bounds
                            val relativeLeft = (currentCropRect.left - imageBounds.left) / imageBounds.width()
                            val relativeTop = (currentCropRect.top - imageBounds.top) / imageBounds.height()
                            val relativeRight = (currentCropRect.right - imageBounds.left) / imageBounds.width()
                            val relativeBottom = (currentCropRect.bottom - imageBounds.top) / imageBounds.height()

                            // Apply the relative positions to the new image bounds
                            val newLeft = imageBounds.left + relativeLeft * imageBounds.width()
                            val newTop = imageBounds.top + relativeTop * imageBounds.height()
                            val newRight = imageBounds.left + relativeRight * imageBounds.width()
                            val newBottom = imageBounds.top + relativeBottom * imageBounds.height()

                            // Ensure the crop rectangle stays within bounds
                            cropRect.set(
                                newLeft.coerceIn(imageBounds.left, imageBounds.right - handleSize),
                                newTop.coerceIn(imageBounds.top, imageBounds.bottom - handleSize),
                                newRight.coerceIn(imageBounds.left + handleSize, imageBounds.right),
                                newBottom.coerceIn(imageBounds.top + handleSize, imageBounds.bottom),
                            )

                            updateHandlePositions()
                            invalidate()
                        } else {
                            // If bounds are not ready, reinitialize the crop rectangle
                            initializeCropRectangle()
                        }
                    }
                    null
                }
                else -> null
            }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            // Update crop rectangle when view size changes (e.g., during orientation change)
            post {
                handleCropRect("update")
            }
        }

        private fun updateHandlePositions() {
            val halfHandle = handleSize / 2

            // Corner handles
            handles[0].set(
                cropRect.left - halfHandle,
                cropRect.top - halfHandle,
                cropRect.left + halfHandle,
                cropRect.top + halfHandle,
            ) // top-left
            handles[1].set(
                cropRect.right - halfHandle,
                cropRect.top - halfHandle,
                cropRect.right + halfHandle,
                cropRect.top + halfHandle,
            ) // top-right
            handles[HANDLE_INDEX_BOTTOM_LEFT].set(
                cropRect.left - halfHandle,
                cropRect.bottom - halfHandle,
                cropRect.left + halfHandle,
                cropRect.bottom + halfHandle,
            ) // bottom-left
            handles[HANDLE_INDEX_BOTTOM_RIGHT].set(
                cropRect.right - halfHandle,
                cropRect.bottom - halfHandle,
                cropRect.right + halfHandle,
                cropRect.bottom + halfHandle,
            ) // bottom-right

            // Edge handles
            handles[HANDLE_INDEX_TOP].set(
                cropRect.centerX() - halfHandle,
                cropRect.top - halfHandle,
                cropRect.centerX() + halfHandle,
                cropRect.top + halfHandle,
            ) // top
            handles[HANDLE_INDEX_BOTTOM].set(
                cropRect.centerX() - halfHandle,
                cropRect.bottom - halfHandle,
                cropRect.centerX() + halfHandle,
                cropRect.bottom + halfHandle,
            ) // bottom
            handles[HANDLE_INDEX_LEFT].set(
                cropRect.left - halfHandle,
                cropRect.centerY() - halfHandle,
                cropRect.left + halfHandle,
                cropRect.centerY() + halfHandle,
            ) // left
            handles[HANDLE_INDEX_RIGHT].set(
                cropRect.right - halfHandle,
                cropRect.centerY() - halfHandle,
                cropRect.right + halfHandle,
                cropRect.centerY() + halfHandle,
            ) // right
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            updateHandlePositions()

            // Draw semi-transparent overlay
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

            // Clear the crop area
            canvas.save()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                canvas.clipOutRect(cropRect)
            } else {
                @Suppress("DEPRECATION")
                canvas.clipRect(cropRect, android.graphics.Region.Op.DIFFERENCE)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            canvas.restore()

            // Draw crop rectangle border
            canvas.drawRect(cropRect, paint)

            // Draw corner handles
            for (i in 0..HANDLE_INDEX_BOTTOM_RIGHT) {
                canvas.drawRect(handles[i], cornerPaint)
            }

            // Draw edge handles
            for (i in HANDLE_INDEX_TOP..HANDLE_INDEX_RIGHT) {
                canvas.drawRect(handles[i], cornerPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(0f, width.toFloat())
            val y = event.y.coerceIn(0f, height.toFloat())
            val handled = handleTouchInteraction(x, y, event.action)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (handled) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (handled) {
                        updateHandlePositions()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }

            return handled || super.onTouchEvent(event)
        }

        private fun handleTouchInteraction(
            x: Float,
            y: Float,
            action: Int,
        ): Boolean =
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    // Inline findHandle to reduce function count
                    dragHandle = -1
                    for (i in handles.indices) {
                        val expandedHandle =
                            RectF(
                                handles[i].left - touchSlop,
                                handles[i].top - touchSlop,
                                handles[i].right + touchSlop,
                                handles[i].bottom + touchSlop,
                            )
                        if (expandedHandle.contains(x, y)) {
                            dragHandle = i
                            break
                        }
                    }
                    isDragging = dragHandle != -1
                    isDragging
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging && dragHandle != -1) {
                        updateCropRect(x, y)
                        invalidate()
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                    dragHandle = -1
                    false
                }
                else -> false
            }

        private fun updateCropRect(
            x: Float,
            y: Float,
        ) {
            val imageBounds = getImageBoundsInView()
            when (dragHandle) {
                0 -> {
                    cropRect.left = x.coerceIn(imageBounds.left, cropRect.right - handleSize)
                    cropRect.top = y.coerceIn(imageBounds.top, cropRect.bottom - handleSize)
                }
                HANDLE_INDEX_TOP_RIGHT -> {
                    cropRect.right = x.coerceIn(cropRect.left + handleSize, imageBounds.right)
                    cropRect.top = y.coerceIn(imageBounds.top, cropRect.bottom - handleSize)
                }
                HANDLE_INDEX_BOTTOM_LEFT -> {
                    cropRect.left = x.coerceIn(imageBounds.left, cropRect.right - handleSize)
                    cropRect.bottom = y.coerceIn(cropRect.top + handleSize, imageBounds.bottom)
                }
                HANDLE_INDEX_BOTTOM_RIGHT -> {
                    cropRect.right = x.coerceIn(cropRect.left + handleSize, imageBounds.right)
                    cropRect.bottom = y.coerceIn(cropRect.top + handleSize, imageBounds.bottom)
                }
                HANDLE_INDEX_TOP -> {
                    cropRect.top = y.coerceIn(imageBounds.top, cropRect.bottom - handleSize)
                }
                HANDLE_INDEX_BOTTOM -> {
                    cropRect.bottom = y.coerceIn(cropRect.top + handleSize, imageBounds.bottom)
                }
                HANDLE_INDEX_LEFT -> {
                    cropRect.left = x.coerceIn(imageBounds.left, cropRect.right - handleSize)
                }
                HANDLE_INDEX_RIGHT -> {
                    cropRect.right = x.coerceIn(cropRect.left + handleSize, imageBounds.right)
                }
            }
        }

        // findHandle logic is inlined in handleTouchInteraction to reduce function count
    }
