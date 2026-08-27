package com.wasl.app.data

import com.wasl.domain.DebtId
import java.time.Instant
import java.time.LocalDate

/**
 * A historical record that the counterparty requested payment from the user.
 *
 * This is deliberately separate from the financial ledger, the debt due date,
 * payment promises, and installments. Recording a claim must never change the
 * balance or create a financial transaction.
 */
enum class PaymentClaimFollowUpKind {
    TODAY,
    TOMORROW,
    SALARY,
    CUSTOM,
}

enum class PaymentClaimStatus {
    ACTIVE,
    RESOLVED,
    CANCELLED,
}

data class PaymentClaimRecord(
    val id: String,
    val debtId: DebtId,
    val claimedAt: Instant,
    val followUpKind: PaymentClaimFollowUpKind,
    val followUpDate: LocalDate?,
    val note: String? = null,
    val status: PaymentClaimStatus = PaymentClaimStatus.ACTIVE,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
    val resolutionNote: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Payment claim ID cannot be blank." }
        require((status == PaymentClaimStatus.ACTIVE) == (resolvedAt == null)) {
            "Only an active payment claim may have no resolution timestamp."
        }
        require(followUpKind != PaymentClaimFollowUpKind.CUSTOM || followUpDate != null) {
            "A custom payment claim follow-up requires an explicit date."
        }
        require(followUpKind != PaymentClaimFollowUpKind.SALARY || followUpDate == null) {
            "Salary follow-up remains date-free until the user chooses a salary policy/date."
        }
        require(note == null || note.isNotBlank()) { "Payment claim note must be null or non-blank." }
        require(resolutionNote == null || resolutionNote.isNotBlank()) {
            "Payment claim resolution note must be null or non-blank."
        }
    }
}

data class CreatePaymentClaimCommand(
    val commandId: String,
    val claimId: String,
    val debtId: DebtId,
    val claimedAt: Instant,
    val followUpKind: PaymentClaimFollowUpKind,
    val followUpDate: LocalDate?,
    val note: String? = null,
    val createdAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Payment claim command ID cannot be blank." }
        require(claimId.isNotBlank()) { "Payment claim ID cannot be blank." }
        require(!createdAt.isBefore(claimedAt)) {
            "Payment claim creation cannot predate the claim timestamp."
        }
        require(followUpKind != PaymentClaimFollowUpKind.CUSTOM || followUpDate != null) {
            "A custom payment claim follow-up requires an explicit date."
        }
        require(followUpKind != PaymentClaimFollowUpKind.SALARY || followUpDate == null) {
            "Salary follow-up must not guess a date without an explicit user policy."
        }
        require(note == null || note.isNotBlank()) { "Payment claim note must be null or non-blank." }
    }
}

data class ResolvePaymentClaimCommand(
    val commandId: String,
    val claimId: String,
    val debtId: DebtId,
    val status: PaymentClaimStatus,
    val resolvedAt: Instant,
    val note: String? = null,
) {
    init {
        require(commandId.isNotBlank()) { "Payment claim resolution command ID cannot be blank." }
        require(claimId.isNotBlank()) { "Payment claim ID cannot be blank." }
        require(status != PaymentClaimStatus.ACTIVE) {
            "A payment claim resolution requires a terminal status."
        }
        require(note == null || note.isNotBlank()) {
            "Payment claim resolution note must be null or non-blank."
        }
    }
}
