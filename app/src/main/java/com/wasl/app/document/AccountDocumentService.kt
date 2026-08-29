package com.wasl.app.document

import android.content.Context
import com.wasl.app.data.AccountDocumentStore
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PrepareAccountStatementCommand
import com.wasl.app.data.PrepareDebtReceiptCommand
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AccountDocumentService {
    suspend fun issueDebtReceipt(command: PrepareDebtReceiptCommand): IssuedDocumentRecord

    suspend fun issueAccountStatement(command: PrepareAccountStatementCommand): IssuedDocumentRecord

    suspend fun retry(documentId: String): IssuedDocumentRecord
}

object UnavailableAccountDocumentService : AccountDocumentService {
    override suspend fun issueDebtReceipt(command: PrepareDebtReceiptCommand): IssuedDocumentRecord =
        error("Account document service is unavailable.")

    override suspend fun issueAccountStatement(
        command: PrepareAccountStatementCommand,
    ): IssuedDocumentRecord = error("Account document service is unavailable.")

    override suspend fun retry(documentId: String): IssuedDocumentRecord =
        error("Account document service is unavailable.")
}

class AndroidAccountDocumentService(
    context: Context,
    private val store: AccountDocumentStore,
    renderer: AccountDocumentPdfRenderer? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val bannerStore: DocumentBannerAssetStore = AndroidDocumentBannerAssetStore(context.applicationContext),
) : AccountDocumentService {
    private val filesDir = context.applicationContext.filesDir
    private val renderer: AccountDocumentPdfRenderer =
        renderer ?: AndroidAccountDocumentPdfRenderer(bannerStore)

    override suspend fun issueDebtReceipt(
        command: PrepareDebtReceiptCommand,
    ): IssuedDocumentRecord = ensurePdf(store.prepareDebtReceipt(command))

    override suspend fun issueAccountStatement(
        command: PrepareAccountStatementCommand,
    ): IssuedDocumentRecord = ensurePdf(store.prepareAccountStatement(command))

    override suspend fun retry(documentId: String): IssuedDocumentRecord {
        val document = requireNotNull(store.getAccountDocument(documentId)) {
            "Document $documentId was not found."
        }
        return ensurePdf(document)
    }

    private suspend fun ensurePdf(document: IssuedDocumentRecord): IssuedDocumentRecord =
        withContext(Dispatchers.IO) {
            val target = ReceiptFileAccess.resolve(filesDir, document.pdfRelativePath)
            if (document.status == DocumentStatus.READY) {
                val expectedHash = requireNotNull(document.pdfSha256)
                check(target.isFile && target.sha256Hex() == expectedHash) {
                    "Ready document file is missing or failed its integrity check."
                }
                return@withContext document
            }

            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, ".${target.name}.${document.id}.tmp")
            try {
                if (temporary.exists()) check(temporary.delete()) {
                    "Stale document temporary file could not be removed."
                }
                val pageCount = temporary.outputStream().buffered().use { output ->
                    renderer.render(document.snapshot, output)
                }
                val checksum = temporary.sha256Hex()
                moveAtomically(temporary, target)
                store.markAccountDocumentReady(
                    documentId = document.id,
                    pdfSha256 = checksum,
                    pageCount = pageCount,
                    updatedAt = safeNow(document.issuedAt),
                )
            } catch (error: Exception) {
                if (temporary.exists()) temporary.delete()
                runCatching {
                    store.markAccountDocumentFailed(
                        documentId = document.id,
                        failureCode = PDF_RENDER_FAILED,
                        updatedAt = safeNow(document.issuedAt),
                    )
                }
                throw error
            }
        }

    private fun safeNow(notBefore: Instant): Instant =
        Instant.now(clock).let { now -> if (now.isBefore(notBefore)) notBefore else now }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val PDF_RENDER_FAILED = "PDF_RENDER_FAILED"
    }
}
