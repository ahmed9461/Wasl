package com.wasl.domain

import java.time.Instant

@JvmInline
value class GroupExpenseId(val value: String) {
    init {
        require(value.isNotBlank()) { "Group expense ID cannot be blank." }
    }
}

@JvmInline
value class GroupExpenseShareId(val value: String) {
    init {
        require(value.isNotBlank()) { "Group expense share ID cannot be blank." }
    }
}

data class GroupExpenseShare(
    val id: GroupExpenseShareId,
    val debtId: DebtId,
    val personId: PersonId,
    val amount: Money,
) {
    init {
        require(amount.minorUnits > 0L) { "Group expense share amount must be positive." }
    }
}

/**
 * One original shared financial operation split into ordinary per-person debts.
 *
 * The group operation is historical context; each share points at a normal Debt
 * so payments, reminders, documents, and ledger replay remain single-sourced.
 */
data class GroupExpense(
    val id: GroupExpenseId,
    val direction: DebtDirection,
    val totalAmount: Money,
    val occurredAt: Instant,
    val description: String,
    val notes: String? = null,
    val shares: List<GroupExpenseShare>,
) {
    init {
        require(totalAmount.minorUnits > 0L) { "Group expense total must be positive." }
        require(description.isNotBlank()) { "Group expense description cannot be blank." }
        require(notes == null || notes.isNotBlank()) { "Group expense notes must be null or non-blank." }
        require(shares.size >= 2) { "A group expense requires at least two participants." }
        require(shares.map { it.id }.distinct().size == shares.size) {
            "Group expense share IDs must be unique."
        }
        require(shares.map { it.debtId }.distinct().size == shares.size) {
            "Each group expense share must use a unique debt ID."
        }
        require(shares.map { it.personId }.distinct().size == shares.size) {
            "Each person can appear only once in a group expense."
        }

        val allocated = shares.fold(Money.zero(totalAmount.currency)) { sum, share ->
            sum.plus(share.amount)
        }
        require(allocated == totalAmount) {
            "Group expense shares must add up exactly to the total amount."
        }
    }
}
