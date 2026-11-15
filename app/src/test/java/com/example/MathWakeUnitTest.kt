package com.example

import com.example.alarm.AlarmScheduler
import com.example.data.model.AlarmEntity
import com.example.math.MathProblemGenerator
import com.example.util.QrUnlockPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
