package com.wasl.app.data.local

import androidx.room.withTransaction
import com.wasl.app.data.AccountDocumentStore
import com.wasl.app.data.AccountStatementSnapshot
import com.wasl.app.data.CommandConflictException
import com.wasl.app.data.DebtLifecycleState
import com.wasl.app.data.DebtReceiptSnapshot
import com.wasl.app.data.DocumentIdentitySnapshot
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.DocumentType
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PrepareAccountStatementCommand
import com.wasl.app.data.PrepareDebtReceiptCommand
import com.wasl.app.data.RecordNotFoundException
import com.wasl.app.data.StatementEntryType
import com.wasl.app.data.StatementLedgerEntrySnapshot
import com.wasl.app.data.WaslRepository
import com.wasl.app.data.local.entity.DocumentIdentityEntity
import com.wasl.app.data.local.entity.IssuedDocumentEntity
import com.wasl.domain.DebtId
import com.wasl.domain.PaymentRecorded
import com.wasl.domain.PaymentReversed
import java.time.Instant

class RoomAccountDocumentStore(
    private val database: WaslDatabase,
    private val repository: WaslRepository,
) : AccountDocumentStore {
    private val identityDao = database.documentIdentityDao()
    private val documentDao = database.issuedDocumentDao()

    override suspend fun prepareDebtReceipt(
        command: PrepareDebtReceiptCommand,
    ): IssuedDocumentRecord = database.withTransaction {
        documentDao.findByCommandId(command.commandId)?.let { existing ->
            validateDebtReceiptReplay(command, existing)
            return@withTransaction existing.toAccountDocumentRecord()
        }

        val account = repository.getAccount(command.debtId)
            ?: throw RecordNotFoundException("Debt ${command.debtId.value} was not found.")
        require(account.lifecycleState != DebtLifecycleState.VOID) {
            "A void debt cannot issue a debt receipt."
        }
        require(!command.issuedAt.isBefore(account.ledger.header.openedAt)) {
            "A debt receipt cannot be issued before the debt was opened."
        }
        require(!command.issuedAt.isBefore(account.latestLedgerTimestamp())) {
            "A debt receipt cannot predate the latest ledger event."
        }

        val identity = command.toIdentitySnapshot()
        saveDefaultIdentity(command.identityId, command.issuedAt, identity)
        val issueYear = command.issuedAt.atZone(command.issueZoneId).year
        val sequence = documentDao.nextSequenceNumber(issueYear)
        val number = "DEBT-$issueYear-${sequence.toString().padStart(5, '0')}"
        val snapshot = DebtReceiptSnapshot(
            version = DEBT_RECEIPT_SNAPSHOT_VERSION,
            documentId = command.documentId,
            documentNumber = number,
            issuedAt = command.issuedAt,
            issueZoneId = command.issueZoneId,
            debtId = command.debtId,
            personId = account.person.id,
            personName = account.person.displayName,
            direction = account.ledger.header.direction,
            originalAmount = account.ledger.header.originalAmount,
            balanceAtIssue = account.ledger.balance,
            paidAmountAtIssue = account.ledger.paidAmount,
            openedAt = account.ledger.header.openedAt,
            dueDate = account.ledger.header.dueDate,
            debtDescription = account.ledger.header.description,
            identity = identity,
        )
        val entity = IssuedDocumentEntity(
            id = command.documentId,
            commandId = command.commandId,
            documentType = DocumentType.DEBT_RECEIPT.name,
            status = DocumentStatus.PENDING_PDF.name,
            documentNumber = number,
            issueYear = issueYear,
            sequenceNumber = sequence,
            debtId = command.debtId.value,
            ledgerEntryId = null,
            identityId = command.identityId,
            personId = account.person.id.value,
            personNameSnapshot = account.person.displayName,
            amountMinor = account.ledger.header.originalAmount.minorUnits,
            currencyCode = account.ledger.header.originalAmount.currency.value,
            issuedAt = command.issuedAt.toEpochMilli(),
            snapshotVersion = DEBT_RECEIPT_SNAPSHOT_VERSION,
            snapshotJson = AccountDocumentSnapshotCodec.encode(snapshot),
            pdfRelativePath = "documents/$number.pdf",
            pdfSha256 = null,
            pageCount = null,
            failureCode = null,
            createdAt = command.issuedAt.toEpochMilli(),
            updatedAt = command.issuedAt.toEpochMilli(),
        )
        documentDao.insert(entity)
        entity.toAccountDocumentRecord()
    }

    override suspend fun prepareAccountStatement(
        command: PrepareAccountStatementCommand,
    ): IssuedDocumentRecord = database.withTransaction {
        documentDao.findByCommandId(command.commandId)?.let { existing ->
            validateStatementReplay(command, existing)
            return@withTransaction existing.toAccountDocumentRecord()
        }

        val account = repository.getAccount(command.debtId)
            ?: throw RecordNotFoundException("Debt ${command.debtId.value} was not found.")
        require(account.lifecycleState != DebtLifecycleState.VOID) {
            "A void debt cannot issue an account statement."
        }
        require(!command.issuedAt.isBefore(account.latestLedgerTimestamp())) {
            "An account statement cannot predate the latest ledger event."
        }

        val identity = command.toIdentitySnapshot()
        saveDefaultIdentity(command.identityId, command.issuedAt, identity)
        val issueYear = command.issuedAt.atZone(command.issueZoneId).year
        val sequence = documentDao.nextSequenceNumber(issueYear)
        val number = "STAT-$issueYear-${sequence.toString().padStart(5, '0')}"
        val snapshot = AccountStatementSnapshot(
            version = ACCOUNT_STATEMENT_SNAPSHOT_VERSION,
            documentId = command.documentId,
            documentNumber = number,
            issuedAt = command.issuedAt,
            issueZoneId = command.issueZoneId,
            debtId = command.debtId,
            personId = account.person.id,
            personName = account.person.displayName,
            direction = account.ledger.header.direction,
            originalAmount = account.ledger.header.originalAmount,
            balanceAtIssue = account.ledger.balance,
            paidAmountAtIssue = account.ledger.paidAmount,
            openedAt = account.ledger.header.openedAt,
            dueDate = account.ledger.header.dueDate,
            debtDescription = account.ledger.header.description,
            entries = account.ledger.entries.map { entry ->
                when (entry) {
                    is PaymentRecorded -> StatementLedgerEntrySnapshot(
                        id = entry.id,
                        type = StatementEntryType.PAYMENT,
                        recordedAt = entry.recordedAt,
                        amount = entry.amount,
                        occurredAt = entry.paidAt,
                        note = entry.note,
                    )
                    is PaymentReversed -> StatementLedgerEntrySnapshot(
                        id = entry.id,
                        type = StatementEntryType.PAYMENT_REVERSAL,
                        recordedAt = entry.recordedAt,
                        reversesPaymentId = entry.paymentId,
                        reason = entry.reason,
                    )
                }
            },
            identity = identity,
        )
        val entity = IssuedDocumentEntity(
            id = command.documentId,
            commandId = command.commandId,
            documentType = DocumentType.ACCOUNT_STATEMENT.name,
            status = DocumentStatus.PENDING_PDF.name,
            documentNumber = number,
            issueYear = issueYear,
            sequenceNumber = sequence,
            debtId = command.debtId.value,
            ledgerEntryId = null,
            identityId = command.identityId,
            personId = account.person.id.value,
            personNameSnapshot = account.person.displayName,
            amountMinor = account.ledger.balance.minorUnits,
            currencyCode = account.ledger.header.originalAmount.currency.value,
            issuedAt = command.issuedAt.toEpochMilli(),
            snapshotVersion = ACCOUNT_STATEMENT_SNAPSHOT_VERSION,
            snapshotJson = AccountDocumentSnapshotCodec.encode(snapshot),
            pdfRelativePath = "documents/$number.pdf",
            pdfSha256 = null,
            pageCount = null,
            failureCode = null,
            createdAt = command.issuedAt.toEpochMilli(),
            updatedAt = command.issuedAt.toEpochMilli(),
        )
        documentDao.insert(entity)
        entity.toAccountDocumentRecord()
    }

    override suspend fun getAccountDocument(documentId: String): IssuedDocumentRecord? =
        documentDao.findById(documentId)
            ?.takeIf { it.documentType != DocumentType.PAYMENT_RECEIPT.name }
            ?.toAccountDocumentRecord()

    override suspend fun markAccountDocumentReady(
        documentId: String,
        pdfSha256: String,
        pageCount: Int,
        updatedAt: Instant,
    ): IssuedDocumentRecord = database.withTransaction {
        require(pdfSha256.matches(Regex("[0-9a-f]{64}"))) {
            "PDF checksum must be a lowercase SHA-256 value."
        }
        require(pageCount > 0) { "A PDF must contain at least one page." }
        val existing = documentDao.findById(documentId)
            ?: throw RecordNotFoundException("Document $documentId was not found.")
        require(existing.documentType != DocumentType.PAYMENT_RECEIPT.name) {
            "Payment receipts are managed by the payment receipt store."
        }
        if (existing.status == DocumentStatus.READY.name) {
            if (existing.pdfSha256 != pdfSha256 || existing.pageCount != pageCount) {
                throw CommandConflictException("Ready document metadata cannot be replaced.")
            }
            return@withTransaction existing.toAccountDocumentRecord()
        }
        check(
            documentDao.markReady(
                id = documentId,
                pdfSha256 = pdfSha256,
                pageCount = pageCount,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Document $documentId was not marked ready." }
        requireNotNull(documentDao.findById(documentId)).toAccountDocumentRecord()
    }

    override suspend fun markAccountDocumentFailed(
        documentId: String,
        failureCode: String,
        updatedAt: Instant,
    ): IssuedDocumentRecord = database.withTransaction {
        require(failureCode.matches(Regex("[A-Z0-9_]{1,48}"))) {
            "Document failure code is invalid."
        }
        val existing = documentDao.findById(documentId)
            ?: throw RecordNotFoundException("Document $documentId was not found.")
        require(existing.documentType != DocumentType.PAYMENT_RECEIPT.name) {
            "Payment receipts are managed by the payment receipt store."
        }
        if (existing.status == DocumentStatus.READY.name) {
            return@withTransaction existing.toAccountDocumentRecord()
        }
        check(
            documentDao.markFailed(
                id = documentId,
                failureCode = failureCode,
                updatedAt = updatedAt.toEpochMilli(),
            ) == 1,
        ) { "Document $documentId was not marked failed." }
        requireNotNull(documentDao.findById(documentId)).toAccountDocumentRecord()
    }

    private suspend fun saveDefaultIdentity(
        identityId: String,
        issuedAt: Instant,
        snapshot: DocumentIdentitySnapshot,
    ) {
        identityDao.clearOtherDefaults(identityId)
        val existing = identityDao.findById(identityId)
        if (existing == null) {
            identityDao.insert(
                DocumentIdentityEntity(
                    id = identityId,
                    displayName = snapshot.displayName,
                    activityName = snapshot.activityName,
                    phone = snapshot.phone,
                    footerText = snapshot.footerText,
                    isDefault = true,
                    createdAt = issuedAt.toEpochMilli(),
                    updatedAt = issuedAt.toEpochMilli(),
                ),
            )
        } else {
            check(
                identityDao.updateDefault(
                    id = identityId,
                    displayName = snapshot.displayName,
                    activityName = snapshot.activityName,
                    phone = snapshot.phone,
                    footerText = snapshot.footerText,
                    updatedAt = issuedAt.toEpochMilli(),
                ) == 1,
            ) { "Document identity $identityId was not updated." }
        }
    }

    private fun validateDebtReceiptReplay(
        command: PrepareDebtReceiptCommand,
        persisted: IssuedDocumentEntity,
    ) {
        val snapshot = persisted.toAccountDocumentRecord().snapshot as? DebtReceiptSnapshot
            ?: throw CommandConflictException("Document command belongs to another document type.")
        val matches = persisted.id == command.documentId &&
            persisted.documentType == DocumentType.DEBT_RECEIPT.name &&
            persisted.debtId == command.debtId.value &&
            persisted.ledgerEntryId == null &&
            persisted.identityId == command.identityId &&
            persisted.issuedAt == command.issuedAt.toEpochMilli() &&
            snapshot.issueZoneId == command.issueZoneId &&
            snapshot.identity == command.toIdentitySnapshot()
        if (!matches) {
            throw CommandConflictException("Document command ID was reused with different debt receipt data.")
        }
    }

    private fun validateStatementReplay(
        command: PrepareAccountStatementCommand,
        persisted: IssuedDocumentEntity,
    ) {
        val snapshot = persisted.toAccountDocumentRecord().snapshot as? AccountStatementSnapshot
            ?: throw CommandConflictException("Document command belongs to another document type.")
        val matches = persisted.id == command.documentId &&
            persisted.documentType == DocumentType.ACCOUNT_STATEMENT.name &&
            persisted.debtId == command.debtId.value &&
            persisted.ledgerEntryId == null &&
            persisted.identityId == command.identityId &&
            persisted.issuedAt == command.issuedAt.toEpochMilli() &&
            snapshot.issueZoneId == command.issueZoneId &&
            snapshot.identity == command.toIdentitySnapshot()
        if (!matches) {
            throw CommandConflictException("Document command ID was reused with different statement data.")
        }
    }

    private fun IssuedDocumentEntity.toAccountDocumentRecord(): IssuedDocumentRecord {
        val type = DocumentType.valueOf(documentType)
        require(type != DocumentType.PAYMENT_RECEIPT) {
            "Payment receipt cannot be decoded as an account document."
        }
        val snapshot = AccountDocumentSnapshotCodec.decode(type, snapshotJson)
        check(snapshotVersion == snapshot.version) { "Document snapshot version is corrupt." }
        check(personId == snapshot.personId.value) { "Document person metadata is corrupt." }
        check(personNameSnapshot == snapshot.personName) { "Document person snapshot is corrupt." }
        check(ledgerEntryId == null) { "Account document unexpectedly references a ledger entry." }
        val expectedAmount = when (snapshot) {
            is DebtReceiptSnapshot -> snapshot.originalAmount
            is AccountStatementSnapshot -> snapshot.balanceAtIssue
            else -> error("Unsupported account document snapshot.")
        }
        check(amountMinor == expectedAmount.minorUnits) { "Document amount metadata is corrupt." }
        check(currencyCode == expectedAmount.currency.value) { "Document currency metadata is corrupt." }
        return IssuedDocumentRecord(
            id = id,
            commandId = commandId,
            type = type,
            status = DocumentStatus.valueOf(status),
            documentNumber = documentNumber,
            debtId = DebtId(debtId),
            ledgerEntryId = null,
            identityId = identityId,
            issuedAt = Instant.ofEpochMilli(issuedAt),
            snapshot = snapshot,
            pdfRelativePath = pdfRelativePath,
            pdfSha256 = pdfSha256,
            pageCount = pageCount,
            failureCode = failureCode,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun PrepareDebtReceiptCommand.toIdentitySnapshot() = DocumentIdentitySnapshot(
        displayName = issuerDisplayName.trim(),
        activityName = issuerActivityName.normalizedOptional(),
        phone = issuerPhone.normalizedOptional(),
        footerText = footerText.normalizedOptional(),
    )

    private fun PrepareAccountStatementCommand.toIdentitySnapshot() = DocumentIdentitySnapshot(
        displayName = issuerDisplayName.trim(),
        activityName = issuerActivityName.normalizedOptional(),
        phone = issuerPhone.normalizedOptional(),
        footerText = footerText.normalizedOptional(),
    )

    private fun com.wasl.app.data.AccountOverview.latestLedgerTimestamp(): Instant =
        ledger.entries.lastOrNull()?.recordedAt ?: ledger.header.openedAt

    private fun String?.normalizedOptional(): String? = this?.trim()?.ifEmpty { null }

    private companion object {
        const val DEBT_RECEIPT_SNAPSHOT_VERSION = 1
        const val ACCOUNT_STATEMENT_SNAPSHOT_VERSION = 1
    }
}
