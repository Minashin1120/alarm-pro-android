package com.example.alarm

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Enters/exits Android's fully-locked task mode (kiosk mode) while an alarm is ringing.
 *
 * Only a Device Owner can configure this mode. With [DevicePolicyManager.LOCK_TASK_FEATURE_NONE]
 * the system disables:
 *  - the notification shade / quick settings,
 *  - the global actions dialog (power menu / restart),
 *  - Home / Overview,
 *  - the lock screen (so face/fingerprint unlock cannot expose notifications),
 *  - launching other apps.
 *
 * Screen pinning (calling startLockTask() without Device Owner) is NOT enough on Android 12+:
 * the power menu and sometimes the status bar remain usable.
 */
object LockTaskController {

  private const val TAG = "LockTaskController"

  fun admin(context: Context): ComponentName =
    ComponentName(context, MathWakeDeviceAdminReceiver::class.java)

  fun isDeviceOwner(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
      ?: return false
    return dpm.isDeviceOwnerApp(context.packageName)
  }

  fun isLockTaskActive(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
      ?: return false
    return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
  }

  /**
   * Configures and enters fully-locked task mode.
   *
   * Must be called from the Activity that should be pinned.
   * Returns true only when Device Owner kiosk mode was actually entered.
   */
  fun enter(activity: Activity): Boolean {
    val context = activity.applicationContext
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
      ?: return false

    if (!dpm.isDeviceOwnerApp(context.packageName)) {
      // Do not fall back to screen pinning here: it shows a confirmation dialog and,
      // more importantly, still allows the power menu on Android 12+.
      return false
    }

    if (isLockTaskActive(context)) {
      return true
    }

    val admin = admin(context)
    return try {
      // Android 14+ bundles lock-task packages and features; both must succeed.
      dpm.setLockTaskPackages(admin, arrayOf(context.packageName))

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Disable power menu, notifications, status bar, Home/Overview and keyguard.
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
      }

      activity.startLockTask()
      true
    } catch (e: Exception) {
      Log.w(TAG, "Could not enter Device Owner lock task mode", e)
      false
    }
  }

  fun exit(activity: Activity) {
    try {
      if (isLockTaskActive(activity)) {
        activity.stopLockTask()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not exit lock task mode", e)
    }
  }
}
