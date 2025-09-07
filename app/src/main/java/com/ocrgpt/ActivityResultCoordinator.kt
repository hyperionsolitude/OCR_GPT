package com.ocrgpt

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityResultCoordinator(
    private val activity: AppCompatActivity,
    private val onCameraSuccess: (ActivityResult) -> Unit,
    private val onGallerySuccess: (ActivityResult) -> Unit,
    private val onCropSuccess: (Uri) -> Unit,
) {
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var cropLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    var currentPhotoPath: String? = null
    var currentPhotoUri: Uri? = null

    fun init() {
        cameraLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            Log.d("OCR", "Camera result: ${result.resultCode}")
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                onCameraSuccess(result)
            } else {
                Toast.makeText(activity, "Camera cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        galleryLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            Log.d("OCR", "Gallery result: ${result.resultCode}")
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                onGallerySuccess(result)
            } else {
                Toast.makeText(activity, "Gallery cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        cropLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            val hasCropped = data?.hasExtra(CustomCropActivity.EXTRA_CROPPED_URI) == true
            if (result.resultCode == AppCompatActivity.RESULT_OK && hasCropped) {
                val croppedUriString = data?.getStringExtra(CustomCropActivity.EXTRA_CROPPED_URI)
                croppedUriString?.let { uriString ->
                    val uri = Uri.parse(uriString)
                    onCropSuccess(uri)
                }
            } else {
                Log.d("OCR", "Crop cancelled or failed: ${result.resultCode}")
            }
        }

        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                Log.d("OCR", "Camera permission granted")
                launchCamera()
            } else {
                Log.d("OCR", "Camera permission denied")
                Toast.makeText(activity, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun takePhoto() {
        val hasCamera = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            launchCamera()
        } else {
            Log.d("OCR", "Requesting camera permission")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    fun launchCrop(sourceUri: Uri) {
        try {
            Log.d("OCR", "Launching crop activity with URI: $sourceUri")
            if ("file" == sourceUri.scheme) {
                val file = File(sourceUri.path ?: "")
                if (!file.exists()) {
                    Log.e("OCR", "File does not exist for URI: $sourceUri")
                    Toast.makeText(activity, "Image file does not exist", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            activity.contentResolver.openInputStream(sourceUri)?.close()
            val intent = Intent(activity, CustomCropActivity::class.java)
            intent.putExtra(CustomCropActivity.EXTRA_IMAGE_URI, sourceUri.toString())
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if ("file" == sourceUri.scheme ||
                ("content" == sourceUri.scheme &&
                    sourceUri.authority?.contains(activity.packageName) == true)
            ) {
                val cropActivityInfo =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val flags = android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong()
                        val resolveFlags = android.content.pm.PackageManager.ResolveInfoFlags.of(flags)
                        activity.packageManager
                            .resolveActivity(intent, resolveFlags)
                            ?.activityInfo
                    } else {
                        @Suppress("DEPRECATION")
                        activity.packageManager.resolveActivity(intent, 0)?.activityInfo
                    }
                if (cropActivityInfo != null) {
                    Log.d("OCR", "Granting URI permission to crop activity: ${cropActivityInfo.packageName}")
                    activity.grantUriPermission(
                        cropActivityInfo.packageName,
                        sourceUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            cropLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("OCR", "Crop activity not found", e)
            Toast.makeText(activity, "Crop not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFileInCache()
            currentPhotoPath = photoFile.absolutePath
            currentPhotoUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                photoFile,
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                putExtra("android.intent.extras.CAMERA_FACING", 0)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            val resolveInfo =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val flags = android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong()
                    val resolveFlags = android.content.pm.PackageManager.ResolveInfoFlags.of(flags)
                    activity.packageManager.resolveActivity(intent, resolveFlags)
                } else {
                    @Suppress("DEPRECATION")
                    intent.resolveActivity(activity.packageManager)
                }
            if (resolveInfo != null) {
                cameraLauncher.launch(intent)
            } else {
                launchHarmonyOSCamera(photoFile)
            }
        } catch (e: ActivityNotFoundException) {
            Log.e("OCR", "Camera activity not found", e)
            Toast.makeText(activity, "Camera not available: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e("OCR", "Camera permission denied", e)
            Toast.makeText(activity, "Camera permission denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchHarmonyOSCamera(@Suppress("UNUSED_PARAMETER") photoFile: File) {
        try {
            val huaweiCameraIntent = Intent().apply {
                setClassName("com.huawei.camera", "com.huawei.camera.ThirdCamera")
                putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                putExtra("android.intent.extras.CAMERA_FACING", 0)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(huaweiCameraIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e("OCR", "Huawei camera not found", e)
            // ignore, fallback not necessary here
        } catch (e: SecurityException) {
            Log.e("OCR", "Huawei camera permission denied", e)
            val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                putExtra("android.intent.extras.CAMERA_FACING", 0)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            cameraLauncher.launch(fallbackIntent)
        }
    }

    private fun createImageFileInCache(): File {
        val storageDir = activity.cacheDir
        return File.createTempFile("JPEG_", ".jpg", storageDir)
    }
}
