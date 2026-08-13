package com.wasl.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalSearchQueryTest {
    @Test
    fun normalizesLeadingTrailingAndRepeatedWhitespace() {
        assertEquals("أحمد علي", LocalSearchQuery.normalize("  أحمد\n  علي  "))
    }

    @Test
    fun escapesSqlLikeControlCharactersAsLiteralText() {
        assertEquals(
            "%100\\%\\_\\\\%",
            LocalSearchQuery.toSqlLikePattern("100%_\\"),
        )
    }

    @Test
    fun blankInputDoesNotCreateAnUnboundedPattern() {
        assertNull(LocalSearchQuery.toSqlLikePattern(" \n\t "))
    }
}
