package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ActiveAlarmActivity
import com.example.alarm.AlarmScheduler
import com.example.alarm.AlarmService
import com.example.data.AppDatabase
import com.example.data.model.AlarmEntity
import com.example.data.repository.AlarmRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: AlarmRepository

  val alarms: StateFlow<List<AlarmEntity>>

  init {
    val alarmDao = AppDatabase.getDatabase(application).alarmDao()
    repository = AlarmRepository(alarmDao)
    alarms = repository.allAlarms.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

    // Pre-populate with default alarm if empty
    viewModelScope.launch {
      val existing = repository.getEnabledAlarms()
      if (existing.isEmpty() && alarms.value.isEmpty()) {
        val sampleAlarm = AlarmEntity(
          hour = 7,
          minute = 0,
          label = "朝の起床",
          isEnabled = true,
          daysOfWeek = 31, // Weekdays (Mon-Fri)
          difficulty = "MEDIUM",
          requiredCorrectCount = 3,
          useDeviceVolume = false,
          volumePercent = 85,
          vibrate = true
        )
        val id = repository.insert(sampleAlarm)
        AlarmScheduler.scheduleAlarm(application, sampleAlarm.copy(id = id))
      }
    }
  }

  fun saveAlarm(alarm: AlarmEntity) {
    viewModelScope.launch {
      if (alarm.id == 0L) {
        val newId = repository.insert(alarm)
        val saved = alarm.copy(id = newId)
        if (saved.isEnabled) {
          AlarmScheduler.scheduleAlarm(getApplication(), saved)
        }
      } else {
        repository.update(alarm)
        if (alarm.isEnabled) {
          AlarmScheduler.scheduleAlarm(getApplication(), alarm)
        } else {
          AlarmScheduler.cancelAlarm(getApplication(), alarm)
        }
      }
    }
  }

  fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
    viewModelScope.launch {
      val updated = alarm.copy(isEnabled = enabled)
      repository.update(updated)
      if (enabled) {
        AlarmScheduler.scheduleAlarm(getApplication(), updated)
      } else {
        AlarmScheduler.cancelAlarm(getApplication(), updated)
      }
    }
  }

  fun deleteAlarm(alarm: AlarmEntity) {
    viewModelScope.launch {
      AlarmScheduler.cancelAlarm(getApplication(), alarm)
      repository.delete(alarm)
    }
  }

  fun testAlarm(context: Context, alarm: AlarmEntity) {
    // Immediate test: Start service and launch ActiveAlarmActivity
    val serviceIntent = Intent(context, AlarmService::class.java).apply {
      action = AlarmService.ACTION_START_ALARM
      putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
      putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, "${alarm.label} (テスト鳴動)")
      putExtra(AlarmScheduler.EXTRA_DIFFICULTY, alarm.difficulty)
      putExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, alarm.requiredCorrectCount)
      putExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, alarm.useDeviceVolume)
      putExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, alarm.volumePercent)
      putExtra(AlarmScheduler.EXTRA_VIBRATE, alarm.vibrate)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }

    val activityIntent = Intent(context, ActiveAlarmActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
      putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, "${alarm.label} (テスト鳴動)")
      putExtra(AlarmScheduler.EXTRA_DIFFICULTY, alarm.difficulty)
      putExtra(AlarmScheduler.EXTRA_REQUIRED_COUNT, alarm.requiredCorrectCount)
      putExtra(AlarmScheduler.EXTRA_USE_DEVICE_VOLUME, alarm.useDeviceVolume)
      putExtra(AlarmScheduler.EXTRA_VOLUME_PERCENT, alarm.volumePercent)
    }
    context.startActivity(activityIntent)
  }

  fun testLockScreenAlarm(context: Context, alarm: AlarmEntity) {
    AlarmScheduler.scheduleLockScreenTest(context, alarm, 5)
  }
}
