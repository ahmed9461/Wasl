package com.wasl.app.data

import com.wasl.domain.DebtId
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

interface AttachmentStore {
    fun observeForDebt(debtId: DebtId): Flow<List<AttachmentRecord>>

    suspend fun importAttachment(
        command: AddAttachmentCommand,
        content: InputStream,
    ): AttachmentRecord

    suspend fun findById(id: String): AttachmentRecord?
}

object UnavailableAttachmentStore : AttachmentStore {
    override fun observeForDebt(debtId: DebtId): Flow<List<AttachmentRecord>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun importAttachment(
        command: AddAttachmentCommand,
        content: InputStream,
    ): AttachmentRecord = error("Attachment store is unavailable")

    override suspend fun findById(id: String): AttachmentRecord? = null
}
