package com.wasl.app

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectionalTextTest {
    @Test
    fun ltrIsolateWrapsMixedDirectionValues() {
        assertEquals("\u206612/08/2026 14:30\u2069", ltrIsolate("12/08/2026 14:30"))
        assertEquals("\u2066STAT-2026-00044\u2069", ltrIsolate("STAT-2026-00044"))
    }

    @Test
    fun ltrIsolateDoesNotDoubleWrapAlreadyIsolatedText() {
        val isolated = "\u2066123,456.78 USD\u2069"
        assertEquals(isolated, ltrIsolate(isolated))
    }
}
