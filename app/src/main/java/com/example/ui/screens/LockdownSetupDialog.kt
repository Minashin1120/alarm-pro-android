package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CrimsonAlarm
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.LockdownPermissionStatus
import com.example.util.PermissionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun LockdownSetupDialog(
  onDismiss: () -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onRequestDeviceAdmin: () -> Unit
) {
  val context = LocalContext.current
  var status by remember { mutableStateOf(PermissionManager.checkStatus(context)) }

  // Real-time automatic polling: updates instantly the moment user returns from Settings
  LaunchedEffect(Unit) {
    while (isActive) {
      status = PermissionManager.checkStatus(context)
      delay(300)
    }
  }

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
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                  when {
                    status.isFullLockdownGranted -> EmeraldSuccess.copy(alpha = 0.2f)
                    status.allCriticalGranted -> AmberPrimary.copy(alpha = 0.2f)
                    else -> CrimsonAlarm.copy(alpha = 0.2f)
                  }
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = when {
                  status.isFullLockdownGranted -> EmeraldSuccess
                  status.allCriticalGranted -> AmberPrimary
                  else -> CrimsonAlarm
                },
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "脱出防止セットアップ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate100
              )
              Text(
                text = when {
                  status.isFullLockdownGranted -> "OSレベルの完全ロックダウンが有効です"
                  status.allCriticalGranted -> "簡易遮断モード（Device Owner未設定）"
                  else -> "${status.missingCount} 件の必須権限が未設定です"
                },
                fontSize = 12.sp,
                color = when {
                  status.isFullLockdownGranted -> EmeraldSuccess
                  status.allCriticalGranted -> AmberPrimary
                  else -> CrimsonAlarm
                },
                fontWeight = FontWeight.SemiBold
              )
            }
          }

          // Manual refresh button
          IconButton(
            onClick = { status = PermissionManager.checkStatus(context) },
            modifier = Modifier.testTag("refresh_permission_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "状態を再確認",
              tint = AmberPrimary
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "Androidの制限により、権限がないとロック画面への全画面強制起動やバックグラウンドアラームがOSにブロックされてしまいます。下記の設定をすべて有効化してください。",
          fontSize = 12.sp,
          color = Slate400,
          lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Overlay Permission (Most Critical for Screen takeover)
        PermissionItemCard(
          title = "他のアプリの上に重ねて表示",
          description = "【最重要】画面ロック中や他のアプリを開いていても、アラーム画面を強制的に最前面へ呼び出します。",
          isGranted = status.isOverlayGranted,
          icon = Icons.Default.Layers,
          buttonText = "重ねて表示を許可",
          onClick = {
            PermissionManager.requestOverlayPermission(context)
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Exact Alarm Permission
        PermissionItemCard(
          title = "正確なアラームの設定",
          description = "設定時刻に1秒の狂いもなく確実にアラームを作動させます。",
          isGranted = status.isExactAlarmGranted,
          icon = Icons.Default.Alarm,
          buttonText = "アラーム権限を許可",
          onClick = {
            PermissionManager.requestExactAlarmPermission(context)
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Notification Permission
        PermissionItemCard(
          title = "通知の送信許可",
          description = "大音量とバイブレーションを維持する高優先度フォアグラウンドサービス通知に必須です。",
          isGranted = status.isNotificationGranted,
          icon = Icons.Default.Notifications,
          buttonText = "通知を許可",
          onClick = {
            onRequestNotificationPermission()
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Battery Optimization Ignore
        PermissionItemCard(
          title = "電池の最適化の除外 (Doze回避)",
          description = "端末が深いスリープに入っても、アラームの強制停止や遅延を防ぎます。",
          isGranted = status.isBatteryOptimizationIgnored,
          icon = Icons.Default.BatteryAlert,
          buttonText = "電池最適化を除外",
          onClick = {
            PermissionManager.requestBatteryOptimizationIgnore(context)
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Device Administrator Permission
        PermissionItemCard(
          title = "端末管理者権限（Device Admin）",
          description = "【OSレベルの保護】ロック画面カメラ起動の無効化や、アラーム鳴動中にアプリがアンインストールされて脱出されるのを防止します。",
          isGranted = status.isDeviceAdminActive,
          icon = Icons.Default.Security,
          buttonText = "管理者権限を有効化",
          onClick = {
            onRequestDeviceAdmin()
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Accessibility Service (fallback when Device Owner is unavailable)
        PermissionItemCard(
          title = "ユーザー補助サービス（通知シェード遮断）",
          description = "アラーム鳴動中に通知シェードや電源メニューを検知して自動で閉じます。Android 14以降でAPKを直接インストールした場合は、アプリ情報 → 右上メニュー →「制限付き設定を許可」も必要です。",
          isGranted = status.isAccessibilityEnabled,
          icon = Icons.Default.Lock,
          buttonText = "ユーザー補助を有効化",
          onClick = {
            PermissionManager.requestAccessibilityPermission(context)
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 7. Device Owner (real OS-level kiosk mode)
        PermissionItemCard(
          title = "完全ロックダウン（Device Owner）",
          description = "電源メニュー・通知シェード・ロック画面・ホーム/履歴をOSレベルで無効化します。PCでUSBデバッグを有効にして、以下のコマンドを実行してください。\n\n" +
            PermissionManager.deviceOwnerProvisioningCommand(context) +
            "\n\n※端末にGoogleアカウントなどが残っていると失敗します。既にセットアップ済みの端末では初期化が必要な場合があります。",
          isGranted = status.isDeviceOwner,
          icon = Icons.Default.Security,
          buttonText = "ADBコマンドをコピー",
          onClick = {
            val command = PermissionManager.deviceOwnerProvisioningCommand(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Device Owner command", command))
            Toast.makeText(context, "ADBコマンドをコピーしました", Toast.LENGTH_SHORT).show()
          }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Lockdown protection details explanation
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Slate950),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = EmeraldSuccess,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "🔒 脱出防止の動作メカニズム",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate200
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "・Device Owner設定時: ロックタスクモードで電源メニュー・通知シェード・ロック画面・ホーム/履歴をOSレベルで無効化します。\n・未設定時: ユーザー補助サービスが通知シェード/電源メニューを検知して即座に閉じます。ただしAndroid 12以降では電源ボタン自体は横取りできないため、完全な遮断にはDevice Ownerが必要です。\n・端末ロック中: アラーム画面をロック画面の上に全画面表示し、他アプリへの離脱を防ぎます。",
              fontSize = 11.sp,
              color = Slate400,
              lineHeight = 16.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Close button
        Button(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("close_setup_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = AmberPrimary,
            contentColor = Slate950
          )
        ) {
          Text(
            text = "設定を完了して閉じる",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }
      }
    }
  }
}

@Composable
fun PermissionItemCard(
  title: String,
  description: String,
  isGranted: Boolean,
  icon: ImageVector,
  buttonText: String,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Slate950),
    shape = RoundedCornerShape(16.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (isGranted) EmeraldSuccess.copy(alpha = 0.4f) else CrimsonAlarm.copy(alpha = 0.4f)
    )
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) EmeraldSuccess else AmberPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Slate100
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) EmeraldSuccess else CrimsonAlarm,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isGranted) "許可済み" else "未設定",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGranted) EmeraldSuccess else CrimsonAlarm
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = description,
        fontSize = 11.sp,
        color = Slate400,
        lineHeight = 15.sp
      )

      if (!isGranted) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = onClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = CrimsonAlarm,
            contentColor = Color.White
          )
        ) {
          Text(
            text = buttonText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
