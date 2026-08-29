package com.wasl.app

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveLayoutTest {
    @Test
    fun veryNarrowContentStacksDenseRows() {
        assertTrue(shouldStackDenseRows(320.dp, fontScale = 1f))
    }

    @Test
    fun standardPhoneWidthKeepsDenseRowsHorizontalAtDefaultFontScale() {
        assertFalse(shouldStackDenseRows(360.dp, fontScale = 1f))
        assertFalse(shouldStackDenseRows(390.dp, fontScale = 1f))
        assertFalse(shouldStackDenseRows(420.dp, fontScale = 1f))
        assertFalse(shouldStackDenseRows(500.dp, fontScale = 1f))
    }

    @Test
    fun largeFontScaleStacksDenseRowsEvenOnWideContent() {
        assertTrue(shouldStackDenseRows(500.dp, fontScale = 1.3f))
        assertTrue(shouldStackDenseRows(760.dp, fontScale = 2f))
    }
}
