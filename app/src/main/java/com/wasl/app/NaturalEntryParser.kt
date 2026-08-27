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
        val majorAmount = parseMajorAmount(normalized)
        val amountMinorUnits = if (majorAmount != null && currency != null) {
            majorToMinor(majorAmount, currency)
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
            if (majorAmount == null) add(NaturalDraftField.AMOUNT)
            if (currency == null) add(NaturalDraftField.CURRENCY)
        }
        val warnings = buildList {
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

    private fun parseCurrency(text: String): CurrencyCode? = when {
        Regex("(?:sar|ريال\\s+سعودي|سعودي)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> CurrencyCode.SAR
        Regex("(?:yer|ريال\\s+يمني|يمني)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> CurrencyCode.YER
        Regex("(?:usd|دولار)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> CurrencyCode.USD
        else -> null
    }

    private fun parseMajorAmount(text: String): Long? {
        val digitMatch = Regex("(?<![\\p{L}])([0-9][0-9,]*)").find(text)
        digitMatch?.groupValues?.getOrNull(1)
            ?.replace(",", "")
            ?.toLongOrNull()
            ?.let { return it }

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
        val thousands = Regex("([\\p{L}]+)\\s+(?:الاف|الاف|آلاف|الف|ألف)").find(text)
        val word = thousands?.groupValues?.getOrNull(1)?.let(::stripArabicDiacritics)
        return unit[word]?.times(1_000L)
    }

    private fun majorToMinor(major: Long, currency: CurrencyCode): Long {
        val fractionDigits = MoneyInputParser.fractionDigits(currency)
        var multiplier = 1L
        repeat(fractionDigits) { multiplier = Math.multiplyExact(multiplier, 10L) }
        return Math.multiplyExact(major, multiplier)
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
        var candidate = reference.with(TemporalAdjusters.nextOrSame(mentioned.value))
        if (candidate == reference && !text.contains("اليوم")) {
            candidate = reference.with(TemporalAdjusters.next(mentioned.value))
        }
        return candidate
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
