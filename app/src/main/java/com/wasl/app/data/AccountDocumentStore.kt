package com.wasl.app.data

import java.time.Instant

interface AccountDocumentStore {
    suspend fun prepareDebtReceipt(command: PrepareDebtReceiptCommand): IssuedDocumentRecord

    suspend fun prepareAccountStatement(command: PrepareAccountStatementCommand): IssuedDocumentRecord

    suspend fun getAccountDocument(documentId: String): IssuedDocumentRecord?

    suspend fun markAccountDocumentReady(
        documentId: String,
        pdfSha256: String,
        pageCount: Int,
        updatedAt: Instant,
    ): IssuedDocumentRecord

    suspend fun markAccountDocumentFailed(
        documentId: String,
        failureCode: String,
        updatedAt: Instant,
    ): IssuedDocumentRecord
}
