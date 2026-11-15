package com.example.util

import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

/**
 * Decodes a QR code from a CameraX [ImageProxy] without loading the full camera bitmap.
 *
 * Only the Y (luminance) plane is used. The frame is decoded in the orientation the camera
 * delivered it: ZXing's QR detector locates the three finder patterns in any orientation, so
 * `ImageInfo.rotationDegrees` must NOT be applied here.
 *
 * Do not rotate a [PlanarYUVLuminanceSource]: it does not support rotation and
 * `LuminanceSource.rotateCounterClockwise()` throws `UnsupportedOperationException`. Doing that
 * returned no result at all on portrait phones, where the sensor reports 90 degrees.
 */
object QrCodeDecoder {

  fun decode(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null

    val luminance = extractLuminance(
      buffer = plane.buffer,
      rowStride = plane.rowStride,
      pixelStride = plane.pixelStride,
      width = width,
      height = height,
    ) ?: return null
    return decodeLuminance(luminance, width, height)
  }
}

private val QR_HINTS: Map<DecodeHintType, Any> = mapOf(
  DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
  DecodeHintType.CHARACTER_SET to "UTF-8",
  DecodeHintType.TRY_HARDER to true,
)

/**
 * Decodes a tightly packed `width x height` luminance buffer.
 * Kept free of Android types so it is covered by JVM unit tests.
 */
internal fun decodeLuminance(luminance: ByteArray, width: Int, height: Int): String? {
  if (width <= 0 || height <= 0) return null
  if (luminance.size < width * height) return null
  return try {
    val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
    val reader = MultiFormatReader()
    reader.setHints(QR_HINTS)
    reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
  } catch (_: Exception) {
    // NotFoundException dominates (no QR in this frame); keep scanning.
    null
  }
}

/**
 * Copies a camera Y plane into a tightly packed, `width x height` array.
 * Handles padding (`rowStride > width`) used by many camera HALs.
 *
 * Kept free of Android types so it is covered by JVM unit tests.
 */
internal fun extractLuminance(
  buffer: ByteBuffer,
  rowStride: Int,
  pixelStride: Int,
  width: Int,
  height: Int,
): ByteArray? {
  if (width <= 0 || height <= 0) return null
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
