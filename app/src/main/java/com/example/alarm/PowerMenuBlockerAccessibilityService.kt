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
 * AccessibilityService that aggressively closes the notification shade / quick settings
 * and power menu while an alarm is active.
 *
 * Strategy: Do not rely only on fragile detection (OEM-dependent class names).
 * Continuously attempt to dismiss the shade when the alarm is ringing.
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
      // Immediate reaction on any relevant event
      dismissNotificationShadeAggressively()
      checkAndClosePowerMenu()
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
          // Continuously force-dismiss shade while alarm is active
          // This is more reliable than pure detection across OEMs
          dismissNotificationShadeAggressively()
          checkAndClosePowerMenu()
        }
        delay(150)
      }
    }
  }

  private fun dismissNotificationShadeAggressively() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
      } else {
        performGlobalAction(GLOBAL_ACTION_BACK)
      }
    } catch (_: Exception) {}

    // Extra insurance shortly after
    serviceScope.launch {
      delay(40)
      try {
        if (AlarmService.isAlarmActive) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
          } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
          }
        }
      } catch (_: Exception) {}
    }
  }

  private fun checkAndClosePowerMenu() {
    try {
      val root = rootInActiveWindow ?: return
      if (containsPowerRelatedText(root)) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        serviceScope.launch {
          delay(60)
          performGlobalAction(GLOBAL_ACTION_BACK)
        }
      }
      root.recycle()
    } catch (_: Exception) {}
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

  override fun onDestroy() {
    monitorJob?.cancel()
    super.onDestroy()
  }
}
