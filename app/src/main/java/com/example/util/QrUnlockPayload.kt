package com.example.util

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Pure logic for the "printed QR code" alarm unlock.
 *
 * The user prints a QR code generated in the app and places it away from the bed
 * (for example in the bathroom). An alarm that enables [com.example.data.model.AlarmEntity.qrRequired]
 * can only be dismissed after the printed code is scanned, so the user has to physically
 * get out of bed even when the math puzzle alone is not enough.
 *
 * The QR payload is `MATHWAKE1:<32文字トークン>`. The token never leaves the device except
 * as the printed QR code.
 */
object QrUnlockPayload {

  const val PREFIX = "MATHWAKE1:"
  const val TOKEN_LENGTH = 32

  // Excludes 0/O/1/I so the printed code stays unambiguous for humans.
  const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

  /** Generates a new random unlock token. [random] is injectable for tests. */
  fun generateToken(random: Random = SecureRandom().asKotlinRandom()): String {
    val builder = StringBuilder(TOKEN_LENGTH)
    repeat(TOKEN_LENGTH) {
      builder.append(ALPHABET[random.nextInt(ALPHABET.length)])
    }
    return builder.toString()
  }

  fun isTokenWellFormed(token: String?): Boolean {
    if (token == null || token.length != TOKEN_LENGTH) return false
    return token.all { it in ALPHABET }
  }

  fun encode(token: String): String = PREFIX + token

  /**
   * Returns true only when [scanned] is exactly the payload issued for [token].
   * Comparison is length-checked and constant-time-ish so a scanned payload cannot
   * be guessed from timing behaviour.
   */
  fun isValid(scanned: String?, token: String?): Boolean {
    val expectedToken = token ?: return false
    if (!isTokenWellFormed(expectedToken)) return false
    val expected = encode(expectedToken)
    val actual = scanned?.trim() ?: return false
    if (actual.length != expected.length) return false
    var difference = 0
    for (index in expected.indices) {
      difference = difference or (expected[index].code xor actual[index].code)
    }
    return difference == 0
  }
}
