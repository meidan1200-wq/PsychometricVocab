package com.example.psychometricvocab.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Yellow,
    onPrimary = TextPrimary,
    primaryContainer = YellowLight,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = White,
    background = OffWhite,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = TextSecondary,
    outline = DividerGray,
    error = WrongRed,
    onError = Color.White
)

@Composable
fun PsychometricVocabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
