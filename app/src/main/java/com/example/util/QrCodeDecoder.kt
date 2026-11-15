package com.example.util

import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes a QR code from a CameraX [ImageProxy] without loading the full camera bitmap.
 *
 * Only the Y (luminance) plane is used, and the frame is tried in the rotation reported by
 * CameraX first so the common case costs a single decode attempt.
 */
object QrCodeDecoder {

  private val HINTS: Map<DecodeHintType, Any> = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.CHARACTER_SET to "UTF-8",
    DecodeHintType.TRY_HARDER to true,
  )

  fun decode(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null

    val luminance = extractLuminance(plane, width, height) ?: return null
    val source = PlanarYUVLuminanceSource(
      luminance,
      width,
      height,
      0,
      0,
      width,
      height,
      false,
    )

    // rotationDegrees is clockwise, rotateCounterClockwise() is counter-clockwise.
    val expectedRotations = ((360 - image.imageInfo.rotationDegrees) % 360) / 90
    val rotationOrder = buildList {
      if (expectedRotations in 0..3) add(expectedRotations)
      add(0)
      add(1)
      add(2)
      add(3)
    }.distinct()

    for (rotation in rotationOrder) {
      var rotated: LuminanceSource = source
      repeat(rotation) { rotated = rotated.rotateCounterClockwise() }
      decodeSource(rotated)?.let { return it }
    }
    return null
  }

  private fun decodeSource(source: LuminanceSource): String? {
    return try {
      val reader = MultiFormatReader()
      reader.setHints(HINTS)
      reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
      // NotFoundException dominates (no QR in this frame); keep scanning.
      null
    }
  }

  /**
   * Copies the Y plane into a tightly packed, width x height array.
   * Handles padding (rowStride > width) used by many camera HALs.
   */
  private fun extractLuminance(
    plane: ImageProxy.PlaneProxy,
    width: Int,
    height: Int,
  ): ByteArray? {
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    if (rowStride <= 0 || pixelStride <= 0) return null

    val target = ByteArray(width * height)
    val start = buffer.position()
    val end = buffer.limit()
    if (start >= end) return null

    if (pixelStride == 1 && rowStride == width) {
      if (end - start < target.size) return null
      buffer.position(start)
      buffer.get(target, 0, target.size)
      return target
    }

    val row = ByteArray(rowStride)
    var output = 0
    for (y in 0 until height) {
      val rowStart = start + y * rowStride
      if (rowStart >= end) break
      buffer.position(rowStart)
      val readable = minOf(rowStride, end - rowStart)
      buffer.get(row, 0, readable)

      var x = 0
      while (x < width && x * pixelStride < readable) {
        target[output++] = row[x * pixelStride]
        x++
      }
      // Pad a short/odd row with black so the luminance buffer stays rectangular.
      while (x < width) {
        target[output++] = 0.toByte()
        x++
      }
    }
    return target
  }
}
