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
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.example.alarm.MathWakeDeviceAdminReceiver
import com.example.alarm.PowerMenuBlockerAccessibilityService

data class LockdownPermissionStatus(
  val isOverlayGranted: Boolean,
  val isExactAlarmGranted: Boolean,
  val isNotificationGranted: Boolean,
  val isBatteryOptimizationIgnored: Boolean,
  val isDeviceAdminActive: Boolean,
  val isAccessibilityEnabled: Boolean
) {
  val allCriticalGranted: Boolean
    get() = isOverlayGranted && isExactAlarmGranted && isNotificationGranted && isBatteryOptimizationIgnored && isAccessibilityEnabled

  val missingCount: Int
    get() {
      var count = 0
      if (!isOverlayGranted) count++
      if (!isExactAlarmGranted) count++
      if (!isNotificationGranted) count++
      if (!isBatteryOptimizationIgnored) count++
      if (!isDeviceAdminActive) count++
      if (!isAccessibilityEnabled) count++
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

    // 6. Accessibility Service for power menu blocking
    val isAccessibilityEnabled = isAccessibilityServiceEnabled(context)

    return LockdownPermissionStatus(
      isOverlayGranted = isOverlayGranted,
      isExactAlarmGranted = isExactAlarmGranted,
      isNotificationGranted = isNotificationGranted,
      isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
      isDeviceAdminActive = isDeviceAdminActive,
      isAccessibilityEnabled = isAccessibilityEnabled
    )
  }

  private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, PowerMenuBlockerAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
      if (splitter.next().equals(expected, ignoreCase = true)) {
        return true
      }
    }
    return false
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
