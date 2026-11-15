package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders the unlock payload as a printable QR bitmap. */
object QrCodeEncoder {

  private const val QUIET_ZONE_MODULES = 2

  fun generate(payload: String, sizePx: Int = 900): Bitmap {
    val dimension = sizePx.coerceAtLeast(128)
    val matrix = QRCodeWriter().encode(
      payload,
      BarcodeFormat.QR_CODE,
      dimension,
      dimension,
      mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
      ),
    )

    val pixels = IntArray(dimension * dimension)
    for (y in 0 until dimension) {
      val rowOffset = y * dimension
      for (x in 0 until dimension) {
        pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
      }
    }
    return Bitmap.createBitmap(pixels, dimension, dimension, Bitmap.Config.ARGB_8888)
  }
}
