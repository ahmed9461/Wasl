package com.wasl.app.data

import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate

enum class DebtLifecycleState {
    ACTIVE,
    ARCHIVED,
    VOID,
}

data class PersonRecord(
    val id: PersonId,
    val displayName: String,
    val phone: String? = null,
    val email: String? = null,
    val photoUri: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
) {
    init {
        require(displayName.isNotBlank()) { "Person name cannot be blank." }
    }
}

data class AccountOverview(
    val person: PersonRecord,
    val ledger: DebtLedger,
    val lifecycleState: DebtLifecycleState,
    val notes: String? = null,
    val closedAt: Instant? = null,
)

data class CreatePersonWithDebtCommand(
    val personId: PersonId,
    val debtId: DebtId,
    val personName: String,
    val direction: DebtDirection,
    val originalAmount: Money,
    val openedAt: Instant,
    val createdAt: Instant,
    val dueDate: LocalDate? = null,
    val description: String? = null,
    val personNotes: String? = null,
    val debtNotes: String? = null,
) {
    init {
        require(personName.isNotBlank()) { "Person name cannot be blank." }
        require(originalAmount.minorUnits > 0L) { "Original amount must be positive." }
        require(description == null || description.isNotBlank()) {
            "Description must be null or non-blank."
        }
        require(personNotes == null || personNotes.isNotBlank()) {
            "Person notes must be null or non-blank."
        }
        require(debtNotes == null || debtNotes.isNotBlank()) {
            "Debt notes must be null or non-blank."
        }
    }
}

data class RecordPaymentCommand(
    val commandId: String,
    val entryId: LedgerEntryId,
    val debtId: DebtId,
    val amount: Money,
    val paidAt: Instant,
    val recordedAt: Instant,
    val note: String? = null,
) {
    init {
        require(commandId.isNotBlank()) { "Command ID cannot be blank." }
    }
}

data class ReversePaymentCommand(
    val commandId: String,
    val entryId: LedgerEntryId,
    val debtId: DebtId,
    val paymentId: LedgerEntryId,
    val recordedAt: Instant,
    val reason: String,
) {
    init {
        require(commandId.isNotBlank()) { "Command ID cannot be blank." }
        require(reason.isNotBlank()) { "Reversal reason cannot be blank." }
    }
}

class RecordNotFoundException(message: String) : IllegalStateException(message)

class CommandConflictException(message: String) : IllegalStateException(message)
