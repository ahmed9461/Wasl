package com.wasl.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyInputParserTest {
    @Test
    fun parsesYerAndArabicDigitsExactly() {
        assertEquals(
            Money(100_000L, CurrencyCode.YER),
            MoneyInputParser.parse("١٠٠٬٠٠٠", CurrencyCode.YER),
        )
    }

    @Test
    fun parsesFractionalCurrenciesWithoutDouble() {
        assertEquals(
            Money(12_350L, CurrencyCode.SAR),
            MoneyInputParser.parse("123٫50", CurrencyCode.SAR),
        )
        assertEquals(
            Money(125L, CurrencyCode.USD),
            MoneyInputParser.parse("1,25", CurrencyCode.USD),
        )
    }

    @Test
    fun rejectsUnsupportedPrecisionZeroAndOverflow() {
        assertFailsWith<IllegalArgumentException> {
            MoneyInputParser.parse("10.5", CurrencyCode.YER)
        }
        assertFailsWith<IllegalArgumentException> {
            MoneyInputParser.parse("0", CurrencyCode.SAR)
        }
        assertFailsWith<IllegalArgumentException> {
            MoneyInputParser.parse("999999999999999999999999", CurrencyCode.USD)
        }
    }
}
