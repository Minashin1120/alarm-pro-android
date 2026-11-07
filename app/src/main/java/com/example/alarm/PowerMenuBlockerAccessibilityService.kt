package com.example.alarm

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AccessibilityService that detects the system power menu (long-press power button)
 * and immediately closes it with BACK while an alarm is active.
 * This prevents users from powering off / restarting to escape the math alarm.
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
        }
        delay(250)
      }
    }
  }

  private fun checkAndClosePowerMenu() {
    try {
      val root = rootInActiveWindow ?: return
      if (containsPowerRelatedText(root)) {
        // Close the power dialog immediately
        performGlobalAction(GLOBAL_ACTION_BACK)
        // Extra insurance: try again shortly after
        serviceScope.launch {
          delay(100)
          performGlobalAction(GLOBAL_ACTION_BACK)
        }
      }
      root.recycle()
    } catch (_: Exception) {
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

  override fun onDestroy() {
    monitorJob?.cancel()
    super.onDestroy()
  }
}
