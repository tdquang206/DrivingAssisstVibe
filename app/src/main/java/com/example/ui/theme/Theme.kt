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

private val LightColorScheme =
  lightColorScheme(
    primary = VDashPrimary,
    background = VDashBackground,
    surface = VDashSurface,
    onPrimary = VDashSurface,
    onBackground = VDashTextDark,
    onSurface = VDashTextDark,
    surfaceVariant = VDashSurfaceAccent,
    onSurfaceVariant = VDashTextDark,
    outline = VDashBorder,
    error = androidx.compose.ui.graphics.Color(0xFFBA1A1A), // Standard M3 error
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF410002)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Force light theme to match design specs for now
  // Dynamic color is disabled by default to show branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> LightColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
