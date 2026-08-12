package com.wasl.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceSummaryTest {
    @Test
    fun totalsStaySeparatedByDirectionAndCurrency() {
        val summary = BalanceSummaryCalculator.calculate(
            listOf(
                ledger("debt-1", DebtDirection.RECEIVABLE, Money(100_00L, CurrencyCode.YER)),
                ledger("debt-2", DebtDirection.RECEIVABLE, Money(50_00L, CurrencyCode.SAR)),
                ledger("debt-3", DebtDirection.PAYABLE, Money(20_00L, CurrencyCode.YER)),
                ledger("debt-4", DebtDirection.PAYABLE, Money(7_00L, CurrencyCode.USD)),
            ),
        )

        assertEquals(
            mapOf(
                CurrencyCode.SAR to Money(50_00L, CurrencyCode.SAR),
                CurrencyCode.YER to Money(100_00L, CurrencyCode.YER),
            ),
            summary.receivableByCurrency,
        )
        assertEquals(
            mapOf(
                CurrencyCode.USD to Money(7_00L, CurrencyCode.USD),
                CurrencyCode.YER to Money(20_00L, CurrencyCode.YER),
            ),
            summary.payableByCurrency,
        )
    }

    @Test
    fun settledBalanceContributesZeroWithoutChangingOtherCurrencies() {
        val settled = ledger(
            id = "debt-1",
            direction = DebtDirection.RECEIVABLE,
            amount = Money(10_00L, CurrencyCode.SAR),
        ).recordPayment(
            id = LedgerEntryId("payment-1"),
            amount = Money(10_00L, CurrencyCode.SAR),
            paidAt = Instant.parse("2026-08-02T10:00:00Z"),
        )

        val summary = BalanceSummaryCalculator.calculate(listOf(settled))

        assertEquals(
            Money.zero(CurrencyCode.SAR),
            summary.receivableByCurrency.getValue(CurrencyCode.SAR),
        )
    }

    private fun ledger(
        id: String,
        direction: DebtDirection,
        amount: Money,
    ): DebtLedger = DebtLedger(
        header = DebtHeader(
            id = DebtId(id),
            personId = PersonId("person-" + id),
            direction = direction,
            originalAmount = amount,
            openedAt = Instant.parse("2026-08-01T10:00:00Z"),
        ),
    )
}
