package com.wasl.app.data

import com.wasl.domain.CurrencyCode
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdvancedSearchQueryTest {
    private val zoneId = ZoneId.of("Asia/Aden")

    @Test
    fun numericQueryProducesExactMinorUnitCandidatesWithoutFloatingPoint() {
        val criteria = requireNotNull(LocalSearchQuery.toAdvancedCriteria("1,234.50", zoneId))

        assertNull(criteria.amountMinor(CurrencyCode.YER))
        assertEquals(123_450L, criteria.amountMinor(CurrencyCode.SAR))
        assertEquals(123_450L, criteria.amountMinor(CurrencyCode.USD))
    }

    @Test
    fun explicitCurrencyRestrictsAmountCandidateToThatCurrency() {
        val criteria = requireNotNull(LocalSearchQuery.toAdvancedCriteria("٢٠٠٠٠ YER", zoneId))

        assertEquals(20_000L, criteria.amountMinor(CurrencyCode.YER))
        assertNull(criteria.amountMinor(CurrencyCode.SAR))
        assertNull(criteria.amountMinor(CurrencyCode.USD))
    }

    @Test
    fun exactCivilDateUsesTheProvidedZoneAndEpochDay() {
        val criteria = requireNotNull(LocalSearchQuery.toAdvancedCriteria("١٣/٠٨/٢٠٢٦", zoneId))

        assertEquals(20_678L, criteria.dateEpochDay)
        assertEquals(1_786_568_400_000L, criteria.dateStartMillis)
        assertEquals(1_786_654_800_000L, criteria.dateEndMillis)
    }

    @Test
    fun ordinaryTextDoesNotInventAmountOrDateCriteria() {
        val criteria = requireNotNull(LocalSearchQuery.toAdvancedCriteria("إيجار المنزل", zoneId))

        assertEquals(emptyMap(), criteria.amountMinorByCurrency)
        assertNull(criteria.dateEpochDay)
        assertNull(criteria.dateStartMillis)
        assertNull(criteria.dateEndMillis)
    }
}
