package com.wasl.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun exactMinorUnitArithmeticDoesNotUseFloatingPoint() {
        val first = Money(10L, CurrencyCode.USD)
        val second = Money(20L, CurrencyCode.USD)

        assertEquals(Money(30L, CurrencyCode.USD), first.plus(second))
        assertEquals(Money(10L, CurrencyCode.USD), first.plus(second).minus(second))
    }

    @Test
    fun currenciesCannotBeMixed() {
        assertFailsWith<IllegalArgumentException> {
            Money(10L, CurrencyCode.SAR).plus(Money(10L, CurrencyCode.YER))
        }
    }

    @Test
    fun overflowFailsInsteadOfCorruptingBalance() {
        assertFailsWith<ArithmeticException> {
            Money(Long.MAX_VALUE, CurrencyCode.YER).plus(Money(1L, CurrencyCode.YER))
        }
    }
}
