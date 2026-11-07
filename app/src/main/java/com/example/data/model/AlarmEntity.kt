package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val hour: Int,
  val minute: Int,
  val label: String = "アラーム",
  val isEnabled: Boolean = true,
  val daysOfWeek: Int = 127, // 127 = 0b1111111 (毎日)
  val difficulty: String = "MEDIUM", // EASY, MEDIUM, HARD
  val requiredCorrectCount: Int = 3, // 1, 3, 5
  val useDeviceVolume: Boolean = false, // false = 独自音量指定, true = 端末音量を遵守
  val volumePercent: Int = 85, // 0..100
  val vibrate: Boolean = true
) {
  val formattedTime: String
    get() = "%02d:%02d".format(hour, minute)

  fun isDaySelected(dayIndex: Int): Boolean { // 0: Mon, 1: Tue, ... 6: Sun
    return (daysOfWeek and (1 shl dayIndex)) != 0
  }

  val daysSummary: String
    get() {
      if (daysOfWeek == 0) return "1回のみ"
      if (daysOfWeek == 127) return "毎日"
      if (daysOfWeek == 31) return "平日（月〜金）"
      if (daysOfWeek == 96) return "休日（土・日）"
      val dayNames = listOf("月", "火", "水", "木", "金", "土", "日")
      val selected = mutableListOf<String>()
      for (i in 0..6) {
        if (isDaySelected(i)) selected.add(dayNames[i])
      }
      return selected.joinToString("・")
    }

  val difficultyLabel: String
    get() = com.example.math.MathProblemGenerator.getDifficultyLabel(difficulty)
}
