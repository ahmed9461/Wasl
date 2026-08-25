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
    val strongAlarm: ReminderRecord? = null,
    val dueScheduleAuditEvents: List<DueScheduleAuditEvent> = emptyList(),
    val issuedDocuments: List<IssuedDocumentRecord> = emptyList(),
)

enum class DocumentType {
    DEBT_RECEIPT,
    PAYMENT_RECEIPT,
    ACCOUNT_STATEMENT,
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

sealed interface DocumentSnapshot {
    val version: Int
    val documentId: String
    val documentNumber: String
    val issuedAt: Instant
    val issueZoneId: ZoneId
    val debtId: DebtId
    val personId: PersonId
    val personName: String
    val direction: DebtDirection
    val identity: DocumentIdentitySnapshot
}

data class DebtReceiptSnapshot(
    override val version: Int,
    override val documentId: String,
    override val documentNumber: String,
    override val issuedAt: Instant,
    override val issueZoneId: ZoneId,
    override val debtId: DebtId,
    override val personId: PersonId,
    override val personName: String,
    override val direction: DebtDirection,
    val originalAmount: Money,
    val balanceAtIssue: Money,
    val paidAmountAtIssue: Money,
    val openedAt: Instant,
    val dueDate: LocalDate? = null,
    val debtDescription: String? = null,
    override val identity: DocumentIdentitySnapshot,
) : DocumentSnapshot {
    init {
        require(version > 0) { "Snapshot version must be positive." }
        require(documentId.isNotBlank()) { "Document ID cannot be blank." }
        require(documentNumber.isNotBlank()) { "Document number cannot be blank." }
        require(personName.isNotBlank()) { "Snapshot person name cannot be blank." }
        require(originalAmount.currency == balanceAtIssue.currency) {
            "Debt receipt balance currency must match the original debt currency."
        }
        require(originalAmount.minus(balanceAtIssue) == paidAmountAtIssue) {
            "Debt receipt paid amount must match its snapshotted balance."
        }
    }
}

data class PaymentReceiptSnapshot(
    override val version: Int,
    override val documentId: String,
    override val documentNumber: String,
    override val issuedAt: Instant,
    override val issueZoneId: ZoneId,
    override val debtId: DebtId,
    val paymentId: LedgerEntryId,
    override val personId: PersonId,
    override val personName: String,
    override val direction: DebtDirection,
    val originalAmount: Money,
    val balanceBefore: Money,
    val paymentAmount: Money,
    val balanceAfter: Money,
    val paidAt: Instant,
    val paymentNote: String? = null,
    val debtDescription: String? = null,
    override val identity: DocumentIdentitySnapshot,
) : DocumentSnapshot {
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

enum class StatementEntryType {
    PAYMENT,
    PAYMENT_REVERSAL,
}

data class StatementLedgerEntrySnapshot(
    val id: LedgerEntryId,
    val type: StatementEntryType,
    val recordedAt: Instant,
    val amount: Money? = null,
    val occurredAt: Instant? = null,
    val note: String? = null,
    val reversesPaymentId: LedgerEntryId? = null,
    val reason: String? = null,
) {
    init {
        when (type) {
            StatementEntryType.PAYMENT -> require(
                amount != null && occurredAt != null && reversesPaymentId == null && reason == null,
            ) { "Payment statement entry fields are invalid." }
            StatementEntryType.PAYMENT_REVERSAL -> require(
                amount == null && occurredAt == null && reversesPaymentId != null && !reason.isNullOrBlank(),
            ) { "Reversal statement entry fields are invalid." }
        }
    }
}

data class AccountStatementSnapshot(
    override val version: Int,
    override val documentId: String,
    override val documentNumber: String,
    override val issuedAt: Instant,
    override val issueZoneId: ZoneId,
    override val debtId: DebtId,
    override val personId: PersonId,
    override val personName: String,
    override val direction: DebtDirection,
    val originalAmount: Money,
    val balanceAtIssue: Money,
    val paidAmountAtIssue: Money,
    val openedAt: Instant,
    val dueDate: LocalDate? = null,
    val debtDescription: String? = null,
    val entries: List<StatementLedgerEntrySnapshot>,
    override val identity: DocumentIdentitySnapshot,
) : DocumentSnapshot {
    init {
        require(version > 0) { "Snapshot version must be positive." }
        require(documentId.isNotBlank()) { "Document ID cannot be blank." }
        require(documentNumber.isNotBlank()) { "Document number cannot be blank." }
        require(personName.isNotBlank()) { "Snapshot person name cannot be blank." }
        require(originalAmount.currency == balanceAtIssue.currency) {
            "Statement balance currency must match the original debt currency."
        }
        require(originalAmount.minus(balanceAtIssue) == paidAmountAtIssue) {
            "Statement paid amount must match its snapshotted balance."
        }
        require(entries.map { it.id }.distinct().size == entries.size) {
            "Statement entry IDs must be unique."
        }
        require(entries.all { it.amount == null || it.amount.currency == originalAmount.currency }) {
            "Statement entry currency must match the debt currency."
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
    val ledgerEntryId: LedgerEntryId?,
    val identityId: String,
    val issuedAt: Instant,
    val snapshot: DocumentSnapshot,
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
        require(snapshot.debtId == debtId) { "Snapshot debt must match its document record." }
        when (type) {
            DocumentType.PAYMENT_RECEIPT -> {
                val payment = snapshot as? PaymentReceiptSnapshot
                    ?: error("Payment receipt requires a payment snapshot.")
                require(ledgerEntryId != null && payment.paymentId == ledgerEntryId) {
                    "Payment receipt source must match its ledger entry."
                }
            }
            DocumentType.DEBT_RECEIPT -> {
                require(snapshot is DebtReceiptSnapshot && ledgerEntryId == null) {
                    "Debt receipt cannot reference a ledger entry."
                }
            }
            DocumentType.ACCOUNT_STATEMENT -> {
                require(snapshot is AccountStatementSnapshot && ledgerEntryId == null) {
                    "Account statement cannot reference a ledger entry."
                }
            }
        }
        require(status != DocumentStatus.READY || !pdfSha256.isNullOrBlank()) {
            "A ready document requires a PDF checksum."
        }
    }
}

data class PrepareDebtReceiptCommand(
    val commandId: String,
    val documentId: String,
    val identityId: String,
    val debtId: DebtId,
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

data class PrepareAccountStatementCommand(
    val commandId: String,
    val documentId: String,
    val identityId: String,
    val debtId: DebtId,
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

enum class ReminderType {
    DUE_DATE,
    STRONG_ALARM,
}

enum class ReminderScheduleType {
    WORK,
    EXACT_ALARM,
}

data class ReminderRecord(
    val id: String,
    val debtId: DebtId,
    val triggerAt: Instant,
    val zoneId: ZoneId,
    val status: ReminderStatus,
    val type: ReminderType = ReminderType.DUE_DATE,
    val scheduleType: ReminderScheduleType = ReminderScheduleType.WORK,
    val platformRequestCode: Int? = null,
    val lastFailureCode: String? = null,
    val deliveredAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Reminder ID cannot be blank." }
        require(
            (type == ReminderType.DUE_DATE && scheduleType == ReminderScheduleType.WORK) ||
                (type == ReminderType.STRONG_ALARM &&
                    scheduleType == ReminderScheduleType.EXACT_ALARM),
        ) { "Reminder type and schedule type are incompatible." }
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

data class StrongAlarmRequest(
    val id: String,
    val triggerAt: Instant,
    val zoneId: ZoneId,
) {
    init {
        require(id.isNotBlank()) { "Strong alarm ID cannot be blank." }
    }
}

data class DueScheduleSnapshot(
    val dueDate: LocalDate?,
    val dueReminder: DueReminderRequest?,
    val strongAlarm: StrongAlarmRequest? = null,
) {
    init {
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
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
    val strongAlarm: StrongAlarmRequest? = null,
    val updatedAt: Instant,
) {
    init {
        require(commandId.isNotBlank()) { "Command ID cannot be blank." }
        require(auditEventId.isNotBlank()) { "Audit event ID cannot be blank." }
        require(dueReminder == null || dueDate != null) {
            "A due reminder requires a due date."
        }
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
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
    val strongAlarm: StrongAlarmRequest? = null,
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
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
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
    val strongAlarm: StrongAlarmRequest? = null,
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
        require(strongAlarm == null || dueDate != null) {
            "A strong alarm requires a due date."
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
