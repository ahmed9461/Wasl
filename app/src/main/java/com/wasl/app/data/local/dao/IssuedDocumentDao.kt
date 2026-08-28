package com.wasl.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wasl.app.data.local.entity.IssuedDocumentEntity

@Dao
interface IssuedDocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(document: IssuedDocumentEntity)

    @Query("SELECT * FROM issued_documents WHERE id = :id")
    suspend fun findById(id: String): IssuedDocumentEntity?

    @Query("SELECT * FROM issued_documents WHERE command_id = :commandId")
    suspend fun findByCommandId(commandId: String): IssuedDocumentEntity?

    @Query(
        """
        SELECT * FROM issued_documents
        WHERE document_type = :documentType AND ledger_entry_id = :ledgerEntryId
        """,
    )
    suspend fun findBySource(documentType: String, ledgerEntryId: String): IssuedDocumentEntity?

    @Query(
        """
        SELECT COALESCE(MAX(sequence_number), 0) + 1
        FROM issued_documents
        WHERE issue_year = :issueYear
        """,
    )
    suspend fun nextSequenceNumber(issueYear: Int): Long

    @Query(
        """
        UPDATE issued_documents
        SET status = 'READY',
            pdf_sha256 = :pdfSha256,
            page_count = :pageCount,
            failure_code = NULL,
            updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markReady(
        id: String,
        pdfSha256: String,
        pageCount: Int,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE issued_documents
        SET status = 'FAILED',
            failure_code = :failureCode,
            updated_at = :updatedAt
        WHERE id = :id AND status != 'READY'
        """,
    )
    suspend fun markFailed(id: String, failureCode: String, updatedAt: Long): Int

    @Query("SELECT COUNT(*) FROM issued_documents")
    suspend fun count(): Int
}
