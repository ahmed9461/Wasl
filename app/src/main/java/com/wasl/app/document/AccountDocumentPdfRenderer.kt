package com.wasl.app.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.BidiFormatter
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.wasl.app.data.AccountStatementSnapshot
import com.wasl.app.data.DebtReceiptSnapshot
import com.wasl.app.data.DocumentSnapshot
import com.wasl.app.data.StatementEntryType
import com.wasl.domain.DebtDirection
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import java.io.OutputStream
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun interface AccountDocumentPdfRenderer {
    fun render(snapshot: DocumentSnapshot, output: OutputStream): Int
}

class AndroidAccountDocumentPdfRenderer(
    private val bannerStore: DocumentBannerAssetStore = UnavailableDocumentBannerAssetStore,
) : AccountDocumentPdfRenderer {
    override fun render(snapshot: DocumentSnapshot, output: OutputStream): Int {
        require(snapshot is DebtReceiptSnapshot || snapshot is AccountStatementSnapshot) {
            "Account document renderer supports debt receipts and statements only."
        }
        val bannerBitmap = snapshot.identity.banner?.let { asset ->
            val bytes = bannerStore.readVerified(asset)
            requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "Document banner could not be decoded."
            }
        }
        val document = PdfDocument()
        return try {
            val writer = Writer(document, snapshot, bannerBitmap)
            when (snapshot) {
                is DebtReceiptSnapshot -> writer.drawDebtReceipt(snapshot)
                is AccountStatementSnapshot -> writer.drawStatement(snapshot)
                else -> error("Unsupported account document snapshot.")
            }
            writer.finish()
            document.writeTo(output)
            writer.pageCount
        } finally {
            document.close()
        }
    }

    private class Writer(
        private val document: PdfDocument,
        private val snapshot: DocumentSnapshot,
        private val bannerBitmap: Bitmap?,
    ) {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        var pageCount = 0
            private set

        private val regular = textPaint(11.5f)
        private val small = textPaint(9.5f, COLOR_MUTED)
        private val label = textPaint(10.5f, COLOR_MUTED)
        private val value = textPaint(11.5f, Color.BLACK, Typeface.BOLD)
        private val section = textPaint(13.5f, COLOR_PRIMARY, Typeface.BOLD)
        private val title = textPaint(21f, COLOR_PRIMARY, Typeface.BOLD)
        private val brand = textPaint(18f, COLOR_PRIMARY, Typeface.BOLD)

        fun drawDebtReceipt(receipt: DebtReceiptSnapshot) {
            startPage("إيصال دين")
            keyValue("رقم المستند", receipt.documentNumber, ltr = true)
            keyValue("تاريخ الإصدار", formatInstant(receipt.issuedAt), ltr = true)
            rule()
            section("بيانات الحساب")
            keyValue("الطرف", receipt.personName)
            keyValue("اتجاه الحساب", directionLabel(receipt.direction))
            keyValue("تاريخ فتح الحساب", formatInstant(receipt.openedAt), ltr = true)
            receipt.dueDate?.let { keyValue("تاريخ الاستحقاق", formatDate(it), ltr = true) }
            receipt.debtDescription?.let { paragraph("البيان", it) }
            rule()
            section("المبالغ")
            keyValue("أصل الدين", formatMoney(receipt.originalAmount), ltr = true)
            keyValue("المسدّد حتى الإصدار", formatMoney(receipt.paidAmountAtIssue), ltr = true)
            keyValue("المتبقي", formatMoney(receipt.balanceAtIssue), ltr = true, emphasized = true)
            rule()
            drawIdentity(receipt.identity.displayName, receipt.identity.activityName, receipt.identity.phone, receipt.identity.footerText)
            disclaimer("هذا المستند سجل شخصي صادر من تطبيق وَصل استنادًا إلى بيانات الحساب المحلية وقت الإصدار، ولا يمثل ضمانًا قانونيًا أو مصرفيًا مستقلًا.")
        }

        fun drawStatement(statement: AccountStatementSnapshot) {
            startPage("كشف حساب")
            keyValue("رقم المستند", statement.documentNumber, ltr = true)
            keyValue("تاريخ الإصدار", formatInstant(statement.issuedAt), ltr = true)
            keyValue("الطرف", statement.personName)
            keyValue("اتجاه الحساب", directionLabel(statement.direction))
            statement.dueDate?.let { keyValue("الاستحقاق", formatDate(it), ltr = true) }
            statement.debtDescription?.let { paragraph("البيان", it) }
            rule()
            section("ملخص الحساب")
            keyValue("أصل الدين", formatMoney(statement.originalAmount), ltr = true)
            keyValue("صافي المسدّد", formatMoney(statement.paidAmountAtIssue), ltr = true)
            keyValue("الرصيد المتبقي", formatMoney(statement.balanceAtIssue), ltr = true, emphasized = true)
            rule()
            section("الحركات")
            if (statement.entries.isEmpty()) {
                paragraph(null, "لا توجد دفعات أو عمليات عكس مسجلة حتى وقت إصدار الكشف.")
            } else {
                statement.entries.forEachIndexed { index, entry ->
                    val body = when (entry.type) {
                        StatementEntryType.PAYMENT -> buildString {
                            append("${index + 1}. سداد — ${formatMoney(requireNotNull(entry.amount))}")
                            append(" — ${formatInstant(requireNotNull(entry.occurredAt))}")
                            entry.note?.let { append(" — $it") }
                        }
                        StatementEntryType.PAYMENT_REVERSAL -> buildString {
                            append("${index + 1}. عكس دفعة")
                            append(" — ${formatInstant(entry.recordedAt)}")
                            entry.reason?.let { append(" — $it") }
                        }
                    }
                    paragraph(null, body)
                }
            }
            rule()
            drawIdentity(statement.identity.displayName, statement.identity.activityName, statement.identity.phone, statement.identity.footerText)
            disclaimer("كشف الحساب Snapshot تاريخي من دفتر وَصل المحلي وقت الإصدار. تعديل البيانات لاحقًا لا يغيّر هذه النسخة.")
        }

        fun finish() = finishPage()

        private fun drawIdentity(
            displayName: String,
            activityName: String?,
            phone: String?,
            footerText: String?,
        ) {
            section("هوية مُصدر المستند")
            keyValue("الاسم", displayName)
            activityName?.let { keyValue("النشاط", it) }
            phone?.let { keyValue("الهاتف", it, ltr = true) }
            footerText?.let { paragraph("العبارة", it) }
            spacer(6f)
        }

        private fun disclaimer(text: String) {
            paragraph(null, text, small)
        }

        private fun startPage(documentTitle: String? = null) {
            pageCount += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create(),
            )
            canvas = requireNotNull(page).canvas.apply { drawColor(Color.WHITE) }
            if (pageCount == 1 && bannerBitmap != null) {
                drawBanner(bannerBitmap)
                if (documentTitle != null) {
                    drawText(documentTitle, MARGIN, 128f, CONTENT_WIDTH, title, false)
                }
                canvas?.drawLine(
                    MARGIN, 166f, PAGE_WIDTH - MARGIN, 166f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY; strokeWidth = 2f },
                )
                y = 186f
            } else {
                drawText(
                    if (pageCount == 1) "وَصل" else "وَصل — تابع",
                    MARGIN, 40f, CONTENT_WIDTH, brand, false,
                )
                if (pageCount == 1 && documentTitle != null) {
                    drawText(documentTitle, MARGIN, 70f, CONTENT_WIDTH, title, false)
                }
                canvas?.drawLine(
                    MARGIN, 110f, PAGE_WIDTH - MARGIN, 110f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY; strokeWidth = 2f },
                )
                y = 132f
            }
        }

        private fun drawBanner(bitmap: Bitmap) {
            val maxWidth = CONTENT_WIDTH.toFloat()
            val maxHeight = 82f
            val scale = minOf(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat())
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = MARGIN + (maxWidth - width) / 2f
            val top = 28f + (maxHeight - height) / 2f
            canvas?.drawBitmap(
                bitmap, null, RectF(left, top, left + width, top + height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }

        private fun finishPage() {
            val current = page ?: return
            val targetCanvas = requireNotNull(canvas)
            targetCanvas.drawLine(
                MARGIN,
                FOOTER_TOP,
                PAGE_WIDTH - MARGIN,
                FOOTER_TOP,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_RULE
                    strokeWidth = 1f
                },
            )
            drawText(
                "${snapshot.documentNumber}   |   صفحة $pageCount",
                MARGIN,
                FOOTER_TOP + 8f,
                CONTENT_WIDTH,
                small,
                false,
            )
            document.finishPage(current)
            page = null
            canvas = null
        }

        private fun newPage() {
            finishPage()
            startPage()
        }

        private fun section(text: String) {
            val layout = layout(text, section, CONTENT_WIDTH, false)
            ensureSpace(layout.height + 9f)
            draw(layout, MARGIN, y)
            y += layout.height + 9f
        }

        private fun keyValue(
            labelText: String,
            valueText: String,
            ltr: Boolean = false,
            emphasized: Boolean = false,
        ) {
            val labelLayout = layout(labelText, label, LABEL_WIDTH, false)
            val selectedValue = if (emphasized) TextPaint(value).apply {
                textSize = 13.5f
                color = COLOR_PRIMARY
            } else value
            val valueLayout = layout(valueText, selectedValue, VALUE_WIDTH, ltr)
            val height = maxOf(labelLayout.height, valueLayout.height).toFloat()
            ensureSpace(height + 10f)
            draw(labelLayout, LABEL_X, y)
            draw(valueLayout, VALUE_X, y)
            y += height + 10f
        }

        private fun paragraph(labelText: String?, text: String, paint: TextPaint = regular) {
            labelText?.let {
                val header = layout(it, label, CONTENT_WIDTH, false)
                ensureSpace(header.height + 5f)
                draw(header, MARGIN, y)
                y += header.height + 5f
            }
            var remaining = text.trim()
            while (remaining.isNotEmpty()) {
                val available = CONTENT_BOTTOM - y
                if (available < paint.textSize * 2.2f) {
                    newPage()
                    continue
                }
                val full = layout(remaining, paint, CONTENT_WIDTH, false)
                if (full.height <= available) {
                    draw(full, MARGIN, y)
                    y += full.height + 9f
                    remaining = ""
                } else {
                    val fitting = (0 until full.lineCount)
                        .lastOrNull { full.getLineBottom(it) <= available }
                    if (fitting == null) {
                        newPage()
                    } else {
                        val splitAt = full.getLineEnd(fitting)
                        val chunk = remaining.substring(0, splitAt).trimEnd()
                        draw(layout(chunk, paint, CONTENT_WIDTH, false), MARGIN, y)
                        remaining = remaining.substring(splitAt).trimStart()
                        newPage()
                    }
                }
            }
        }

        private fun rule() {
            ensureSpace(18f)
            canvas?.drawLine(
                MARGIN,
                y + 3f,
                PAGE_WIDTH - MARGIN,
                y + 3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_RULE
                    strokeWidth = 1f
                },
            )
            y += 18f
        }

        private fun spacer(height: Float) {
            ensureSpace(height)
            y += height
        }

        private fun ensureSpace(required: Float) {
            if (y + required > CONTENT_BOTTOM) newPage()
        }

        private fun drawText(
            text: String,
            x: Float,
            top: Float,
            width: Int,
            paint: TextPaint,
            ltr: Boolean,
        ) = draw(layout(text, paint, width, ltr), x, top)

        private fun draw(layout: StaticLayout, x: Float, top: Float) {
            val target = requireNotNull(canvas)
            target.save()
            target.translate(x, top)
            layout.draw(target)
            target.restore()
        }

        private fun layout(
            text: String,
            paint: TextPaint,
            width: Int,
            ltr: Boolean,
        ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(
                if (ltr) TextDirectionHeuristics.LTR else TextDirectionHeuristics.FIRSTSTRONG_RTL,
            )
            .setIncludePad(false)
            .setLineSpacing(2f, 1.08f)
            .build()

        private fun formatMoney(money: Money): String {
            val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
            val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
            val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
                isGroupingUsed = true
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }
            return bidiLtr("${formatter.format(major)} ${money.currency.value}")
        }

        private fun formatInstant(instant: Instant): String {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu - HH:mm", Locale.US)
            return bidiLtr(formatter.format(instant.atZone(snapshot.issueZoneId)))
        }

        private fun formatDate(date: LocalDate): String =
            bidiLtr(DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.US).format(date))

        private fun bidiLtr(value: String): String =
            BidiFormatter.getInstance(Locale.forLanguageTag("ar")).unicodeWrap(
                value,
                TextDirectionHeuristics.LTR,
            )

        private fun directionLabel(direction: DebtDirection): String = when (direction) {
            DebtDirection.RECEIVABLE -> "لي عنده"
            DebtDirection.PAYABLE -> "عليّ له"
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val CONTENT_WIDTH = 499
        const val CONTENT_BOTTOM = 770f
        const val FOOTER_TOP = 790f
        const val LABEL_X = 360f
        const val VALUE_X = 48f
        const val LABEL_WIDTH = 187
        const val VALUE_WIDTH = 300
        const val COLOR_PRIMARY = 0xFF285C4D.toInt()
        const val COLOR_MUTED = 0xFF5F6B66.toInt()
        const val COLOR_RULE = 0xFFD6DDDA.toInt()

        fun textPaint(
            size: Float,
            color: Int = Color.BLACK,
            weight: Int = Typeface.NORMAL,
        ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, weight)
        }
    }
}
