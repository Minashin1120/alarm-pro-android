package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ActiveAlarmActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmService : Service() {

  companion object {
    const val CHANNEL_ID = "mathwake_alarm_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_START_ALARM = "com.aistudio.mathwake.ACTION_START_ALARM"
    const val ACTION_STOP_ALARM = "com.aistudio.mathwake.ACTION_STOP_ALARM"
    const val ACTION_RECLAIM_FOCUS = "com.aistudio.mathwake.ACTION_RECLAIM_FOCUS"

    @Volatile
    var isAlarmActive: Boolean = false
      private set

    @Volatile
    var currentAlarmId: Long = -1L
      private set

    @Volatile
    var isActivityForeground: Boolean = false

    fun notifyActivityResumed() {
      isActivityForeground = true
    }

    fun notifyActivityPaused(context: Context) {
      isActivityForeground = false
      if (isAlarmActive) {
        // Send reclaim intent immediately to pull alarm screen back over Camera/other apps
        val intent = Intent(context, AlarmService::class.java).apply {
          action = ACTION_RECLAIM_FOCUS
        }
        try {
          context.startService(intent)
        } catch (_: Exception) {}
      }
    }
  }

  private var mediaPlayer: MediaPlayer? = null
  private var vibrator: Vibrator? = null
  private var volumeLockJob: Job? = null
  private var foregroundEnforcerJob: Job? = null
  private val serviceScope = CoroutineScope(Dispatchers.Default)

  private var cachedLabel: String = "アラーム"
  private var cachedDifficulty: String = "MEDIUM"
  private var cachedRequiredCount: Int = 3
  private var cachedUseDeviceVolume: Boolean = false
  private var cachedVolumePercent: Int = 85

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    initVibrator()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: ACTION_START_ALARM

    if (action == ACTION_STOP_ALARM) {
      stopAlarm()
      return START_NOT_STICKY
    }

    if (action == ACTION_RECLAIM_FOCUS) {
      reclaimScreenFocus()
      return START_STICKY
    }

    val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
    cachedLabel = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "アラーム"
    cachedDifficulty = intent?.getStringExtra(AlarmScheduler.EXTRA_DIFFICULTY) ?: "MEDIUM"
    cachedRequiredCount = intent?.getIntExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, 3) ?: 3
    cachedUseDeviceVolume = intent?.getBooleanExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, false) ?: false
    cachedVolumePercent = intent?.getIntExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, 85) ?: 85
    val shouldVibrate = intent?.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true) ?: true

    currentAlarmId = alarmId
    isAlarmActive = true

    // 1. Start foreground with high priority full-screen intent
    val notification = buildAlarmNotification(
      alarmId,
      cachedLabel,
      cachedDifficulty,
      cachedRequiredCount,
      cachedUseDeviceVolume,
      cachedVolumePercent
    )
    startForeground(NOTIFICATION_ID, notification)

    // 2. Play Sound
    startSoundPlayback()

    // 3. Start Vibration
    if (shouldVibrate) {
      startVibration()
    }

    // 4. Volume Lockdown (if useDeviceVolume is false)
    manageVolume(cachedUseDeviceVolume, cachedVolumePercent)

    // 5. Watchdog to continuously pull screen to front if Camera or other activity interrupts
    startForegroundEnforcer()

    return START_STICKY
  }

  private fun reclaimScreenFocus() {
    serviceScope.launch {
      delay(400) // Brief delay to let the interrupting launch settle before grabbing back
      if (isAlarmActive) {
        launchActiveAlarmActivity()
      }
    }
  }

  private fun startForegroundEnforcer() {
    foregroundEnforcerJob?.cancel()
    foregroundEnforcerJob = serviceScope.launch {
      while (isActive && isAlarmActive) {
        delay(1200)
        // If the activity lost focus (e.g. camera launched via double-press power button)
        if (!isActivityForeground && isAlarmActive) {
          launchActiveAlarmActivity()
        }
      }
    }
  }

  private fun launchActiveAlarmActivity() {
    try {
      val intent = Intent(this@AlarmService, ActiveAlarmActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(AlarmScheduler.EXTRA_ALARM_ID, currentAlarmId)
        putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, cachedLabel)
        putExtra(AlarmScheduler.EXTRA_DIFFICULTY, cachedDifficulty)
        putExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, cachedRequiredCount)
        putExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, cachedUseDeviceVolume)
        putExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, cachedVolumePercent)
      }
      startActivity(intent)
    } catch (e: Exception) {
      Log.w("AlarmService", "Failed to launch ActiveAlarmActivity from service", e)
    }
  }

  private fun buildAlarmNotification(
    alarmId: Long,
    label: String,
    difficulty: String,
    requiredCount: Int,
    useDeviceVolume: Boolean,
    volumePercent: Int
  ): Notification {
    val fullScreenIntent = Intent(this, ActiveAlarmActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_NO_USER_ACTION or
              Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
      putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
      putExtra(AlarmScheduler.EXTRA_DIFFICULTY, difficulty)
      putExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, requiredCount)
      putExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, useDeviceVolume)
      putExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, volumePercent)
    }

    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("【MathWake】アラーム鳴動中！")
      .setContentText("計算問題を解いてアラームを停止してください")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setAutoCancel(false)
      .setFullScreenIntent(pendingIntent, true)
      .setContentIntent(pendingIntent)
      .build()
  }

  private fun startSoundPlayback() {
    try {
      mediaPlayer?.release()

      var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      if (alarmUri == null) {
        alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      }
      if (alarmUri == null) {
        alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      }

      mediaPlayer = MediaPlayer().apply {
        setDataSource(this@AlarmService, alarmUri)
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
        isLooping = true
        prepare()
        start()
      }
    } catch (e: Exception) {
      Log.e("AlarmService", "Failed to start media player", e)
    }
  }

  private fun initVibrator() {
    vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vibratorManager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  private fun startVibration() {
    vibrator?.let { vib ->
      if (!vib.hasVibrator()) return
      val pattern = longArrayOf(0, 800, 300, 800, 300)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = VibrationEffect.createWaveform(pattern, 0)
        vib.vibrate(effect)
      } else {
        @Suppress("DEPRECATION")
        vib.vibrate(pattern, 0)
      }
    }
  }

  private fun manageVolume(useDeviceVolume: Boolean, volumePercent: Int) {
    volumeLockJob?.cancel()

    if (useDeviceVolume) {
      return
    }

    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    val targetVol = ((maxVol * volumePercent) / 100).coerceIn(1, maxVol)

    try {
      audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)
    } catch (e: Exception) {
      Log.w("AlarmService", "Could not set initial alarm volume", e)
    }

    volumeLockJob = serviceScope.launch {
      while (isActive && isAlarmActive) {
        delay(400)
        try {
          val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
          if (currentVol < targetVol) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)
          }
        } catch (_: Exception) {
        }
      }
    }
  }

  private fun stopAlarm() {
    isAlarmActive = false
    currentAlarmId = -1L
    isActivityForeground = false

    volumeLockJob?.cancel()
    volumeLockJob = null

    foregroundEnforcerJob?.cancel()
    foregroundEnforcerJob = null

    try {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    } catch (_: Exception) {
    }
    mediaPlayer = null

    try {
      vibrator?.cancel()
    } catch (_: Exception) {
    }

    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "MathWake 目覚ましアラーム",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "数学パズルを解いて解除する目覚ましアラーム"
        setSound(null, null)
        enableVibration(true)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager?.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    stopAlarm()
    super.onDestroy()
  }
}
