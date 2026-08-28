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
import com.wasl.app.privacy.AppAppearance

private val LightColors = lightColorScheme(
    primary = Color(0xFF087F72), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F5EE), onPrimaryContainer = Color(0xFF083D38),
    secondary = Color(0xFF49645F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECE9), onSecondaryContainer = Color(0xFF213532),
    tertiary = Color(0xFF9A6A18), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9B7), onTertiaryContainer = Color(0xFF3E2A00),
    background = Color(0xFFF6F8F7), onBackground = Color(0xFF17201E),
    surface = Color(0xFFFCFDFC), onSurface = Color(0xFF17201E),
    surfaceVariant = Color(0xFFE3E9E6), onSurfaceVariant = Color(0xFF52605D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F3),
    surfaceContainer = Color(0xFFEBF0EE),
    surfaceContainerHigh = Color(0xFFE5EBE8),
    surfaceContainerHighest = Color(0xFFDCE5E1),
    outline = Color(0xFF778481), outlineVariant = Color(0xFFCBD5D1),
    error = Color(0xFFB42318), onError = Color.White,
    errorContainer = Color(0xFFFEE4E2), onErrorContainer = Color(0xFF7A271A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55DCC7), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0D4D47), onPrimaryContainer = Color(0xFFC4FFF3),
    secondary = Color(0xFFB3CBC5), onSecondary = Color(0xFF1E3531),
    secondaryContainer = Color(0xFF2B403C), onSecondaryContainer = Color(0xFFD2E8E2),
    tertiary = Color(0xFFE8C77B), onTertiary = Color(0xFF3D2F00),
    tertiaryContainer = Color(0xFF594600), onTertiaryContainer = Color(0xFFFFE8A9),
    background = Color(0xFF071114), onBackground = Color(0xFFE4EEEB),
    surface = Color(0xFF0B1518), onSurface = Color(0xFFE4EEEB),
    surfaceVariant = Color(0xFF243337), onSurfaceVariant = Color(0xFFB9C8C5),
    surfaceContainerLowest = Color(0xFF050D0F),
    surfaceContainerLow = Color(0xFF0D181B),
    surfaceContainer = Color(0xFF111E21),
    surfaceContainerHigh = Color(0xFF17262A),
    surfaceContainerHighest = Color(0xFF203136),
    outline = Color(0xFF81918E), outlineVariant = Color(0xFF2D3E42),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val WaslTypography = Typography(
    displaySmall = TextStyle(FontFamily.SansSerif, FontWeight.ExtraBold, 34.sp, 43.sp),
    headlineMedium = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 28.sp, 37.sp),
    headlineSmall = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 24.sp, 33.sp),
    titleLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 21.sp, 30.sp),
    titleMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 17.sp, 26.sp),
    titleSmall = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 15.sp, 23.sp),
    bodyLarge = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 16.sp, 26.sp),
    bodyMedium = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 14.sp, 22.sp),
    bodySmall = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 12.sp, 19.sp),
    labelLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 14.sp, 21.sp),
    labelMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 12.sp, 18.sp),
    labelSmall = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 11.sp, 17.sp),
)

private val WaslShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun WaslTheme(
    appearance: AppAppearance = AppAppearance.SYSTEM,
    darkTheme: Boolean = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.DARK -> true
        AppAppearance.LIGHT -> false
    },
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WaslTypography,
        shapes = WaslShapes,
        content = content,
    )
}