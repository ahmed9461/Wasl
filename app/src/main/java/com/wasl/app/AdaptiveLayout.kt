package com.wasl.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val WaslMaxContentWidth = 760.dp

internal fun shouldStackDenseRows(
    availableWidth: Dp,
    fontScale: Float,
): Boolean = availableWidth < 420.dp || fontScale >= 1.3f

@Composable
internal fun shouldStackDenseRows(availableWidth: Dp): Boolean =
    shouldStackDenseRows(
        availableWidth = availableWidth,
        fontScale = LocalDensity.current.fontScale,
    )
