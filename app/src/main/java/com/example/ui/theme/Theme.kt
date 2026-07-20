package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantPurple,
    onPrimary = Color.Black,
    primaryContainer = ElegantDeepPurple,
    onPrimaryContainer = ElegantPurple,
    secondary = ElegantPurple,
    onSecondary = Color.Black,
    secondaryContainer = ElegantDeepPurple,
    onSecondaryContainer = ElegantPurple,
    background = Black,
    onBackground = ElegantTextPrimary,
    surface = ElegantDarkGray,
    onSurface = ElegantTextPrimary,
    surfaceVariant = ElegantDarkGray,
    onSurfaceVariant = ElegantOnSurfaceVariant,
    outline = ElegantBorder,
    outlineVariant = ElegantDarkGray
  )

private val LightColorScheme = DarkColorScheme // Force dark theme aesthetic throughout

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme by default
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve 'Elegant Dark' styling
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme


  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
