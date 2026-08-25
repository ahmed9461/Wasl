package com.wasl.app.data

import java.time.Instant

interface PaymentReceiptStore {
    suspend fun getDefaultDocumentIdentity(): DocumentIdentityRecord?

    suspend fun prepareDebtReceipt(
        command: PrepareDebtReceiptCommand,
    ): IssuedDocumentRecord

    suspend fun preparePaymentReceipt(
        command: PreparePaymentReceiptCommand,
    ): IssuedDocumentRecord

    suspend fun prepareAccountStatement(
        command: PrepareAccountStatementCommand,
    ): IssuedDocumentRecord

    suspend fun getIssuedDocument(documentId: String): IssuedDocumentRecord?

    suspend fun markDocumentReady(
        documentId: String,
        pdfSha256: String,
        pageCount: Int,
        updatedAt: Instant,
    ): IssuedDocumentRecord

    suspend fun markDocumentFailed(
        documentId: String,
        failureCode: String,
        updatedAt: Instant,
    ): IssuedDocumentRecord
}
