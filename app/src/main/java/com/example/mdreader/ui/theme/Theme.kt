package com.example.mdreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand colors
private val LightPrimary = Color(0xFF1A73E8)
private val LightBackground = Color(0xFFFAFAFA)
private val LightSurface = Color(0xFFFFFFFF)
private val DarkPrimary = Color(0xFF8AB4F8)
private val DarkBackground = Color(0xFF1A1A1A)
private val DarkSurface = Color(0xFF2D2D2D)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    background = LightBackground,
    surface = LightSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.Black,
    background = DarkBackground,
    surface = DarkSurface,
)

@Composable
fun MDReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
