package com.example.util

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.alarm.PowerMenuBlockerAccessibilityService

/**
 * Keeps the optional accessibility fallback off while no alarm is ringing.
 *
 * Why this exists: payment / banking apps commonly flag any enabled non-system accessibility
 * service without checking what it can actually read. Play Integrity's App Access Risk also
 * reports side-loaded apps with such a service as `UNKNOWN_CAPTURING` / `UNKNOWN_CONTROLLING`.
 * Detection therefore does not require the service to read the screen, and the install source
 * makes it riskier than a Play-installed accessibility app. Keeping the service enabled all day
 * breaks those apps even though it only does useful work during an alarm.
 *
 * Android has no public API that lets a normal app enable an accessibility service:
 *  - Device Owner installs do not need this service at all (lock task mode already blocks the
 *    shade and power menu), so the service simply disables itself when idle.
 *  - With `WRITE_SECURE_SETTINGS` granted over ADB, the service is added when an alarm starts
 *    and removed again when the alarm stops.
 *  - Without either capability the service has to stay enabled (previous behaviour); the setup
 *    UI explains how to move to the automatic mode.
 */
object AccessibilityController {

  private const val PREFS_NAME = "mathwake_accessibility"
  private const val KEY_MASTER_WAS_ENABLED = "master_was_enabled"

  fun componentName(context: Context): ComponentName =
    ComponentName(context, PowerMenuBlockerAccessibilityService::class.java)

  /** True when this app's accessibility service is currently enabled in system settings. */
  fun isServiceEnabled(context: Context): Boolean {
    val masterEnabled = Settings.Secure.getInt(
      context.contentResolver,
      Settings.Secure.ACCESSIBILITY_ENABLED,
      0
    ) == 1
    if (!masterEnabled) return false

    val target = componentName(context).flattenToString()
    val enabled = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return AccessibilityServiceList.parse(enabled)
      .any { it.equals(target, ignoreCase = true) }
  }

  /** Granted from a PC with `adb shell pm grant ... WRITE_SECURE_SETTINGS`. */
  fun hasSecureSettingsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED

  fun isDeviceOwner(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    return dpm?.isDeviceOwnerApp(context.packageName) == true
  }

  /**
   * True when the service may disable itself while no alarm is ringing.
   *
   * Device Owner mode does not need the service (lock task mode is stronger), and with
   * `WRITE_SECURE_SETTINGS` the app can turn it back on when the next alarm fires. Without one
   * of those, disabling now would permanently remove the escape protection.
   */
  fun canDisableWhileIdle(context: Context): Boolean =
    hasSecureSettingsPermission(context) || isDeviceOwner(context)

  /**
   * Enables the fallback for the alarm that is currently ringing.
   *
   * Returns false when the app cannot (or does not need to) enable it.
   */
  fun enableForAlarm(context: Context): Boolean {
    if (isDeviceOwner(context)) return false
    if (!hasSecureSettingsPermission(context)) return false
    if (isServiceEnabled(context)) return true

    return try {
      val resolver = context.contentResolver
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      if (!prefs.contains(KEY_MASTER_WAS_ENABLED)) {
        val masterWasEnabled = Settings.Secure.getInt(
          resolver,
          Settings.Secure.ACCESSIBILITY_ENABLED,
          0
        ) == 1
        prefs.edit().putBoolean(KEY_MASTER_WAS_ENABLED, masterWasEnabled).commit()
      }

      val target = componentName(context).flattenToString()
      val listWritten = Settings.Secure.putString(
        resolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        AccessibilityServiceList.add(
          Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
          ),
          target
        )
      )
      val masterWritten = Settings.Secure.putInt(
        resolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        1
      )
      listWritten && masterWritten
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Removes this app's service from the enabled list while preserving other services.
   * Does nothing when the app lacks [Manifest.permission.WRITE_SECURE_SETTINGS].
   */
  fun disable(context: Context): Boolean {
    if (!hasSecureSettingsPermission(context)) return false

    return try {
      val resolver = context.contentResolver
      val target = componentName(context).flattenToString()
      val current = Settings.Secure.getString(
        resolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
      )
      val isListed = AccessibilityServiceList.parse(current)
        .any { it.equals(target, ignoreCase = true) }
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

      if (isListed) {
        val remaining = AccessibilityServiceList.remove(current, target)
        Settings.Secure.putString(
          resolver,
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
          remaining
        )

        val masterWasEnabled = prefs.getBoolean(KEY_MASTER_WAS_ENABLED, true)
        if (remaining.isEmpty() || !masterWasEnabled) {
          Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        }
      }
      prefs.edit().remove(KEY_MASTER_WAS_ENABLED).commit()
      true
    } catch (_: Exception) {
      false
    }
  }

  fun secureSettingsGrantCommand(context: Context): String = buildString {
    append("adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Android 13+ gates accessibility services of side-loaded apps behind
      // ACCESS_RESTRICTED_SETTINGS; allowing it lets the automatic toggle bind the service.
      append("\nadb shell cmd appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow")
    }
  }
}
