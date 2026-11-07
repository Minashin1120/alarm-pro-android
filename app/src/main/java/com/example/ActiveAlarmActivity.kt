package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmScheduler
import com.example.alarm.AlarmService
import com.example.math.MathProblem
import com.example.math.MathProblemGenerator
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CrimsonAlarm
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActiveAlarmActivity : ComponentActivity() {

  private var useDeviceVolume: Boolean = false
  private var statusBarBlockerView: View? = null
  private var focusWatchdogJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    configureScreenLockdown()
    setupStatusBarBlocker()

    val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "アラーム"
    val baseDifficulty = intent.getStringExtra(AlarmScheduler.EXTRA_DIFFICULTY) ?: MathProblemGenerator.DIFF_MEDIUM
    val requiredCount = intent.getIntExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, 3).coerceAtLeast(1)
    useDeviceVolume = intent.getBooleanExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, false)
    val volumePercent = intent.getIntExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, 85)

    setContent {
      MyApplicationTheme {
        // Prevent dismissal by back gesture or back button
        BackHandler(enabled = true) {
          // Strictly disabled: user cannot dismiss without completing the math streak!
        }

        ActiveAlarmScreen(
          label = label,
          initialDifficulty = baseDifficulty,
          requiredCount = requiredCount,
          useDeviceVolume = useDeviceVolume,
          volumePercent = volumePercent,
          onDismissAlarm = {
            dismissAlarmAndExit()
          }
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    try { startLockTask() } catch (_: Exception) {}
    AlarmService.notifyActivityResumed()
    setupStatusBarBlocker()
    applyImmersiveSystemBars()
    applyDeviceAdminKeyguardLock()

    focusWatchdogJob?.cancel()
    focusWatchdogJob = lifecycleScope.launch {
      while (isActive) {
        kotlinx.coroutines.delay(200)
        if (!hasWindowFocus()) {
          collapseStatusBar()
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    focusWatchdogJob?.cancel()
    AlarmService.notifyActivityPaused(this)
  }

  override fun onDestroy() {
    super.onDestroy()
    focusWatchdogJob?.cancel()
    removeStatusBarBlocker()
  }

  private fun setupStatusBarBlocker() {
    if (statusBarBlockerView != null) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
      try {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val blocker = View(this).apply {
          setBackgroundColor(android.graphics.Color.TRANSPARENT)
          setOnTouchListener { _, _ ->
            collapseStatusBar()
            true
          }
        }
        val heightPx = (resources.displayMetrics.density * 56).toInt().coerceAtLeast(120)
        val params = WindowManager.LayoutParams(
          WindowManager.LayoutParams.MATCH_PARENT,
          heightPx,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
          PixelFormat.TRANSLUCENT
        ).apply {
          gravity = Gravity.TOP
        }
        wm.addView(blocker, params)
        statusBarBlockerView = blocker
      } catch (_: Exception) {}
    }
  }

  private fun removeStatusBarBlocker() {
    statusBarBlockerView?.let { blocker ->
      try {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.removeView(blocker)
      } catch (_: Exception) {}
      statusBarBlockerView = null
    }
  }

  private fun applyDeviceAdminKeyguardLock() {
    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
    val comp = android.content.ComponentName(this, com.example.alarm.MathWakeDeviceAdminReceiver::class.java)
    if (dpm?.isAdminActive(comp) == true) {
      try {
        dpm.setKeyguardDisabledFeatures(
          comp,
          android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL
        )
      } catch (_: Exception) {}
    }
  }

  private fun clearDeviceAdminKeyguardLock() {
    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
    val comp = android.content.ComponentName(this, com.example.alarm.MathWakeDeviceAdminReceiver::class.java)
    if (dpm?.isAdminActive(comp) == true) {
      try {
        dpm.setKeyguardDisabledFeatures(
          comp,
          android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
        )
      } catch (_: Exception) {}
    }
  }

  private fun collapseStatusBar() {
    try {
      val statusBarService = getSystemService("statusbar")
      val statusBarManager = Class.forName("android.app.StatusBarManager")
      val collapseMethod = statusBarManager.getMethod("collapsePanels")
      collapseMethod.invoke(statusBarService)
    } catch (_: Exception) {
      try {
        val statusBarService = getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val collapseMethod = statusBarManager.getMethod("collapse")
        collapseMethod.invoke(statusBarService)
      } catch (_: Exception) {
        try {
          @Suppress("DEPRECATION")
          val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
          sendBroadcast(closeDialogs)
        } catch (_: Exception) {}
      }
    }
  }

  private fun configureScreenLockdown() {
    // Keep device locked behind the activity - do NOT dismiss keyguard!
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    }
    window.addFlags(
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
      WindowManager.LayoutParams.FLAG_FULLSCREEN or
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    applyImmersiveSystemBars()
  }

  private fun applyImmersiveSystemBars() {
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      applyImmersiveSystemBars()
    } else {
      // Immediately collapse status bar / quick settings multiple times during pull down
      collapseStatusBar()
      window.decorView.postDelayed({ collapseStatusBar() }, 50)
      window.decorView.postDelayed({ collapseStatusBar() }, 150)
      window.decorView.postDelayed({ collapseStatusBar() }, 300)
      applyImmersiveSystemBars()
      // If camera or another app stole focus, notify service to reclaim screen immediately
      AlarmService.notifyActivityPaused(this)
    }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // Always consume power button to prevent power menu
    if (event.keyCode == KeyEvent.KEYCODE_POWER) {
      return true
    }
    if (!useDeviceVolume) {
      if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
        // Consume the volume button event to block muting
        return true
      }
    }
    return super.dispatchKeyEvent(event)
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_POWER) {
      event?.startTracking()
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_POWER) {
      return true
    }
    return super.onKeyLongPress(keyCode, event)
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_POWER) {
      return true
    }
    return super.onKeyUp(keyCode, event)
  }

  private fun dismissAlarmAndExit() {
    clearDeviceAdminKeyguardLock()
    removeStatusBarBlocker()

    try {
      stopLockTask()
    } catch (_: Exception) {}

    val stopIntent = Intent(this, AlarmService::class.java).apply {
      action = AlarmService.ACTION_STOP_ALARM
    }
    startService(stopIntent)
    finishAndRemoveTask()
  }
}

@Composable
fun ActiveAlarmScreen(
  label: String,
  initialDifficulty: String,
  requiredCount: Int,
  useDeviceVolume: Boolean,
  volumePercent: Int,
  onDismissAlarm: () -> Unit
) {
  val context = LocalContext.current
  var currentDifficulty by remember { mutableStateOf(initialDifficulty) }
  var streak by remember { mutableIntStateOf(0) }
  var currentProblem by remember { mutableStateOf(MathProblemGenerator.generate(currentDifficulty)) }
  var questionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

  var feedbackMessage by remember { mutableStateOf<String?>(null) }
  var isCorrectFeedback by remember { mutableStateOf(false) }
  var isEscalated by remember { mutableStateOf(false) }
  var isCompleted by remember { mutableStateOf(false) }

  // Realtime clock
  var currentTimeString by remember { mutableStateOf("") }
  LaunchedEffect(Unit) {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    while (true) {
      currentTimeString = formatter.format(Date())
      delay(1000)
    }
  }

  // Header pulse animation
  val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )

  fun triggerHapticError() {
    try {
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(350)
      }
    } catch (_: Exception) {}
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Slate950
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Slate950,
              if (isCompleted) EmeraldDark.copy(alpha = 0.35f) else CrimsonAlarm.copy(alpha = 0.2f),
              Slate900
            )
          )
        )
    ) {
      if (isCompleted) {
        // Completion / Celebration Screen
        Column(
          modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("alarm_cleared_screen"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Cleared",
            tint = EmeraldSuccess,
            modifier = Modifier.size(96.dp)
          )
          Spacer(modifier = Modifier.height(20.dp))
          Text(
            text = "起床成功！",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Slate100
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "計算難問 $requiredCount 問を連続正解しました！\n脳が完全に覚醒しました。",
            style = MaterialTheme.typography.bodyLarge,
            color = Slate400,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(32.dp))
          Button(
            onClick = onDismissAlarm,
            modifier = Modifier
              .fillMaxWidth(0.85f)
              .height(56.dp)
              .testTag("confirm_dismiss_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = EmeraldSuccess,
              contentColor = Slate950
            ),
            shape = RoundedCornerShape(16.dp)
          ) {
            Text(
              text = "アラームを停止して完了",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      } else {
        // Active Quiz & Lockdown Content
        Column(
          modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // 1. Alarm Ringing Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(Slate900)
              .border(1.dp, CrimsonAlarm.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .scale(pulseScale)
                  .clip(CircleShape)
                  .background(CrimsonAlarm),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Alarm,
                  contentDescription = "Alarm Ringing",
                  tint = Color.White,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = label.ifBlank { "目覚ましアラーム" },
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Slate100
                )
                Text(
                  text = if (useDeviceVolume) "デバイス音量を使用" else "音量ロック中 ($volumePercent%)",
                  style = MaterialTheme.typography.bodySmall,
                  color = Slate400
                )
              }
            }
            Text(
              text = currentTimeString,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = AmberPrimary,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 2. Progress / Streak
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "解答ストリーク: $streak / $requiredCount",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = if (isEscalated) OrangeAccent else AmberPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
              progress = { streak.toFloat() / requiredCount.toFloat() },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
              color = if (isEscalated) OrangeAccent else AmberPrimary,
              trackColor = Slate800
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 3. Math Problem
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("math_problem_card"),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = currentProblem.questionText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Slate100,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "難易度: ${currentDifficulty.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = Slate400
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 4. Options Grid
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            currentProblem.options.chunked(2).forEach { rowOptions ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                rowOptions.forEach { option ->
                  QuizOptionCard(
                    modifier = Modifier.weight(1f),
                    value = option,
                    onClick = {
                      val elapsed = System.currentTimeMillis() - questionStartTime
                      if (option == currentProblem.correctAnswer) {
                        isCorrectFeedback = true
                        streak += 1
                        feedbackMessage = if (elapsed < 3000) "⚡ 高速正解！難易度が上昇" else "✅ 正解！"
                        if (elapsed < 3000 && currentDifficulty != MathProblemGenerator.DIFF_HARD) {
                          isEscalated = true
                          currentDifficulty = when (currentDifficulty) {
                            MathProblemGenerator.DIFF_EASY -> MathProblemGenerator.DIFF_MEDIUM
                            MathProblemGenerator.DIFF_MEDIUM -> MathProblemGenerator.DIFF_HARD
                            else -> MathProblemGenerator.DIFF_HARD
                          }
                        }
                        if (streak >= requiredCount) {
                          isCompleted = true
                        } else {
                          currentProblem = MathProblemGenerator.generate(currentDifficulty)
                          questionStartTime = System.currentTimeMillis()
                        }
                      } else {
                        // INCORRECT: User requested progress reset!
                        triggerHapticError()
                        isCorrectFeedback = false
                        streak = 0 // Reset progress to 0!
                        feedbackMessage = "❌ 不正解！カウントがリセットされました (正解: ${currentProblem.correctAnswer})"
                        currentProblem = MathProblemGenerator.generate(currentDifficulty)
                        questionStartTime = System.currentTimeMillis()
                      }
                    }
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "※誤答するとカウントが0にリセットされます。\n素早く正解すると難易度が昇格します！",
            fontSize = 11.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
          )
        }
      }

      // Top status bar pull-down shield: intercepts swipe gestures from top edge
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(72.dp)
          .align(Alignment.TopCenter)
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                try {
                  val statusBarService = context.getSystemService("statusbar")
                  val statusBarManager = Class.forName("android.app.StatusBarManager")
                  val collapseMethod = statusBarManager.getMethod("collapsePanels")
                  collapseMethod.invoke(statusBarService)
                } catch (_: Exception) {}
              }
            }
          }
      )
    }
  }
}

@Composable
fun QuizOptionCard(
  modifier: Modifier = Modifier,
  value: Int,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(Slate900)
      .border(1.5.dp, Slate700, RoundedCornerShape(16.dp))
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = value.toString(),
      fontSize = if (value.toString().length > 4) 22.sp else 26.sp,
      fontWeight = FontWeight.ExtraBold,
      fontFamily = FontFamily.Monospace,
      color = AmberPrimary
    )
  }
}
