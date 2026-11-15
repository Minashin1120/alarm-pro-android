package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Camera availability helpers for the printed-QR unlock.
 *
 * Kept outside [PermissionManager] on purpose: the camera is only required by alarms that
 * opt into the QR stage, so it must not affect the lockdown permission counters.
 */
object CameraPermission {

  fun isGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun hasCamera(context: Context): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
  }
}
