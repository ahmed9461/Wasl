package com.wasl.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DebtLedgerTest {
    @Test
    fun partialPaymentsPreserveOriginalAmountAndDeriveBalance() {
        val original = Money(100_00L, CurrencyCode.YER)
        val ledger = ledger(original)
            .recordPayment(
                id = LedgerEntryId("payment-1"),
                amount = Money(20_00L, CurrencyCode.YER),
                paidAt = instant("2026-08-02T10:00:00Z"),
            )
            .recordPayment(
                id = LedgerEntryId("payment-2"),
                amount = Money(5_00L, CurrencyCode.YER),
                paidAt = instant("2026-08-03T10:00:00Z"),
            )

        assertEquals(original, ledger.header.originalAmount)
        assertEquals(Money(75_00L, CurrencyCode.YER), ledger.balance)
        assertEquals(Money(25_00L, CurrencyCode.YER), ledger.paidAmount)
        assertEquals(DebtState.PARTIALLY_PAID, ledger.state)
        assertEquals(2, ledger.entries.size)
    }

    @Test
    fun exactFinalPaymentSettlesDebt() {
        val ledger = ledger(Money(10_00L, CurrencyCode.SAR))
            .recordPayment(
                id = LedgerEntryId("payment-1"),
                amount = Money(10_00L, CurrencyCode.SAR),
                paidAt = instant("2026-08-02T10:00:00Z"),
            )

        assertTrue(ledger.balance.isZero)
        assertEquals(DebtState.SETTLED, ledger.state)
        assertEquals(DueState.SETTLED, ledger.dueState(LocalDate.parse("2026-08-10")))
    }

    @Test
    fun overpaymentIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ledger(Money(10_00L, CurrencyCode.USD))
                .recordPayment(
                    id = LedgerEntryId("payment-1"),
                    amount = Money(10_01L, CurrencyCode.USD),
                    paidAt = instant("2026-08-02T10:00:00Z"),
                )
        }
    }

    @Test
    fun paymentWithDifferentCurrencyIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ledger(Money(10_00L, CurrencyCode.SAR))
                .recordPayment(
                    id = LedgerEntryId("payment-1"),
                    amount = Money(10_00L, CurrencyCode.USD),
                    paidAt = instant("2026-08-02T10:00:00Z"),
                )
        }
    }

    @Test
    fun reversalReopensDebtWithoutDeletingHistory() {
        val ledger = ledger(Money(10_00L, CurrencyCode.SAR))
            .recordPayment(
                id = LedgerEntryId("payment-1"),
                amount = Money(10_00L, CurrencyCode.SAR),
                paidAt = instant("2026-08-02T10:00:00Z"),
            )
            .reversePayment(
                id = LedgerEntryId("reversal-1"),
                paymentId = LedgerEntryId("payment-1"),
                recordedAt = instant("2026-08-03T10:00:00Z"),
                reason = "Incorrect payment",
            )

        assertEquals(Money(10_00L, CurrencyCode.SAR), ledger.balance)
        assertEquals(Money.zero(CurrencyCode.SAR), ledger.paidAmount)
        assertEquals(DebtState.OPEN, ledger.state)
        assertEquals(2, ledger.entries.size)
        assertEquals(setOf(LedgerEntryId("payment-1")), ledger.reversedPaymentIds)
        assertTrue(ledger.entries[0] is PaymentRecorded)
        assertTrue(ledger.entries[1] is PaymentReversed)
    }

    @Test
    fun duplicateEntryIdIsRejected() {
        val ledger = ledger(Money(20_00L, CurrencyCode.YER))
            .recordPayment(
                id = LedgerEntryId("entry-1"),
                amount = Money(5_00L, CurrencyCode.YER),
                paidAt = instant("2026-08-02T10:00:00Z"),
            )

        assertFailsWith<IllegalArgumentException> {
            ledger.recordPayment(
                id = LedgerEntryId("entry-1"),
                amount = Money(5_00L, CurrencyCode.YER),
                paidAt = instant("2026-08-03T10:00:00Z"),
            )
        }
    }

    @Test
    fun paymentCannotBeReversedTwice() {
        val ledger = ledger(Money(20_00L, CurrencyCode.YER))
            .recordPayment(
                id = LedgerEntryId("payment-1"),
                amount = Money(5_00L, CurrencyCode.YER),
                paidAt = instant("2026-08-02T10:00:00Z"),
            )
            .reversePayment(
                id = LedgerEntryId("reversal-1"),
                paymentId = LedgerEntryId("payment-1"),
                recordedAt = instant("2026-08-03T10:00:00Z"),
                reason = "Duplicate entry",
            )

        assertFailsWith<IllegalArgumentException> {
            ledger.reversePayment(
                id = LedgerEntryId("reversal-2"),
                paymentId = LedgerEntryId("payment-1"),
                recordedAt = instant("2026-08-04T10:00:00Z"),
                reason = "Second attempt",
            )
        }
    }

    @Test
    fun dueStateIsDerivedAndNotStoredAsUiText() {
        val ledger = ledger(
            original = Money(10_00L, CurrencyCode.YER),
            dueDate = LocalDate.parse("2026-08-12"),
        )

        assertEquals(DueState.UPCOMING, ledger.dueState(LocalDate.parse("2026-08-11")))
        assertEquals(DueState.DUE_TODAY, ledger.dueState(LocalDate.parse("2026-08-12")))
        assertEquals(DueState.OVERDUE, ledger.dueState(LocalDate.parse("2026-08-13")))
    }

    @Test
    fun constructorDefensivelyCopiesMutableEntries() {
        val source = mutableListOf<LedgerEntry>()
        val ledger = DebtLedger(
            header = header(Money(10_00L, CurrencyCode.YER)),
            entries = source,
        )

        source += PaymentRecorded(
            id = LedgerEntryId("outside-payment"),
            amount = Money(10_00L, CurrencyCode.YER),
            paidAt = instant("2026-08-02T10:00:00Z"),
            recordedAt = instant("2026-08-02T10:00:00Z"),
        )

        assertEquals(Money(10_00L, CurrencyCode.YER), ledger.balance)
        assertTrue(ledger.entries.isEmpty())
    }

    @Test
    fun entriesRecordedOutOfOrderAreRejected() {
        val ledger = ledger(Money(20_00L, CurrencyCode.YER))
            .recordPayment(
                id = LedgerEntryId("payment-1"),
                amount = Money(5_00L, CurrencyCode.YER),
                paidAt = instant("2026-08-03T10:00:00Z"),
            )

        assertFailsWith<IllegalArgumentException> {
            ledger.recordPayment(
                id = LedgerEntryId("payment-2"),
                amount = Money(5_00L, CurrencyCode.YER),
                paidAt = instant("2026-08-02T10:00:00Z"),
            )
        }
    }

    @Test
    fun paymentBeforeDebtOpeningIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ledger(Money(10_00L, CurrencyCode.YER))
                .recordPayment(
                    id = LedgerEntryId("payment-1"),
                    amount = Money(5_00L, CurrencyCode.YER),
                    paidAt = instant("2026-07-31T10:00:00Z"),
                    recordedAt = instant("2026-08-02T10:00:00Z"),
                )
        }
    }

    private fun ledger(
        original: Money,
        dueDate: LocalDate? = null,
    ): DebtLedger = DebtLedger(
        header = header(original, dueDate),
    )

    private fun header(
        original: Money,
        dueDate: LocalDate? = null,
    ): DebtHeader =
        DebtHeader(
            id = DebtId("debt-1"),
            personId = PersonId("person-1"),
            direction = DebtDirection.RECEIVABLE,
            originalAmount = original,
            openedAt = instant("2026-08-01T10:00:00Z"),
            dueDate = dueDate,
        )

    private fun instant(value: String): Instant = Instant.parse(value)
}
