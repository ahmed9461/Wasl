package com.wasl.app.data.local

import android.content.Context
import com.wasl.app.data.AddAttachmentCommand
import com.wasl.app.data.AttachmentIntegrity
import com.wasl.app.data.AttachmentRecord
import com.wasl.app.data.AttachmentStore
import com.wasl.app.data.local.entity.AttachmentEntity
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomAttachmentStore(
    context: Context,
    private val database: WaslDatabase,
) : AttachmentStore {
    private val root = File(context.applicationContext.filesDir, ATTACHMENT_DIRECTORY)

    override fun observeForDebt(debtId: DebtId): Flow<List<AttachmentRecord>> =
        database.attachmentDao().observeForDebt(debtId.value)
            .map { rows -> rows.map(::toRecordWithIntegrity) }
            .flowOn(Dispatchers.IO)

    override suspend fun findById(id: String): AttachmentRecord? = withContext(Dispatchers.IO) {
        database.attachmentDao().findById(id)?.let(::toRecordWithIntegrity)
    }

    override suspend fun importAttachment(
        command: AddAttachmentCommand,
        content: InputStream,
    ): AttachmentRecord = withContext(Dispatchers.IO) {
        require(command.id.isNotBlank()) { "Attachment id is required" }
        require(command.displayName.trim().isNotBlank()) { "Display name is required" }
        require(command.mimeType.trim().isNotBlank()) { "MIME type is required" }
        require(database.debtDao().findAggregateById(command.debtId.value) != null) {
            "Debt does not exist"
        }
        command.ledgerEntryId?.let { ledgerId ->
            val row = database.ledgerDao().findById(ledgerId.value)
                ?: error("Ledger entry does not exist")
            require(row.debtId == command.debtId.value) {
                "Ledger entry belongs to another debt"
            }
        }

        root.mkdirs()
        val relativePath = "$ATTACHMENT_DIRECTORY/${command.id}.blob"
        require(database.attachmentDao().countForRelativePath(relativePath) == 0) {
            "Attachment path already exists"
        }
        val finalFile = safeResolve(relativePath)
        val tempFile = File(root, ".${command.id}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            tempFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = content.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    require(total <= MAX_ATTACHMENT_BYTES) { "Attachment is larger than 25 MB" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            require(total > 0L) { "Attachment is empty" }
            if (finalFile.exists()) error("Attachment file already exists")
            require(tempFile.renameTo(finalFile)) { "Unable to finalize attachment file" }

            val entity = AttachmentEntity(
                id = command.id,
                debtId = command.debtId.value,
                ledgerEntryId = command.ledgerEntryId?.value,
                displayName = command.displayName.trim().take(MAX_DISPLAY_NAME_LENGTH),
                mimeType = command.mimeType.trim().take(MAX_MIME_LENGTH),
                sizeBytes = total,
                relativePath = relativePath,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                createdAt = command.createdAt.toEpochMilli(),
                note = command.note?.trim()?.takeIf(String::isNotBlank)?.take(MAX_NOTE_LENGTH),
            )
            try {
                database.attachmentDao().insert(entity)
            } catch (error: Throwable) {
                finalFile.delete()
                throw error
            }
            toRecordWithIntegrity(entity)
        } finally {
            tempFile.delete()
        }
    }

    private fun toRecordWithIntegrity(entity: AttachmentEntity): AttachmentRecord {
        val file = runCatching { safeResolve(entity.relativePath) }.getOrNull()
        val integrity = when {
            file == null || !file.isFile -> AttachmentIntegrity.MISSING
            file.length() != entity.sizeBytes -> AttachmentIntegrity.HASH_MISMATCH
            sha256(file) != entity.sha256 -> AttachmentIntegrity.HASH_MISMATCH
            else -> AttachmentIntegrity.OK
        }
        return AttachmentRecord(
            id = entity.id,
            debtId = DebtId(entity.debtId),
            ledgerEntryId = entity.ledgerEntryId?.let(::LedgerEntryId),
            displayName = entity.displayName,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            relativePath = entity.relativePath,
            sha256 = entity.sha256,
            createdAt = java.time.Instant.ofEpochMilli(entity.createdAt),
            note = entity.note,
            integrity = integrity,
        )
    }

    private fun safeResolve(relativePath: String): File {
        require(relativePath.startsWith("$ATTACHMENT_DIRECTORY/")) { "Unsafe attachment path" }
        val rootCanonical = root.canonicalFile
        val candidate = File(root.parentFile, relativePath).canonicalFile
        require(candidate.parentFile == rootCanonical) { "Unsafe attachment path" }
        return candidate
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val ATTACHMENT_DIRECTORY = "attachments"
        const val MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L
        private const val MAX_DISPLAY_NAME_LENGTH = 240
        private const val MAX_MIME_LENGTH = 160
        private const val MAX_NOTE_LENGTH = 2_000
    }
}
