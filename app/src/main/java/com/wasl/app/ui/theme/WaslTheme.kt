package com.wasl.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F4EA),
    onPrimaryContainer = Color(0xFF062E29),
    secondary = Color(0xFF315B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8ECF6),
    onSecondaryContainer = Color(0xFF173746),
    tertiary = Color(0xFF8A5B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE2A8),
    onTertiaryContainer = Color(0xFF392500),
    background = Color(0xFFF6F9F8),
    onBackground = Color(0xFF15201E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15201E),
    surfaceVariant = Color(0xFFE4ECE9),
    onSurfaceVariant = Color(0xFF52605C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F3),
    surfaceContainer = Color(0xFFEBF1EF),
    surfaceContainerHigh = Color(0xFFE4EBE8),
    surfaceContainerHighest = Color(0xFFDCE5E2),
    outline = Color(0xFF7A8A85),
    outlineVariant = Color(0xFFC5D1CD),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF63DCCB),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFB7F2E6),
    secondary = Color(0xFFA8D1E5),
    onSecondary = Color(0xFF123746),
    secondaryContainer = Color(0xFF244D60),
    onSecondaryContainer = Color(0xFFD0EDFA),
    tertiary = Color(0xFFF7C86A),
    onTertiary = Color(0xFF482F00),
    tertiaryContainer = Color(0xFF664500),
    onTertiaryContainer = Color(0xFFFFE2A8),
    background = Color(0xFF0D1513),
    onBackground = Color(0xFFDCE5E2),
    surface = Color(0xFF101A18),
    onSurface = Color(0xFFDCE5E2),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color(0xFF08110F),
    surfaceContainerLow = Color(0xFF111B18),
    surfaceContainer = Color(0xFF15201D),
    surfaceContainerHigh = Color(0xFF1E2926),
    surfaceContainerHighest = Color(0xFF293431),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF8C1D18),
)

private val WaslTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 35.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

private val WaslShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun WaslTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WaslTypography,
        shapes = WaslShapes,
        content = content,
    )
}
