package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.util.CameraPermission
import com.example.util.QrCodeEncoder
import com.example.util.QrCodeExporter
import com.example.util.QrUnlockManager
import com.example.util.QrUnlockPayload

/**
 * Issues the device-wide "wake-up QR" sheet that can be printed and placed away from the bed.
 *
 * The QR code is the physical half of the alarm unlock: alarms with `qrRequired` cannot be
 * dismissed from the bed, because the printed sheet must be scanned by the alarm screen camera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeSetupScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var token by remember { mutableStateOf(QrUnlockManager.getOrCreateToken(context)) }
  val payload = remember(token) { QrUnlockPayload.encode(token) }
  val qrBitmap = remember(payload) { QrCodeEncoder.generate(payload, 1024) }

  var isCameraGranted by remember { mutableStateOf(CameraPermission.isGranted(context)) }
  var isRegenerateDialogOpen by remember { mutableStateOf(false) }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    isCameraGranted = granted
    if (!granted) {
      Toast.makeText(
        context,
        "カメラが許可されないとQRコード解除を使えません",
        Toast.LENGTH_LONG
      ).show()
    }
  }

  // Re-check the permission after returning from the system settings screen.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        isCameraGranted = CameraPermission.isGranted(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "起床QRコード",
            fontWeight = FontWeight.Bold,
            color = Slate100
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("qr_setup_back")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "戻る",
              tint = Slate100
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate950)
      )
    },
    containerColor = Slate950
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .background(SkyInfo.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.QrCode2,
              contentDescription = null,
              tint = SkyInfo,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "ベッドから離れた場所に貼るQRコード",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Slate100
            )
            Text(
              text = "印刷して洗面所などに置くと、二度寝を物理的に防げます。",
              fontSize = 11.sp,
              color = Slate400,
              lineHeight = 15.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, Slate700, RoundedCornerShape(20.dp)),
        color = Color.White,
        shape = RoundedCornerShape(20.dp)
      ) {
        Image(
          bitmap = qrBitmap.asImageBitmap(),
          contentDescription = "起床QRコード",
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(14.dp)
            .testTag("qr_code_image")
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "アラーム編集画面で「印刷QRコードで解除」をオンにすると、\n数学に加えてこのQRのスキャンが必要になります。",
        fontSize = 12.sp,
        color = Slate400,
        lineHeight = 17.sp
      )

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = {
            val activity = context.findActivity()
            if (activity == null) {
              Toast.makeText(context, "印刷画面を開けませんでした", Toast.LENGTH_SHORT).show()
            } else {
              try {
                QrCodeExporter.print(activity, qrBitmap)
              } catch (_: Exception) {
                Toast.makeText(context, "印刷画面を開けませんでした", Toast.LENGTH_SHORT).show()
              }
            }
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("qr_print_button"),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = AmberPrimary,
            contentColor = Slate950
          )
        ) {
          Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("印刷する", fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = {
            val activity = context.findActivity()
            if (activity == null) {
              Toast.makeText(context, "共有画面を開けませんでした", Toast.LENGTH_SHORT).show()
            } else {
              try {
                QrCodeExporter.share(activity, qrBitmap)
              } catch (_: Exception) {
                Toast.makeText(context, "共有画面を開けませんでした", Toast.LENGTH_SHORT).show()
              }
            }
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("qr_share_button"),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate200)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("画像を共有", fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Camera permission: the alarm screen scans with the front/back camera in-process.
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CameraAlt,
              contentDescription = null,
              tint = if (isCameraGranted) EmeraldSuccess else CrimsonAlarm,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "カメラ権限",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Slate100
              )
              Text(
                text = if (isCameraGranted) {
                  "許可済み：鳴動中にQRをスキャンできます"
                } else {
                  "未許可：このままではQR解除のアラームを止められません"
                },
                fontSize = 11.sp,
                color = Slate400,
                lineHeight = 15.sp
              )
            }
          }

          if (!isCameraGranted) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonAlarm,
                contentColor = Color.White
              )
            ) {
              Text("カメラを許可する", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              Text(
                text = "※ 権限ダイアログが出ない場合は、端末の設定 → アプリ → MathWake → カメラ を許可してください。",
                fontSize = 10.sp,
                color = Slate400,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              tint = Slate400,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "QRコードを再発行",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Slate100
            )
          }
          Text(
            text = "新しいQRに差し替えます。印刷済みの古いQRは使えなくなるため、必ず再印刷してください。",
            fontSize = 11.sp,
            color = Slate400,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
          )
          OutlinedButton(
            onClick = { isRegenerateDialogOpen = true },
            modifier = Modifier
              .fillMaxWidth()
              .height(44.dp)
              .testTag("qr_regenerate_button"),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate200)
          ) {
            Text("再発行する", fontSize = 13.sp)
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "※ この画面はスクリーンショット防止が有効です。保存・印刷は上のボタンから行ってください。\n※ 印刷した紙はベッドから離れた場所に置き、スマホの写真には保存しないでください。\n※ アプリのデータを消去するとQRは無効になります（再発行が必要です）。",
        fontSize = 10.sp,
        color = Slate400,
        lineHeight = 15.sp,
        modifier = Modifier.padding(bottom = 24.dp)
      )
    }
  }

  if (isRegenerateDialogOpen) {
    AlertDialog(
      onDismissRequest = { isRegenerateDialogOpen = false },
      title = {
        Text("QRコードを再発行しますか？", color = Slate100, fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          "印刷済みのQRコードはすべて無効になります。新しいQRを印刷し直すまで、QR解除をオンにしたアラームは止められません。",
          color = Slate400
        )
      },
      confirmButton = {
        Button(
          onClick = {
            token = QrUnlockManager.regenerate(context)
            isRegenerateDialogOpen = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
        ) {
          Text("再発行", fontWeight = FontWeight.Bold, color = Slate950)
        }
      },
      dismissButton = {
        TextButton(onClick = { isRegenerateDialogOpen = false }) {
          Text("キャンセル", color = Slate400)
        }
      },
      containerColor = Slate900
    )
  }
}

private fun Context.findActivity(): Activity? {
  var current: Context = this
  while (current is ContextWrapper) {
    if (current is Activity) return current
    current = current.baseContext
  }
  return null
}
