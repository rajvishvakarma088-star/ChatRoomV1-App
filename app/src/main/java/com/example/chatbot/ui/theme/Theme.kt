package com.example.chatbot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Sky500,
    secondary = Mint500,
    tertiary = Coral500,
    background = Ink900,
    surface = Ink700,
    onPrimary = White,
    onSecondary = Ink900,
    onBackground = White,
    onSurface = White
)

private val LightColorScheme = lightColorScheme(
    primary = Ink900,
    secondary = Sky500,
    tertiary = Mint500,
    background = Sand100,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onBackground = Ink900,
    onSurface = Ink900
)

@Composable
fun ChatBotTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
