package com.wasl.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val LocalOpenInstallmentsHub = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalOpenSettingsHub = staticCompositionLocalOf<(() -> Unit)?> { null }

internal enum class WaslTopLevelDestination {
    HOME,
    TODAY,
    SEARCH,
    INSTALLMENTS,
    SETTINGS,
}

@Composable
internal fun WaslTopLevelNavigation(
    selected: WaslTopLevelDestination,
    onOpenHome: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val onOpenInstallments = LocalOpenInstallmentsHub.current
    val onOpenSettings = LocalOpenSettingsHub.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            WaslNavigationItem(
                selected = selected == WaslTopLevelDestination.HOME,
                destination = WaslTopLevelDestination.HOME,
                label = "الرئيسية",
                testTag = "nav-home",
                onClick = onOpenHome,
            )
            WaslNavigationItem(
                selected = selected == WaslTopLevelDestination.TODAY,
                destination = WaslTopLevelDestination.TODAY,
                label = "اليوم",
                testTag = "nav-today",
                onClick = onOpenToday,
            )
            WaslNavigationItem(
                selected = selected == WaslTopLevelDestination.SEARCH,
                destination = WaslTopLevelDestination.SEARCH,
                label = "البحث",
                testTag = "nav-search",
                onClick = onOpenSearch,
            )
            onOpenInstallments?.let { openInstallments ->
                WaslNavigationItem(
                    selected = selected == WaslTopLevelDestination.INSTALLMENTS,
                    destination = WaslTopLevelDestination.INSTALLMENTS,
                    label = "الأقساط",
                    testTag = "open-installments-hub",
                    onClick = openInstallments,
                )
            }
            onOpenSettings?.let { openSettings ->
                WaslNavigationItem(
                    selected = selected == WaslTopLevelDestination.SETTINGS,
                    destination = WaslTopLevelDestination.SETTINGS,
                    label = "الإعدادات",
                    testTag = "open-settings-hub",
                    onClick = openSettings,
                )
            }
        }
    }
}

@Composable
private fun RowScope.WaslNavigationItem(
    selected: Boolean,
    destination: WaslTopLevelDestination,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val largeFontScale = LocalDensity.current.fontScale >= 1.3f
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .semantics {
                contentDescription = label
                stateDescription = if (selected) "محددة" else "غير محددة"
            }
            .testTag(testTag),
        icon = {
            WaslDestinationIcon(
                destination = destination,
                selected = selected,
            )
        },
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        alwaysShowLabel = !largeFontScale || selected,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun WaslDestinationIcon(
    destination: WaslTopLevelDestination,
    selected: Boolean,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(modifier = Modifier.size(24.dp)) {
        val strokeWidth = 2.15.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (destination) {
            WaslTopLevelDestination.HOME -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.48f),
                    end = Offset(size.width * 0.50f, size.height * 0.20f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.50f, size.height * 0.20f),
                    end = Offset(size.width * 0.82f, size.height * 0.48f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.26f, size.height * 0.43f),
                    size = Size(size.width * 0.48f, size.height * 0.40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
            }

            WaslTopLevelDestination.TODAY -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.34f,
                    center = center,
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(center.x, size.height * 0.30f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(size.width * 0.67f, size.height * 0.57f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            WaslTopLevelDestination.SEARCH -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.25f,
                    center = Offset(size.width * 0.43f, size.height * 0.42f),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.61f, size.height * 0.60f),
                    end = Offset(size.width * 0.82f, size.height * 0.81f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            WaslTopLevelDestination.INSTALLMENTS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.23f),
                    size = Size(size.width * 0.64f, size.height * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.32f, size.height * 0.40f),
                    end = Offset(size.width * 0.68f, size.height * 0.40f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.32f, size.height * 0.57f),
                    end = Offset(size.width * 0.68f, size.height * 0.57f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            WaslTopLevelDestination.SETTINGS -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.20f,
                    center = center,
                    style = stroke,
                )
                val rays = listOf(
                    Offset(0.50f, 0.10f) to Offset(0.50f, 0.24f),
                    Offset(0.50f, 0.76f) to Offset(0.50f, 0.90f),
                    Offset(0.10f, 0.50f) to Offset(0.24f, 0.50f),
                    Offset(0.76f, 0.50f) to Offset(0.90f, 0.50f),
                    Offset(0.22f, 0.22f) to Offset(0.32f, 0.32f),
                    Offset(0.68f, 0.68f) to Offset(0.78f, 0.78f),
                    Offset(0.78f, 0.22f) to Offset(0.68f, 0.32f),
                    Offset(0.32f, 0.68f) to Offset(0.22f, 0.78f),
                )
                rays.forEach { (start, end) ->
                    drawLine(
                        color = color,
                        start = Offset(size.width * start.x, size.height * start.y),
                        end = Offset(size.width * end.x, size.height * end.y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
