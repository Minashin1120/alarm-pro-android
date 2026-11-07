package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
      CoroutineScope(Dispatchers.IO).launch {
        try {
          val db = AppDatabase.getDatabase(context)
          val enabledAlarms = db.alarmDao().getEnabledAlarms()
          for (alarm in enabledAlarms) {
            AlarmScheduler.scheduleAlarm(context, alarm)
          }
        } catch (_: Exception) {
        }
      }
    }
  }
}
