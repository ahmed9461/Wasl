package com.wasl.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupExpenseTest {
    private val occurredAt = Instant.parse("2026-08-28T00:00:00Z")

    @Test
    fun supportsUnequalSharesWhenTheyExactlyMatchTotal() {
        val expense = GroupExpense(
            id = GroupExpenseId("group-1"),
            direction = DebtDirection.RECEIVABLE,
            totalAmount = Money(30_000L, CurrencyCode.YER),
            occurredAt = occurredAt,
            description = "مطعم",
            shares = listOf(
                share("share-1", "debt-1", "person-1", 5_000L),
                share("share-2", "debt-2", "person-2", 10_000L),
                share("share-3", "debt-3", "person-3", 15_000L),
            ),
        )

        assertEquals(30_000L, expense.totalAmount.minorUnits)
        assertEquals(3, expense.shares.size)
    }

    @Test
    fun rejectsShareSumThatDoesNotMatchTotal() {
        assertFailsWith<IllegalArgumentException> {
            GroupExpense(
                id = GroupExpenseId("group-sum"),
                direction = DebtDirection.RECEIVABLE,
                totalAmount = Money(30_000L, CurrencyCode.YER),
                occurredAt = occurredAt,
                description = "مطعم",
                shares = listOf(
                    share("share-a", "debt-a", "person-a", 10_000L),
                    share("share-b", "debt-b", "person-b", 10_000L),
                ),
            )
        }
    }

    @Test
    fun rejectsMixedCurrencies() {
        assertFailsWith<IllegalArgumentException> {
            GroupExpense(
                id = GroupExpenseId("group-currency"),
                direction = DebtDirection.RECEIVABLE,
                totalAmount = Money(20_000L, CurrencyCode.YER),
                occurredAt = occurredAt,
                description = "رحلة",
                shares = listOf(
                    share("share-y", "debt-y", "person-y", 10_000L),
                    GroupExpenseShare(
                        id = GroupExpenseShareId("share-s"),
                        debtId = DebtId("debt-s"),
                        personId = PersonId("person-s"),
                        amount = Money(10_000L, CurrencyCode.SAR),
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsDuplicatePersonOrDebt() {
        assertFailsWith<IllegalArgumentException> {
            GroupExpense(
                id = GroupExpenseId("group-duplicate"),
                direction = DebtDirection.RECEIVABLE,
                totalAmount = Money(20_000L, CurrencyCode.YER),
                occurredAt = occurredAt,
                description = "طلب مشترك",
                shares = listOf(
                    share("share-1", "debt-same", "person-same", 10_000L),
                    share("share-2", "debt-other", "person-same", 10_000L),
                ),
            )
        }
    }

    private fun share(
        id: String,
        debtId: String,
        personId: String,
        amountMinor: Long,
    ): GroupExpenseShare = GroupExpenseShare(
        id = GroupExpenseShareId(id),
        debtId = DebtId(debtId),
        personId = PersonId(personId),
        amount = Money(amountMinor, CurrencyCode.YER),
    )
}
