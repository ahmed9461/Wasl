package com.wasl.app

import com.wasl.domain.CurrencyCode
import com.wasl.domain.Money
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPaymentIntentFormattingTest {
    @Test
    fun fullPaymentPrefillKeepsExactMinorUnitsForMvpCurrencies() {
        assertEquals("100000", paymentInputValue(Money(100_000L, CurrencyCode.YER)))
        assertEquals("123.45", paymentInputValue(Money(12_345L, CurrencyCode.SAR)))
        assertEquals("0.01", paymentInputValue(Money(1L, CurrencyCode.USD)))
    }
}
