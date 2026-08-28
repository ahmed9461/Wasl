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
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 8.dp,
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
            WaslDestinationIcon(destination = destination, selected = selected)
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        alwaysShowLabel = !largeFontScale || selected,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
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
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = Modifier.size(23.dp)) {
        val strokeWidth = if (selected) 2.35.dp.toPx() else 2.05.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (destination) {
            WaslTopLevelDestination.HOME -> {
                drawLine(color, Offset(size.width * .18f, size.height * .48f), Offset(size.width * .50f, size.height * .20f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * .50f, size.height * .20f), Offset(size.width * .82f, size.height * .48f), strokeWidth, StrokeCap.Round)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .26f, size.height * .43f),
                    size = Size(size.width * .48f, size.height * .40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
            }
            WaslTopLevelDestination.TODAY -> {
                drawCircle(color, size.minDimension * .34f, center, style = stroke)
                drawLine(color, center, Offset(center.x, size.height * .30f), strokeWidth, StrokeCap.Round)
                drawLine(color, center, Offset(size.width * .67f, size.height * .57f), strokeWidth, StrokeCap.Round)
            }
            WaslTopLevelDestination.SEARCH -> {
                drawCircle(color, size.minDimension * .25f, Offset(size.width * .43f, size.height * .42f), style = stroke)
                drawLine(color, Offset(size.width * .61f, size.height * .60f), Offset(size.width * .82f, size.height * .81f), strokeWidth, StrokeCap.Round)
            }
            WaslTopLevelDestination.INSTALLMENTS -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .18f, size.height * .23f),
                    size = Size(size.width * .64f, size.height * .56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(size.width * .32f, size.height * .40f), Offset(size.width * .68f, size.height * .40f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * .32f, size.height * .57f), Offset(size.width * .68f, size.height * .57f), strokeWidth, StrokeCap.Round)
            }
            WaslTopLevelDestination.SETTINGS -> {
                drawCircle(color, size.minDimension * .20f, center, style = stroke)
                listOf(
                    Offset(.50f, .10f) to Offset(.50f, .24f),
                    Offset(.50f, .76f) to Offset(.50f, .90f),
                    Offset(.10f, .50f) to Offset(.24f, .50f),
                    Offset(.76f, .50f) to Offset(.90f, .50f),
                    Offset(.22f, .22f) to Offset(.32f, .32f),
                    Offset(.68f, .68f) to Offset(.78f, .78f),
                    Offset(.78f, .22f) to Offset(.68f, .32f),
                    Offset(.32f, .68f) to Offset(.22f, .78f),
                ).forEach { (start, end) ->
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
