package com.ocrgpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.util.Log

class PermissionManager(private val context: Context) {
    
    companion object {
        const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        const val STORAGE_PERMISSION_REQUEST_CODE = 1002
        const val WRITE_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 1003
        
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        private val REQUIRED_PERMISSIONS_API_33_PLUS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    }
    
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            REQUIRED_PERMISSIONS_API_33_PLUS
        } else {
            REQUIRED_PERMISSIONS
        }
    }
    
    fun hasAllPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasWriteStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For API 33+, we don't need WRITE_EXTERNAL_STORAGE for media files
            true
        } else {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun getMissingPermissions(): Array<String> {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
    
    fun shouldShowRationale(permission: String): Boolean {
        return try {
            if (context is androidx.activity.ComponentActivity) {
                context.shouldShowRequestPermissionRationale(permission)
            } else {
                false
            }
        } catch (e: IllegalStateException) {
            Log.e("PermissionManager", "Illegal state error checking permission rationale: ${e.message}")
            false
        } catch (e: IllegalArgumentException) {
            Log.e("PermissionManager", "Invalid argument error checking permission rationale: ${e.message}")
            false
        }
    }
    
    fun logPermissionStatus() {
        Log.d("PermissionManager", "Camera permission: ${hasCameraPermission()}")
        Log.d("PermissionManager", "Storage permission: ${hasStoragePermission()}")
        Log.d("PermissionManager", "Write storage permission: ${hasWriteStoragePermission()}")
        Log.d("PermissionManager", "All permissions: ${hasAllPermissions()}")
    }
}
