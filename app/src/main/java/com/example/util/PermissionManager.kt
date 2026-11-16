package com.example.util

import android.Manifest
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.alarm.MathWakeDeviceAdminReceiver

data class LockdownPermissionStatus(
  val isOverlayGranted: Boolean,
  val isExactAlarmGranted: Boolean,
  val isNotificationGranted: Boolean,
  val isBatteryOptimizationIgnored: Boolean,
  val isDeviceAdminActive: Boolean,
  val isAccessibilityEnabled: Boolean,
  val isAccessibilityControlGranted: Boolean,
  val isDeviceOwner: Boolean
) {
  /**
   * Minimum setup for reliable alarm delivery and lock-screen takeover.
   *
   * The accessibility service is deliberately *not* part of this set. It is an optional
   * best-effort fallback, and keeping it enabled makes banking / payment apps warn the user,
   * so the app must not require (or nag about) it.
   */
  val allCriticalGranted: Boolean
    get() = isOverlayGranted && isExactAlarmGranted && isNotificationGranted &&
      isBatteryOptimizationIgnored

  /** True when the power menu / notification shade can be blocked at OS level. */
  val isFullLockdownGranted: Boolean
    get() = allCriticalGranted && isDeviceAdminActive && isDeviceOwner

  /**
   * True when the optional accessibility fallback can do its job.
   *
   * It is either enabled right now (manual mode), or it is enabled automatically only while
   * an alarm rings because `WRITE_SECURE_SETTINGS` was granted over ADB.
   */
  val isAccessibilityFallbackActive: Boolean
    get() = isAccessibilityEnabled || isAccessibilityControlGranted

  /** Accessibility is enabled all the time and cannot be turned off automatically. */
  val isAccessibilityAlwaysOn: Boolean
    get() = isAccessibilityEnabled && !isDeviceOwner && !isAccessibilityControlGranted

  val missingCount: Int
    get() {
      var count = 0
      if (!isOverlayGranted) count++
      if (!isExactAlarmGranted) count++
      if (!isNotificationGranted) count++
      if (!isBatteryOptimizationIgnored) count++
      if (!isDeviceAdminActive) count++
      return count
    }
}

object PermissionManager {

  fun checkStatus(context: Context): LockdownPermissionStatus {
    // 1. Overlay (Display over other apps)
    val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Settings.canDrawOverlays(context)
    } else {
      true
    }

    // 2. Exact Alarm
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val isExactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      alarmManager?.canScheduleExactAlarms() ?: true
    } else {
      true
    }

    // 3. Notification
    val isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

    // 4. Battery Optimization
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isBatteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    } else {
      true
    }

    // 5. Device Administrator
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    val componentName = ComponentName(context, MathWakeDeviceAdminReceiver::class.java)
    val isDeviceAdminActive = dpm?.isAdminActive(componentName) ?: false

    // 6. Device Owner (required for OS-level lock task / power menu blocking)
    val isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) ?: false

    // 7. Accessibility Service (optional fallback for non-Device-Owner installs)
    val isAccessibilityEnabled = AccessibilityController.isServiceEnabled(context)
    val isAccessibilityControlGranted = AccessibilityController.hasSecureSettingsPermission(context)

    return LockdownPermissionStatus(
      isOverlayGranted = isOverlayGranted,
      isExactAlarmGranted = isExactAlarmGranted,
      isNotificationGranted = isNotificationGranted,
      isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
      isDeviceAdminActive = isDeviceAdminActive,
      isAccessibilityEnabled = isAccessibilityEnabled,
      isAccessibilityControlGranted = isAccessibilityControlGranted,
      isDeviceOwner = isDeviceOwner
    )
  }

  /**
   * One-time ADB commands that let MathWake enable the accessibility fallback only while an
   * alarm is ringing instead of leaving it enabled all day.
   */
  fun accessibilityGrantCommand(context: Context): String =
    AccessibilityController.secureSettingsGrantCommand(context)

  /**
   * Command for the user to run once from a PC with USB debugging enabled.
   * Device Owner cannot be granted from inside the app.
   */
  fun deviceOwnerProvisioningCommand(context: Context): String {
    val component = ComponentName(context, MathWakeDeviceAdminReceiver::class.java)
    return "adb shell dpm set-device-owner ${component.flattenToShortString()}"
  }

  fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try {
        context.startActivity(intent)
      } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
      }
    }
  }

  fun requestExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try {
        context.startActivity(intent)
      } catch (_: Exception) {
        openAppDetails(context)
      }
    }
  }

  fun requestBatteryOptimizationIgnore(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try {
        context.startActivity(intent)
      } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
      }
    }
  }

  fun createDeviceAdminIntent(context: Context): Intent {
    val componentName = ComponentName(context, MathWakeDeviceAdminReceiver::class.java)
    return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
      putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
      putExtra(
        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
        "アラーム鳴動中の脱出防止・ロック画面機能の保護に端末管理者権限を使用します。"
      )
    }
  }

  fun requestDeviceAdmin(context: Context) {
    val intent = createDeviceAdminIntent(context)
    if (context !is android.app.Activity) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      context.startActivity(intent)
    } catch (_: Exception) {
      try {
        val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
      } catch (_: Exception) {
        openAppDetails(context)
      }
    }
  }

  fun requestAccessibilityPermission(context: Context) {
    // Opens the accessibility service list. Android has no public API to toggle an
    // accessibility service; the automatic on/off during alarms uses WRITE_SECURE_SETTINGS
    // (see AccessibilityController). This screen is for manual inspection / turning it off.
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      context.startActivity(intent)
    } catch (_: Exception) {
      openAppDetails(context)
    }
  }

  fun openAppNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try {
        context.startActivity(intent)
      } catch (_: Exception) {
        openAppDetails(context)
      }
    } else {
      openAppDetails(context)
    }
  }

  fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.parse("package:${context.packageName}")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
