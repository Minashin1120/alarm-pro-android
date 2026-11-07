package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.example.ActiveAlarmActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val wakeLock = powerManager?.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
      "MathWake:AlarmReceiverWakeLock"
    )
    wakeLock?.acquire(60_000L) // 1 minute timeout safeguard

    val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
    val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "アラーム"
    val difficulty = intent.getStringExtra(AlarmScheduler.EXTRA_DIFFICULTY) ?: "MEDIUM"
    val requiredCount = intent.getIntExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, 3)
    val useDeviceVolume = intent.getBooleanExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, false)
    val volumePercent = intent.getIntExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, 85)
    val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true)

    // 1. Start AlarmService (Foreground Service)
    val serviceIntent = Intent(context, AlarmService::class.java).apply {
      action = AlarmService.ACTION_START_ALARM
      putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
      putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
      putExtra(AlarmScheduler.EXTRA_DIFFICULTY, difficulty)
      putExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, requiredCount)
      putExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, useDeviceVolume)
      putExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, volumePercent)
      putExtra(AlarmScheduler.EXTRA_VIBRATE, vibrate)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }

    // 2. Launch ActiveAlarmActivity directly over lock screen
    val activityIntent = Intent(context, ActiveAlarmActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
      putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
      putExtra(AlarmScheduler.EXTRA_DIFFICULTY, difficulty)
      putExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, requiredCount)
      putExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, useDeviceVolume)
      putExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, volumePercent)
    }
    context.startActivity(activityIntent)

    // 3. Reschedule repeating alarm or mark one-time alarm as disabled
    if (alarmId > 0) {
      CoroutineScope(Dispatchers.IO).launch {
        try {
          val db = AppDatabase.getDatabase(context)
          val alarm = db.alarmDao().getAlarmById(alarmId)
          if (alarm != null) {
            if (alarm.daysOfWeek != 0) {
              // Repeating alarm: reschedule for next occurrence
              AlarmScheduler.scheduleAlarm(context, alarm)
            } else {
              // One-time alarm: disable after firing
              db.alarmDao().updateAlarm(alarm.copy(isEnabled = false))
            }
          }
        } catch (_: Exception) {
        } finally {
          if (wakeLock?.isHeld == true) {
            wakeLock.release()
          }
        }
      }
    } else {
      if (wakeLock?.isHeld == true) {
        wakeLock.release()
      }
    }
  }
}
