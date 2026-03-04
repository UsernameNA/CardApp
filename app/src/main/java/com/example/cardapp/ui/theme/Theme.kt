package com.example.cardapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LeatherColorScheme = darkColorScheme(
    primary            = GoldPrimary,
    onPrimary          = LeatherDark,
    primaryContainer   = LeatherMid,
    onPrimaryContainer = CreamPrimary,
    secondary          = BurgundyAccent,
    onSecondary        = CreamPrimary,
    background         = LeatherDark,
    onBackground       = CreamPrimary,
    surface            = LeatherMid,
    onSurface          = CreamPrimary,
    outline            = GoldMuted,
)

@Composable
fun CardAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LeatherColorScheme,
        typography = Typography,
        content = content,
    )
}
