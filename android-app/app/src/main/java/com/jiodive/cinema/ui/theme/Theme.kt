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

/**
 * Restrained single-accent matte cinema palette:
 * One deliberate cyan brand accent paired with deep obsidian/graphite neutrals.
 * Avoids multi-accent neon saturation so video frames and posters own visual hierarchy.
 */
val LocalCinemaExtras = staticCompositionLocalOf {
    CinemaPaletteExtras(
        turquoiseAccent = Color(0xFF0A7E8C),
        emeraldReady = Color(0xFF2E8B6E),
        emeraldContainer = Color(0xFFE7F2EE),
        goldHighlight = Color(0xFFB5892E),
        goldContainer = Color(0xFFF6F1E6),
        inkPrimary = Color(0xFF101619),
        inkMuted = Color(0xFF606E75),
        cardBorder = Color(0xFFE1E6E9),
        activePillBg = Color(0xFFE3F2F4),
        bottomBarBg = Color(0xFFFAFAFC),
        fabSurface = Color(0xFF0A7E8C),
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0A7E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2F4),
    onPrimaryContainer = Color(0xFF063D45),
    secondary = Color(0xFF47555E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEFF1),
    onSecondaryContainer = Color(0xFF1D262B),
    tertiary = Color(0xFF5A666E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0F2F4),
    onTertiaryContainer = Color(0xFF283136),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF101619),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101619),
    surfaceVariant = Color(0xFFEBEEF1),
    onSurfaceVariant = Color(0xFF606E75),
    outline = Color(0xFFDCE2E6),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38C6BD),
    onPrimary = Color(0xFF042220),
    primaryContainer = Color(0xFF163233),
    onPrimaryContainer = Color(0xFFB8ECE8),
    secondary = Color(0xFF94A3AB),
    onSecondary = Color(0xFF13181A),
    secondaryContainer = Color(0xFF1D2326),
    onSecondaryContainer = Color(0xFFD5DEE2),
    tertiary = Color(0xFF8C9AA2),
    onTertiary = Color(0xFF13181A),
    tertiaryContainer = Color(0xFF1E2427),
    onTertiaryContainer = Color(0xFFDCE4E8),
    background = Color(0xFF0C0F11),
    onBackground = Color(0xFFEDF1F2),
    surface = Color(0xFF14181B),
    onSurface = Color(0xFFEDF1F2),
    surfaceVariant = Color(0xFF1C2125),
    onSurfaceVariant = Color(0xFF8C99A0),
    outline = Color(0xFF23292D),
)

private val LightExtras = CinemaPaletteExtras(
    turquoiseAccent = Color(0xFF0A7E8C),
    emeraldReady = Color(0xFF2E8B6E),
    emeraldContainer = Color(0xFFE7F2EE),
    goldHighlight = Color(0xFF7B6B4F),
    goldContainer = Color(0xFFF2EFE9),
    inkPrimary = Color(0xFF101619),
    inkMuted = Color(0xFF606E75),
    cardBorder = Color(0xFFE2E7EA),
    activePillBg = Color(0xFFE3F2F4),
    bottomBarBg = Color(0xFFFAFAFC),
    fabSurface = Color(0xFF0A7E8C),
)

private val DarkExtras = CinemaPaletteExtras(
    turquoiseAccent = Color(0xFF38C6BD),
    emeraldReady = Color(0xFF56B88C),
    emeraldContainer = Color(0xFF152922),
    goldHighlight = Color(0xFFC2A66D),
    goldContainer = Color(0xFF242018),
    inkPrimary = Color(0xFFEDF1F2),
    inkMuted = Color(0xFF8C99A0),
    cardBorder = Color(0xFF22282C),
    activePillBg = Color(0xFF172C2E),
    bottomBarBg = Color(0xFF121618),
    fabSurface = Color(0xFF38C6BD),
)

private val CinemaTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
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
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
    ),
)

private val CinemaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
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
