package com.wasl.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
internal val LocalOpenNaturalEntry = staticCompositionLocalOf<(() -> Unit)?> { null }

internal enum class WaslTopLevelDestination { HOME, TODAY, SEARCH, INSTALLMENTS, SETTINGS }

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
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            WaslNavigationItem(selected == WaslTopLevelDestination.HOME, WaslTopLevelDestination.HOME, "الرئيسية", "nav-home", onOpenHome)
            WaslNavigationItem(selected == WaslTopLevelDestination.TODAY, WaslTopLevelDestination.TODAY, "اليوم", "nav-today", onOpenToday)
            WaslNavigationItem(selected == WaslTopLevelDestination.SEARCH, WaslTopLevelDestination.SEARCH, "البحث", "nav-search", onOpenSearch)
            onOpenInstallments?.let { WaslNavigationItem(selected == WaslTopLevelDestination.INSTALLMENTS, WaslTopLevelDestination.INSTALLMENTS, "الأقساط", "open-installments-hub", it) }
            onOpenSettings?.let { WaslNavigationItem(selected == WaslTopLevelDestination.SETTINGS, WaslTopLevelDestination.SETTINGS, "الإعدادات", "open-settings-hub", it) }
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
        icon = { WaslDestinationIcon(destination, selected) },
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
            indicatorColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun WaslDestinationIcon(destination: WaslTopLevelDestination, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = if (selected) 2.35.dp.toPx() else 1.9.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (destination) {
            WaslTopLevelDestination.HOME -> {
                drawLine(color, Offset(size.width * .18f, size.height * .48f), Offset(size.width * .50f, size.height * .20f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * .50f, size.height * .20f), Offset(size.width * .82f, size.height * .48f), strokeWidth, StrokeCap.Round)
                drawRoundRect(color = color, topLeft = Offset(size.width * .26f, size.height * .43f), size = Size(size.width * .48f, size.height * .40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
            }
            WaslTopLevelDestination.TODAY -> {
                drawRoundRect(color = color, topLeft = Offset(size.width * .18f, size.height * .22f), size = Size(size.width * .64f, size.height * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = stroke)
                drawLine(color, Offset(size.width * .31f, size.height * .14f), Offset(size.width * .31f, size.height * .31f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * .69f, size.height * .14f), Offset(size.width * .69f, size.height * .31f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * .28f, size.height * .44f), Offset(size.width * .72f, size.height * .44f), strokeWidth, StrokeCap.Round)
            }
            WaslTopLevelDestination.SEARCH -> {
                drawCircle(color, size.minDimension * .25f, Offset(size.width * .43f, size.height * .42f), style = stroke)
                drawLine(color, Offset(size.width * .61f, size.height * .60f), Offset(size.width * .82f, size.height * .81f), strokeWidth, StrokeCap.Round)
            }
            WaslTopLevelDestination.INSTALLMENTS -> {
                drawRoundRect(color = color, topLeft = Offset(size.width * .18f, size.height * .20f), size = Size(size.width * .64f, size.height * .60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = stroke)
                drawLine(color, Offset(size.width * .31f, size.height * .39f), Offset(size.width * .69f, size.height * .39f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * .31f, size.height * .58f), Offset(size.width * .69f, size.height * .58f), strokeWidth, StrokeCap.Round)
            }
            WaslTopLevelDestination.SETTINGS -> {
                drawCircle(color, size.minDimension * .19f, center, style = stroke)
                listOf(
                    Offset(.50f, .10f) to Offset(.50f, .24f), Offset(.50f, .76f) to Offset(.50f, .90f),
                    Offset(.10f, .50f) to Offset(.24f, .50f), Offset(.76f, .50f) to Offset(.90f, .50f),
                    Offset(.22f, .22f) to Offset(.32f, .32f), Offset(.68f, .68f) to Offset(.78f, .78f),
                    Offset(.78f, .22f) to Offset(.68f, .32f), Offset(.32f, .68f) to Offset(.22f, .78f),
                ).forEach { (start, end) -> drawLine(color, Offset(size.width * start.x, size.height * start.y), Offset(size.width * end.x, size.height * end.y), strokeWidth, StrokeCap.Round) }
            }
        }
        if (selected) {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * .32f, size.height * .94f),
                size = Size(size.width * .36f, 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}
