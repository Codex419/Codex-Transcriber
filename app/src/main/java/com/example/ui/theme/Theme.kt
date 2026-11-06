package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricAmethyst,
    onPrimary = Color.White,
    primaryContainer = ElectricAmethystContainer,
    onPrimaryContainer = ElectricAmethystLight,
    secondary = MagmaCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4B231B),
    onSecondaryContainer = Color(0xFFFFB4A3),
    tertiary = SnapdragonCyan,
    onTertiary = StudioBackground,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = StatusErrorRed,
    onError = StudioBackground
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}
