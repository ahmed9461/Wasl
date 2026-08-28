package com.wasl.app.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.AccountStatementSnapshot
import com.wasl.app.data.DebtReceiptSnapshot
import com.wasl.app.data.DocumentIdentitySnapshot
import com.wasl.app.data.StatementEntryType
import com.wasl.app.data.StatementLedgerEntrySnapshot
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDocumentPdfInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val renderer = AndroidAccountDocumentPdfRenderer()

    @Test
    fun debtReceiptRendersAsReadablePdfArtifact() {
        val output = artifact("wasl-debt-receipt-sample.pdf")
        val pageCount = output.outputStream().buffered().use { stream ->
            renderer.render(debtReceiptSnapshot(), stream)
        }

        assertTrue(output.length() > 3_000L)
        assertTrue(pageCount >= 1)
        assertPdfHasInk(output, pageCount)
    }

    @Test
    fun accountStatementRendersMultiplePagesAndKeepsHistoricalEntries() {
        val output = artifact("wasl-account-statement-sample.pdf")
        val pageCount = output.outputStream().buffered().use { stream ->
            renderer.render(accountStatementSnapshot(), stream)
        }

        assertTrue(output.length() > 5_000L)
        assertTrue(pageCount >= 2)
        assertPdfHasInk(output, pageCount)
    }

    private fun artifact(name: String): File =
        File(context.filesDir, "test-artifacts").apply { mkdirs() }.resolve(name)

    private fun assertPdfHasInk(file: File, expectedPages: Int) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                assertEquals(expectedPages, pdf.pageCount)
                assertPageContainsInk(pdf, 0)
                assertPageContainsInk(pdf, pdf.pageCount - 1)
            }
        }
    }

    private fun assertPageContainsInk(renderer: PdfRenderer, pageIndex: Int) {
        renderer.openPage(pageIndex).use { page ->
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            var nonWhitePixels = 0
            for (y in 0 until bitmap.height step 4) {
                for (x in 0 until bitmap.width step 4) {
                    if (bitmap.getPixel(x, y) != Color.WHITE) nonWhitePixels += 1
                }
            }
            bitmap.recycle()
            assertTrue(nonWhitePixels > 100, "Rendered page $pageIndex appears blank.")
        }
    }

    private fun debtReceiptSnapshot(): DebtReceiptSnapshot = DebtReceiptSnapshot(
        version = 1,
        documentId = "debt-pdf-fixture-document",
        documentNumber = "DEBT-2026-00043",
        issuedAt = Instant.parse("2026-08-25T12:30:00Z"),
        issueZoneId = ZoneId.of("Asia/Aden"),
        debtId = DebtId("debt-pdf-fixture-debt"),
        personId = PersonId("debt-pdf-fixture-person"),
        personName = "شركة النور للتجارة — AL NOOR TRADING",
        direction = DebtDirection.RECEIVABLE,
        originalAmount = Money(1_000_000L, CurrencyCode.YER),
        balanceAtIssue = Money(750_000L, CurrencyCode.YER),
        paidAmountAtIssue = Money(250_000L, CurrencyCode.YER),
        openedAt = Instant.parse("2026-07-01T09:00:00Z"),
        dueDate = LocalDate.of(2026, 9, 15),
        debtDescription = "توريد أجهزة ومستلزمات — Contract CN-2026-08",
        identity = fixtureIdentity(),
    )

    private fun accountStatementSnapshot(): AccountStatementSnapshot {
        val entries = List(35) { index ->
            StatementLedgerEntrySnapshot(
                id = LedgerEntryId("statement-payment-${index + 1}"),
                type = StatementEntryType.PAYMENT,
                amount = Money(1_000L, CurrencyCode.YER),
                occurredAt = Instant.parse("2026-08-01T10:00:00Z").plusSeconds(index * 86_400L),
                recordedAt = Instant.parse("2026-08-01T10:05:00Z").plusSeconds(index * 86_400L),
                note = "دفعة كشف رقم ${index + 1} — REF-${index + 1}",
            )
        }
        return AccountStatementSnapshot(
            version = 1,
            documentId = "statement-pdf-fixture-document",
            documentNumber = "STAT-2026-00044",
            issuedAt = Instant.parse("2026-08-25T12:35:00Z"),
            issueZoneId = ZoneId.of("Asia/Aden"),
            debtId = DebtId("statement-pdf-fixture-debt"),
            personId = PersonId("statement-pdf-fixture-person"),
            personName = "شركة النور للتجارة — AL NOOR TRADING",
            direction = DebtDirection.RECEIVABLE,
            originalAmount = Money(1_000_000L, CurrencyCode.YER),
            balanceAtIssue = Money(965_000L, CurrencyCode.YER),
            paidAmountAtIssue = Money(35_000L, CurrencyCode.YER),
            openedAt = Instant.parse("2026-07-01T09:00:00Z"),
            dueDate = LocalDate.of(2026, 9, 15),
            debtDescription = "كشف تاريخي متعدد الصفحات لاختبار الحركات واتجاه النص.",
            entries = entries,
            identity = fixtureIdentity(),
        )
    }

    private fun fixtureIdentity(): DocumentIdentitySnapshot = DocumentIdentitySnapshot(
        displayName = "مؤسسة أحمد للتجارة",
        activityName = "تجارة عامة وخدمات تقنية",
        phone = "+967 777 000 000",
        footerText = "شكرًا لتعاملكم معنا — Thank you",
    )
}
