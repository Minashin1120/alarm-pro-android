package com.example.util

/**
 * Pure helpers for the colon-separated list stored in
 * [android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES].
 *
 * Keeping the parsing separate makes it unit-testable without Android.
 */
object AccessibilityServiceList {

  private const val SEPARATOR = ':'

  fun parse(raw: String?): List<String> =
    raw.orEmpty().split(SEPARATOR).filter { it.isNotBlank() }

  /** Adds [component] while preserving every other enabled service. */
  fun add(raw: String?, component: String): String {
    val services = parse(raw).filterNot { it.equals(component, ignoreCase = true) }
    return (services + component).joinToString(SEPARATOR.toString())
  }

  /** Removes only [component]; other services stay enabled. */
  fun remove(raw: String?, component: String): String =
    parse(raw)
      .filterNot { it.equals(component, ignoreCase = true) }
      .joinToString(SEPARATOR.toString())
}
