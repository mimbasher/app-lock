package com.turboo.applock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.turboo.applock.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7DB8),
    secondary = Color(0xFF4A6572),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ECBF5),
    secondary = Color(0xFFB0C6D2),
)

/** Resolves [ThemeMode.SYSTEM] against the current device setting. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppLockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.isDark()) DarkColors else LightColors,
        content = content,
    )
}
