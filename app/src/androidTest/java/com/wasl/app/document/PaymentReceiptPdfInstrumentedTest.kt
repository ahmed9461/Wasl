package com.wasl.app.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wasl.app.data.DocumentIdentitySnapshot
import com.wasl.app.data.PaymentReceiptSnapshot
import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.DebtId
import com.wasl.domain.LedgerEntryId
import com.wasl.domain.Money
import com.wasl.domain.PersonId
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentReceiptPdfInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun arabicReceiptRendersRtlLtrLargeAmountsAndMultiplePages() {
        val outputDirectory = File(context.filesDir, "test-artifacts").apply { mkdirs() }
        val output = File(outputDirectory, "wasl-payment-receipt-sample.pdf")
        val pageCount = output.outputStream().buffered().use { stream ->
            AndroidPaymentReceiptPdfRenderer().render(fixedSnapshot(), stream)
        }

        assertTrue(output.length() > 5_000L)
        assertTrue(pageCount >= 2)
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(pageCount, renderer.pageCount)
                assertPageContainsInk(renderer, 0)
                assertPageContainsInk(renderer, renderer.pageCount - 1)
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

    private fun fixedSnapshot(): PaymentReceiptSnapshot {
        val currency = CurrencyCode.USD
        val longArabicNote = List(180) { index ->
            "سطر توثيقي عربي رقم ${index + 1} مع مرجع LTR-${index + 1} لاختبار اتجاه النص وتتابع الصفحات."
        }.joinToString(" ")
        return PaymentReceiptSnapshot(
            version = 1,
            documentId = "pdf-fixture-document",
            documentNumber = "PAY-2026-00042",
            issuedAt = Instant.parse("2026-08-13T10:15:30Z"),
            issueZoneId = ZoneId.of("Asia/Aden"),
            debtId = DebtId("pdf-fixture-debt"),
            paymentId = LedgerEntryId("pdf-fixture-payment"),
            personId = PersonId("pdf-fixture-person"),
            personName = "شركة النور للتجارة — AL NOOR TRADING",
            direction = DebtDirection.RECEIVABLE,
            originalAmount = Money(999_999_999L, currency),
            balanceBefore = Money(987_654_321L, currency),
            paymentAmount = Money(12_345_678L, currency),
            balanceAfter = Money(975_308_643L, currency),
            paidAt = Instant.parse("2026-08-13T09:45:00Z"),
            paymentNote = longArabicNote,
            debtDescription = "توريد أجهزة ومستلزمات — Contract CN-2026-08",
            identity = DocumentIdentitySnapshot(
                displayName = "مؤسسة أحمد للتجارة",
                activityName = "تجارة عامة وخدمات تقنية",
                phone = "+967 777 000 000",
                footerText = "شكرًا لتعاملكم معنا — Thank you",
            ),
        )
    }
}
