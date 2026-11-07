package com.example.data.repository

import com.example.data.dao.AlarmDao
import com.example.data.model.AlarmEntity
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmDao: AlarmDao) {
  val allAlarms: Flow<List<AlarmEntity>> = alarmDao.getAllAlarms()

  suspend fun getEnabledAlarms(): List<AlarmEntity> {
    return alarmDao.getEnabledAlarms()
  }

  suspend fun getAlarmById(id: Long): AlarmEntity? {
    return alarmDao.getAlarmById(id)
  }

  suspend fun insert(alarm: AlarmEntity): Long {
    return alarmDao.insertAlarm(alarm)
  }

  suspend fun update(alarm: AlarmEntity) {
    alarmDao.updateAlarm(alarm)
  }

  suspend fun delete(alarm: AlarmEntity) {
    alarmDao.deleteAlarm(alarm)
  }
}
