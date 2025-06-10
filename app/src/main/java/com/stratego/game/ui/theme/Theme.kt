package com.stratego.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Stratego oyunu için özel renkler
private val StrategoBluePrimary = Color(0xFF1976D2)
private val StrategoBlueSecondary = Color(0xFF42A5F5)
private val StrategoGreen = Color(0xFF4CAF50)
private val StrategoRed = Color(0xFFF44336)
private val StrategoGold = Color(0xFFFFB300)

private val DarkColorScheme = darkColorScheme(
    primary = StrategoBluePrimary,
    secondary = StrategoBlueSecondary,
    tertiary = StrategoGold,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = StrategoBluePrimary,
    secondary = StrategoBlueSecondary,
    tertiary = StrategoGold,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)

@Composable
fun StrategoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}