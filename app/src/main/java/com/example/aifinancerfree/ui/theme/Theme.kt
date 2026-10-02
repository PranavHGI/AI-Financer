package com.example.aifinancerfree.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = FinanceTealLight,
    secondary = FinanceMint,
    tertiary = WarmAccent,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Ink,
    onBackground = Color(0xFFE0E7E2),
    onSurface = Color(0xFFE0E7E2)
)

private val LightColorScheme = lightColorScheme(
    primary = FinanceTeal,
    secondary = Color(0xFF315A50),
    tertiary = Color(0xFF8B4F00),
    background = SoftBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = Ink
)

@Composable
fun AIFinancerFreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
