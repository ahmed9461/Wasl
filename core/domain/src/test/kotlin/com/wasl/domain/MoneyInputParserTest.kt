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

    @Test
    fun validatesGroupingBeforeRemovingSeparators() {
        for (raw in listOf("1,234.50", "١٬٢٣٤٫٥٠", "۱٬۲۳۴٫۵۰", "1 234.50", "1_234.50", "1\u00a0234.50", "1\u202f234,50")) {
            assertEquals(Money(123_450L, CurrencyCode.SAR), MoneyInputParser.parse(raw, CurrencyCode.SAR), raw)
        }
        for (raw in listOf("1,2.50", "١٢٬٣٤", "1 2", "1_2", "1,234.5,0", "1,23,456", "1٬234,567", "1  234", "_100", "100_")) {
            assertFailsWith<IllegalArgumentException>(raw) { MoneyInputParser.parse(raw, CurrencyCode.SAR) }
        }
    }

    @Test
    fun rejectsExponentNotationAndOversizedInputBeforeDecimalConversion() {
        for (raw in listOf("1e3", "1E+3", "1e2147483647", "NaN", "Infinity", "1".repeat(10_000))) {
            assertFailsWith<IllegalArgumentException>(raw.take(40)) { MoneyInputParser.parse(raw, CurrencyCode.SAR) }
        }
    }

    @Test
    fun preservesExactLongBoundaryAndDecimalCommaRules() {
        assertEquals(Money(Long.MAX_VALUE, CurrencyCode.YER), MoneyInputParser.parse("9223372036854775807", CurrencyCode.YER))
        assertEquals(Money(Long.MAX_VALUE, CurrencyCode.SAR), MoneyInputParser.parse("92233720368547758.07", CurrencyCode.SAR))
        assertEquals(Money(125L, CurrencyCode.SAR), MoneyInputParser.parse("1,25", CurrencyCode.SAR))
        assertEquals(Money(50L, CurrencyCode.SAR), MoneyInputParser.parse(".50", CurrencyCode.SAR))
        assertEquals(Money(123_400L, CurrencyCode.SAR), MoneyInputParser.parse("1,234", CurrencyCode.SAR))
        for (raw in listOf("92233720368547758.08", "1.001", "0", "-1", "1,", "1.", "+", ".")) {
            assertFailsWith<IllegalArgumentException>(raw) { MoneyInputParser.parse(raw, CurrencyCode.SAR) }
        }
        assertFailsWith<IllegalArgumentException> { MoneyInputParser.parse("1,25", CurrencyCode.YER) }
    }
}
