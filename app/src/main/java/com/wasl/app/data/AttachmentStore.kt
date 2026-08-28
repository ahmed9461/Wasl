package com.wasl.app.data

import com.wasl.domain.DebtId
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AttachmentStore {
    fun observeForDebt(debtId: DebtId): Flow<List<AttachmentRecord>>

    suspend fun importAttachment(
        command: AddAttachmentCommand,
        content: InputStream,
    ): AttachmentRecord

    suspend fun findById(id: String): AttachmentRecord?
}

/**
 * Safe bridge used by composable previews/tests that do not inject an AttachmentStore.
 * Production installs the real vault once from WaslApplication.onCreate().
 */
object UnavailableAttachmentStore : AttachmentStore {
    @Volatile
    private var delegate: AttachmentStore? = null

    fun install(store: AttachmentStore) {
        delegate = store
    }

    override fun observeForDebt(debtId: DebtId): Flow<List<AttachmentRecord>> =
        delegate?.observeForDebt(debtId) ?: flowOf(emptyList())

    override suspend fun importAttachment(
        command: AddAttachmentCommand,
        content: InputStream,
    ): AttachmentRecord = delegate?.importAttachment(command, content)
        ?: error("Attachment store is unavailable")

    override suspend fun findById(id: String): AttachmentRecord? = delegate?.findById(id)
}
