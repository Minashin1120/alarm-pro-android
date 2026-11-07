package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.AlarmEntity
import java.util.Calendar

object AlarmScheduler {

  const val ACTION_ALARM_TRIGGER = "com.aistudio.mathwake.ACTION_ALARM_TRIGGER"
  const val EXTRA_ALARM_ID = "extra_alarm_id"
  const val EXTRA_ALARM_LABEL = "extra_alarm_label"
  const val EXTRA_DIFFICULTY = "extra_difficulty"
  const val EXTRA_REQUIRED_COUNT = "extra_required_count"
  const val EXTRA_USE_DEVICE_VOLUME = "extra_use_device_volume"
  const val EXTRA_VOLUME_PERCENT = "extra_volume_percent"
  const val EXTRA_VIBRATE = "extra_vibrate"

  fun scheduleAlarm(context: Context, alarm: AlarmEntity) {
    if (!alarm.isEnabled) return

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_ALARM_TRIGGER
      putExtra(EXTRA_ALARM_ID, alarm.id)
      putExtra(EXTRA_ALARM_LABEL, alarm.label)
      putExtra(EXTRA_DIFFICULTY, alarm.difficulty)
      putExtra(EXTRA_REQUIRED_COUNT, alarm.requiredCorrectCount)
      putExtra(EXTRA_USE_DEVICE_VOLUME, alarm.useDeviceVolume)
      putExtra(EXTRA_VOLUME_PERCENT, alarm.volumePercent)
      putExtra(EXTRA_VIBRATE, alarm.vibrate)
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      alarm.id.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerAtMillis = calculateNextTriggerTime(alarm)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      }
    } else {
      alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        pendingIntent
      )
    }
  }

  fun cancelAlarm(context: Context, alarm: AlarmEntity) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_ALARM_TRIGGER
    }
    val pendingIntent = PendingIntent.getBroadcast(
      context,
      alarm.id.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
  }

  fun calculateNextTriggerTime(alarm: AlarmEntity): Long {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, alarm.hour)
      set(Calendar.MINUTE, alarm.minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    // One-time alarm
    if (alarm.daysOfWeek == 0) {
      if (target.before(now) || target.timeInMillis <= now.timeInMillis) {
        target.add(Calendar.DAY_OF_YEAR, 1)
      }
      return target.timeInMillis
    }

    // Repeating alarm
    // In Java Calendar, SUNDAY=1, MONDAY=2, ... SATURDAY=7
    // In our bitmask: 0: Mon, 1: Tue, 2: Wed, 3: Thu, 4: Fri, 5: Sat, 6: Sun
    for (dayOffset in 0..7) {
      val candidate = Calendar.getInstance().apply {
        timeInMillis = target.timeInMillis
        add(Calendar.DAY_OF_YEAR, dayOffset)
      }

      if (candidate.timeInMillis > now.timeInMillis) {
        val calDay = candidate.get(Calendar.DAY_OF_WEEK)
        val bitIndex = when (calDay) {
          Calendar.MONDAY -> 0
          Calendar.TUESDAY -> 1
          Calendar.WEDNESDAY -> 2
          Calendar.THURSDAY -> 3
          Calendar.FRIDAY -> 4
          Calendar.SATURDAY -> 5
          Calendar.SUNDAY -> 6
          else -> 0
        }
        if (alarm.isDaySelected(bitIndex)) {
          return candidate.timeInMillis
        }
      }
    }

    // Fallback: 24h later
    target.add(Calendar.DAY_OF_YEAR, 1)
    return target.timeInMillis
  }

  fun scheduleLockScreenTest(context: Context, alarm: AlarmEntity, delaySeconds: Int = 5) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = ACTION_ALARM_TRIGGER
      putExtra(EXTRA_ALARM_ID, 999999L)
      putExtra(EXTRA_ALARM_LABEL, "${alarm.label} (ロック画面テスト)")
      putExtra(EXTRA_DIFFICULTY, alarm.difficulty)
      putExtra(EXTRA_REQUIRED_COUNT, alarm.requiredCorrectCount)
      putExtra(EXTRA_USE_DEVICE_VOLUME, alarm.useDeviceVolume)
      putExtra(EXTRA_VOLUME_PERCENT, alarm.volumePercent)
      putExtra(EXTRA_VIBRATE, alarm.vibrate)
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      999999,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val triggerAtMillis = System.currentTimeMillis() + (delaySeconds * 1000L)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      }
    } else {
      alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAtMillis,
        pendingIntent
      )
    }
  }
}
