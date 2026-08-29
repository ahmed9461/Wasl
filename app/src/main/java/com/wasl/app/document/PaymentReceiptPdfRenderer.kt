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
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.wasl.app.data.PaymentReceiptSnapshot
import com.wasl.domain.DebtDirection
import com.wasl.domain.Money
import com.wasl.domain.MoneyInputParser
import java.io.OutputStream
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

fun interface PaymentReceiptPdfRenderer {
    fun render(snapshot: PaymentReceiptSnapshot, output: OutputStream): Int
}

class AndroidPaymentReceiptPdfRenderer(
    private val bannerStore: DocumentBannerAssetStore = UnavailableDocumentBannerAssetStore,
) : PaymentReceiptPdfRenderer {
    override fun render(snapshot: PaymentReceiptSnapshot, output: OutputStream): Int {
        val bannerBitmap = snapshot.identity.banner?.let { asset ->
            val bytes = bannerStore.readVerified(asset)
            requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "Document banner could not be decoded."
            }
        }
        val document = PdfDocument()
        return try {
            val writer = ReceiptPageWriter(document, snapshot, bannerBitmap)
            writer.drawReceipt()
            writer.finish()
            document.writeTo(output)
            writer.pageCount
        } finally {
            document.close()
        }
    }

    private class ReceiptPageWriter(
        private val document: PdfDocument,
        private val snapshot: PaymentReceiptSnapshot,
        private val bannerBitmap: Bitmap?,
    ) {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        var pageCount: Int = 0
            private set

        private val regular = textPaint(size = 12f)
        private val secondary = textPaint(size = 10f, color = COLOR_MUTED)
        private val label = textPaint(size = 11f, color = COLOR_MUTED)
        private val value = textPaint(size = 12f, weight = Typeface.BOLD)
        private val section = textPaint(size = 14f, weight = Typeface.BOLD, color = COLOR_PRIMARY)
        private val title = textPaint(size = 23f, weight = Typeface.BOLD, color = COLOR_PRIMARY)
        private val brand = textPaint(size = 18f, weight = Typeface.BOLD, color = COLOR_PRIMARY)

        fun drawReceipt() {
            startPage()
            keyValue("رقم المستند", snapshot.documentNumber, TextDirectionHeuristics.LTR)
            keyValue("تاريخ الإصدار", formatInstant(snapshot.issuedAt), TextDirectionHeuristics.LTR)
            horizontalRule()

            sectionTitle("بيانات الإيصال")
            keyValue("الطرف", snapshot.personName, TextDirectionHeuristics.FIRSTSTRONG_RTL)
            keyValue(
                "اتجاه الحساب",
                when (snapshot.direction) {
                    DebtDirection.RECEIVABLE -> "لي عنده"
                    DebtDirection.PAYABLE -> "عليّ له"
                },
                TextDirectionHeuristics.FIRSTSTRONG_RTL,
            )
            snapshot.debtDescription?.let { paragraph("بيان الدين", it) }
            horizontalRule()

            sectionTitle("تفاصيل السداد")
            keyValue("أصل الدين", formatMoney(snapshot.originalAmount), TextDirectionHeuristics.LTR)
            keyValue("الرصيد قبل الدفعة", formatMoney(snapshot.balanceBefore), TextDirectionHeuristics.LTR)
            keyValue("مبلغ السداد", formatMoney(snapshot.paymentAmount), TextDirectionHeuristics.LTR, emphasized = true)
            keyValue("المتبقي بعد الدفعة", formatMoney(snapshot.balanceAfter), TextDirectionHeuristics.LTR)
            keyValue("وقت السداد", formatInstant(snapshot.paidAt), TextDirectionHeuristics.LTR)
            snapshot.paymentNote?.let { paragraph("بيان الدفعة", it) }
            horizontalRule()

            sectionTitle("هوية مُصدر الإيصال")
            keyValue("الاسم", snapshot.identity.displayName, TextDirectionHeuristics.FIRSTSTRONG_RTL)
            snapshot.identity.activityName?.let {
                keyValue("النشاط", it, TextDirectionHeuristics.FIRSTSTRONG_RTL)
            }
            snapshot.identity.phone?.let {
                keyValue("الهاتف", it, TextDirectionHeuristics.LTR)
            }
            snapshot.identity.footerText?.let { paragraph("عبارة الإيصال", it) }
            spacer(10f)
            paragraph(
                label = null,
                text = "هذا الإيصال سجل شخصي صادر من تطبيق وَصل استنادًا إلى عملية محلية محفوظة، ولا يمثل تحويلًا مصرفيًا أو ضمانًا قانونيًا مستقلًا.",
                paint = secondary,
            )
        }

        fun finish() {
            finishPage()
        }

        private fun startPage() {
            pageCount += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCount).create(),
            )
            canvas = requireNotNull(page).canvas.apply { drawColor(Color.WHITE) }
            if (pageCount == 1 && bannerBitmap != null) {
                drawBanner(bannerBitmap)
                drawLayout(
                    text = "إيصال سداد",
                    x = MARGIN,
                    top = 128f,
                    width = CONTENT_WIDTH,
                    paint = title,
                    direction = TextDirectionHeuristics.FIRSTSTRONG_RTL,
                )
                canvas?.drawLine(
                    MARGIN, 166f, PAGE_WIDTH - MARGIN, 166f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY; strokeWidth = 2f },
                )
                y = 186f
            } else {
                drawLayout(
                    text = if (pageCount == 1) "وَصل" else "وَصل — تابع",
                    x = MARGIN,
                    top = 42f,
                    width = CONTENT_WIDTH,
                    paint = brand,
                    direction = TextDirectionHeuristics.FIRSTSTRONG_RTL,
                )
                if (pageCount == 1) {
                    drawLayout(
                        text = "إيصال سداد",
                        x = MARGIN,
                        top = 72f,
                        width = CONTENT_WIDTH,
                        paint = title,
                        direction = TextDirectionHeuristics.FIRSTSTRONG_RTL,
                    )
                }
                canvas?.drawLine(
                    MARGIN, 112f, PAGE_WIDTH - MARGIN, 112f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_PRIMARY; strokeWidth = 2f },
                )
                y = 134f
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
                bitmap,
                null,
                RectF(left, top, left + width, top + height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }

        private fun finishPage() {
            val currentPage = page ?: return
            val currentCanvas = requireNotNull(canvas)
            currentCanvas.drawLine(
                MARGIN,
                FOOTER_TOP,
                PAGE_WIDTH - MARGIN,
                FOOTER_TOP,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_RULE
                    strokeWidth = 1f
                },
            )
            drawLayout(
                text = "${snapshot.documentNumber}   |   صفحة $pageCount",
                x = MARGIN,
                top = FOOTER_TOP + 9f,
                width = CONTENT_WIDTH,
                paint = secondary,
                direction = TextDirectionHeuristics.FIRSTSTRONG_RTL,
            )
            document.finishPage(currentPage)
            page = null
            canvas = null
        }

        private fun newPage() {
            finishPage()
            startPage()
        }

        private fun sectionTitle(text: String) {
            val layout = layout(text, section, CONTENT_WIDTH, TextDirectionHeuristics.FIRSTSTRONG_RTL)
            ensureSpace(layout.height + 10f)
            draw(layout, MARGIN, y)
            y += layout.height + 10f
        }

        private fun keyValue(
            labelText: String,
            valueText: String,
            valueDirection: TextDirectionHeuristic,
            emphasized: Boolean = false,
        ) {
            val labelLayout = layout(
                labelText,
                label,
                LABEL_WIDTH,
                TextDirectionHeuristics.FIRSTSTRONG_RTL,
            )
            val valueLayout = layout(
                valueText,
                if (emphasized) value.copy(size = 14f, color = COLOR_PRIMARY) else value,
                VALUE_WIDTH,
                valueDirection,
            )
            val height = maxOf(labelLayout.height, valueLayout.height).toFloat()
            ensureSpace(height + 12f)
            draw(labelLayout, LABEL_X, y)
            draw(valueLayout, VALUE_X, y)
            y += height + 12f
        }

        private fun paragraph(
            label: String?,
            text: String,
            paint: TextPaint = regular,
        ) {
            label?.let { sectionLabel ->
                val labelLayout = layout(
                    sectionLabel,
                    this.label,
                    CONTENT_WIDTH,
                    TextDirectionHeuristics.FIRSTSTRONG_RTL,
                )
                ensureSpace(labelLayout.height + 6f)
                draw(labelLayout, MARGIN, y)
                y += labelLayout.height + 6f
            }

            var remaining = text.trim()
            while (remaining.isNotEmpty()) {
                val available = CONTENT_BOTTOM - y
                if (available < paint.textSize * 2.2f) {
                    newPage()
                    continue
                }
                val fullLayout = layout(
                    remaining,
                    paint,
                    CONTENT_WIDTH,
                    TextDirectionHeuristics.FIRSTSTRONG_RTL,
                )
                if (fullLayout.height <= available) {
                    draw(fullLayout, MARGIN, y)
                    y += fullLayout.height + 12f
                    remaining = ""
                } else {
                    val lastFittingLine = (0 until fullLayout.lineCount)
                        .lastOrNull { line -> fullLayout.getLineBottom(line) <= available }
                    if (lastFittingLine == null) {
                        newPage()
                        continue
                    }
                    val splitAt = fullLayout.getLineEnd(lastFittingLine)
                    val chunk = remaining.substring(0, splitAt).trimEnd()
                    val chunkLayout = layout(
                        chunk,
                        paint,
                        CONTENT_WIDTH,
                        TextDirectionHeuristics.FIRSTSTRONG_RTL,
                    )
                    draw(chunkLayout, MARGIN, y)
                    remaining = remaining.substring(splitAt).trimStart()
                    newPage()
                }
            }
        }

        private fun horizontalRule() {
            ensureSpace(22f)
            canvas?.drawLine(
                MARGIN,
                y + 4f,
                PAGE_WIDTH - MARGIN,
                y + 4f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COLOR_RULE
                    strokeWidth = 1f
                },
            )
            y += 22f
        }

        private fun spacer(height: Float) {
            ensureSpace(height)
            y += height
        }

        private fun ensureSpace(requiredHeight: Float) {
            if (y + requiredHeight > CONTENT_BOTTOM) newPage()
        }

        private fun drawLayout(
            text: String,
            x: Float,
            top: Float,
            width: Int,
            paint: TextPaint,
            direction: TextDirectionHeuristic,
        ) {
            draw(layout(text, paint, width, direction), x, top)
        }

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
            direction: TextDirectionHeuristic,
        ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(direction)
            .setIncludePad(false)
            .setLineSpacing(2f, 1.08f)
            .build()

        private fun TextPaint.copy(
            size: Float = textSize,
            color: Int = this.color,
        ): TextPaint = TextPaint(this).apply {
            textSize = size
            this.color = color
        }

        private fun formatMoney(money: Money): String {
            val fractionDigits = MoneyInputParser.fractionDigits(money.currency)
            val major = BigDecimal.valueOf(money.minorUnits, fractionDigits)
            val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
                isGroupingUsed = true
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }
            val raw = "${formatter.format(major)} ${money.currency.value}"
            return BidiFormatter.getInstance(Locale.forLanguageTag("ar")).unicodeWrap(
                raw,
                TextDirectionHeuristics.LTR,
            )
        }

        private fun formatInstant(instant: java.time.Instant): String {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu - HH:mm", Locale.US)
            val raw = formatter.format(instant.atZone(snapshot.issueZoneId))
            return BidiFormatter.getInstance(Locale.forLanguageTag("ar")).unicodeWrap(
                raw,
                TextDirectionHeuristics.LTR,
            )
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
        const val LABEL_WIDTH = 187
        const val VALUE_X = 48f
        const val VALUE_WIDTH = 292
        const val COLOR_PRIMARY = 0xFF175C55.toInt()
        const val COLOR_MUTED = 0xFF54645F.toInt()
        const val COLOR_RULE = 0xFFD6E1DE.toInt()

        fun textPaint(
            size: Float,
            weight: Int = Typeface.NORMAL,
            color: Int = Color.BLACK,
        ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", weight)
        }
    }
}
