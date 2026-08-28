package com.wasl.domain

import java.time.Instant
import java.time.LocalDate

enum class DebtDirection {
    RECEIVABLE,
    PAYABLE,
}

enum class DebtState {
    OPEN,
    PARTIALLY_PAID,
    SETTLED,
}

enum class DueState {
    NO_DUE_DATE,
    UPCOMING,
    DUE_TODAY,
    OVERDUE,
    SETTLED,
}

data class DebtHeader(
    val id: DebtId,
    val personId: PersonId,
    val direction: DebtDirection,
    val originalAmount: Money,
    val openedAt: Instant,
    val dueDate: LocalDate? = null,
    val description: String? = null,
) {
    init {
        require(originalAmount.minorUnits > 0L) { "Original debt amount must be positive." }
        require(description == null || description.isNotBlank()) {
            "Debt description must be null or non-blank."
        }
    }
}

sealed interface LedgerEntry {
    val id: LedgerEntryId
    val recordedAt: Instant
}

data class PaymentRecorded(
    override val id: LedgerEntryId,
    val amount: Money,
    val paidAt: Instant,
    override val recordedAt: Instant,
    val note: String? = null,
) : LedgerEntry {
    init {
        require(amount.minorUnits > 0L) { "Payment amount must be positive." }
        require(!paidAt.isAfter(recordedAt)) { "Payment time cannot be after its recording time." }
        require(note == null || note.isNotBlank()) { "Payment note must be null or non-blank." }
    }
}

data class PaymentReversed(
    override val id: LedgerEntryId,
    val paymentId: LedgerEntryId,
    override val recordedAt: Instant,
    val reason: String,
) : LedgerEntry {
    init {
        require(reason.isNotBlank()) { "A payment reversal requires a reason." }
    }
}
