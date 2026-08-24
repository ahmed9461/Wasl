package com.wasl.domain

import java.time.LocalDate

/**
 * A scheduled slice of one debt. Installments never own a second balance;
 * progress is derived from the debt's effective Ledger payments.
 */
data class InstallmentScheduleItem(
    val sequenceNumber: Int,
    val dueDate: LocalDate,
    val amount: Money,
) {
    init {
        require(sequenceNumber > 0) { "Installment sequence must be positive." }
        require(amount.minorUnits > 0L) { "Installment amount must be positive." }
    }
}

data class InstallmentProgress(
    val item: InstallmentScheduleItem,
    val paidAmount: Money,
    val remainingAmount: Money,
) {
    init {
        require(paidAmount.currency == item.amount.currency) {
            "Installment progress currency must match the schedule."
        }
        require(remainingAmount.currency == item.amount.currency) {
            "Installment remaining currency must match the schedule."
        }
        require(paidAmount.plus(remainingAmount) == item.amount) {
            "Installment progress must reconcile to its scheduled amount."
        }
    }

    val isPaid: Boolean
        get() = remainingAmount.isZero

    val isPartiallyPaid: Boolean
        get() = paidAmount.minorUnits > 0L && !isPaid
}

object InstallmentSchedule {
    const val MAX_INSTALLMENTS: Int = 120

    fun equalMonthly(
        total: Money,
        count: Int,
        firstDueDate: LocalDate,
    ): List<InstallmentScheduleItem> {
        require(total.minorUnits > 0L) { "Installment total must be positive." }
        require(count in 1..MAX_INSTALLMENTS) {
            "Installment count must be between 1 and $MAX_INSTALLMENTS."
        }
        require(total.minorUnits >= count.toLong()) {
            "Each installment must contain at least one minor unit."
        }

        val base = total.minorUnits / count.toLong()
        val remainder = total.minorUnits % count.toLong()
        val items = List(count) { index ->
            val extraMinorUnit = if (index.toLong() < remainder) 1L else 0L
            InstallmentScheduleItem(
                sequenceNumber = index + 1,
                dueDate = firstDueDate.plusMonths(index.toLong()),
                amount = Money(
                    minorUnits = Math.addExact(base, extraMinorUnit),
                    currency = total.currency,
                ),
            )
        }
        check(items.sumMinorUnitsExact() == total.minorUnits) {
            "Generated installment schedule does not reconcile to the debt total."
        }
        return items
    }

    fun progress(
        items: List<InstallmentScheduleItem>,
        effectivePaidAmount: Money,
    ): List<InstallmentProgress> {
        require(items.isNotEmpty()) { "Installment schedule cannot be empty." }
        val currency = items.first().amount.currency
        require(items.all { it.amount.currency == currency }) {
            "All installments must use one currency."
        }
        require(effectivePaidAmount.currency == currency) {
            "Paid amount currency must match the installment schedule."
        }
        val totalMinor = items.sumMinorUnitsExact()
        require(effectivePaidAmount.minorUnits <= totalMinor) {
            "Paid amount cannot exceed the installment schedule total."
        }

        var unallocatedPaidMinor = effectivePaidAmount.minorUnits
        return items.sortedBy { it.sequenceNumber }.map { item ->
            val paidMinor = minOf(unallocatedPaidMinor, item.amount.minorUnits)
            unallocatedPaidMinor = Math.subtractExact(unallocatedPaidMinor, paidMinor)
            val paid = Money(paidMinor, currency)
            InstallmentProgress(
                item = item,
                paidAmount = paid,
                remainingAmount = item.amount.minus(paid),
            )
        }.also {
            check(unallocatedPaidMinor == 0L) {
                "Installment payment allocation left an unreconciled amount."
            }
        }
    }

    fun validateExactTotal(
        items: List<InstallmentScheduleItem>,
        expectedTotal: Money,
    ) {
        require(items.isNotEmpty()) { "Installment schedule cannot be empty." }
        require(items.map { it.sequenceNumber } == (1..items.size).toList()) {
            "Installment sequence must be contiguous and ordered."
        }
        require(items.zipWithNext().all { (first, second) -> second.dueDate > first.dueDate }) {
            "Installment due dates must be strictly increasing."
        }
        require(items.all { it.amount.currency == expectedTotal.currency }) {
            "Installment currency must match the debt currency."
        }
        require(items.sumMinorUnitsExact() == expectedTotal.minorUnits) {
            "Installment schedule total must match the debt total."
        }
    }

    private fun List<InstallmentScheduleItem>.sumMinorUnitsExact(): Long =
        fold(0L) { total, item -> Math.addExact(total, item.amount.minorUnits) }
}
