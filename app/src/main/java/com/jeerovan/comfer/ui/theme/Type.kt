package com.jeerovan.comfer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.jeerovan.comfer.R

import androidx.compose.ui.text.googlefonts.Font

@OptIn(ExperimentalTextApi::class)
val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)
@OptIn(ExperimentalTextApi::class)
val fontName = GoogleFont("Inter")

@OptIn(ExperimentalTextApi::class)
val interFontFamily = FontFamily(
    Font(googleFont = fontName, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = fontName, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = fontName, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = fontName, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = fontName, fontProvider = fontProvider, weight = FontWeight.Bold)
)
// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = interFontFamily, // Use the Inter font family
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = interFontFamily, // Use the Inter font family
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = interFontFamily, // Use the Inter font family
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)