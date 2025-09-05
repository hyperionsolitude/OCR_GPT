package com.ocrgpt

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
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

class CustomCropActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var cropButton: Button
    private lateinit var cancelButton: Button
    private var originalBitmap: Bitmap? = null
    private var imageUri: Uri? = null

    companion object {
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_CROPPED_URI = "cropped_uri"
    }

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
            cropOverlay.updateForOrientationChange()
        }
    }

    private fun loadImage() {
        try {
            // First, decode image bounds to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val inputStream = contentResolver.openInputStream(imageUri!!)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate sample size to fit in memory
            val maxSize = 2048 // Maximum dimension for display
            val sampleSize = maxOf(
                options.outWidth / maxSize,
                options.outHeight / maxSize,
                1
            )

            // Decode the actual bitmap with sampling
            val decodeOptions = BitmapFactory.Options().apply {
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
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CustomCrop", "Error loading image", e)
            finish()
        }
    }

    private fun fixImageOrientation(bitmap: Bitmap): Bitmap {
        try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            val exif = android.media.ExifInterface(inputStream!!)
            inputStream.close()
            
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
            }
            
            return if (matrix.isIdentity) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            Log.e("CustomCrop", "Error fixing image orientation", e)
            return bitmap
        }
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
            val cropRect = cropOverlay.getCropRect()
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                Toast.makeText(this, "Invalid crop area", Toast.LENGTH_SHORT).show()
                return
            }

            // Load the original high-resolution image for cropping
            val originalHighResBitmap = loadOriginalImage()
            if (originalHighResBitmap == null) {
                Toast.makeText(this, "Error loading original image for cropping", Toast.LENGTH_SHORT).show()
                return
            }

            // Get the actual image bounds within the ImageView (accounting for fitCenter scaling)
            val imageBounds = getImageBoundsInView()
            Log.d("CustomCrop", "Image bounds in view: $imageBounds")
            Log.d("CustomCrop", "Crop rect in overlay: $cropRect")

            // Calculate the actual crop rectangle in original bitmap coordinates
            val originalWidth = originalHighResBitmap.width
            val originalHeight = originalHighResBitmap.height

            // Map crop rectangle from overlay coordinates to image coordinates
            val imageLeft = (cropRect.left - imageBounds.left) / imageBounds.width() * originalWidth
            val imageTop = (cropRect.top - imageBounds.top) / imageBounds.height() * originalHeight
            val imageRight = (cropRect.right - imageBounds.left) / imageBounds.width() * originalWidth
            val imageBottom = (cropRect.bottom - imageBounds.top) / imageBounds.height() * originalHeight

            val actualCropRect = Rect(
                imageLeft.toInt(),
                imageTop.toInt(),
                imageRight.toInt(),
                imageBottom.toInt()
            )

            // Ensure the crop rectangle is within bounds
            actualCropRect.left = actualCropRect.left.coerceIn(0, originalWidth)
            actualCropRect.top = actualCropRect.top.coerceIn(0, originalHeight)
            actualCropRect.right = actualCropRect.right.coerceIn(actualCropRect.left, originalWidth)
            actualCropRect.bottom = actualCropRect.bottom.coerceIn(actualCropRect.top, originalHeight)

            Log.d("CustomCrop", "Original image: ${originalWidth}x${originalHeight}")
            Log.d("CustomCrop", "Final crop rect: ${actualCropRect.width()}x${actualCropRect.height()} at (${actualCropRect.left},${actualCropRect.top})")

            // Perform the crop
            val croppedBitmap = Bitmap.createBitmap(
                originalHighResBitmap,
                actualCropRect.left,
                actualCropRect.top,
                actualCropRect.width(),
                actualCropRect.height()
            )

            // Save the cropped image
            val croppedFile = File(cacheDir, "custom_cropped_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(croppedFile)
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()

            val croppedUri = Uri.fromFile(croppedFile)
            val resultIntent = Intent().apply {
                putExtra(EXTRA_CROPPED_URI, croppedUri.toString())
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Error cropping image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CustomCrop", "Error cropping image", e)
        }
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

    private fun loadOriginalImage(): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(imageUri!!)
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                bitmap = fixImageOrientation(bitmap)
            }
            
            bitmap
        } catch (e: Exception) {
            Log.e("CustomCrop", "Error loading original image", e)
            null
        }
    }
}

class CropOverlayView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val cropRect = RectF(100f, 100f, 300f, 300f)
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val cornerPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }
    private val overlayPaint = Paint().apply {
        color = Color.argb(128, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private var isDragging = false
    private var dragHandle = -1
    private val handleSize = 60f  // Increased handle size for better touch response
    private val touchSlop = 30f   // Increased touch slop for better responsiveness

    private val handles = arrayOf(
        RectF(), // top-left
        RectF(), // top-right
        RectF(), // bottom-left
        RectF(), // bottom-right
        RectF(), // top
        RectF(), // bottom
        RectF(), // left
        RectF()  // right
    )

    private var imageView: ImageView? = null

    fun setImageBitmap(bitmap: Bitmap, imageView: ImageView) {
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
        val size = maxOf(minDimension * 0.6f, 200f)
        
        cropRect.set(
            centerX - size / 2,
            centerY - size / 2,
            centerX + size / 2,
            centerY + size / 2
        )
        
        // Ensure crop rectangle is within bounds
        cropRect.left = cropRect.left.coerceIn(imageBounds.left, imageBounds.right - handleSize)
        cropRect.top = cropRect.top.coerceIn(imageBounds.top, imageBounds.bottom - handleSize)
        cropRect.right = cropRect.right.coerceIn(cropRect.left + handleSize, imageBounds.right)
        cropRect.bottom = cropRect.bottom.coerceIn(cropRect.top + handleSize, imageBounds.bottom)
        
        updateHandles()
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

    fun getCropRect(): RectF {
        return RectF(cropRect)
    }

    fun updateForOrientationChange() {
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
                    newBottom.coerceIn(imageBounds.top + handleSize, imageBounds.bottom)
                )
                
                updateHandles()
                invalidate()
            } else {
                // If bounds are not ready, reinitialize the crop rectangle
                initializeCropRectangle()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update crop rectangle when view size changes (e.g., during orientation change)
        post {
            updateForOrientationChange()
        }
    }

    private fun updateHandles() {
        val halfHandle = handleSize / 2

        // Corner handles
        handles[0].set(cropRect.left - halfHandle, cropRect.top - halfHandle, cropRect.left + halfHandle, cropRect.top + halfHandle) // top-left
        handles[1].set(cropRect.right - halfHandle, cropRect.top - halfHandle, cropRect.right + halfHandle, cropRect.top + halfHandle) // top-right
        handles[2].set(cropRect.left - halfHandle, cropRect.bottom - halfHandle, cropRect.left + halfHandle, cropRect.bottom + halfHandle) // bottom-left
        handles[3].set(cropRect.right - halfHandle, cropRect.bottom - halfHandle, cropRect.right + halfHandle, cropRect.bottom + halfHandle) // bottom-right

        // Edge handles
        handles[4].set(cropRect.centerX() - halfHandle, cropRect.top - halfHandle, cropRect.centerX() + halfHandle, cropRect.top + halfHandle) // top
        handles[5].set(cropRect.centerX() - halfHandle, cropRect.bottom - halfHandle, cropRect.centerX() + halfHandle, cropRect.bottom + halfHandle) // bottom
        handles[6].set(cropRect.left - halfHandle, cropRect.centerY() - halfHandle, cropRect.left + halfHandle, cropRect.centerY() + halfHandle) // left
        handles[7].set(cropRect.right - halfHandle, cropRect.centerY() - halfHandle, cropRect.right + halfHandle, cropRect.centerY() + halfHandle) // right
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw semi-transparent overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Clear the crop area
        canvas.save()
        canvas.clipOutRect(cropRect)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.restore()

        // Draw crop rectangle border
        canvas.drawRect(cropRect, paint)

        // Draw corner handles
        for (i in 0..3) {
            canvas.drawRect(handles[i], cornerPaint)
        }

        // Draw edge handles
        for (i in 4..7) {
            canvas.drawRect(handles[i], cornerPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                dragHandle = findHandle(x, y)
                isDragging = dragHandle != -1
                if (isDragging) {
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                return isDragging
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && dragHandle != -1) {
                    val x = event.x.coerceIn(0f, width.toFloat())
                    val y = event.y.coerceIn(0f, height.toFloat())
                    updateCropRect(x, y)
                    updateHandles()
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                dragHandle = -1
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                dragHandle = -1
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findHandle(x: Float, y: Float): Int {
        for (i in handles.indices) {
            // Expand touch area for better responsiveness
            val expandedHandle = RectF(
                handles[i].left - touchSlop,
                handles[i].top - touchSlop,
                handles[i].right + touchSlop,
                handles[i].bottom + touchSlop
            )
            if (expandedHandle.contains(x, y)) {
                return i
            }
        }
        return -1
    }

    private fun updateCropRect(x: Float, y: Float) {
        val imageBounds = getImageBoundsInView()
        
        when (dragHandle) {
            0 -> { // top-left
                cropRect.left = x.coerceIn(imageBounds.left, cropRect.right - handleSize)
                cropRect.top = y.coerceIn(imageBounds.top, cropRect.bottom - handleSize)
            }
            1 -> { // top-right
                cropRect.right = x.coerceIn(cropRect.left + handleSize, imageBounds.right)
                cropRect.top = y.coerceIn(imageBounds.top, cropRect.bottom - handleSize)
            }
            2 -> { // bottom-left
                cropRect.left = x.coerceIn(imageBounds.left, cropRect.right - handleSize)
                cropRect.bottom = y.coerceIn(cropRect.top + handleSize, imageBounds.bottom)
            }
            3 -> { // bottom-right
                cropRect.right = x.coerceIn(cropRect.left + handleSize, imageBounds.right)
                cropRect.bottom = y.coerceIn(cropRect.top + handleSize, imageBounds.bottom)
            }
            4 -> { // top
                cropRect.top = y.coerceIn(imageBounds.top, cropRect.bottom - handleSize)
            }
            5 -> { // bottom
                cropRect.bottom = y.coerceIn(cropRect.top + handleSize, imageBounds.bottom)
            }
            6 -> { // left
                cropRect.left = x.coerceIn(imageBounds.left, cropRect.right - handleSize)
            }
            7 -> { // right
                cropRect.right = x.coerceIn(cropRect.left + handleSize, imageBounds.right)
            }
        }
    }
} 