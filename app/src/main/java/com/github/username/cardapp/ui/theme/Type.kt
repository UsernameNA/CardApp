package com.github.username.cardapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = 10.sp,
        lineHeight = 58.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        letterSpacing = 5.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 4.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.25.sp,
    ),
)
