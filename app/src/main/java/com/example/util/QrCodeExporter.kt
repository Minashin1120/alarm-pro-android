package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Print / share helpers for the wake-up QR sheet. */
object QrCodeExporter {

  private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

  /** Opens the system print dialog with a one-page PDF containing [bitmap]. */
  fun print(activity: Activity, bitmap: Bitmap) {
    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(
      "MathWake 起床QR",
      QrPrintDocumentAdapter(activity, bitmap, "MathWake 起床QRコード"),
      null,
    )
  }

  /** Shares the QR as a PNG so it can be saved or printed by another app. */
  fun share(activity: Activity, bitmap: Bitmap) {
    val directory = File(activity.cacheDir, "qr").apply { mkdirs() }
    val file = File(directory, "mathwake_qr.png")
    FileOutputStream(file).use { output ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }

    val uri = FileProvider.getUriForFile(
      activity,
      activity.packageName + FILE_PROVIDER_SUFFIX,
      file,
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "MathWake 起床QRコード")
      putExtra(Intent.EXTRA_TEXT, "印刷してベッドから離れた場所に貼ってください。")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(sendIntent, "QRコードを印刷・保存"))
  }
}
