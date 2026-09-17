package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = UkubiiPrimary,
    onPrimary = Color.White,
    primaryContainer = UkubiiPrimaryVariant,
    secondary = UkubiiGold,
    onSecondary = Color.Black,
    tertiary = UkubiiGreen,
    background = UkubiiDarkBg,
    onBackground = UkubiiTextPrimary,
    surface = UkubiiCardBg,
    onSurface = UkubiiTextPrimary,
    surfaceVariant = UkubiiCardBorder,
    onSurfaceVariant = UkubiiTextMuted,
    error = UkubiiRed
)

private val LightColorScheme = DarkColorScheme // Telegram theme default dark luxury

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
