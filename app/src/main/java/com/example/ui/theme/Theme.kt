package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary = Slate500,
    onSecondary = Color.White,
    tertiary = Green700,
    tertiaryContainer = Green100,
    onTertiaryContainer = Green700,
    background = BackgroundLight,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Slate50,
    outline = Slate400,
    outlineVariant = Slate200,
    error = Red600,
    onError = Color.White,
    errorContainer = Red50,
    onErrorContainer = Red700,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = Indigo300,
    onPrimary = Indigo900,
    primaryContainer = Indigo700,
    onPrimaryContainer = Indigo50,
    secondary = Slate400,
    onSecondary = Slate900,
    tertiary = Green500,
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Green100,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    surfaceContainer = Slate900,
    surfaceContainerHigh = Slate800,
    outline = Slate500,
    outlineVariant = Slate700,
    error = Red300,
    onError = Red900,
    errorContainer = Red900,
    onErrorContainer = Red100,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    typography = Typography,
    content = content,
  )
}
