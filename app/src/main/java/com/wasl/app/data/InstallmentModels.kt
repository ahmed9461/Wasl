package com.wasl.app.data

import com.wasl.domain.DebtId
import com.wasl.domain.Money
import java.time.Instant
import java.time.LocalDate

enum class InstallmentPlanStatus {
    ACTIVE,
    SUPERSEDED,
}

data class InstallmentPlanItemInput(
    val id: String,
    val sequenceNumber: Int,
    val dueDate: LocalDate,
    val amount: Money,
) {
    init {
        require(id.isNotBlank()) { "Installment ID cannot be blank." }
        require(sequenceNumber > 0) { "Installment sequence must be positive." }
        require(amount.minorUnits > 0L) { "Installment amount must be positive." }
    }
}

data class InstallmentRecord(
    val id: String,
    val planId: String,
    val debtId: DebtId,
    val revisionNumber: Int,
    val sequenceNumber: Int,
    val dueDate: LocalDate,
    val scheduledAmount: Money,
    val paidAmount: Money,
    val remainingAmount: Money,
) {
    init {
        require(id.isNotBlank()) { "Installment ID cannot be blank." }
        require(planId.isNotBlank()) { "Installment plan ID cannot be blank." }
        require(revisionNumber > 0) { "Installment revision must be positive." }
        require(sequenceNumber > 0) { "Installment sequence must be positive." }
        require(paidAmount.plus(remainingAmount) == scheduledAmount) {
            "Installment progress must reconcile to its scheduled amount."
        }
    }

    val isPaid: Boolean
        get() = remainingAmount.isZero

    val isPartiallyPaid: Boolean
        get() = paidAmount.minorUnits > 0L && !isPaid

    fun isOverdue(onDate: LocalDate): Boolean = !isPaid && dueDate.isBefore(onDate)

    fun isDueToday(onDate: LocalDate): Boolean = !isPaid && dueDate == onDate
}

data class InstallmentPlanRecord(
    val id: String,
    val debtId: DebtId,
    val revisionNumber: Int,
    val status: InstallmentPlanStatus,
    val createdAt: Instant,
    val supersedesPlanId: String? = null,
    val supersededAt: Instant? = null,
    val supersededAfterLedgerSequence: Long? = null,
    val reason: String? = null,
    val installments: List<InstallmentRecord>,
) {
    init {
        require(id.isNotBlank()) { "Installment plan ID cannot be blank." }
        require(revisionNumber > 0) { "Installment plan revision must be positive." }
        require(installments.isNotEmpty()) { "Installment plan cannot be empty." }
        require(installments.all { it.planId == id && it.debtId == debtId }) {
            "Installments must belong to their plan and debt."
        }
        require(
            (status == InstallmentPlanStatus.ACTIVE && supersededAt == null && supersededAfterLedgerSequence == null) ||
                (status == InstallmentPlanStatus.SUPERSEDED && supersededAt != null && supersededAfterLedgerSequence != null),
        ) { "Installment plan supersession metadata is inconsistent." }
    }
}

data class CreateInstallmentPlanCommand(
    val commandId: String,
    val planId: String,
    val debtId: DebtId,
    val installments: List<InstallmentPlanItemInput>,
    val createdAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Installment command ID cannot be blank." }
        require(planId.isNotBlank()) { "Installment plan ID cannot be blank." }
        require(installments.isNotEmpty()) { "Installment plan cannot be empty." }
    }
}

data class ReviseInstallmentPlanCommand(
    val commandId: String,
    val planId: String,
    val debtId: DebtId,
    val supersedesPlanId: String,
    val installments: List<InstallmentPlanItemInput>,
    val createdAt: Instant,
    val reason: String? = null,
) {
    init {
        require(commandId.isNotBlank()) { "Installment revision command ID cannot be blank." }
        require(planId.isNotBlank()) { "Installment plan ID cannot be blank." }
        require(supersedesPlanId.isNotBlank()) { "Superseded plan ID cannot be blank." }
        require(planId != supersedesPlanId) { "A plan cannot supersede itself." }
        require(installments.isNotEmpty()) { "Installment plan cannot be empty." }
        require(reason == null || reason.isNotBlank()) { "Revision reason must be null or non-blank." }
    }
}
