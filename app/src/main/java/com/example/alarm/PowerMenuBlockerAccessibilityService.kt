package com.example.alarm

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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
 * best-effort layer for normal installs: it notices that System UI opened the notification shade
 * or the power menu while an alarm is ringing and closes it immediately.
 *
 * The service is intentionally unprivileged. It declares no window-content retrieval, no key event
 * filtering and no gesture capability (see `res/xml/accessibility_service_config.xml`), because
 * requesting those makes banking / payment apps warn the user about this app. Detection therefore
 * relies only on the class names reported by accessibility window events.
 *
 * Important limitations on Android 12+:
 *  - The power button itself cannot be intercepted by a normal app or accessibility service.
 *  - The power menu is only closed *after* System UI opens it.
 *  - Android 14+ requires "Allow restricted settings" before a sideloaded APK can enable this
 *    accessibility service.
 *  - Any enabled accessibility service can still be flagged by some banking apps. The service is
 *    optional: Device Owner installs do not need it.
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

  override fun onServiceConnected() {
    super.onServiceConnected()

    // Subscribe to window changes only. No content retrieval / key filtering is requested here.
    serviceInfo?.let { info ->
      info.eventTypes = info.eventTypes or
        AccessibilityEvent.TYPE_WINDOWS_CHANGED or
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
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
      }
      isShadeClass(className) && isSystemUiPackage(event?.packageName) -> {
        closeNotificationShade()
      }
    }
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
        } else {
          removeTopShield()
          removePowerBlocker()
          powerBlockerConsumed = false
        }
        // Stay cheap while idle; react quickly only while an alarm is actually ringing.
        delay(if (AlarmService.isAlarmActive) ACTIVE_POLL_INTERVAL_MS else IDLE_POLL_INTERVAL_MS)
      }
    }
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

  private companion object {
    const val ACTIVE_POLL_INTERVAL_MS = 150L
    const val IDLE_POLL_INTERVAL_MS = 500L
  }
}
