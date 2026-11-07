package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AlarmEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.OrangeAccent
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditAlarmDialog(
  initialAlarm: AlarmEntity?,
  onDismiss: () -> Unit,
  onSave: (AlarmEntity) -> Unit
) {
  var hour by remember { mutableIntStateOf(initialAlarm?.hour ?: 7) }
  var minute by remember { mutableIntStateOf(initialAlarm?.minute ?: 0) }
  var label by remember { mutableStateOf(initialAlarm?.label ?: "朝の起床") }
  var daysOfWeek by remember { mutableIntStateOf(initialAlarm?.daysOfWeek ?: 127) }
  var difficulty by remember { mutableStateOf(initialAlarm?.difficulty ?: "MEDIUM") }
  var requiredCount by remember { mutableIntStateOf(initialAlarm?.requiredCorrectCount ?: 3) }
  var useDeviceVolume by remember { mutableStateOf(initialAlarm?.useDeviceVolume ?: false) }
  var volumePercent by remember { mutableFloatStateOf(initialAlarm?.volumePercent?.toFloat() ?: 85f) }
  var vibrate by remember { mutableStateOf(initialAlarm?.vibrate ?: true) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .padding(vertical = 16.dp),
      shape = RoundedCornerShape(24.dp),
      color = Slate900,
      border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Title
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(AmberPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Alarm,
              contentDescription = null,
              tint = AmberPrimary,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = if (initialAlarm == null) "新しいアラームを追加" else "アラームを編集",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Slate100
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Time Selector (Hour & Minute Picker)
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Slate950),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Hour
            NumberSpinner(
              value = hour,
              range = 0..23,
              label = "時",
              onValueChange = { hour = it },
              testTag = "hour_spinner"
            )

            Text(
              text = ":",
              fontSize = 36.sp,
              fontWeight = FontWeight.Bold,
              color = AmberPrimary,
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Minute
            NumberSpinner(
              value = minute,
              range = 0..59,
              label = "分",
              onValueChange = { minute = it },
              testTag = "minute_spinner"
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Label Input
        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("アラーム名 / メモ") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_label_input"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AmberPrimary,
            unfocusedBorderColor = Slate700,
            focusedTextColor = Slate100,
            unfocusedTextColor = Slate200,
            focusedLabelColor = AmberPrimary,
            unfocusedLabelColor = Slate400
          ),
          shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Repeat Days Selector
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Slate950),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "繰り返し曜日",
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = Slate400
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Preset Chips
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              QuickDayPresetChip(
                label = "毎日",
                isSelected = daysOfWeek == 127,
                onClick = { daysOfWeek = 127 }
              )
              QuickDayPresetChip(
                label = "平日",
                isSelected = daysOfWeek == 31,
                onClick = { daysOfWeek = 31 }
              )
              QuickDayPresetChip(
                label = "休日",
                isSelected = daysOfWeek == 96,
                onClick = { daysOfWeek = 96 }
              )
              QuickDayPresetChip(
                label = "1回",
                isSelected = daysOfWeek == 0,
                onClick = { daysOfWeek = 0 }
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Individual Day Buttons (Mon-Sun)
            val dayNames = listOf("月", "火", "水", "木", "金", "土", "日")
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              for (i in 0..6) {
                val isSelected = (daysOfWeek and (1 shl i)) != 0
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) AmberPrimary else Slate800)
                    .clickable {
                      daysOfWeek = if (isSelected) {
                        daysOfWeek and (1 shl i).inv()
                      } else {
                        daysOfWeek or (1 shl i)
                      }
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = dayNames[i],
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Slate950 else Slate200
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Math Difficulty & Consecutive Count
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Slate950),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "算数パズル設定",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Slate100
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "難易度を選択",
              fontSize = 12.sp,
              color = Slate400
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 1: EASY, MEDIUM, HARD
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              DifficultyButton(
                label = "簡単",
                sample = "例: 8 + 7",
                isSelected = difficulty == "EASY",
                modifier = Modifier.weight(1f),
                onClick = { difficulty = "EASY" }
              )
              DifficultyButton(
                label = "普通",
                sample = "例: 34 + 48",
                isSelected = difficulty == "MEDIUM",
                modifier = Modifier.weight(1f),
                onClick = { difficulty = "MEDIUM" }
              )
              DifficultyButton(
                label = "難しい",
                sample = "例: 14 × 13",
                isSelected = difficulty == "HARD",
                modifier = Modifier.weight(1f),
                onClick = { difficulty = "HARD" }
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: EXPERT, NIGHTMARE
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              DifficultyButton(
                label = "超難問",
                sample = "例: 23×34-185",
                isSelected = difficulty == "EXPERT",
                modifier = Modifier.weight(1f),
                onClick = { difficulty = "EXPERT" }
              )
              DifficultyButton(
                label = "鬼畜・極限",
                sample = "例: 36×28+19×44",
                isSelected = difficulty == "NIGHTMARE",
                modifier = Modifier.weight(1f),
                onClick = { difficulty = "NIGHTMARE" }
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = "アラーム解除に必要な連続正解数",
              fontSize = 12.sp,
              color = Slate400
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(1, 3, 5).forEach { count ->
                val isSelected = requiredCount == count
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) OrangeAccent else Slate800)
                    .clickable { requiredCount = count }
                    .padding(vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "$count 問連続",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Slate200
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Volume Configuration Card (with Device Volume Respect toggle)
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Slate950),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (useDeviceVolume) Icons.Default.VolumeDown else Icons.Default.VolumeUp,
                  contentDescription = null,
                  tint = if (useDeviceVolume) SkyInfo else AmberPrimary,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                  Text(
                    text = "端末の音量設定を遵守",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate100
                  )
                  Text(
                    text = if (useDeviceVolume) "端末の現在音量を使用" else "アプリで音量を固定・ロック",
                    fontSize = 11.sp,
                    color = Slate400
                  )
                }
              }

              Switch(
                checked = useDeviceVolume,
                onCheckedChange = { useDeviceVolume = it },
                modifier = Modifier.testTag("device_volume_switch"),
                colors = SwitchDefaults.colors(
                  checkedThumbColor = SkyInfo,
                  checkedTrackColor = Slate800,
                  uncheckedThumbColor = AmberPrimary,
                  uncheckedTrackColor = Slate800
                )
              )
            }

            // Custom Volume Slider (shown when NOT respecting device volume)
            AnimatedVisibility(visible = !useDeviceVolume) {
              Column(modifier = Modifier.padding(top = 10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Lock,
                      contentDescription = null,
                      tint = AmberPrimary,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "固定アラーム音量",
                      fontSize = 12.sp,
                      color = Slate200
                    )
                  }
                  Text(
                    text = "${volumePercent.toInt()}%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary
                  )
                }

                Slider(
                  value = volumePercent,
                  onValueChange = { volumePercent = it },
                  valueRange = 10f..100f,
                  steps = 17,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("volume_slider"),
                  colors = SliderDefaults.colors(
                    thumbColor = AmberPrimary,
                    activeTrackColor = AmberPrimary,
                    inactiveTrackColor = Slate800
                  )
                )

                Text(
                  text = "※鳴動中に音量キーで下げられても、自動で ${volumePercent.toInt()}% に強制復帰して鳴り続けます。",
                  fontSize = 11.sp,
                  color = Slate400,
                  lineHeight = 15.sp
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 6. Vibration Switch
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Slate950),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Vibration,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "バイブレーション",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Slate100
              )
            }
            Switch(
              checked = vibrate,
              onCheckedChange = { vibrate = it },
              modifier = Modifier.testTag("vibrate_switch"),
              colors = SwitchDefaults.colors(
                checkedThumbColor = AmberPrimary,
                checkedTrackColor = Slate800
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: Cancel and Save
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("cancel_alarm_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400)
          ) {
            Text("キャンセル")
          }

          Button(
            onClick = {
              val alarm = AlarmEntity(
                id = initialAlarm?.id ?: 0L,
                hour = hour,
                minute = minute,
                label = label.ifBlank { "アラーム" },
                isEnabled = true,
                daysOfWeek = daysOfWeek,
                difficulty = difficulty,
                requiredCorrectCount = requiredCount,
                useDeviceVolume = useDeviceVolume,
                volumePercent = volumePercent.toInt(),
                vibrate = vibrate
              )
              onSave(alarm)
            },
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("save_alarm_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = AmberPrimary,
              contentColor = Slate950
            )
          ) {
            Text(
              text = "保存する",
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@Composable
fun NumberSpinner(
  value: Int,
  range: IntRange,
  label: String,
  onValueChange: (Int) -> Unit,
  testTag: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.testTag(testTag)
  ) {
    IconButton(
      onClick = {
        val next = if (value >= range.last) range.first else value + 1
        onValueChange(next)
      },
      modifier = Modifier.size(36.dp)
    ) {
      Text("▲", color = Slate400, fontSize = 14.sp)
    }

    Text(
      text = "%02d".format(value),
      fontSize = 44.sp,
      fontWeight = FontWeight.Black,
      fontFamily = FontFamily.Monospace,
      color = Slate100
    )

    Text(
      text = label,
      fontSize = 11.sp,
      color = Slate400
    )

    IconButton(
      onClick = {
        val prev = if (value <= range.first) range.last else value - 1
        onValueChange(prev)
      },
      modifier = Modifier.size(36.dp)
    ) {
      Text("▼", color = Slate400, fontSize = 14.sp)
    }
  }
}

@Composable
fun QuickDayPresetChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (isSelected) AmberPrimary else Slate800)
      .clickable { onClick() }
      .padding(horizontal = 10.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = if (isSelected) Slate950 else Slate200
    )
  }
}

@Composable
fun DifficultyButton(
  label: String,
  sample: String,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) AmberPrimary else Slate800)
      .border(
        1.dp,
        if (isSelected) AmberPrimary else Slate700,
        RoundedCornerShape(12.dp)
      )
      .clickable { onClick() }
      .padding(vertical = 10.dp, horizontal = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (isSelected) Slate950 else Slate100
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = sample,
        fontSize = 9.sp,
        color = if (isSelected) Slate900 else Slate400
      )
    }
  }
}
