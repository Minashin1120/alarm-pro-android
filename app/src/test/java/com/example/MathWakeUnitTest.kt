package com.example

import com.example.alarm.AlarmScheduler
import com.example.data.model.AlarmEntity
import com.example.math.MathProblemGenerator
import com.example.util.AccessibilityServiceList
import com.example.util.QrUnlockPayload
import com.example.util.decodeLuminance
import com.example.util.extractLuminance
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.random.Random

class MathWakeUnitTest {

  @Test
  fun testMathProblemGenerator_Easy() {
    for (i in 0 until 50) {
      val problem = MathProblemGenerator.generate(MathProblemGenerator.DIFF_EASY)
      assertNotNull(problem.questionText)
      assertEquals("EASY", problem.difficulty)
      assertEquals(4, problem.options.size)
      assertEquals(4, problem.options.toSet().size)
      assertTrue(problem.options.contains(problem.correctAnswer))
    }
  }

  @Test
  fun testMathProblemGenerator_Medium() {
    for (i in 0 until 50) {
      val problem = MathProblemGenerator.generate(MathProblemGenerator.DIFF_MEDIUM)
      assertNotNull(problem.questionText)
      assertEquals("MEDIUM", problem.difficulty)
      assertEquals(4, problem.options.size)
      assertEquals(4, problem.options.toSet().size)
      assertTrue(problem.options.contains(problem.correctAnswer))
    }
  }

  @Test
  fun testMathProblemGenerator_Hard() {
    for (i in 0 until 50) {
      val problem = MathProblemGenerator.generate(MathProblemGenerator.DIFF_HARD)
      assertNotNull(problem.questionText)
      assertEquals("HARD", problem.difficulty)
      assertEquals(4, problem.options.size)
      assertEquals(4, problem.options.toSet().size)
      assertTrue(problem.options.contains(problem.correctAnswer))
    }
  }

  @Test
  fun testMathProblemGenerator_Expert() {
    for (i in 0 until 50) {
      val problem = MathProblemGenerator.generate(MathProblemGenerator.DIFF_EXPERT)
      assertNotNull(problem.questionText)
      assertEquals("EXPERT", problem.difficulty)
      assertEquals(4, problem.options.size)
      assertEquals(4, problem.options.toSet().size)
      assertTrue(problem.options.contains(problem.correctAnswer))
    }
  }

  @Test
  fun testMathProblemGenerator_Nightmare() {
    for (i in 0 until 50) {
      val problem = MathProblemGenerator.generate(MathProblemGenerator.DIFF_NIGHTMARE)
      assertNotNull(problem.questionText)
      assertEquals("NIGHTMARE", problem.difficulty)
      assertEquals(4, problem.options.size)
      assertEquals(4, problem.options.toSet().size)
      assertTrue(problem.options.contains(problem.correctAnswer))
    }
  }

  @Test
  fun testDifficultyEscalation() {
    assertEquals(MathProblemGenerator.DIFF_MEDIUM, MathProblemGenerator.getNextDifficulty(MathProblemGenerator.DIFF_EASY))
    assertEquals(MathProblemGenerator.DIFF_HARD, MathProblemGenerator.getNextDifficulty(MathProblemGenerator.DIFF_MEDIUM))
    assertEquals(MathProblemGenerator.DIFF_EXPERT, MathProblemGenerator.getNextDifficulty(MathProblemGenerator.DIFF_HARD))
    assertEquals(MathProblemGenerator.DIFF_NIGHTMARE, MathProblemGenerator.getNextDifficulty(MathProblemGenerator.DIFF_EXPERT))
    assertEquals(MathProblemGenerator.DIFF_NIGHTMARE, MathProblemGenerator.getNextDifficulty(MathProblemGenerator.DIFF_NIGHTMARE))
  }

  @Test
  fun testAlarmEntity_formattedTimeAndDays() {
    val alarm = AlarmEntity(
      hour = 6,
      minute = 30,
      daysOfWeek = 31, // Weekdays
      difficulty = "EXPERT"
    )
    assertEquals("06:30", alarm.formattedTime)
    assertEquals("平日（月〜金）", alarm.daysSummary)
    assertEquals("超難問", alarm.difficultyLabel)
    assertTrue(alarm.isDaySelected(0)) // Mon
    assertTrue(alarm.isDaySelected(4)) // Fri
    assertTrue(!alarm.isDaySelected(5)) // Sat

    val dailyAlarm = AlarmEntity(hour = 8, minute = 5, daysOfWeek = 127, difficulty = "NIGHTMARE")
    assertEquals("08:05", dailyAlarm.formattedTime)
    assertEquals("毎日", dailyAlarm.daysSummary)
    assertEquals("鬼畜・極限", dailyAlarm.difficultyLabel)

    val weekendAlarm = AlarmEntity(hour = 9, minute = 0, daysOfWeek = 96)
    assertEquals("休日（土・日）", weekendAlarm.daysSummary)
  }

  @Test
  fun testAlarmScheduler_calculateNextTriggerTime() {
    val alarm = AlarmEntity(
      hour = 7,
      minute = 0,
      daysOfWeek = 127
    )
    val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm)
    assertTrue(nextTrigger > System.currentTimeMillis())
  }

  @Test
  fun testAlarmEntity_qrRequiredDefaultsOff() {
    val alarm = AlarmEntity(hour = 6, minute = 45)
    assertFalse(alarm.qrRequired)

    val withQr = alarm.copy(qrRequired = true)
    assertTrue(withQr.qrRequired)
  }

  @Test
  fun testQrUnlockPayload_generatesWellFormedToken() {
    val token = QrUnlockPayload.generateToken(Random(1234))
    assertEquals(QrUnlockPayload.TOKEN_LENGTH, token.length)
    assertTrue(QrUnlockPayload.isTokenWellFormed(token))
    assertTrue(token.all { it in QrUnlockPayload.ALPHABET })

    val anotherToken = QrUnlockPayload.generateToken(Random(5678))
    assertFalse(token == anotherToken)
  }

  @Test
  fun testQrUnlockPayload_onlyAcceptsIssuedPayload() {
    val token = QrUnlockPayload.generateToken(Random(42))
    val payload = QrUnlockPayload.encode(token)

    assertTrue(QrUnlockPayload.isValid(payload, token))
    assertTrue(QrUnlockPayload.isValid("  $payload\n", token))

    assertFalse(QrUnlockPayload.isValid("MATHWAKE1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", token))
    assertFalse(QrUnlockPayload.isValid(payload, QrUnlockPayload.generateToken(Random(43))))
    assertFalse(QrUnlockPayload.isValid(null, token))
    assertFalse(QrUnlockPayload.isValid(payload, null))
    assertFalse(QrUnlockPayload.isValid("", token))
    assertFalse(QrUnlockPayload.isValid(payload, "short"))
  }

  /**
   * Round-trip guard: the payload size must stay small enough for a printed QR code,
   * and the generated image must decode back to exactly the issued payload.
   */
  @Test
  fun testQrCodePayload_roundTripsThroughZxing() {
    val token = QrUnlockPayload.generateToken(Random(7))
    val payload = QrUnlockPayload.encode(token)
    val size = 480

    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
      for (x in 0 until size) {
        pixels[y * size + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
      }
    }

    val decoded = MultiFormatReader().decode(
      BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
    )
    assertEquals(payload, decoded.text)
    assertTrue(QrUnlockPayload.isValid(decoded.text, token))
  }

  /**
   * Regression guard for the alarm-time scanner: camera Y planes are padded
   * (`rowStride > width`), and the decoder used to rotate the luminance source with
   * `rotateCounterClockwise()`, which ZXing's `PlanarYUVLuminanceSource` does not support.
   * That threw for every frame on portrait phones, so the QR was never decoded.
   */
  @Test
  fun testQrCodeDecoder_readsPaddedCameraPlane() {
    val payload = QrUnlockPayload.encode(QrUnlockPayload.generateToken(Random(21)))
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, QR_TEST_SIZE, QR_TEST_SIZE)
    val frame = buildCameraFrame(matrix)

    val luminance = extractLuminance(frame.buffer, frame.rowStride, 1, frame.width, frame.height)
      ?: error("padded Y plane extraction failed")
    assertEquals(frame.width * frame.height, luminance.size)
    assertEquals(payload, decodeLuminance(luminance, frame.width, frame.height))
  }

  /** Portrait phones deliver the frame rotated by 90 degrees; it must still decode. */
  @Test
  fun testQrCodeDecoder_decodesRotatedCameraFrame() {
    val payload = QrUnlockPayload.encode(QrUnlockPayload.generateToken(Random(22)))
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, QR_TEST_SIZE, QR_TEST_SIZE)
    matrix.rotate90()
    val frame = buildCameraFrame(matrix)

    val luminance = extractLuminance(frame.buffer, frame.rowStride, 1, frame.width, frame.height)
      ?: error("padded Y plane extraction failed")
    assertEquals(payload, decodeLuminance(luminance, frame.width, frame.height))
  }

  @Test
  fun testQrCodeDecoder_blankFrameReturnsNullWithoutThrowing() {
    val width = 64
    val height = 48
    val buffer = ByteBuffer.allocate(width * height)

    val luminance = extractLuminance(buffer, width, 1, width, height)
      ?: error("Y plane extraction failed")
    assertNull(decodeLuminance(luminance, width, height))
  }

  @Test
  fun testAccessibilityServiceList_addPreservesOtherServices() {
    val ours = "com.aistudio.mathwake.qvxn/com.example.alarm.PowerMenuBlockerAccessibilityService"
    val talkback = "com.google.android.marvin.talkback/.TalkBackService"

    val result = AccessibilityServiceList.add(talkback, ours)

    assertEquals("$talkback:$ours", result)
    assertEquals(listOf(talkback, ours), AccessibilityServiceList.parse(result))
  }

  @Test
  fun testAccessibilityServiceList_addDoesNotDuplicate() {
    val ours = "com.aistudio.mathwake.qvxn/com.example.alarm.PowerMenuBlockerAccessibilityService"

    val result = AccessibilityServiceList.add(ours, ours)

    assertEquals(ours, result)
    assertEquals(listOf(ours), AccessibilityServiceList.parse(result))
  }

  @Test
  fun testAccessibilityServiceList_removeOnlyOurService() {
    val ours = "com.aistudio.mathwake.qvxn/com.example.alarm.PowerMenuBlockerAccessibilityService"
    val talkback = "com.google.android.marvin.talkback/.TalkBackService"

    val result = AccessibilityServiceList.remove("$talkback:$ours", ours)

    assertEquals(talkback, result)
    assertEquals(listOf(talkback), AccessibilityServiceList.parse(result))
  }

  @Test
  fun testAccessibilityServiceList_handlesEmptySettings() {
    val ours = "com.aistudio.mathwake.qvxn/com.example.alarm.PowerMenuBlockerAccessibilityService"

    assertEquals(emptyList<String>(), AccessibilityServiceList.parse(null))
    assertEquals(emptyList<String>(), AccessibilityServiceList.parse(""))
    assertEquals(ours, AccessibilityServiceList.add(null, ours))
    assertEquals("", AccessibilityServiceList.remove(ours, ours))
  }
}

private const val QR_TEST_SIZE = 480

private class CameraFrame(
  val buffer: ByteBuffer,
  val width: Int,
  val height: Int,
  val rowStride: Int,
)

/** Builds a padded camera Y plane (white page, black modules) the way CameraX delivers it. */
private fun buildCameraFrame(matrix: BitMatrix): CameraFrame {
  val padding = 24
  val width = matrix.width + padding * 2
  val height = matrix.height + padding * 2
  val rowStride = width + padding
  val buffer = ByteBuffer.allocate(rowStride * height)

  for (y in 0 until height) {
    for (x in 0 until rowStride) {
      buffer.put(y * rowStride + x, 0xFF.toByte())
    }
  }

  val left = (width - matrix.width) / 2
  val top = (height - matrix.height) / 2
  for (y in 0 until matrix.height) {
    for (x in 0 until matrix.width) {
      val value = if (matrix[x, y]) 0x00.toByte() else 0xFF.toByte()
      buffer.put((top + y) * rowStride + left + x, value)
    }
  }
  return CameraFrame(buffer, width, height, rowStride)
}
