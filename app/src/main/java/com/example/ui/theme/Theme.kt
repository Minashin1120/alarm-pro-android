package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = AmberPrimary,
  onPrimary = Slate950,
  primaryContainer = OrangeAccent,
  onPrimaryContainer = Color.White,
  secondary = SkyInfo,
  onSecondary = Slate950,
  secondaryContainer = Slate700,
  onSecondaryContainer = Slate100,
  tertiary = EmeraldSuccess,
  onTertiary = Color.White,
  background = Slate950,
  onBackground = Slate100,
  surface = Slate900,
  onSurface = Slate100,
  surfaceVariant = Slate800,
  onSurfaceVariant = Slate200,
  outline = Slate600,
  error = CrimsonAlarm,
  onError = Color.White
)

private val LightColorScheme = darkColorScheme(
  // Keep high-contrast vibrant dark look as the signature visual direction for alarm reliability
  primary = AmberPrimary,
  onPrimary = Slate950,
  primaryContainer = OrangeAccent,
  onPrimaryContainer = Color.White,
  secondary = SkyInfo,
  onSecondary = Slate950,
  secondaryContainer = Slate700,
  onSecondaryContainer = Slate100,
  tertiary = EmeraldSuccess,
  onTertiary = Color.White,
  background = Slate950,
  onBackground = Slate100,
  surface = Slate900,
  onSurface = Slate100,
  surfaceVariant = Slate800,
  onSurfaceVariant = Slate200,
  outline = Slate600,
  error = CrimsonAlarm,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}
