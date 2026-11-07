package com.example.alarm

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fallback protection used when the app is not provisioned as Device Owner.
 *
 * A Device Owner gets real OS-level protection through [LockTaskController]. This service is the
 * best-effort layer for normal installs: it detects the notification shade / power menu in the
 * System UI window tree and closes it immediately.
 *
 * Important limitations on Android 12+:
 *  - The power button itself cannot be intercepted by a normal app or accessibility service.
 *  - The power menu is only closed *after* System UI opens it.
 *  - Android 14+ requires "Allow restricted settings" before a sideloaded APK can enable this
 *    accessibility service.
 */
class PowerMenuBlockerAccessibilityService : AccessibilityService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var monitorJob: Job? = null
  private var lastShadeActionAt = 0L
  private var lastPowerActionAt = 0L

  private var topShieldView: View? = null
  private var powerBlockerView: View? = null
  private var powerBlockerHideAt = 0L
  private var powerBlockerConsumed = false

  private data class SystemUiWindowState(
    val powerMenu: Boolean,
    val notificationShade: Boolean
  )

  override fun onServiceConnected() {
    super.onServiceConnected()

    serviceInfo?.let { info ->
      info.flags = info.flags or
        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
        AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
      info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOWS_CHANGED
      info.notificationTimeout = 50
      serviceInfo = info
    }

    startMonitor()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!AlarmService.isAlarmActive) return

    // Some OEMs use a non-SystemUI package for the power dialog, so check the class first.
    val className = event?.className?.toString()?.lowercase().orEmpty()
    when {
      isPowerClass(className) -> {
        showPowerBlocker()
        closePowerMenu()
        return
      }
      isShadeClass(className) && isSystemUiPackage(event?.packageName) -> {
        closeNotificationShade()
        return
      }
    }

    if (!isSystemUiPackage(event?.packageName)) return

    val state = scanSystemUiWindows()
    if (state.powerMenu) {
      showPowerBlocker()
      closePowerMenu()
    } else if (state.notificationShade) {
      closeNotificationShade()
    }
  }

  override fun onKeyEvent(event: KeyEvent): Boolean {
    // On most devices the power key is consumed by the system before accessibility services,
    // but some OEMs deliver it. Consume both down and up to keep the event stream consistent.
    if (AlarmService.isAlarmActive && event.keyCode == KeyEvent.KEYCODE_POWER) {
      return true
    }
    return super.onKeyEvent(event)
  }

  override fun onInterrupt() {
    // No-op
  }

  override fun onDestroy() {
    monitorJob?.cancel()
    monitorJob = null
    removeTopShield()
    removePowerBlocker()
    powerBlockerConsumed = false
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun startMonitor() {
    monitorJob?.cancel()
    monitorJob = serviceScope.launch {
      while (isActive) {
        if (AlarmService.isAlarmActive) {
          ensureTopShield()

          val now = SystemClock.uptimeMillis()
          if (powerBlockerView != null && now >= powerBlockerHideAt) {
            removePowerBlocker()
          }

          val state = scanSystemUiWindows()
          if (state.powerMenu) {
            showPowerBlocker()
            closePowerMenu()
          } else {
            // The user can interact normally again once the power menu is gone.
            powerBlockerConsumed = false
            removePowerBlocker()
            if (state.notificationShade) {
              closeNotificationShade()
            }
          }
        } else {
          removeTopShield()
          removePowerBlocker()
          powerBlockerConsumed = false
        }
        delay(200)
      }
    }
  }

  private fun scanSystemUiWindows(): SystemUiWindowState {
    var powerMenu = false
    var notificationShade = false
    val screenHeight = resources.displayMetrics.heightPixels

    val currentWindows = windows ?: return SystemUiWindowState(false, false)
    for (window in currentWindows) {
      if (!window.isActive && !window.isFocused) continue

      val root = window.root ?: continue
      try {
        val packageName = root.packageName?.toString().orEmpty()
        val className = root.className?.toString().orEmpty()
        val lower = className.lowercase()

        // Power menu detection does not depend on the package; OEMs move it around.
        if (isPowerClass(lower)) {
          powerMenu = true
        }

        if (isSystemUiPackage(packageName)) {
          if (isShadeClass(lower)) {
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            // The status bar window exists even when the shade is closed. Treat it as open only
            // when it covers a large part of the screen, or when the class is a shade/QS panel.
            val coversScreen = bounds.height() > screenHeight / 2
            if (coversScreen ||
              lower.contains("shade") ||
              lower.contains("qspanel") ||
              lower.contains("quicksettings") ||
              lower.contains("notificationpanel")
            ) {
              notificationShade = true
            }
          }

          // Text fallback for System UI variants without recognizable class names.
          if (!powerMenu && containsPowerRelatedText(root)) {
            powerMenu = true
          }
        }
      } catch (_: Exception) {
        // Window may disappear while it is being inspected.
      } finally {
        @Suppress("DEPRECATION")
        root.recycle()
      }
    }

    return SystemUiWindowState(powerMenu, notificationShade)
  }

  /**
   * Transparent touch shield over the status bar area.
   *
   * Accessibility overlays are layered above the status bar, so this blocks the downward swipe
   * that opens the notification shade even when the app is not Device Owner.
   */
  private fun ensureTopShield() {
    if (topShieldView != null) return
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

    val shield = View(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      setOnTouchListener { _, _ -> true }
    }

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      topShieldHeightPx(),
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP
    }

    try {
      windowManager.addView(shield, params)
      topShieldView = shield
    } catch (_: Exception) {
      // Overlay permission can be revoked while the service is running.
    }
  }

  private fun removeTopShield() {
    val view = topShieldView ?: return
    topShieldView = null
    try {
      (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(view)
    } catch (_: Exception) {
    }
  }

  private fun topShieldHeightPx(): Int {
    val res = resources
    val id = res.getIdentifier("status_bar_height", "dimen", "android")
    val statusBarHeight = if (id > 0) {
      res.getDimensionPixelSize(id)
    } else {
      (24 * res.displayMetrics.density).toInt()
    }
    return statusBarHeight + (24 * res.displayMetrics.density).toInt()
  }

  /**
   * Full-screen touch shield shown only for a short moment when the power menu appears.
   * It prevents an immediate tap on "Restart"/"Power off" while the back action closes it.
   */
  private fun showPowerBlocker() {
    if (powerBlockerConsumed) return
    powerBlockerConsumed = true

    val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
    val blocker = View(this).apply {
      setBackgroundColor(Color.TRANSPARENT)
      setOnTouchListener { _, _ -> true }
    }

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    )

    try {
      windowManager.addView(blocker, params)
      powerBlockerView = blocker
      powerBlockerHideAt = SystemClock.uptimeMillis() + 700L
    } catch (_: Exception) {
      powerBlockerConsumed = false
    }
  }

  private fun removePowerBlocker() {
    val view = powerBlockerView ?: return
    powerBlockerView = null
    powerBlockerHideAt = 0L
    try {
      (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(view)
    } catch (_: Exception) {
    }
  }

  private fun closeNotificationShade() {
    val now = SystemClock.uptimeMillis()
    if (now - lastShadeActionAt < 120L) return
    lastShadeActionAt = now

    var handled = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      handled = performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }
    if (!handled) {
      performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // Second attempt in case the shade animation was still starting.
    serviceScope.launch {
      delay(80L)
      if (AlarmService.isAlarmActive) {
        performGlobalAction(GLOBAL_ACTION_BACK)
      }
    }
  }

  private fun closePowerMenu() {
    val now = SystemClock.uptimeMillis()
    if (now - lastPowerActionAt < 150L) return
    lastPowerActionAt = now

    performGlobalAction(GLOBAL_ACTION_BACK)
    serviceScope.launch {
      delay(60L)
      if (AlarmService.isAlarmActive) {
        performGlobalAction(GLOBAL_ACTION_BACK)
      }
    }
  }

  private fun isPowerClass(className: String): Boolean =
    className.contains("globalactions") ||
      className.contains("powermenu") ||
      className.contains("powerdialog")

  private fun isShadeClass(className: String): Boolean =
    className.contains("shade") ||
      className.contains("statusbar") ||
      className.contains("qspanel") ||
      className.contains("quicksettings") ||
      className.contains("notificationpanel")

  private fun isSystemUiPackage(packageName: CharSequence?): Boolean {
    val pkg = packageName?.toString()?.lowercase() ?: return false
    return pkg == "com.android.systemui" ||
      pkg.contains("systemui") ||
      pkg == "com.miui.securitycenter" ||
      pkg.contains("samsung.android.app.power") ||
      pkg.contains("samsung.android.globalactions")
  }

  private fun containsPowerRelatedText(
    node: AccessibilityNodeInfo?,
    depth: Int = 0
  ): Boolean {
    if (node == null || depth > 8) return false

    val text = node.text?.toString().orEmpty()
    val description = node.contentDescription?.toString().orEmpty()
    val combined = "$text $description".lowercase()
    if (POWER_KEYWORDS.any { combined.contains(it) }) {
      return true
    }

    for (index in 0 until node.childCount) {
      val child = node.getChild(index) ?: continue
      try {
        if (containsPowerRelatedText(child, depth + 1)) {
          return true
        }
      } finally {
        @Suppress("DEPRECATION")
        child.recycle()
      }
    }
    return false
  }

  private companion object {
    val POWER_KEYWORDS = listOf(
      "power off",
      "shut down",
      "shutdown",
      "restart",
      "reboot",
      "power menu",
      "emergency",
      "lockdown",
      "電源オフ",
      "電源を切る",
      "再起動",
      "シャットダウン",
      "緊急通報",
      "緊急",
      "電源オプション",
      "ロックダウン"
    )
  }
}
