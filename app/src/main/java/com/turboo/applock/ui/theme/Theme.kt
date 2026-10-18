package com.turboo.applock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF2E7DB8)

private val LightColors = lightColorScheme(
    primary = Accent,
    secondary = Color(0xFF4A6572),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ECBF5),
    secondary = Color(0xFFB0C6D2),
)

@Composable
fun AppLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
