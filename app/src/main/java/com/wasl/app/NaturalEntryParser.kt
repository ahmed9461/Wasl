package com.wasl.app

import com.wasl.domain.CurrencyCode
import com.wasl.domain.DebtDirection
import com.wasl.domain.MoneyInputParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal class NaturalEntryParser(
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    fun parse(text: String): NaturalEntryDraft {
        val source = text.trim()
        val normalized = normalizeArabicText(source)
        val referenceDate = today()
        val direction = parseDirection(normalized)
        val person = parsePerson(normalized, direction)
        val currency = parseCurrency(normalized)
        val amountText = parseAmountText(normalized)
        val amountMinorUnits = if (amountText != null && currency != null) {
            try {
                MoneyInputParser.parse(amountText, currency).minorUnits
            } catch (_: IllegalArgumentException) {
                // Invalid precision, grouping or overflow needs user correction,
                // never a truncated amount or an exception on the UI thread.
                null
            }
        } else {
            null
        }
        val entryDate = parseEntryDate(normalized, referenceDate)
        val promisedDate = parsePromisedDate(normalized, referenceDate)
        val kind = when {
            direction != null || normalized.contains("سلفت") || normalized.contains("دين") -> NaturalEntryKind.DEBT
            normalized.contains("دفعت") || normalized.contains("سددت") || normalized.contains("دفع") -> NaturalEntryKind.PAYMENT
            normalized.contains("وعد") -> NaturalEntryKind.PROMISE
            else -> NaturalEntryKind.UNKNOWN
        }
        val missing = buildSet {
            if (person.isNullOrBlank()) add(NaturalDraftField.PERSON)
            if (direction == null) add(NaturalDraftField.DIRECTION)
            if (amountText == null || (currency != null && amountMinorUnits == null)) {
                add(NaturalDraftField.AMOUNT)
            }
            if (currency == null) add(NaturalDraftField.CURRENCY)
        }
        val warnings = buildList {
            if (amountText != null && currency != null && amountMinorUnits == null) {
                add("المبلغ غير صالح لهذه العملة؛ راجع الأرقام والفواصل قبل التأكيد.")
            }
            if (currency != null && amountText == null) {
                add("تعذر تحديد مبلغ واحد؛ اكتب المبلغ بالأرقام متبوعًا بالعملة، مثل 125.50 سعودي.")
            }
            if (currency == null) {
                add("حدد عملة واحدة صراحة: سعودي أو يمني أو دولار.")
            }
            if (kind != NaturalEntryKind.DEBT) {
                add("هذا الإصدار من المحلل المحلي يجهز معاينة الديون فقط؛ لم يتم حفظ أي عملية.")
            }
            if (promisedDate != null && promisedDate.isBefore(entryDate ?: referenceDate)) {
                add("تاريخ الوعد المستخرج يسبق تاريخ العملية ويحتاج مراجعة.")
            }
        }
        return NaturalEntryDraft(
            sourceText = source,
            kind = kind,
            personName = person,
            direction = direction,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            entryDate = entryDate ?: referenceDate,
            promisedDate = promisedDate,
            missingRequiredFields = missing,
            warnings = warnings,
        )
    }

    private fun parseDirection(text: String): DebtDirection? = when {
        Regex("(?:^|\\s)سلفت(?:\\s|$)").containsMatchIn(text) -> DebtDirection.RECEIVABLE
        Regex("(?:^|\\s)لي\\s+عند(?:\\s|$)").containsMatchIn(text) -> DebtDirection.RECEIVABLE
        Regex("(?:^|\\s)علي(?:\\s+|$)").containsMatchIn(text) -> DebtDirection.PAYABLE
        else -> null
    }

    private fun parsePerson(text: String, direction: DebtDirection?): String? {
        val patterns = when (direction) {
            DebtDirection.RECEIVABLE -> listOf(
                Regex("سلفت\\s+([\\p{L}][\\p{L}._-]*)"),
                Regex("لي\\s+عند\\s+([\\p{L}][\\p{L}._-]*)"),
            )
            DebtDirection.PAYABLE -> listOf(
                Regex("علي\\s+ل([\\p{L}][\\p{L}._-]*)"),
                Regex("علي\\s+لـ\\s*([\\p{L}][\\p{L}._-]*)"),
                Regex("علي\\s+([\\p{L}][\\p{L}._-]*)"),
            )
            null -> emptyList()
        }
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseCurrency(text: String): CurrencyCode? {
        // A person's name such as Sara must not be interpreted as SAR, and two
        // different currencies require correction rather than priority guessing.
        val candidates = listOf(
            CurrencyCode.SAR to "(?:sar|ريال\\s+سعودي|سعودي)",
            CurrencyCode.YER to "(?:yer|ريال\\s+يمني|يمني)",
            CurrencyCode.USD to "(?:usd|دولار)",
        ).filter { (_, term) ->
            Regex("(?<![\\p{L}\\p{N}_])$term(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
        }
        return candidates.singleOrNull()?.first
    }

    private val currencyLabel = "(?:sar|yer|usd|(?:ريال\\s+)?(?:سعودي|يمني)|دولار)"
    private val currencyAfterAmount = Regex("^\\s+$currencyLabel(?=$|\\s|[،.,؛])")

    private fun parseAmountText(text: String): String? {
        // Capture the WHOLE numeric token, including separators/sign/exponent,
        // so invalid input cannot silently become a smaller valid prefix.
        // The parser deliberately refuses multiple numeric amounts/dates.
        val numbers = Regex("\\S+").findAll(text)
            .filter { token -> token.value.any { it in '0'..'9' } }.toList()
        if (numbers.isNotEmpty()) {
            val number = numbers.singleOrNull() ?: return null
            // Require the complete amount immediately before its currency.
            // "5 آلاف سعودي" must never become 5 SAR; unsupported mixed
            // word/numeric expressions need an explicit numerical correction.
            if (!currencyAfterAmount.containsMatchIn(text.substring(number.range.last + 1))) return null
            return number.value
        }

        val unit = mapOf(
            "واحد" to 1L,
            "واحدة" to 1L,
            "اثنين" to 2L,
            "اثنان" to 2L,
            "اثنتين" to 2L,
            "ثلاثة" to 3L,
            "ثلاث" to 3L,
            "اربعة" to 4L,
            "اربع" to 4L,
            "خمسة" to 5L,
            "خمس" to 5L,
            "ستة" to 6L,
            "ست" to 6L,
            "سبعة" to 7L,
            "سبع" to 7L,
            "ثمانية" to 8L,
            "ثمان" to 8L,
            "تسعة" to 9L,
            "تسع" to 9L,
            "عشرة" to 10L,
            "عشر" to 10L,
        )
        val thousands = Regex(
            "(?:^|\\s)([\\p{L}]+)\\s+(?:الاف|الف)(?=\\s+$currencyLabel(?=$|\\s|[،.,؛]))",
        ).findAll(text).singleOrNull()
        val word = thousands?.groupValues?.getOrNull(1)?.let(::stripArabicDiacritics)
        return unit[word]?.times(1_000L)?.toString()
    }

    private fun parseEntryDate(text: String, reference: LocalDate): LocalDate? = when {
        Regex("(?:^|\\s)امس(?:\\s|$)").containsMatchIn(text) -> reference.minusDays(1)
        Regex("(?:^|\\s)اليوم(?:\\s|$)").containsMatchIn(text) -> reference
        else -> null
    }

    private fun parsePromisedDate(text: String, reference: LocalDate): LocalDate? {
        if (text.contains("اخر الشهر") || text.contains("نهاية الشهر")) {
            return reference.with(TemporalAdjusters.lastDayOfMonth())
        }
        val weekdays = mapOf(
            "الاثنين" to DayOfWeek.MONDAY,
            "الثلاثاء" to DayOfWeek.TUESDAY,
            "الاربعاء" to DayOfWeek.WEDNESDAY,
            "الخميس" to DayOfWeek.THURSDAY,
            "الجمعة" to DayOfWeek.FRIDAY,
            "السبت" to DayOfWeek.SATURDAY,
            "الاحد" to DayOfWeek.SUNDAY,
        )
        val mentioned = weekdays.entries.firstOrNull { (name, _) -> text.contains(name) } ?: return null
        return reference.with(TemporalAdjusters.next(mentioned.value))
    }

    private fun normalizeArabicText(value: String): String = stripArabicDiacritics(value)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .map { char ->
            when (char) {
                '٠' -> '0'
                '١' -> '1'
                '٢' -> '2'
                '٣' -> '3'
                '٤' -> '4'
                '٥' -> '5'
                '٦' -> '6'
                '٧' -> '7'
                '٨' -> '8'
                '٩' -> '9'
                in '\u06F0'..'\u06F9' -> '0' + (char - '\u06F0')
                else -> char
            }
        }
        .joinToString("")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun stripArabicDiacritics(value: String): String =
        value.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
}
