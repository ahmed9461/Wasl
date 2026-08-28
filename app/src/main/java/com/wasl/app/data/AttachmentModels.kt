package com.wasl.app.data

import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import java.time.Instant

enum class AttachmentIntegrity {
    OK,
    MISSING,
    HASH_MISMATCH,
}

data class AttachmentRecord(
    val id: String,
    val debtId: DebtId,
    val ledgerEntryId: LedgerEntryId?,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val sha256: String,
    val createdAt: Instant,
    val note: String?,
    val integrity: AttachmentIntegrity,
)

data class AddAttachmentCommand(
    val id: String,
    val debtId: DebtId,
    val ledgerEntryId: LedgerEntryId? = null,
    val displayName: String,
    val mimeType: String,
    val createdAt: Instant,
    val note: String? = null,
)
