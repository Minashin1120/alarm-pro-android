package com.example.util

import android.content.Context

/**
 * Stores the single device-wide "wake-up QR" token.
 *
 * A single token keeps the feature practical: the user prints one sheet and can enable
 * the QR requirement on any alarm. Regenerating the token invalidates every printed sheet,
 * so the setup screen asks for confirmation first.
 */
object QrUnlockManager {

  private const val PREFS_NAME = "mathwake_qr_unlock"
  private const val KEY_TOKEN = "qr_token"

  fun getToken(context: Context): String? {
    val prefs = context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val token = prefs.getString(KEY_TOKEN, null)
    return if (QrUnlockPayload.isTokenWellFormed(token)) token else null
  }

  /** Returns the current token, creating (and persisting) one on first use. */
  fun getOrCreateToken(context: Context): String {
    getToken(context)?.let { return it }
    return regenerate(context)
  }

  fun getOrCreatePayload(context: Context): String =
    QrUnlockPayload.encode(getOrCreateToken(context))

  fun regenerate(context: Context): String {
    val token = QrUnlockPayload.generateToken()
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_TOKEN, token)
      .apply()
    return token
  }
}
