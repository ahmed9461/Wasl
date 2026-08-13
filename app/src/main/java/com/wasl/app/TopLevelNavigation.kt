package com.wasl.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal enum class WaslTopLevelDestination {
    HOME,
    TODAY,
}

@Composable
internal fun WaslTopLevelNavigation(
    selected: WaslTopLevelDestination,
    onOpenHome: () -> Unit,
    onOpenToday: () -> Unit,
) {
    NavigationBar {
        WaslNavigationItem(
            selected = selected == WaslTopLevelDestination.HOME,
            label = "الرئيسية",
            testTag = "nav-home",
            onClick = onOpenHome,
        )
        WaslNavigationItem(
            selected = selected == WaslTopLevelDestination.TODAY,
            label = "اليوم",
            testTag = "nav-today",
            onClick = onOpenToday,
        )
    }
}

@Composable
private fun WaslNavigationItem(
    selected: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        icon = {
            Box(
                modifier = Modifier
                    .size(if (selected) 10.dp else 8.dp)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
            )
        },
        label = { Text(label) },
    )
}
