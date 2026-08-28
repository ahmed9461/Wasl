package com.wasl.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

@DatabaseView(
    viewName = "payment_issued_documents",
    value = "SELECT * FROM issued_documents WHERE document_type = 'PAYMENT_RECEIPT'",
)
data class PaymentIssuedDocumentView(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "document_type") val documentType: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "document_number") val documentNumber: String,
    @ColumnInfo(name = "issue_year") val issueYear: Int,
    @ColumnInfo(name = "sequence_number") val sequenceNumber: Long,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "ledger_entry_id") val ledgerEntryId: String?,
    @ColumnInfo(name = "identity_id") val identityId: String,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "person_name_snapshot") val personNameSnapshot: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "issued_at") val issuedAt: Long,
    @ColumnInfo(name = "snapshot_version") val snapshotVersion: Int,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
    @ColumnInfo(name = "pdf_relative_path") val pdfRelativePath: String,
    @ColumnInfo(name = "pdf_sha256") val pdfSha256: String?,
    @ColumnInfo(name = "page_count") val pageCount: Int?,
    @ColumnInfo(name = "failure_code") val failureCode: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
