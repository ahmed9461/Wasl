package com.wasl.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasl.app.privacy.AppAppearance
import com.wasl.app.privacy.PrivacyPreferences

private val LocalWaslAppearance = staticCompositionLocalOf<AppAppearance?> { null }

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B8E7E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F5EE), onPrimaryContainer = Color(0xFF073D37),
    secondary = Color(0xFF516965), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EEEB), onSecondaryContainer = Color(0xFF243A36),
    tertiary = Color(0xFFA77A27), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDC2), onTertiaryContainer = Color(0xFF3B2B08),
    background = Color(0xFFF7F9F8), onBackground = Color(0xFF151D1C),
    surface = Color(0xFFFCFDFC), onSurface = Color(0xFF151D1C),
    surfaceVariant = Color(0xFFE5EAE8), onSurfaceVariant = Color(0xFF5A6663),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5F4),
    surfaceContainer = Color(0xFFECF0EF),
    surfaceContainerHigh = Color(0xFFE6EBE9),
    surfaceContainerHighest = Color(0xFFDDE5E2),
    outline = Color(0xFF7E8B87), outlineVariant = Color(0xFFD0D8D5),
    error = Color(0xFFB42318), onError = Color.White,
    errorContainer = Color(0xFFFEE4E2), onErrorContainer = Color(0xFF7A271A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3ED0B4), onPrimary = Color(0xFF00392F),
    primaryContainer = Color(0xFF103C36), onPrimaryContainer = Color(0xFFB8F4E7),
    secondary = Color(0xFFAFC4C0), onSecondary = Color(0xFF1C3430),
    secondaryContainer = Color(0xFF243A36), onSecondaryContainer = Color(0xFFD4E7E2),
    tertiary = Color(0xFFE3BE66), onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF473909), onTertiaryContainer = Color(0xFFFFE6A1),
    background = Color(0xFF060E12), onBackground = Color(0xFFE7EFED),
    surface = Color(0xFF081217), onSurface = Color(0xFFE7EFED),
    surfaceVariant = Color(0xFF1D2B30), onSurfaceVariant = Color(0xFFA9B9B6),
    surfaceContainerLowest = Color(0xFF050C10),
    surfaceContainerLow = Color(0xFF0A1419),
    surfaceContainer = Color(0xFF0D181D),
    surfaceContainerHigh = Color(0xFF132027),
    surfaceContainerHighest = Color(0xFF1A2930),
    outline = Color(0xFF6F817E), outlineVariant = Color(0xFF25343A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF4E1517), onErrorContainer = Color(0xFFFFDAD6),
)

private val WaslTypography = Typography(
    displaySmall = TextStyle(FontFamily.SansSerif, FontWeight.ExtraBold, 32.sp, 40.sp),
    headlineMedium = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 27.sp, 35.sp),
    headlineSmall = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 23.sp, 31.sp),
    titleLarge = TextStyle(FontFamily.SansSerif, FontWeight.Bold, 20.sp, 28.sp),
    titleMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 16.sp, 24.sp),
    titleSmall = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 14.sp, 21.sp),
    bodyLarge = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 16.sp, 25.sp),
    bodyMedium = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 14.sp, 22.sp),
    bodySmall = TextStyle(FontFamily.SansSerif, FontWeight.Normal, 12.sp, 18.sp),
    labelLarge = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 14.sp, 20.sp),
    labelMedium = TextStyle(FontFamily.SansSerif, FontWeight.SemiBold, 12.sp, 18.sp),
    labelSmall = TextStyle(FontFamily.SansSerif, FontWeight.Medium, 11.sp, 16.sp),
)

private val WaslShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun WaslTheme(
    appearance: AppAppearance? = null,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val inheritedAppearance = LocalWaslAppearance.current
    val savedAppearance = remember(context) { PrivacyPreferences(context).appearance }
    val resolvedAppearance = appearance ?: inheritedAppearance ?: savedAppearance
    val resolvedDarkTheme = darkTheme ?: when (resolvedAppearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.DARK -> true
        AppAppearance.LIGHT -> false
    }
    CompositionLocalProvider(LocalWaslAppearance provides resolvedAppearance) {
        MaterialTheme(
            colorScheme = if (resolvedDarkTheme) DarkColors else LightColors,
            typography = WaslTypography,
            shapes = WaslShapes,
            content = content,
        )
    }
}
