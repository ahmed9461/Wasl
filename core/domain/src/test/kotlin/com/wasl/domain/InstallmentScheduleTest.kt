package com.wasl.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallmentScheduleTest {
    @Test
    fun `equal monthly schedule reconciles exact total and dates`() {
        val total = Money(120_000L, CurrencyCode.YER)

        val items = InstallmentSchedule.equalMonthly(
            total = total,
            count = 6,
            firstDueDate = LocalDate.of(2026, 9, 15),
        )

        assertEquals(6, items.size)
        assertEquals(listOf(20_000L, 20_000L, 20_000L, 20_000L, 20_000L, 20_000L), items.map { it.amount.minorUnits })
        assertEquals(LocalDate.of(2027, 2, 15), items.last().dueDate)
        InstallmentSchedule.validateExactTotal(items, total)
    }

    @Test
    fun `remainder minor units are distributed deterministically to earliest installments`() {
        val total = Money(100L, CurrencyCode.YER)

        val items = InstallmentSchedule.equalMonthly(
            total = total,
            count = 3,
            firstDueDate = LocalDate.of(2026, 9, 1),
        )

        assertEquals(listOf(34L, 33L, 33L), items.map { it.amount.minorUnits })
    }

    @Test
    fun `payment progress is allocated fifo without creating another balance`() {
        val total = Money(100_000L, CurrencyCode.YER)
        val items = InstallmentSchedule.equalMonthly(
            total = total,
            count = 4,
            firstDueDate = LocalDate.of(2026, 9, 1),
        )

        val progress = InstallmentSchedule.progress(
            items = items,
            effectivePaidAmount = Money(30_000L, CurrencyCode.YER),
        )

        assertTrue(progress[0].isPaid)
        assertEquals(25_000L, progress[0].paidAmount.minorUnits)
        assertTrue(progress[1].isPartiallyPaid)
        assertEquals(5_000L, progress[1].paidAmount.minorUnits)
        assertEquals(20_000L, progress[1].remainingAmount.minorUnits)
        assertFalse(progress[2].isPartiallyPaid)
        assertEquals(70_000L, progress.sumOf { it.remainingAmount.minorUnits })
    }

    @Test
    fun `schedule rejects an installment count smaller than the available minor units`() {
        assertFailsWith<IllegalArgumentException> {
            InstallmentSchedule.equalMonthly(
                total = Money(2L, CurrencyCode.SAR),
                count = 3,
                firstDueDate = LocalDate.of(2026, 9, 1),
            )
        }
    }

    @Test
    fun `progress rejects another currency`() {
        val items = InstallmentSchedule.equalMonthly(
            total = Money(60_000L, CurrencyCode.YER),
            count = 3,
            firstDueDate = LocalDate.of(2026, 9, 1),
        )

        assertFailsWith<IllegalArgumentException> {
            InstallmentSchedule.progress(
                items = items,
                effectivePaidAmount = Money(1_000L, CurrencyCode.SAR),
            )
        }
    }
}
