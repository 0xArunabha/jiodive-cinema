package com.jiodive.cinema.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class CinemaPaletteExtras(
    val turquoiseAccent: Color,
    val emeraldReady: Color,
    val emeraldContainer: Color,
    val goldHighlight: Color,
    val goldContainer: Color,
    val inkPrimary: Color,
    val inkMuted: Color,
    val cardBorder: Color,
    val activePillBg: Color,
    val bottomBarBg: Color,
    val fabSurface: Color,
)

val LocalCinemaExtras = staticCompositionLocalOf {
    CinemaPaletteExtras(
        turquoiseAccent = Color(0xFF00808A),
        emeraldReady = Color(0xFF109E75),
        emeraldContainer = Color(0xFFDCF7EE),
        goldHighlight = Color(0xFFD99B00),
        goldContainer = Color(0xFFFFF4CC),
        inkPrimary = Color(0xFF0B2230),
        inkMuted = Color(0xFF5B707B),
        cardBorder = Color(0xFFD5E2E6),
        activePillBg = Color(0xFFD9F3F2),
        bottomBarBg = Color(0xFFFFFFFF),
        fabSurface = Color(0xFF00808A),
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00808A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F3F2),
    onPrimaryContainer = Color(0xFF004248),
    secondary = Color(0xFF109E75),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCF7EE),
    onSecondaryContainer = Color(0xFF063E2E),
    tertiary = Color(0xFFD99B00),
    onTertiary = Color(0xFF281A00),
    tertiaryContainer = Color(0xFFFFF4CC),
    onTertiaryContainer = Color(0xFF423000),
    background = Color(0xFFF4F8F9),
    onBackground = Color(0xFF0B2230),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B2230),
    surfaceVariant = Color(0xFFEAF2F4),
    onSurfaceVariant = Color(0xFF5B707B),
    outline = Color(0xFFD5E2E6),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF042B29),
    primaryContainer = Color(0xFF0D3B3E),
    onPrimaryContainer = Color(0xFF9EF6EB),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF04291C),
    secondaryContainer = Color(0xFF0E3B2B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFFACC15),
    onTertiary = Color(0xFF281F00),
    tertiaryContainer = Color(0xFF332B0B),
    onTertiaryContainer = Color(0xFFFEF08A),
    background = Color(0xFF111516),
    onBackground = Color(0xFFEFF5F6),
    surface = Color(0xFF181D1F),
    onSurface = Color(0xFFEFF5F6),
    surfaceVariant = Color(0xFF21282B),
    onSurfaceVariant = Color(0xFF93A4AD),
    outline = Color(0xFF2A3438),
)

private val LightExtras = CinemaPaletteExtras(
    turquoiseAccent = Color(0xFF00808A),
    emeraldReady = Color(0xFF109E75),
    emeraldContainer = Color(0xFFDCF7EE),
    goldHighlight = Color(0xFFD99B00),
    goldContainer = Color(0xFFFFF4CC),
    inkPrimary = Color(0xFF0B2230),
    inkMuted = Color(0xFF5B707B),
    cardBorder = Color(0xFFD5E2E6),
    activePillBg = Color(0xFFD9F3F2),
    bottomBarBg = Color(0xFFFFFFFF),
    fabSurface = Color(0xFF00808A),
)

private val DarkExtras = CinemaPaletteExtras(
    turquoiseAccent = Color(0xFF2DD4BF),
    emeraldReady = Color(0xFF34D399),
    emeraldContainer = Color(0xFF0E3B2B),
    goldHighlight = Color(0xFFFACC15),
    goldContainer = Color(0xFF332B0B),
    inkPrimary = Color(0xFFEFF5F6),
    inkMuted = Color(0xFF93A4AD),
    cardBorder = Color(0xFF2A3438),
    activePillBg = Color(0xFF0D3B3E),
    bottomBarBg = Color(0xFF181D1F),
    fabSurface = Color(0xFF2DD4BF),
)

private val CinemaTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val CinemaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp),
)

@Composable
fun JioDiveCinemaTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extras = if (darkTheme) DarkExtras else LightExtras

    CompositionLocalProvider(LocalCinemaExtras provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CinemaTypography,
            shapes = CinemaShapes,
            content = content,
        )
    }
}
