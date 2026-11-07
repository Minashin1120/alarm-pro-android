package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AlarmEntity
import com.example.ui.screens.AddEditAlarmDialog
import com.example.ui.screens.AlarmListScreen
import com.example.ui.screens.LockdownSetupDialog
import com.example.ui.screens.MathPracticeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AlarmViewModel
import com.example.util.PermissionManager

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      MyApplicationTheme {
        MathWakeApp()
      }
    }
  }
}

enum class CurrentScreen {
  ALARM_LIST,
  MATH_PRACTICE
}

@Composable
fun MathWakeApp(viewModel: AlarmViewModel = viewModel()) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val alarms by viewModel.alarms.collectAsState()
  var currentScreen by remember { mutableStateOf(CurrentScreen.ALARM_LIST) }
  var isAddEditDialogOpen by remember { mutableStateOf(false) }
  var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
  var isLockdownSetupDialogOpen by remember { mutableStateOf(false) }

  var permissionStatus by remember { mutableStateOf(PermissionManager.checkStatus(context)) }

  // Refresh permission status whenever returning from system settings
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        permissionStatus = PermissionManager.checkStatus(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  // Device Admin launcher using startActivityForResult so calling package is preserved
  val deviceAdminLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { _ ->
    permissionStatus = PermissionManager.checkStatus(context)
  }

  // Notification permission request for Android 13+
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ ->
    permissionStatus = PermissionManager.checkStatus(context)
  }

  // Active status poll to guarantee instant UI update on returning from Settings
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Prompt lockdown setup if a required permission is missing.
    // Device Owner is a manual ADB step, so it is shown in the dialog but not auto-prompted.
    val current = PermissionManager.checkStatus(context)
    if (!current.isOverlayGranted ||
      !current.isExactAlarmGranted ||
      !current.isDeviceAdminActive ||
      !current.isAccessibilityEnabled
    ) {
      isLockdownSetupDialogOpen = true
    }

    while (true) {
      kotlinx.coroutines.delay(400)
      val latest = PermissionManager.checkStatus(context)
      if (latest != permissionStatus) {
        permissionStatus = latest
      }
    }
  }

  // Handle back button for sub-screen
  if (currentScreen == CurrentScreen.MATH_PRACTICE) {
    BackHandler {
      currentScreen = CurrentScreen.ALARM_LIST
    }
  }

  when (currentScreen) {
    CurrentScreen.ALARM_LIST -> {
      AlarmListScreen(
        alarms = alarms,
        permissionStatus = permissionStatus,
        onOpenLockdownSetup = { isLockdownSetupDialogOpen = true },
        onAddAlarmClick = {
          editingAlarm = null
          isAddEditDialogOpen = true
        },
        onEditAlarmClick = { alarm ->
          editingAlarm = alarm
          isAddEditDialogOpen = true
        },
        onToggleAlarm = { alarm, enabled ->
          viewModel.toggleAlarm(alarm, enabled)
        },
        onDeleteAlarm = { alarm ->
          viewModel.deleteAlarm(alarm)
        },
        onTestAlarmClick = { ctx, alarm ->
          viewModel.testAlarm(ctx, alarm)
        },
        onTestLockScreenAlarmClick = { ctx, alarm ->
          viewModel.testLockScreenAlarm(ctx, alarm)
        },
        onPracticeClick = {
          currentScreen = CurrentScreen.MATH_PRACTICE
        }
      )
    }

    CurrentScreen.MATH_PRACTICE -> {
      MathPracticeScreen(
        onBack = { currentScreen = CurrentScreen.ALARM_LIST }
      )
    }
  }

  if (isAddEditDialogOpen) {
    AddEditAlarmDialog(
      initialAlarm = editingAlarm,
      onDismiss = {
        isAddEditDialogOpen = false
        editingAlarm = null
      },
      onSave = { alarm ->
        viewModel.saveAlarm(alarm)
        isAddEditDialogOpen = false
        editingAlarm = null
      }
    )
  }

  if (isLockdownSetupDialogOpen) {
    LockdownSetupDialog(
      onDismiss = {
        isLockdownSetupDialogOpen = false
        permissionStatus = PermissionManager.checkStatus(context)
      },
      onRequestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          PermissionManager.openAppNotificationSettings(context)
        }
      },
      onRequestDeviceAdmin = {
        val intent = PermissionManager.createDeviceAdminIntent(context)
        try {
          deviceAdminLauncher.launch(intent)
        } catch (_: Exception) {
          PermissionManager.requestDeviceAdmin(context)
        }
      }
    )
  }
}
