package com.newspush.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6FBA),
    onPrimary = Color.White,
    secondary = Color(0xFFE8752A),
    background = Color(0xFFF7F9FC),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB8E8),
    onPrimary = Color(0xFF0D2A4A),
    secondary = Color(0xFFF0A36D),
    background = Color(0xFF111418),
    surface = Color(0xFF1B1F24)
)

@Composable
fun NewsPushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
