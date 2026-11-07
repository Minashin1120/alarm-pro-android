package com.example.alarm

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AccessibilityService that detects the system power menu and notification shade
 * (quick settings) while an alarm is active, and immediately closes them.
 * This prevents users from powering off / restarting or enabling silent mode
 * via the notification bar to escape the math alarm.
 */
class PowerMenuBlockerAccessibilityService : AccessibilityService() {

  private val serviceScope = CoroutineScope(Dispatchers.Default)
  private var monitorJob: Job? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    startContinuousMonitor()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (AlarmService.isAlarmActive) {
      checkAndClosePowerMenu()
      checkAndCloseNotificationShade(event)
    }
  }

  override fun onInterrupt() {
    // No-op
  }

  private fun startContinuousMonitor() {
    monitorJob?.cancel()
    monitorJob = serviceScope.launch {
      while (isActive) {
        if (AlarmService.isAlarmActive) {
          checkAndClosePowerMenu()
          checkAndCloseNotificationShade(null)
        }
        delay(200)
      }
    }
  }

  private fun checkAndClosePowerMenu() {
    try {
      val root = rootInActiveWindow ?: return
      if (containsPowerRelatedText(root)) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        serviceScope.launch {
          delay(80)
          performGlobalAction(GLOBAL_ACTION_BACK)
        }
      }
      root.recycle()
    } catch (_: Exception) {
    }
  }

  private fun checkAndCloseNotificationShade(event: AccessibilityEvent?) {
    try {
      var shouldDismiss = false

      // Detect via event package/class
      event?.let {
        val pkg = it.packageName?.toString() ?: ""
        val cls = it.className?.toString() ?: ""
        if (pkg == "com.android.systemui" && (
            cls.contains("NotificationPanel", ignoreCase = true) ||
            cls.contains("StatusBar", ignoreCase = true) ||
            cls.contains("QuickSettings", ignoreCase = true) ||
            cls.contains("Shade", ignoreCase = true) ||
            cls.contains("Panel", ignoreCase = true)
          )) {
          shouldDismiss = true
        }
      }

      // Also scan active window content for shade indicators
      val root = rootInActiveWindow
      if (root != null) {
        if (containsNotificationShadeIndicators(root)) {
          shouldDismiss = true
        }
        root.recycle()
      }

      if (shouldDismiss) {
        dismissNotificationShade()
      }
    } catch (_: Exception) {
    }
  }

  private fun dismissNotificationShade() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    } else {
      performGlobalAction(GLOBAL_ACTION_BACK)
    }
    // Extra insurance
    serviceScope.launch {
      delay(50)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
      } else {
        performGlobalAction(GLOBAL_ACTION_BACK)
      }
    }
  }

  private fun containsPowerRelatedText(node: AccessibilityNodeInfo?): Boolean {
    if (node == null) return false

    val text = (node.text?.toString() ?: "").lowercase()
    val desc = (node.contentDescription?.toString() ?: "").lowercase()
    val combined = "$text $desc"

    val keywords = listOf(
      "power off", "shut down", "shutdown", "restart", "reboot",
      "電源オフ", "再起動", "シャットダウン", "緊急", "emergency",
      "power menu", "電源オプション", "lockdown", "ロックダウン"
    )

    if (keywords.any { combined.contains(it) }) {
      return true
    }

    for (i in 0 until node.childCount) {
      val child = node.getChild(i)
      if (containsPowerRelatedText(child)) {
        child?.recycle()
        return true
      }
      child?.recycle()
    }
    return false
  }

  private fun containsNotificationShadeIndicators(node: AccessibilityNodeInfo?): Boolean {
    if (node == null) return false

    val text = (node.text?.toString() ?: "").lowercase()
    val desc = (node.contentDescription?.toString() ?: "").lowercase()
    val combined = "$text $desc"

    val keywords = listOf(
      "silent", "サイレント", "do not disturb", "dnd", "インタラプトしない",
      "volume", "音量", "quick settings", "クイック設定",
      "notification", "通知", "ringer", "着信音",
      "media volume", "メディア音量", "alarm volume", "アラーム音量"
    )

    if (keywords.any { combined.contains(it) }) {
      return true
    }

    // Also check package if available on the node
    val pkg = node.packageName?.toString() ?: ""
    if (pkg == "com.android.systemui" && (text.isNotEmpty() || desc.isNotEmpty())) {
      // Likely shade content when systemui has interactive text
      if (combined.length > 2) return true
    }

    for (i in 0 until node.childCount) {
      val child = node.getChild(i)
      if (containsNotificationShadeIndicators(child)) {
        child?.recycle()
        return true
      }
      child?.recycle()
    }
    return false
  }

  override fun onDestroy() {
    monitorJob?.cancel()
    super.onDestroy()
  }
}
