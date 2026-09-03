package com.kadhafi.aetherhop.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = DarkCanvas,
    surface = DarkSurface,
    onPrimary = DarkCanvas,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = SignalDanger
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightCanvas,
    surface = LightSurface,
    onPrimary = LightSurface,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = SignalDanger
)

import androidx.compose.ui.graphics.Color

enum class ThemePreset {
    DEFAULT,
    AMOLED_BLACK,
    TACTICAL_AMBER,
    RESCUE_RED
}

private val AmoledColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = AmoledCanvas,
    surface = AmoledSurface,
    onPrimary = AmoledCanvas,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = SignalDanger
)

private val AmberColorScheme = darkColorScheme(
    primary = AmberPrimary,
    secondary = SignalWarning,
    background = AmberCanvas,
    surface = DarkSurface,
    onPrimary = AmberCanvas,
    onBackground = AmberPrimary,
    onSurface = AmberPrimary,
    onSurfaceVariant = AmberPrimary.copy(alpha = 0.7f),
    outline = AmberPrimary.copy(alpha = 0.4f),
    error = SignalDanger
)

private val RedColorScheme = darkColorScheme(
    primary = RedPrimary,
    secondary = SignalDanger,
    background = RedCanvas,
    surface = DarkSurface,
    onPrimary = RedCanvas,
    onBackground = RedPrimary,
    onSurface = RedPrimary,
    onSurfaceVariant = RedPrimary.copy(alpha = 0.7f),
    outline = RedPrimary.copy(alpha = 0.4f),
    error = SignalDanger
)

@Composable
fun AetherHopTheme(
    themePreset: ThemePreset = ThemePreset.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (themePreset) {
        ThemePreset.AMOLED_BLACK -> AmoledColorScheme
        ThemePreset.TACTICAL_AMBER -> AmberColorScheme
        ThemePreset.RESCUE_RED -> RedColorScheme
        ThemePreset.DEFAULT -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
