package com.wasl.app.data.local

import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.DocumentType
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.local.entity.PaymentIssuedDocumentView
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import java.time.Instant

internal fun PaymentIssuedDocumentView.toRecord(): IssuedDocumentRecord {
    check(documentType == DocumentType.PAYMENT_RECEIPT.name) {
        "Payment document view contains a non-payment document."
    }
    val snapshot = PaymentReceiptSnapshotCodec.decode(snapshotJson)
    check(snapshotVersion == snapshot.version) { "Document snapshot version is corrupt." }
    check(personId == snapshot.personId.value) { "Document person metadata is corrupt." }
    check(personNameSnapshot == snapshot.personName) { "Document person snapshot is corrupt." }
    check(amountMinor == snapshot.paymentAmount.minorUnits) {
        "Document amount metadata is corrupt."
    }
    check(currencyCode == snapshot.paymentAmount.currency.value) {
        "Document currency metadata is corrupt."
    }
    val sourceId = LedgerEntryId(requireNotNull(ledgerEntryId) {
        "Payment receipt is missing its ledger source."
    })
    return IssuedDocumentRecord(
        id = id,
        commandId = commandId,
        type = DocumentType.PAYMENT_RECEIPT,
        status = DocumentStatus.valueOf(status),
        documentNumber = documentNumber,
        debtId = DebtId(debtId),
        ledgerEntryId = sourceId,
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
