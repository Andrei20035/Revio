package com.revio.app.features.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class OnboardingPage(
    val title: String,
    val subtitle: String,
    @DrawableRes val imageRes: Int,
    val titleColor: Color = Color(0xFFDFA3A3),
    val subtitleColor: Color = Color.White.copy(alpha = 0.8f),
    val titleFontWeight: FontWeight = FontWeight.Medium,
    val subtitleFontWeight: FontWeight = FontWeight.Normal,
    val titleFontSize: TextUnit = 36.sp,
    val subtitleFontSize: TextUnit = 26.sp,
    val titleFontFamily: FontFamily = FontFamily.Default,
    val subtitleFontFamily: FontFamily = FontFamily.Default,
    val lineHeight: TextUnit = 40.sp
)