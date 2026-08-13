package com.wasl.app.data

import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.DebtLedger
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    val dueReminder: ReminderRecord? = null,
    val dueScheduleAuditEvents: List<DueScheduleAuditEvent> = emptyList(),
    val issuedDocuments: List<IssuedDocumentRecord> = emptyList(),
)

enum class DocumentType {
    PAYMENT_RECEIPT,
}

enum class DocumentStatus {
    PENDING_PDF,
    READY,
    FAILED,
}

data class DocumentIdentityRecord(
    val id: String,
    val displayName: String,
    val activityName: String? = null,
    val phone: String? = null,
    val footerText: String? = null,
    val isDefault: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Document identity ID cannot be blank." }
        require(displayName.isNotBlank()) { "Document identity name cannot be blank." }
    }
}

data class DocumentIdentitySnapshot(
    val displayName: String,
    val activityName: String? = null,
    val phone: String? = null,
    val footerText: String? = null,
) {
    init {
        require(displayName.isNotBlank()) { "Document identity name cannot be blank." }
    }
}

data class PaymentReceiptSnapshot(
    val version: Int,
    val documentId: String,
    val documentNumber: String,
    val issuedAt: Instant,
    val issueZoneId: ZoneId,
    val debtId: DebtId,
    val paymentId: LedgerEntryId,
    val personId: PersonId,
    val personName: String,
    val direction: DebtDirection,
    val originalAmount: Money,
    val balanceBefore: Money,
    val paymentAmount: Money,
    val balanceAfter: Money,
    val paidAt: Instant,
    val paymentNote: String? = null,
    val debtDescription: String? = null,
    val identity: DocumentIdentitySnapshot,
) {
    init {
        require(version > 0) { "Snapshot version must be positive." }
        require(documentId.isNotBlank()) { "Document ID cannot be blank." }
        require(documentNumber.isNotBlank()) { "Document number cannot be blank." }
        require(personName.isNotBlank()) { "Snapshot person name cannot be blank." }
        require(originalAmount.currency == paymentAmount.currency) {
            "Receipt currency must match the original debt currency."
        }
        require(balanceBefore.minus(paymentAmount) == balanceAfter) {
            "Receipt balances must match the snapshotted payment."
        }
    }
}

data class IssuedDocumentRecord(
    val id: String,
    val commandId: String,
    val type: DocumentType,
    val status: DocumentStatus,
    val documentNumber: String,
    val debtId: DebtId,
    val ledgerEntryId: LedgerEntryId,
    val identityId: String,
    val issuedAt: Instant,
    val snapshot: PaymentReceiptSnapshot,
    val pdfRelativePath: String,
    val pdfSha256: String? = null,
    val pageCount: Int? = null,
    val failureCode: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Document ID cannot be blank." }
        require(commandId.isNotBlank()) { "Document command ID cannot be blank." }
        require(documentNumber.isNotBlank()) { "Document number cannot be blank." }
        require(pdfRelativePath.isNotBlank()) { "PDF path cannot be blank." }
        require(snapshot.documentId == id) { "Snapshot document ID must match its record." }
        require(snapshot.documentNumber == documentNumber) {
            "Snapshot document number must match its record."
        }
        require(snapshot.debtId == debtId && snapshot.paymentId == ledgerEntryId) {
            "Snapshot source must match its document record."
        }
        require(status != DocumentStatus.READY || !pdfSha256.isNullOrBlank()) {
            "A ready document requires a PDF checksum."
        }
    }
}

data class PreparePaymentReceiptCommand(
    val commandId: String,
    val documentId: String,
    val identityId: String,
    val debtId: DebtId,
    val paymentId: LedgerEntryId,
    val issuerDisplayName: String,
    val issuerActivityName: String? = null,
    val issuerPhone: String? = null,
    val footerText: String? = null,
    val issuedAt: Instant,
    val issueZoneId: ZoneId,
) {
    init {
        require(commandId.isNotBlank()) { "Document command ID cannot be blank." }
        require(documentId.isNotBlank()) { "Document ID cannot be blank." }
        require(identityId.isNotBlank()) { "Document identity ID cannot be blank." }
        require(issuerDisplayName.isNotBlank()) { "Issuer name cannot be blank." }
    }
}

enum class ReminderStatus {
    SCHEDULED,
    DELIVERED,
    BLOCKED_PERMISSION,
    FAILED,
    CANCELLED,
}

data class ReminderRecord(
    val id: String,
    val debtId: DebtId,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val status: ReminderStatus,
    val lastFailureCode: String? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Reminder ID cannot be blank." }
    }
}

data class DueReminderRequest(
    val id: String,
    val triggerAt: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(id.isNotBlank()) { "Reminder ID cannot be blank." }
    }
}

data class DueScheduleSnapshot(
    val dueDate: LocalDate?,
    val dueReminder: DueReminderRequest?,
) {
    init {
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
    }
}

data class DueScheduleAuditEvent(
    val id: String,
    val commandId: String,
    val debtId: DebtId,
    val occurredAt: Instant,
    val before: DueScheduleSnapshot,
    val after: DueScheduleSnapshot,
) {
    init {
        require(id.isNotBlank()) { "Audit event ID cannot be blank." }
        require(commandId.isNotBlank()) { "Command ID cannot be blank." }
        require(before != after) { "Audit event must describe a real change." }
    }
}

data class UpdateDueScheduleCommand(
    val commandId: String,
    val auditEventId: String,
    val debtId: DebtId,
    val dueDate: LocalDate?,
    val dueReminder: DueReminderRequest?,
    val updatedAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Command ID cannot be blank." }
        require(auditEventId.isNotBlank()) { "Audit event ID cannot be blank." }
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
    }
}

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
    val dueReminder: DueReminderRequest? = null,
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
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
    }
}

data class CreateDebtForExistingPersonCommand(
    val personId: PersonId,
    val debtId: DebtId,
    val direction: DebtDirection,
    val originalAmount: Money,
    val openedAt: Instant,
    val createdAt: Instant,
    val dueDate: LocalDate? = null,
    val description: String? = null,
    val debtNotes: String? = null,
    val dueReminder: DueReminderRequest? = null,
) {
    init {
        require(originalAmount.minorUnits > 0L) { "Original amount must be positive." }
        require(description == null || description.isNotBlank()) {
            "Description must be null or non-blank."
        }
        require(debtNotes == null || debtNotes.isNotBlank()) {
            "Debt notes must be null or non-blank."
        }
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
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
