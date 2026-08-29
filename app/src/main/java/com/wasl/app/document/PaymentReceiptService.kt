package com.wasl.app.document

import android.content.Context
import com.wasl.app.data.DocumentIdentityRecord
import com.wasl.app.data.DocumentTemplateRecord
import com.wasl.app.data.DocumentStatus
import com.wasl.app.data.IssuedDocumentRecord
import com.wasl.app.data.PaymentReceiptSnapshot
import com.wasl.app.data.PaymentReceiptStore
import com.wasl.app.data.PrepareAccountStatementCommand
import com.wasl.app.data.PrepareDebtReceiptCommand
import com.wasl.app.data.PreparePaymentReceiptCommand
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PaymentReceiptService {
    suspend fun getDefaultIdentity(): DocumentIdentityRecord?

    suspend fun getDocumentTemplates(): List<DocumentTemplateRecord> = emptyList()

    suspend fun getDefaultTemplate(): DocumentTemplateRecord? = null

    suspend fun importIdentityBanner(content: InputStream): DocumentBannerAsset

    suspend fun readIdentityBanner(asset: DocumentBannerAsset): ByteArray

    suspend fun issue(command: PreparePaymentReceiptCommand): IssuedDocumentRecord

    suspend fun issueDebtReceipt(command: PrepareDebtReceiptCommand): IssuedDocumentRecord =
        error("Account document service is unavailable.")

    suspend fun issueAccountStatement(
        command: PrepareAccountStatementCommand,
    ): IssuedDocumentRecord = error("Account document service is unavailable.")

    suspend fun retry(documentId: String): IssuedDocumentRecord

    suspend fun retryAccountDocument(documentId: String): IssuedDocumentRecord =
        error("Account document service is unavailable.")
}

object UnavailablePaymentReceiptService : PaymentReceiptService {
    override suspend fun getDefaultIdentity(): DocumentIdentityRecord? = null

    override suspend fun importIdentityBanner(content: InputStream): DocumentBannerAsset =
        error("Document banner service is unavailable.")

    override suspend fun readIdentityBanner(asset: DocumentBannerAsset): ByteArray =
        error("Document banner service is unavailable.")

    override suspend fun issue(command: PreparePaymentReceiptCommand): IssuedDocumentRecord =
        error("Payment receipt service is unavailable.")

    override suspend fun issueDebtReceipt(command: PrepareDebtReceiptCommand): IssuedDocumentRecord =
        error("Account document service is unavailable.")

    override suspend fun issueAccountStatement(
        command: PrepareAccountStatementCommand,
    ): IssuedDocumentRecord = error("Account document service is unavailable.")

    override suspend fun retry(documentId: String): IssuedDocumentRecord =
        error("Payment receipt service is unavailable.")

    override suspend fun retryAccountDocument(documentId: String): IssuedDocumentRecord =
        error("Account document service is unavailable.")
}

class AndroidPaymentReceiptService(
    context: Context,
    private val store: PaymentReceiptStore,
    renderer: PaymentReceiptPdfRenderer? = null,
    private val accountDocumentService: AccountDocumentService = UnavailableAccountDocumentService,
    private val clock: Clock = Clock.systemUTC(),
    private val bannerStore: DocumentBannerAssetStore = AndroidDocumentBannerAssetStore(context.applicationContext),
) : PaymentReceiptService {
    private val filesDir = context.applicationContext.filesDir
    private val renderer: PaymentReceiptPdfRenderer =
        renderer ?: AndroidPaymentReceiptPdfRenderer(bannerStore)

    override suspend fun getDefaultIdentity(): DocumentIdentityRecord? =
        store.getDefaultDocumentIdentity()

    override suspend fun getDocumentTemplates(): List<DocumentTemplateRecord> =
        store.getDocumentTemplates()

    override suspend fun getDefaultTemplate(): DocumentTemplateRecord? =
        store.getDefaultDocumentTemplate()

    override suspend fun importIdentityBanner(content: InputStream): DocumentBannerAsset =
        withContext(Dispatchers.IO) { bannerStore.importImage(content) }

    override suspend fun readIdentityBanner(asset: DocumentBannerAsset): ByteArray =
        withContext(Dispatchers.IO) { bannerStore.readVerified(asset) }

    override suspend fun issue(command: PreparePaymentReceiptCommand): IssuedDocumentRecord {
        val prepared = store.preparePaymentReceipt(command)
        return ensurePdf(prepared)
    }

    override suspend fun issueDebtReceipt(command: PrepareDebtReceiptCommand): IssuedDocumentRecord =
        accountDocumentService.issueDebtReceipt(command)

    override suspend fun issueAccountStatement(
        command: PrepareAccountStatementCommand,
    ): IssuedDocumentRecord = accountDocumentService.issueAccountStatement(command)

    override suspend fun retry(documentId: String): IssuedDocumentRecord {
        val document = requireNotNull(store.getIssuedDocument(documentId)) {
            "Document $documentId was not found."
        }
        return ensurePdf(document)
    }

    override suspend fun retryAccountDocument(documentId: String): IssuedDocumentRecord =
        accountDocumentService.retry(documentId)

    private suspend fun ensurePdf(document: IssuedDocumentRecord): IssuedDocumentRecord =
        withContext(Dispatchers.IO) {
            val snapshot = document.snapshot as? PaymentReceiptSnapshot
                ?: error("Payment receipt service received a non-payment document.")
            val target = ReceiptFileAccess.resolve(filesDir, document.pdfRelativePath)
            if (document.status == DocumentStatus.READY) {
                val expectedHash = requireNotNull(document.pdfSha256)
                check(target.isFile && target.sha256Hex() == expectedHash) {
                    "Ready receipt file is missing or failed its integrity check."
                }
                return@withContext document
            }

            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, ".${target.name}.${document.id}.tmp")
            try {
                if (temporary.exists()) check(temporary.delete()) {
                    "Stale receipt temporary file could not be removed."
                }
                val pageCount = temporary.outputStream().buffered().use { output ->
                    renderer.render(snapshot, output)
                }
                val checksum = temporary.sha256Hex()
                moveAtomically(temporary, target)
                store.markDocumentReady(
                    documentId = document.id,
                    pdfSha256 = checksum,
                    pageCount = pageCount,
                    updatedAt = safeNow(document.issuedAt),
                )
            } catch (error: Exception) {
                if (temporary.exists()) temporary.delete()
                runCatching {
                    store.markDocumentFailed(
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
