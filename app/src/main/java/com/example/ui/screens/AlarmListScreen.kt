package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AlarmEntity
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CrimsonAlarm
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
  alarms: List<AlarmEntity>,
  permissionStatus: com.example.util.LockdownPermissionStatus,
  onOpenLockdownSetup: () -> Unit,
  onAddAlarmClick: () -> Unit,
  onEditAlarmClick: (AlarmEntity) -> Unit,
  onToggleAlarm: (AlarmEntity, Boolean) -> Unit,
  onDeleteAlarm: (AlarmEntity) -> Unit,
  onTestAlarmClick: (Context, AlarmEntity) -> Unit,
  onTestLockScreenAlarmClick: (Context, AlarmEntity) -> Unit = { _, _ -> },
  onPracticeClick: () -> Unit
) {
  val context = LocalContext.current
  var alarmToDelete by remember { mutableStateOf<AlarmEntity?>(null) }
  var testAlarmTarget by remember { mutableStateOf<AlarmEntity?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AmberPrimary),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = Slate950,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "MathWake",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Slate100
              )
              Text(
                text = "算数覚醒アラーム",
                fontSize = 11.sp,
                color = Slate400
              )
            }
          }
        },
        actions = {
          // Lockdown Setup Button
          IconButton(
            onClick = onOpenLockdownSetup,
            modifier = Modifier.testTag("lockdown_setup_button")
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = "脱出防止設定",
              tint = if (permissionStatus.allCriticalGranted) EmeraldSuccess else CrimsonAlarm
            )
          }

          // Brain training / Practice mode button
          OutlinedButton(
            onClick = onPracticeClick,
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("practice_mode_button"),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.6f))
          ) {
            Icon(
              imageVector = Icons.Default.Psychology,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "計算練習",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate950)
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = onAddAlarmClick,
        containerColor = AmberPrimary,
        contentColor = Slate950,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.testTag("add_alarm_fab")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, contentDescription = "アラームを追加")
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "アラーム追加",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }
      }
    },
    containerColor = Slate950
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Interactive Lockdown & Defense setup banner
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(
            if (permissionStatus.allCriticalGranted) Slate900
            else CrimsonAlarm.copy(alpha = 0.2f)
          )
          .border(
            1.5.dp,
            if (permissionStatus.allCriticalGranted) EmeraldSuccess.copy(alpha = 0.5f)
            else CrimsonAlarm,
            RoundedCornerShape(16.dp)
          )
          .clickable { onOpenLockdownSetup() }
          .padding(14.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(
                if (permissionStatus.allCriticalGranted) EmeraldSuccess.copy(alpha = 0.2f)
                else CrimsonAlarm.copy(alpha = 0.3f)
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (permissionStatus.allCriticalGranted) Icons.Default.Security else Icons.Default.Warning,
              contentDescription = null,
              tint = if (permissionStatus.allCriticalGranted) EmeraldSuccess else CrimsonAlarm,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (permissionStatus.allCriticalGranted) "完全脱出防止システム: 有効"
                     else "⚠️ 脱出防止の権限設定が必要です（${permissionStatus.missingCount}件未設定）",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = if (permissionStatus.allCriticalGranted) Slate100 else Color.White
            )
            Text(
              text = if (permissionStatus.allCriticalGranted) "全画面オーバーレイ・音量固定・正確なアラーム稼働中（タップで詳細）"
                     else "【タップして今すぐ設定】画面重ねて表示などを許可しないとロック画面で強制起動できません",
              fontSize = 11.sp,
              color = if (permissionStatus.allCriticalGranted) Slate400 else AmberPrimary,
              lineHeight = 15.sp
            )
          }
        }
      }

      if (alarms.isEmpty()) {
        // Empty State
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Default.AlarmOff,
            contentDescription = null,
            tint = Slate600,
            modifier = Modifier.size(72.dp)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "アラームがありません",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Slate200
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "右下の「アラーム追加」ボタンを押して\n計算パズル付きのアラームを作成しましょう。",
            fontSize = 13.sp,
            color = Slate400,
            textAlign = TextAlign.Center
          )
        }
      } else {
        // Alarms List
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(4.dp))
          }

          items(alarms, key = { it.id }) { alarm ->
            AlarmCard(
              alarm = alarm,
              onToggle = { onToggleAlarm(alarm, it) },
              onEdit = { onEditAlarmClick(alarm) },
              onDelete = { alarmToDelete = alarm },
              onTest = { testAlarmTarget = alarm }
            )
          }

          item {
            Spacer(modifier = Modifier.height(80.dp))
          }
        }
      }
    }
  }

  // Delete Confirmation Dialog
  alarmToDelete?.let { alarm ->
    AlertDialog(
      onDismissRequest = { alarmToDelete = null },
      title = { Text("アラームを削除しますか？", color = Slate100, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "${alarm.formattedTime} (${alarm.label}) のアラームを削除します。",
          color = Slate400
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteAlarm(alarm)
            alarmToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlarm)
        ) {
          Text("削除", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { alarmToDelete = null }) {
          Text("キャンセル", color = Slate400)
        }
      },
      containerColor = Slate900
    )
  }

  // Test Mode Selection Dialog
  testAlarmTarget?.let { alarm ->
    androidx.compose.ui.window.Dialog(
      onDismissRequest = { testAlarmTarget = null }
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Slate900,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = AmberPrimary,
            modifier = Modifier.size(36.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "テスト鳴動のモード選択",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Slate100
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "${alarm.formattedTime} (${alarm.label})",
            fontSize = 13.sp,
            color = Slate400
          )

          Spacer(modifier = Modifier.height(18.dp))

          // Option 1: Lock Screen Test (5s delay)
          Button(
            onClick = {
              val target = alarm
              testAlarmTarget = null
              onTestLockScreenAlarmClick(context, target)
              android.widget.Toast.makeText(
                context,
                "🔒 5秒後に鳴動します！今すぐ電源ボタンを押して画面をロックしてください",
                android.widget.Toast.LENGTH_LONG
              ).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = AmberPrimary,
              contentColor = Slate950
            )
          ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("5秒後にロック画面テスト（推奨）", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "※タップ後すぐに電源ボタンで画面をロックしてください。ロック画面で通知欄・ホームキーが完全に遮断されることを実機確認できます。",
            fontSize = 11.sp,
            color = Slate400,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
          )

          Spacer(modifier = Modifier.height(14.dp))

          // Option 2: Immediate App Test
          OutlinedButton(
            onClick = {
              val target = alarm
              testAlarmTarget = null
              onTestAlarmClick(context, target)
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate200)
          ) {
            Text("今すぐアプリ内でテスト", fontSize = 13.sp)
          }

          Spacer(modifier = Modifier.height(8.dp))

          TextButton(
            onClick = { testAlarmTarget = null }
          ) {
            Text("閉じる", color = Slate400)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmCard(
  alarm: AlarmEntity,
  onToggle: (Boolean) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onTest: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("alarm_card_${alarm.id}"),
    colors = CardDefaults.cardColors(
      containerColor = if (alarm.isEnabled) Slate900 else Slate900.copy(alpha = 0.6f)
    ),
    shape = RoundedCornerShape(20.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (alarm.isEnabled) AmberPrimary.copy(alpha = 0.4f) else Slate800
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      // Row 1: Time, Label, and Switch
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(verticalAlignment = Alignment.Bottom) {
            Text(
              text = alarm.formattedTime,
              fontSize = 38.sp,
              fontWeight = FontWeight.Black,
              fontFamily = FontFamily.Monospace,
              color = if (alarm.isEnabled) Slate100 else Slate600
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = alarm.daysSummary,
              fontSize = 12.sp,
              color = if (alarm.isEnabled) AmberPrimary else Slate600,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(bottom = 6.dp)
            )
          }

          Text(
            text = alarm.label.ifBlank { "アラーム" },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (alarm.isEnabled) Slate200 else Slate600
          )
        }

        Switch(
          checked = alarm.isEnabled,
          onCheckedChange = onToggle,
          modifier = Modifier.testTag("alarm_switch_${alarm.id}"),
          colors = SwitchDefaults.colors(
            checkedThumbColor = AmberPrimary,
            checkedTrackColor = Slate800,
            uncheckedThumbColor = Slate600,
            uncheckedTrackColor = Slate800
          )
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Row 2: Badges (Difficulty, Streak, Volume Mode)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Difficulty badge
        BadgeChip(
          text = "難易度: ${alarm.difficultyLabel}",
          color = when (alarm.difficulty) {
            "EASY" -> EmeraldSuccess
            "HARD" -> AmberPrimary
            "EXPERT" -> OrangeAccent
            "NIGHTMARE" -> CrimsonAlarm
            else -> SkyInfo
          }
        )

        // Consecutive streak badge
        BadgeChip(
          text = "${alarm.requiredCorrectCount}問連続正解で解除",
          color = AmberPrimary
        )

        // Volume Mode Badge
        if (alarm.useDeviceVolume) {
          BadgeChip(
            text = "音量: 端末設定準拠",
            icon = Icons.Default.VolumeDown,
            color = SkyInfo
          )
        } else {
          BadgeChip(
            text = "音量固定: ${alarm.volumePercent}% (ロック)",
            icon = Icons.Default.Lock,
            color = AmberPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Row 3: Actions (Test, Edit, Delete)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Test Run Button
        Button(
          onClick = onTest,
          modifier = Modifier.testTag("test_alarm_button_${alarm.id}"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = Slate800,
            contentColor = AmberPrimary
          ),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp,
            vertical = 6.dp
          )
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "テスト鳴動",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Row {
          IconButton(
            onClick = onEdit,
            modifier = Modifier.testTag("edit_alarm_button_${alarm.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "編集",
              tint = Slate400,
              modifier = Modifier.size(20.dp)
            )
          }

          IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete_alarm_button_${alarm.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "削除",
              tint = Slate400,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun BadgeChip(
  text: String,
  color: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(color.copy(alpha = 0.15f))
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(12.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
    }
    Text(
      text = text,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      color = color
    )
  }
}
