package com.example.alarm

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class MathWakeDeviceAdminReceiver : DeviceAdminReceiver() {

  override fun onEnabled(context: Context, intent: Intent) {
    super.onEnabled(context, intent)
    Toast.makeText(context, "MathWake: 端末管理者権限が有効化されました（脱出防止強化）", Toast.LENGTH_SHORT).show()
  }

  override fun onDisabled(context: Context, intent: Intent) {
    super.onDisabled(context, intent)
    Toast.makeText(context, "MathWake: 端末管理者権限が無効化されました", Toast.LENGTH_SHORT).show()
  }
}
