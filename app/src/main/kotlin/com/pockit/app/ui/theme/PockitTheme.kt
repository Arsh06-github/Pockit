package com.pockit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SpaceBlack = Color(0xFF010102)
val DeepLavender = Color(0xFF7022FC)
val SunriseOrange = Color(0xFFFD8F32)
val White = Color(0xFFFFFFFF)
val SurfaceColor = Color(0xFF0E0E13)
val RaisedSurface = Color(0xFF17171E)
val DividerColor = Color(0x1AFFFFFF) // white at 10%

private val PockitColorScheme = darkColorScheme(
    primary = DeepLavender,
    onPrimary = White,
    secondary = SunriseOrange,
    onSecondary = White,
    background = SpaceBlack,
    onBackground = White,
    surface = SurfaceColor,
    onSurface = White,
    surfaceVariant = RaisedSurface,
    onSurfaceVariant = White,
    outline = SunriseOrange,
    error = SunriseOrange,
    onError = White,
)

@Composable
fun PockitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PockitColorScheme,
        typography = PockitTypography,
        content = content,
    )
}
