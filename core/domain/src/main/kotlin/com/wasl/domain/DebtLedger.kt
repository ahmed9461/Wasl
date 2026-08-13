package com.wasl.domain

import java.time.Instant
import java.time.LocalDate

/**
 * The financial source of truth for one debt.
 *
 * The original amount is immutable. Payments and reversals are appended, never
 * overwritten. Replaying the ledger derives the balance and state.
 */
class DebtLedger(
    val header: DebtHeader,
    entries: List<LedgerEntry> = emptyList(),
) {
    val entries: List<LedgerEntry> = entries.toList()

    init {
        replay(this.entries)
    }

    val balance: Money
        get() = Money(replay(entries).balanceMinorUnits, header.originalAmount.currency)

    /** Amount currently paid after applying every recorded reversal. */
    val paidAmount: Money
        get() = header.originalAmount.minus(balance)

    /** Payments whose effect has been cancelled by a later append-only reversal. */
    val reversedPaymentIds: Set<LedgerEntryId>
        get() = entries
            .filterIsInstance<PaymentReversed>()
            .mapTo(linkedSetOf()) { it.paymentId }

    val state: DebtState
        get() = when (balance.minorUnits) {
            0L -> DebtState.SETTLED
            header.originalAmount.minorUnits -> DebtState.OPEN
            else -> DebtState.PARTIALLY_PAID
        }

    fun dueState(onDate: LocalDate): DueState {
        if (state == DebtState.SETTLED) return DueState.SETTLED

        val dueDate = header.dueDate ?: return DueState.NO_DUE_DATE
        return when {
            dueDate.isBefore(onDate) -> DueState.OVERDUE
            dueDate.isEqual(onDate) -> DueState.DUE_TODAY
            else -> DueState.UPCOMING
        }
    }

    fun recordPayment(
        id: LedgerEntryId,
        amount: Money,
        paidAt: Instant,
        recordedAt: Instant = paidAt,
        note: String? = null,
    ): DebtLedger {
        require(!recordedAt.isBefore(header.openedAt)) {
            "Ledger entry cannot be recorded before the debt was opened."
        }
        return DebtLedger(
            header = header,
            entries = entries + PaymentRecorded(
                id = id,
                amount = amount,
                paidAt = paidAt,
                recordedAt = recordedAt,
                note = note,
            ),
        )
    }

    fun reversePayment(
        id: LedgerEntryId,
        paymentId: LedgerEntryId,
        recordedAt: Instant,
        reason: String,
    ): DebtLedger {
        require(!recordedAt.isBefore(header.openedAt)) {
            "Ledger entry cannot be recorded before the debt was opened."
        }
        return DebtLedger(
            header = header,
            entries = entries + PaymentReversed(
                id = id,
                paymentId = paymentId,
                recordedAt = recordedAt,
                reason = reason,
            ),
        )
    }

    private fun replay(source: List<LedgerEntry>): ReplayResult {
        var balanceMinorUnits = header.originalAmount.minorUnits
        val seenEntryIds = mutableSetOf<LedgerEntryId>()
        val payments = mutableMapOf<LedgerEntryId, PaymentRecorded>()
        val reversedPayments = mutableSetOf<LedgerEntryId>()
        var lastRecordedAt = header.openedAt

        source.forEach { entry ->
            require(seenEntryIds.add(entry.id)) { "Ledger entry IDs must be unique." }
            require(!entry.recordedAt.isBefore(header.openedAt)) {
                "Ledger entry cannot be recorded before the debt was opened."
            }
            require(!entry.recordedAt.isBefore(lastRecordedAt)) {
                "Ledger entries must remain in recording order."
            }

            when (entry) {
                is PaymentRecorded -> {
                    require(!entry.paidAt.isBefore(header.openedAt)) {
                        "Payment time cannot be before the debt was opened."
                    }
                    require(entry.amount.currency == header.originalAmount.currency) {
                        "Payment currency must match the debt currency."
                    }
                    require(entry.amount.minorUnits <= balanceMinorUnits) {
                        "Payment cannot exceed the remaining debt balance."
                    }
                    balanceMinorUnits = Math.subtractExact(
                        balanceMinorUnits,
                        entry.amount.minorUnits,
                    )
                    payments[entry.id] = entry
                }

                is PaymentReversed -> {
                    val payment = requireNotNull(payments[entry.paymentId]) {
                        "A reversal must reference an earlier payment."
                    }
                    require(reversedPayments.add(entry.paymentId)) {
                        "A payment can be reversed only once."
                    }
                    require(!entry.recordedAt.isBefore(payment.recordedAt)) {
                        "A reversal cannot be recorded before its payment."
                    }
                    balanceMinorUnits = Math.addExact(
                        balanceMinorUnits,
                        payment.amount.minorUnits,
                    )
                }
            }
            lastRecordedAt = entry.recordedAt
        }

        return ReplayResult(balanceMinorUnits)
    }

    private data class ReplayResult(
        val balanceMinorUnits: Long,
    )
}
