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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class WaslTopLevelDestination {
    HOME,
    TODAY,
    SEARCH,
}

@Composable
internal fun WaslTopLevelNavigation(
    selected: WaslTopLevelDestination,
    onOpenHome: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenSearch: () -> Unit,
) {
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
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
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
        }
    }
}
